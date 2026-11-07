import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * High-performance UDP Voice Relay Server.
 * Listens for incoming audio datagrams from participants and broadcasts them
 * to all other active participants in the voice room.
 */
public class VoiceServer implements Runnable {

    private final int port;
    private DatagramSocket socket;
    private volatile boolean running = false;

    // Track active client endpoints and their last seen timestamp
    private final Map<SocketAddress, Long> activeClients = new ConcurrentHashMap<>();
    private static final long CLIENT_TIMEOUT_MS = 15000; // 15 seconds timeout

    public VoiceServer(int port) {
        this.port = port;
    }

    public VoiceServer() {
        this(AudioConfig.VOICE_PORT);
    }

    public void start() {
        if (running) return;
        running = true;
        Thread serverThread = new Thread(this, "VoiceServer-Thread");
        serverThread.setDaemon(true);
        serverThread.start();

        // Background cleanup thread for inactive audio clients
        Thread cleanerThread = new Thread(this::cleanupStaleClients, "VoiceServer-Cleaner");
        cleanerThread.setDaemon(true);
        cleanerThread.start();
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(port);
            System.out.println("[Voice Server] Listening on UDP port " + port + "...");

            byte[] buffer = new byte[AudioConfig.MAX_PACKET_SIZE];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                SocketAddress senderAddress = packet.getSocketAddress();
                activeClients.put(senderAddress, System.currentTimeMillis());

                // Broadcast packet to all other active clients
                int packetLength = packet.getLength();
                byte[] dataCopy = new byte[packetLength];
                System.arraycopy(packet.getData(), packet.getOffset(), dataCopy, 0, packetLength);

                for (SocketAddress clientAddr : activeClients.keySet()) {
                    if (!clientAddr.equals(senderAddress)) {
                        try {
                            DatagramPacket forwardPacket = new DatagramPacket(
                                    dataCopy,
                                    packetLength,
                                    clientAddr
                            );
                            socket.send(forwardPacket);
                        } catch (IOException ignored) {
                            // Suppress individual transmission error
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Voice Server error: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    private void cleanupStaleClients() {
        while (running) {
            try {
                Thread.sleep(5000);
                long now = System.currentTimeMillis();
                activeClients.entrySet().removeIf(entry -> (now - entry.getValue()) > CLIENT_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        activeClients.clear();
        System.out.println("Voice Server stopped.");
    }

    public static void main(String[] args) {
        VoiceServer server = new VoiceServer();
        server.start();

        // Keep main thread alive if run directly
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {}
    }
}
