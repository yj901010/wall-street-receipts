package com.wallstreetreceipts.api.web.operator;

import static org.assertj.core.api.Assertions.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class CpiPostgresRelayTest {
    @Test void rejectsNonLoopbackTargetBeforeOpeningListener() {
        assertThatThrownBy(() -> new CpiPostgresRelay("192.0.2.1", 5432))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("loopback");
    }

    @Test @Timeout(10)
    void forwardsBytesDiscardsOnlySelectedResponsesAndClosesOwnedSocketsAndWorkers() throws Exception {
        try (var echo = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            var done = new CompletableFuture<Void>();
            var thread = Thread.ofVirtual().start(() -> {
                try (var socket = echo.accept()) {
                    int next;
                    while ((next = socket.getInputStream().read()) != -1) {
                        socket.getOutputStream().write(next);
                        socket.getOutputStream().flush();
                    }
                    done.complete(null);
                } catch (Exception failure) { done.completeExceptionally(failure); }
            });
            int relayPort;
            try (var relay = new CpiPostgresRelay("127.0.0.1", echo.getLocalPort());
                 var client = new Socket("127.0.0.1", relay.port())) {
                relayPort = relay.port();
                client.setSoTimeout(500);
                client.getOutputStream().write(42);
                assertThat(client.getInputStream().read()).isEqualTo(42);
                var silenced = relay.blackholeEstablishedResponses();
                assertThat(silenced).hasSize(1);
                client.getOutputStream().write(43);
                assertThatThrownBy(() -> client.getInputStream().read()).isInstanceOf(SocketTimeoutException.class);
                assertThat(silenced.getFirst().discardedBytes()).isEqualTo(1);
            }
            done.get(3, TimeUnit.SECONDS);
            thread.join(Duration.ofSeconds(3));
            assertThat(thread.isAlive()).isFalse();
            try (var closedProbe = new Socket()) {
                assertThatThrownBy(() -> closedProbe.connect(new InetSocketAddress("127.0.0.1", relayPort), 500))
                        .isInstanceOf(java.io.IOException.class);
            }
        }
    }
}
