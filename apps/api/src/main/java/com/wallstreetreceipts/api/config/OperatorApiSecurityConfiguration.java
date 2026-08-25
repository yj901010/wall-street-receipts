package com.wallstreetreceipts.api.config;

import java.net.InetAddress;
import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.web.security.ApiRequestRejectedHandler;
import com.wallstreetreceipts.api.web.security.OperatorApiSecurityProblemWriter;
import com.wallstreetreceipts.api.web.security.OperatorBearerTokenAuthenticationFilter;
import com.wallstreetreceipts.api.web.security.OperatorBearerTokenAuthenticationProvider;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OperatorApiProperties.class)
public class OperatorApiSecurityConfiguration {

    public static final String OPERATOR_AUTHORITY = "OPERATOR";
    private static final RequestMatcher OPERATOR_API =
            PathPatternRequestMatcher.withDefaults().matcher("/internal/v1/sec/**");

    @Bean
    AuthenticationProvider operatorBearerTokenAuthenticationProvider(
            OperatorApiProperties properties) {
        return new OperatorBearerTokenAuthenticationProvider(properties, OPERATOR_AUTHORITY);
    }

    @Bean
    WebServerFactoryCustomizer<ConfigurableWebServerFactory> operatorApiLoopbackOnlyCustomizer(
            OperatorApiProperties properties) {
        return new OperatorApiLoopbackOnlyCustomizer(properties.enabled());
    }

    @Bean
    OperatorApiSecurityProblemWriter operatorApiSecurityProblemWriter(
            Clock clock,
            ObjectMapper objectMapper) {
        return new OperatorApiSecurityProblemWriter(clock, objectMapper);
    }

    @Bean
    ApiRequestRejectedHandler apiRequestRejectedHandler(
            Clock clock,
            ObjectMapper objectMapper) {
        return new ApiRequestRejectedHandler(clock, objectMapper);
    }

    @Bean
    WebSecurityCustomizer requestRejectedHandlerCustomizer(
            ApiRequestRejectedHandler requestRejectedHandler) {
        return web -> web.requestRejectedHandler(requestRejectedHandler);
    }

    @Bean
    @Order(1)
    @ConditionalOnProperty(
            prefix = "app.operator-api",
            name = "enabled",
            havingValue = "true")
    SecurityFilterChain operatorApiSecurityFilterChain(
            HttpSecurity http,
            AuthenticationProvider operatorBearerTokenAuthenticationProvider,
            OperatorApiSecurityProblemWriter problemWriter) throws Exception {
        http
                .securityMatcher(OPERATOR_API)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemWriter)
                        .accessDeniedHandler(problemWriter))
                .authenticationProvider(operatorBearerTokenAuthenticationProvider)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasAuthority(OPERATOR_AUTHORITY))
                .addFilterBefore(
                        new OperatorBearerTokenAuthenticationFilter(
                                OPERATOR_API,
                                operatorBearerTokenAuthenticationProvider),
                        AnonymousAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain publicApiSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }

    static final class OperatorApiLoopbackOnlyCustomizer
            implements WebServerFactoryCustomizer<ConfigurableWebServerFactory>, Ordered {

        private final boolean enabled;

        OperatorApiLoopbackOnlyCustomizer(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public void customize(ConfigurableWebServerFactory factory) {
            if (enabled) {
                factory.setAddress(InetAddress.getLoopbackAddress());
            }
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}
