package com.mtsharpgrain.js.mainthread;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JS global {@code Ws}.
 * Socket threads never call Graal. Events are posted to the pack owner thread.
 */
public final class WsApi {

    private final boolean mainThread;
    private final ModBridge bridge;
    private final EngineAccess engine;
    private final List<Runnable> shutdowns = new CopyOnWriteArrayList<>();

    public WsApi(boolean mainThread, ModBridge bridge, EngineAccess engine) {
        this.mainThread = mainThread;
        this.bridge = bridge;
        this.engine = engine;
    }

    @HostAccess.Export
    public WsClient connect(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Ws.connect expects a ws:// or wss:// url");
        }
        var client = new WsClient(URI.create(url));
        shutdowns.add(client::close);
        client.start();
        return client;
    }

    @HostAccess.Export
    public WsServer listen(long port) {
        return listen("0.0.0.0", port);
    }

    @HostAccess.Export
    public WsServer listen(String host, long port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Ws.listen port out of range: " + port);
        }
        var bindHost = (host == null || host.isBlank()) ? "0.0.0.0" : host;
        var server = new WsServer(bindHost, (int) port);
        shutdowns.add(server::close);
        server.start();
        return server;
    }

    public void shutdown() {
        for (var stop : shutdowns) {
            try {
                stop.run();
            } catch (Exception ignored) {
            }
        }
        shutdowns.clear();
    }

    private void dispatch(Runnable onOwner) {
        if (mainThread) {
            engine.post(onOwner);
            return;
        }
        if (bridge == null) {
            throw new IllegalStateException("Ws has no mod bridge");
        }
        bridge.submitTask(onOwner);
    }

    private static void call(Value fn, Object... args) {
        if (fn == null || !fn.canExecute()) return;
        try {
            fn.executeVoid(args);
        } catch (PolyglotException e) {
            System.err.println("[Ws] listener threw: " + e.getMessage());
        }
    }

    public final class WsClient {
        private final WebSocketClient socket;
        private final AtomicBoolean stopped = new AtomicBoolean();
        private volatile Value onOpen;
        private volatile Value onMessage;
        private volatile Value onClose;
        private volatile Value onError;

        private WsClient(URI uri) {
            this.socket = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    dispatch(() -> call(onOpen));
                }

                @Override
                public void onMessage(String message) {
                    dispatch(() -> call(onMessage, message));
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    var why = reason == null ? "" : reason;
                    dispatch(() -> call(onClose, code, why));
                }

                @Override
                public void onError(Exception ex) {
                    dispatch(() -> call(onError, messageOf(ex)));
                }
            };
        }

        private void start() {
            socket.connect();
        }

        @HostAccess.Export public void onOpen(Value fn) { this.onOpen = fn; }
        @HostAccess.Export public void onMessage(Value fn) { this.onMessage = fn; }
        @HostAccess.Export public void onClose(Value fn) { this.onClose = fn; }
        @HostAccess.Export public void onError(Value fn) { this.onError = fn; }

        @HostAccess.Export
        public void send(String text) {
            if (text == null || !socket.isOpen()) return;
            socket.send(text);
        }

        @HostAccess.Export
        public boolean isOpen() {
            return socket.isOpen();
        }

        @HostAccess.Export
        public void close() {
            if (!stopped.compareAndSet(false, true)) return;
            socket.close();
        }
    }

    public final class WsServer {
        private final WebSocketServer socket;
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final ConcurrentHashMap<org.java_websocket.WebSocket, WsPeer> peers = new ConcurrentHashMap<>();
        private volatile Value onConnection;
        private volatile Value onError;

        private WsServer(String host, int port) {
            var peersRef = this.peers;
            this.socket = new WebSocketServer(new InetSocketAddress(host, port)) {
                @Override
                public void onOpen(org.java_websocket.WebSocket conn, ClientHandshake handshake) {
                    var peer = new WsPeer(conn);
                    peersRef.put(conn, peer);
                    dispatch(() -> call(onConnection, peer));
                }

                @Override
                public void onMessage(org.java_websocket.WebSocket conn, String message) {
                    var peer = peersRef.get(conn);
                    if (peer == null) return;
                    dispatch(() -> call(peer.onMessage, message));
                }

                @Override
                public void onClose(org.java_websocket.WebSocket conn, int code, String reason, boolean remote) {
                    var peer = peersRef.remove(conn);
                    if (peer == null) return;
                    var why = reason == null ? "" : reason;
                    dispatch(() -> call(peer.onClose, code, why));
                }

                @Override
                public void onError(org.java_websocket.WebSocket conn, Exception ex) {
                    if (conn != null) {
                        var peer = peersRef.get(conn);
                        if (peer != null && peer.onError != null) {
                            dispatch(() -> call(peer.onError, messageOf(ex)));
                            return;
                        }
                    }
                    dispatch(() -> call(onError, messageOf(ex)));
                }

                @Override
                public void onStart() {
                }
            };
        }

        private void start() {
            socket.start();
        }

        @HostAccess.Export public void onConnection(Value fn) { this.onConnection = fn; }
        @HostAccess.Export public void onError(Value fn) { this.onError = fn; }

        @HostAccess.Export
        public void close() {
            if (!stopped.compareAndSet(false, true)) return;
            peers.clear();
            try {
                socket.stop(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public final class WsPeer {
        private final org.java_websocket.WebSocket conn;
        private final AtomicBoolean stopped = new AtomicBoolean();
        private volatile Value onMessage;
        private volatile Value onClose;
        private volatile Value onError;

        private WsPeer(org.java_websocket.WebSocket conn) {
            this.conn = conn;
        }

        @HostAccess.Export public void onMessage(Value fn) { this.onMessage = fn; }
        @HostAccess.Export public void onClose(Value fn) { this.onClose = fn; }
        @HostAccess.Export public void onError(Value fn) { this.onError = fn; }

        @HostAccess.Export
        public void send(String text) {
            if (text == null || !conn.isOpen()) return;
            conn.send(text);
        }

        @HostAccess.Export
        public boolean isOpen() {
            return conn.isOpen();
        }

        @HostAccess.Export
        public void close() {
            if (!stopped.compareAndSet(false, true)) return;
            conn.close();
        }
    }

    private static String messageOf(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}