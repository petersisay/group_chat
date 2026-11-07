import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Upgraded Unified Multi-modal Group Chat Server.
 * Coordinates real-time text messaging over TCP, alongside integrated
 * Voice Server (UDP) and Video Server (UDP) subsystems.
 */
public class GroupChatServer {

    public static final int TEXT_PORT = 5000;
    private static final Map<Socket, String> clientUsers = new ConcurrentHashMap<>();
    private static final Set<PrintWriter> clientWriters = ConcurrentHashMap.newKeySet();

    private static VoiceServer voiceServer;
    private static VideoServer videoServer;
    private static WebChatServer webChatServer;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   Starting Unified Group Chat Server Engine      ");
        System.out.println("==================================================");

        // 1. Initialize and launch UDP Voice Server (Port 5001)
        voiceServer = new VoiceServer(AudioConfig.VOICE_PORT);
        voiceServer.start();

        // 2. Initialize and launch UDP Video Server (Port 5002)
        videoServer = new VideoServer(VideoConfig.VIDEO_PORT);
        videoServer.start();

        // 3. Initialize and launch Web HTTP & WebSocket Server (Port 8080)
        webChatServer = new WebChatServer(WebChatServer.WEB_PORT);
        webChatServer.start();

        // 4. Start TCP Text Messaging Server (Port 5000)
        try (ServerSocket serverSocket = new ServerSocket(TEXT_PORT)) {
            System.out.println("[Chat Server] Listening on TCP port " + TEXT_PORT + "...");
            System.out.println("[System] Ready for Text, Voice, Video, and Browser connections.\n");

            // Register shutdown hook for graceful exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[Server] Shutting down all services...");
                if (voiceServer != null) voiceServer.stop();
                if (videoServer != null) videoServer.stop();
                if (webChatServer != null) webChatServer.stop();
            }));

            while (true) {
                Socket socket = serverSocket.accept();
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                clientWriters.add(writer);

                new Thread(new ClientHandler(socket, writer)).start();
            }
        } catch (IOException e) {
            System.err.println("[Chat Server] Fatal server error: " + e.getMessage());
        }
    }

    /**
     * Broadcasts a text message to all connected clients.
     */
    public static void broadcast(String message) {
        for (PrintWriter writer : clientWriters) {
            try {
                writer.println(message);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Broadcasts an updated list of online usernames to all clients.
     */
    public static void broadcastUserList() {
        Collection<String> users = clientUsers.values();
        String userListMsg = "SYS_USER_LIST:" + String.join(",", users);
        broadcast(userListMsg);
    }

    public static void setUsername(Socket socket, String username) {
        clientUsers.put(socket, username);
        broadcastUserList();
    }

    public static String getUsername(Socket socket) {
        return clientUsers.get(socket);
    }

    public static void removeClient(Socket socket, PrintWriter writer) {
        String username = clientUsers.remove(socket);
        if (writer != null) {
            clientWriters.remove(writer);
        }
        if (username != null) {
            broadcast("[Server] " + username + " left the chat.");
            broadcastUserList();
        }
        try {
            socket.close();
        } catch (IOException ignored) {}
    }
}

class ClientHandler implements Runnable {
    private final Socket socket;
    private final PrintWriter writer;

    public ClientHandler(Socket socket, PrintWriter writer) {
        this.socket = socket;
        this.writer = writer;
    }

    @Override
    public void run() {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8")
            );

            // First line received is the username
            String username = reader.readLine();
            if (username == null || username.trim().isEmpty()) {
                username = "User" + (int) (Math.random() * 1000);
            }
            username = username.trim();

            GroupChatServer.setUsername(socket, username);
            System.out.println("[Chat Server] New participant joined: " + username);
            GroupChatServer.broadcast("[Server] " + username + " joined the chat.");

            // Listen for continuous messages
            String msg;
            while ((msg = reader.readLine()) != null) {
                msg = msg.trim();
                if (msg.isEmpty()) continue;

                if (msg.equalsIgnoreCase("/users")) {
                    writer.println("[Server] Active Users: " + String.join(", ", GroupChatServer.getUsername(socket)));
                } else if (msg.equalsIgnoreCase("/help")) {
                    writer.println("[Server] Available commands: /users, /help");
                } else {
                    GroupChatServer.broadcast(username + ": " + msg);
                }
            }

        } catch (Exception e) {
            // Client disconnected or encountered network interruption
        } finally {
            GroupChatServer.removeClient(socket, writer);
        }
    }
}
