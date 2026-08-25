package com.wallstreetreceipts.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

class OperatorApiPropertiesTest {

    private static final String TOKEN_SHA_256 =
            "905f28def18eaac05ae6f12b2c3452744afaf626da1343d57b395b544e0519b6";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void operatorApiIsDisabledWithoutCredentialsByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(OperatorApiProperties.class).enabled()).isFalse();
        });
    }

    @Test
    void enabledOperatorApiAcceptsLowercaseNonZeroSha256Digest() {
        contextRunner
                .withPropertyValues(
                        "app.operator-api.enabled=true",
                        "app.operator-api.token-sha256=" + TOKEN_SHA_256)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OperatorApiProperties.class).enabled()).isTrue();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "905F28DEF18EAAC05AE6F12B2C3452744AFAF626DA1343D57B395B544E0519B6",
            "905f28def18eaac05ae6f12b2c3452744afaf626da1343d57b395b544e0519b",
            "g05f28def18eaac05ae6f12b2c3452744afaf626da1343d57b395b544e0519b6",
            "0000000000000000000000000000000000000000000000000000000000000000"
    })
    void enabledOperatorApiFailsStartupForMissingOrInvalidDigest(String digest) {
        contextRunner
                .withPropertyValues(
                        "app.operator-api.enabled=true",
                        "app.operator-api.token-sha256=" + digest)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "OPERATOR_API_TOKEN_SHA256 must be a non-zero SHA-256 digest "
                                            + "encoded as exactly 64 lowercase hexadecimal characters "
                                            + "when OPERATOR_API_ENABLED is true");
                });
    }

    @Test
    void enabledOperatorApiFailsStartupWhenDigestPropertyIsAbsent() {
        contextRunner
                .withPropertyValues("app.operator-api.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test
    void propertiesStringRepresentationRedactsDigest() {
        OperatorApiProperties properties = new OperatorApiProperties(true, TOKEN_SHA_256);

        assertThat(properties.toString())
                .doesNotContain(TOKEN_SHA_256)
                .contains("tokenSha256=<redacted>");
    }

    @Test
    void enabledCustomizerRunsLastAndForcesLoopbackAddress() {
        OperatorApiSecurityConfiguration.OperatorApiLoopbackOnlyCustomizer customizer =
                new OperatorApiSecurityConfiguration.OperatorApiLoopbackOnlyCustomizer(true);
        ConfigurableWebServerFactory factory =
                org.mockito.Mockito.mock(ConfigurableWebServerFactory.class);

        customizer.customize(factory);

        ArgumentCaptor<InetAddress> address = ArgumentCaptor.forClass(InetAddress.class);
        org.mockito.Mockito.verify(factory).setAddress(address.capture());
        assertThat(address.getValue().isLoopbackAddress()).isTrue();
        assertThat(customizer.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    void disabledCustomizerDoesNotChangeServerAddress() {
        OperatorApiSecurityConfiguration.OperatorApiLoopbackOnlyCustomizer customizer =
                new OperatorApiSecurityConfiguration.OperatorApiLoopbackOnlyCustomizer(false);
        ConfigurableWebServerFactory factory =
                org.mockito.Mockito.mock(ConfigurableWebServerFactory.class);

        customizer.customize(factory);

        org.mockito.Mockito.verifyNoInteractions(factory);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OperatorApiProperties.class)
    static class PropertiesConfiguration {
    }
}
