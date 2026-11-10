import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * Pure Java Integrated HTTP & WebSocket Server for Live Browser Video/Voice/Text Chat.
 * Listens on port 8080, serves the modern responsive Web UI, and establishes
 * bi-directional WebSocket connections for real-time multi-modal streaming.
 */
public class WebChatServer implements Runnable {

    public static final int WEB_PORT = 8080;
    private static final String WS_MAGIC_KEY = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    private final Set<WebSocketSession> activeSessions = ConcurrentHashMap.newKeySet();
    private final Map<WebSocketSession, String> userNames = new ConcurrentHashMap<>();

    public static int getWebPort() {
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {}
        }
        return WEB_PORT;
    }

    public WebChatServer(int port) {
        this.port = port;
    }

    public WebChatServer() {
        this(getWebPort());
    }

    public void start() {
        if (running) return;
        running = true;
        Thread serverThread = new Thread(this, "WebChatServer-Thread");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[Web Server] Live Web Chat running at http://localhost:" + port);

            while (running) {
                Socket socket = serverSocket.accept();
                CompletableFuture.runAsync(() -> handleConnection(socket));
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[Web Server] Error: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    private void handleConnection(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                socket.close();
                return;
            }

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
                }
            }

            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String path = parts.length > 1 ? parts[1] : "/";

            // Check if this is a WebSocket Upgrade request
            if ("websocket".equalsIgnoreCase(headers.get("upgrade"))) {
                performWebSocketHandshake(headers, out);
                WebSocketSession session = new WebSocketSession(socket, in, out);
                activeSessions.add(session);
                session.listen();
            } else {
                // Serve HTTP Content (HTML)
                serveHttp(path, out);
                socket.close();
            }

        } catch (Exception ignored) {
            try {
                socket.close();
            } catch (IOException ignored2) {}
        }
    }

    private void performWebSocketHandshake(Map<String, String> headers, OutputStream out) throws Exception {
        String key = headers.get("sec-websocket-key");
        String acceptKey = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1").digest((key + WS_MAGIC_KEY).getBytes(StandardCharsets.UTF_8))
        );

        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";

        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void serveHttp(String path, OutputStream out) throws IOException {
        File webFile = new File("web", "index.html");
        if (!webFile.exists()) {
            webFile = new File("index.html");
        }
        if (!webFile.exists()) {
            webFile = new File(System.getProperty("user.dir"), "web/index.html");
        }

        byte[] content;
        if (webFile.exists()) {
            try (FileInputStream fis = new FileInputStream(webFile)) {
                content = fis.readAllBytes();
            }
        } else {
            content = "<html><body><h1>Group Chat Server Active</h1></body></html>".getBytes(StandardCharsets.UTF_8);
        }

        String headers = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "Content-Length: " + content.length + "\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Connection: close\r\n\r\n";

        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.flush();
    }

    private void broadcast(String jsonMessage, WebSocketSession sender) {
        byte[] frame = encodeWebSocketFrame(jsonMessage);
        for (WebSocketSession s : activeSessions) {
            try {
                s.sendRaw(frame);
            } catch (IOException e) {
                s.close();
            }
        }
    }

    private void broadcastUserList() {
        Collection<String> names = userNames.values();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"users\",\"users\":[");
        int count = 0;
        for (String n : names) {
            if (count > 0) sb.append(",");
            sb.append("\"").append(escapeJson(n)).append("\"");
            count++;
        }
        sb.append("]}");
        broadcast(sb.toString(), null);
    }

    private byte[] encodeWebSocketFrame(String message) {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // FIN + Opcode (0x81 = text)
        baos.write(0x81);

        if (payload.length <= 125) {
            baos.write(payload.length);
        } else if (payload.length <= 65535) {
            baos.write(126);
            baos.write((payload.length >> 8) & 0xFF);
            baos.write(payload.length & 0xFF);
        } else {
            baos.write(127);
            for (int i = 7; i >= 0; i--) {
                baos.write((int) ((payload.length >> (8 * i)) & 0xFF));
            }
        }

        baos.write(payload, 0, payload.length);
        return baos.toByteArray();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        for (WebSocketSession s : activeSessions) {
            s.close();
        }
        activeSessions.clear();
        userNames.clear();
        System.out.println("[Web Server] Stopped.");
    }

    private class WebSocketSession {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private volatile boolean closed = false;

        WebSocketSession(Socket socket, InputStream in, OutputStream out) {
            this.socket = socket;
            this.in = in;
            this.out = out;
        }

        void listen() {
            try {
                while (!closed) {
                    String message = readWebSocketFrame();
                    if (message == null) break;

                    handleClientMessage(this, message);
                }
            } catch (Exception ignored) {
            } finally {
                close();
            }
        }

        synchronized void sendRaw(byte[] frame) throws IOException {
            if (!closed) {
                out.write(frame);
                out.flush();
            }
        }

        private String readWebSocketFrame() throws IOException {
            int b1 = in.read();
            if (b1 == -1) return null;

            int opcode = b1 & 0x0F;
            if (opcode == 0x08) { // Close frame
                return null;
            }

            int b2 = in.read();
            if (b2 == -1) return null;

            boolean isMasked = (b2 & 0x80) != 0;
            long payloadLength = b2 & 0x7F;

            if (payloadLength == 126) {
                payloadLength = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
            } else if (payloadLength == 127) {
                payloadLength = 0;
                for (int i = 0; i < 8; i++) {
                    payloadLength = (payloadLength << 8) | (in.read() & 0xFF);
                }
            }

            byte[] maskKey = new byte[4];
            if (isMasked) {
                int read = 0;
                while (read < 4) {
                    int r = in.read(maskKey, read, 4 - read);
                    if (r == -1) return null;
                    read += r;
                }
            }

            byte[] payload = new byte[(int) payloadLength];
            int read = 0;
            while (read < payloadLength) {
                int r = in.read(payload, read, (int) (payloadLength - read));
                if (r == -1) return null;
                read += r;
            }

            if (isMasked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] = (byte) (payload[i] ^ maskKey[i % 4]);
                }
            }

            return new String(payload, StandardCharsets.UTF_8);
        }

        void close() {
            if (closed) return;
            closed = true;
            activeSessions.remove(this);
            String user = userNames.remove(this);
            try {
                socket.close();
            } catch (IOException ignored) {}

            if (user != null) {
                broadcast("{\"type\":\"system\",\"text\":\"" + escapeJson(user) + " left the room.\"}", null);
                broadcastUserList();
            }
        }
    }

    private void handleClientMessage(WebSocketSession session, String json) {
        if (json.contains("\"type\":\"join\"")) {
            String user = extractJsonValue(json, "username");
            if (user == null || user.isEmpty()) user = "User" + (int) (Math.random() * 1000);
            userNames.put(session, user);

            broadcast("{\"type\":\"system\",\"text\":\"" + escapeJson(user) + " joined the room.\"}", null);
            broadcastUserList();
        } else if (json.contains("\"type\":\"chat\"")) {
            broadcast(json, null);
        } else {
            // Forward video frames, stop_video, and voice_level to all other connected clients
            broadcast(json, session);
        }
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    public static void main(String[] args) {
        WebChatServer server = new WebChatServer();
        server.start();

        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {}
    }
}
