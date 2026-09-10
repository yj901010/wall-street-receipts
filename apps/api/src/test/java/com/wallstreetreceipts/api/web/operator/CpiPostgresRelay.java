package com.wallstreetreceipts.api.web.operator;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Owned test-only loopback byte relay. No protocol fabrication, payload storage or remote targets. */
final class CpiPostgresRelay implements AutoCloseable {
    private final ServerSocket listener;
    private final List<Link> links = new CopyOnWriteArrayList<>();
    private final List<Thread> workers = new CopyOnWriteArrayList<>();
    private final InetSocketAddress destination;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread acceptor;

    CpiPostgresRelay(String host, int port) throws IOException {
        var address = InetAddress.getByName(host);
        if (!address.isLoopbackAddress()) throw new IllegalArgumentException("Only a disposable loopback target is permitted");
        destination = new InetSocketAddress(address, port);
        listener = new ServerSocket();
        listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
        acceptor = Thread.ofVirtual().name("cpi-demo-relay-accept").start(this::accept);
    }

    int port() { return listener.getLocalPort(); }

    List<Link> blackholeEstablishedResponses() {
        var selected = links.stream().filter(link -> !link.client.isClosed()).toList();
        selected.forEach(link -> link.blackhole.set(true));
        return selected;
    }

    private void accept() {
        while (!closed.get()) {
            try {
                var client = listener.accept();
                var upstream = new Socket();
                try {
                    upstream.connect(destination, 2000);
                    client.setTcpNoDelay(true);
                    upstream.setTcpNoDelay(true);
                    var link = new Link(client, upstream);
                    links.add(link);
                    workers.add(Thread.ofVirtual().name("cpi-demo-relay-request").start(() -> copy(link, false)));
                    workers.add(Thread.ofVirtual().name("cpi-demo-relay-response").start(() -> copy(link, true)));
                } catch (IOException failure) {
                    closeSocket(client);
                    closeSocket(upstream);
                }
            } catch (IOException failure) {
                if (!closed.get()) throw new IllegalStateException("DEMO relay listener failed", failure);
            }
        }
    }

    private void copy(Link link, boolean downstream) {
        try {
            var input = (downstream ? link.upstream : link.client).getInputStream();
            var output = (downstream ? link.client : link.upstream).getOutputStream();
            var bytes = new byte[8192];
            int count;
            while ((count = input.read(bytes)) != -1) {
                if (downstream && link.blackhole.get()) link.discarded.addAndGet(count);
                else { output.write(bytes, 0, count); output.flush(); }
            }
        } catch (IOException expectedOnSocketClosure) {
            // The real driver closes a failed transport; shutting the owned relay also closes both sockets.
        } finally {
            closeSocket(link.client);
            closeSocket(link.upstream);
        }
    }

    @Override public void close() throws Exception {
        closed.set(true);
        listener.close();
        acceptor.join(Duration.ofSeconds(3));
        for (var link : links) { closeSocket(link.client); closeSocket(link.upstream); }
        for (var worker : workers) worker.join(Duration.ofSeconds(3));
        if (acceptor.isAlive() || workers.stream().anyMatch(Thread::isAlive)) {
            throw new IllegalStateException("Owned DEMO relay worker did not stop");
        }
    }

    private static void closeSocket(Socket socket) {
        try { socket.close(); } catch (IOException ignored) { /* Best effort on already closed sockets. */ }
    }

    static final class Link {
        private final Socket client;
        private final Socket upstream;
        private final AtomicBoolean blackhole = new AtomicBoolean();
        private final AtomicLong discarded = new AtomicLong();
        private Link(Socket client, Socket upstream) { this.client = client; this.upstream = upstream; }
        long discardedBytes() { return discarded.get(); }
        boolean closed() { return client.isClosed() && upstream.isClosed(); }
    }
}
