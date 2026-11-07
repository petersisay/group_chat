import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance UDP Video Relay Server.
 * Relays video frame chunks from transmitting clients to all other participants
 * in the video conference room.
 */
public class VideoServer implements Runnable {

    private final int port;
    private DatagramSocket socket;
    private volatile boolean running = false;

    // Track active video client endpoints and their last seen timestamp
    private final Map<SocketAddress, Long> activeClients = new ConcurrentHashMap<>();
    private static final long CLIENT_TIMEOUT_MS = 15000;

    public VideoServer(int port) {
        this.port = port;
    }

    public VideoServer() {
        this(VideoConfig.VIDEO_PORT);
    }

    public void start() {
        if (running) return;
        running = true;
        Thread serverThread = new Thread(this, "VideoServer-Thread");
        serverThread.setDaemon(true);
        serverThread.start();

        Thread cleanerThread = new Thread(this::cleanupStaleClients, "VideoServer-Cleaner");
        cleanerThread.setDaemon(true);
        cleanerThread.start();
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(port);
            System.out.println("[Video Server] Listening on UDP port " + port + "...");

            byte[] buffer = new byte[VideoConfig.MAX_PACKET_SIZE];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                SocketAddress senderAddress = packet.getSocketAddress();
                activeClients.put(senderAddress, System.currentTimeMillis());

                int packetLength = packet.getLength();
                byte[] dataCopy = new byte[packetLength];
                System.arraycopy(packet.getData(), packet.getOffset(), dataCopy, 0, packetLength);

                // Broadcast packet to all other video clients
                for (SocketAddress clientAddr : activeClients.keySet()) {
                    if (!clientAddr.equals(senderAddress)) {
                        try {
                            DatagramPacket forwardPacket = new DatagramPacket(
                                    dataCopy,
                                    packetLength,
                                    clientAddr
                            );
                            socket.send(forwardPacket);
                        } catch (IOException ignored) {}
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[Video Server] Error: " + e.getMessage());
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
        System.out.println("[Video Server] Stopped.");
    }

    public static void main(String[] args) {
        VideoServer server = new VideoServer();
        server.start();

        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {}
    }
}
