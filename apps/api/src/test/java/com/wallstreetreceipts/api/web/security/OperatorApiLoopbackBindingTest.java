package com.wallstreetreceipts.api.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.apache.coyote.AbstractProtocol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.operator-api.enabled=true",
                "app.operator-api.token-sha256="
                        + "905f28def18eaac05ae6f12b2c3452744afaf626da1343d57b395b544e0519b6",
                "server.address=0.0.0.0"
        })
@ActiveProfiles("test")
class OperatorApiLoopbackBindingTest {

    @Autowired
    private ServletWebServerApplicationContext applicationContext;

    @Test
    void enabledOperatorApiOverridesWildcardConfigurationWithActualLoopbackBinding() {
        TomcatWebServer webServer = (TomcatWebServer) applicationContext.getWebServer();
        AbstractProtocol<?> protocol =
                (AbstractProtocol<?>) webServer.getTomcat().getConnector().getProtocolHandler();
        InetAddress boundAddress = protocol.getAddress();

        assertThat(boundAddress).isNotNull();
        assertThat(boundAddress.isLoopbackAddress()).isTrue();
    }
}
