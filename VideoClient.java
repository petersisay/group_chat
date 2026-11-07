import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side Video Streaming Manager.
 * Supports capturing live screen sharing or live animated camera avatar feed,
 * compressing frames to JPEG, chunking into UDP packets, and reassembling
 * incoming peer video streams.
 */
public class VideoClient {

    public enum VideoMode {
        OFF,
        CAMERA,
        SCREEN_SHARE
    }

    @FunctionalInterface
    public interface VideoFrameListener {
        void onFrameReceived(String username, BufferedImage frame);
    }

    private final String serverHost;
    private final int serverPort;
    private final String username;

    private DatagramSocket udpSocket;
    private InetAddress serverAddress;

    private volatile boolean isRunning = false;
    private volatile VideoMode currentMode = VideoMode.OFF;

    private VideoFrameListener frameListener;
    private Robot robot;

    private int frameCounter = 0;
    private float animatedAngle = 0.0f;
    private double currentVoiceLevel = 0.0;

    // Buffer map for reassembling incoming multi-chunk frames: key = "username:frameId"
    private final Map<String, FrameAssemblyBuffer> assemblyMap = new ConcurrentHashMap<>();

    private static class FrameAssemblyBuffer {
        final int totalChunks;
        final byte[][] chunks;
        int receivedChunks = 0;
        final long createdAt = System.currentTimeMillis();

        FrameAssemblyBuffer(int totalChunks) {
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
        }

        boolean addChunk(int index, byte[] data) {
            if (index >= 0 && index < totalChunks && chunks[index] == null) {
                chunks[index] = data;
                receivedChunks++;
            }
            return receivedChunks == totalChunks;
        }

        byte[] assemble() {
            int totalBytes = 0;
            for (byte[] c : chunks) {
                if (c != null) totalBytes += c.length;
            }
            byte[] full = new byte[totalBytes];
            int offset = 0;
            for (byte[] c : chunks) {
                if (c != null) {
                    System.arraycopy(c, 0, full, offset, c.length);
                    offset += c.length;
                }
            }
            return full;
        }
    }

    public VideoClient(String serverHost, int serverPort, String username) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.username = username;
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            System.err.println("[Video Client] Robot not available for screen capture: " + e.getMessage());
        }
    }

    public VideoClient(String serverHost, String username) {
        this(serverHost, VideoConfig.VIDEO_PORT, username);
    }

    public void setFrameListener(VideoFrameListener listener) {
        this.frameListener = listener;
    }

    public void setVoiceLevel(double level) {
        this.currentVoiceLevel = level;
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;

        try {
            udpSocket = new DatagramSocket();
            serverAddress = InetAddress.getByName(serverHost);

            // Thread for receiving remote video frames
            Thread receiverThread = new Thread(this::receiveVideoPackets, "VideoClient-Receiver");
            receiverThread.setDaemon(true);
            receiverThread.start();

            // Thread for capturing and sending local video frames
            Thread captureThread = new Thread(this::captureAndSendFrames, "VideoClient-Capture");
            captureThread.setDaemon(true);
            captureThread.start();

            // Thread for sending keep-alive heartbeat
            Thread heartbeatThread = new Thread(this::sendHeartbeats, "VideoClient-Heartbeat");
            heartbeatThread.setDaemon(true);
            heartbeatThread.start();

            System.out.println("[Video Client] Video service started for " + username);

        } catch (Exception e) {
            System.err.println("[Video Client] Initialization error: " + e.getMessage());
        }
    }

    public void setMode(VideoMode mode) {
        this.currentMode = mode;
        System.out.println("[Video Client] Mode set to: " + mode);
    }

    public VideoMode getMode() {
        return currentMode;
    }

    private void captureAndSendFrames() {
        while (isRunning) {
            long startTime = System.currentTimeMillis();

            if (currentMode != VideoMode.OFF) {
                try {
                    BufferedImage frame = captureFrame();
                    if (frame != null) {
                        // Notify local listener so sender also sees own preview feed
                        if (frameListener != null) {
                            frameListener.onFrameReceived(username, frame);
                        }

                        byte[] jpegBytes = compressToJpeg(frame, VideoConfig.JPEG_QUALITY);
                        sendFrameChunks(jpegBytes);
                    }
                } catch (Exception e) {
                    System.err.println("[Video Client] Frame capture/send error: " + e.getMessage());
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            long sleepTime = VideoConfig.FRAME_INTERVAL_MS - elapsed;
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private BufferedImage captureFrame() {
        if (currentMode == VideoMode.SCREEN_SHARE && robot != null) {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            BufferedImage screen = robot.createScreenCapture(new Rectangle(screenSize));
            // Scale to standard resolution
            BufferedImage scaled = new BufferedImage(
                    VideoConfig.FRAME_WIDTH,
                    VideoConfig.FRAME_HEIGHT,
                    BufferedImage.TYPE_INT_RGB
            );
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(screen, 0, 0, VideoConfig.FRAME_WIDTH, VideoConfig.FRAME_HEIGHT, null);
            // Draw screen share watermark badge
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRoundRect(10, 10, 150, 24, 8, 8);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString("SCREEN SHARE", 20, 26);
            g.dispose();
            return scaled;
        } else {
            // Live Animated Camera Avatar
            return generateCameraFeedFrame();
        }
    }

    private BufferedImage generateCameraFeedFrame() {
        int w = VideoConfig.FRAME_WIDTH;
        int h = VideoConfig.FRAME_HEIGHT;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        animatedAngle += 0.08f;

        // Elegant background gradient
        Color c1 = new Color(24, 28, 40);
        Color c2 = new Color(42, 53, 76);
        GradientPaint gp = new GradientPaint(0, 0, c1, w, h, c2);
        g.setPaint(gp);
        g.fillRect(0, 0, w, h);

        // Animated soundwave pulsation when speaking
        int centerX = w / 2;
        int centerY = h / 2 - 20;
        int radius = 65;

        if (currentVoiceLevel > 0.02) {
            float pulse = (float) (currentVoiceLevel * 40.0 * Math.sin(animatedAngle * 2));
            int outerRadius = (int) (radius + 20 + Math.abs(pulse));
            g.setColor(new Color(46, 204, 113, 80));
            g.fillOval(centerX - outerRadius, centerY - outerRadius, outerRadius * 2, outerRadius * 2);

            g.setColor(new Color(46, 204, 113, 160));
            g.setStroke(new BasicStroke(3));
            g.drawOval(centerX - outerRadius, centerY - outerRadius, outerRadius * 2, outerRadius * 2);
        }

        // Avatar Circle
        Color avatarColor = getAvatarColor(username);
        g.setColor(avatarColor);
        g.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        // Avatar Border
        g.setColor(new Color(255, 255, 255, 180));
        g.setStroke(new BasicStroke(4));
        g.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        // Avatar Initials
        String initial = (username != null && !username.isEmpty())
                ? username.substring(0, Math.min(2, username.length())).toUpperCase()
                : "U";
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 46));
        FontMetrics fm = g.getFontMetrics();
        int tx = centerX - fm.stringWidth(initial) / 2;
        int ty = centerY + fm.getAscent() / 2 - 6;
        g.drawString(initial, tx, ty);

        // Participant Name Overlay
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(w / 2 - 100, h - 60, 200, 32, 10, 10);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        fm = g.getFontMetrics();
        tx = w / 2 - fm.stringWidth(username) / 2;
        g.drawString(username, tx, h - 39);

        // Status indicator (Camera Live)
        g.setColor(new Color(231, 76, 60));
        g.fillOval(15, 15, 10, 10);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString("LIVE CAMERA", 32, 24);

        g.dispose();
        return img;
    }

    private Color getAvatarColor(String name) {
        int hash = Math.abs(name.hashCode());
        Color[] colors = {
                new Color(52, 152, 219),
                new Color(155, 89, 182),
                new Color(26, 188, 156),
                new Color(241, 196, 15),
                new Color(230, 126, 34),
                new Color(231, 76, 60)
        };
        return colors[hash % colors.length];
    }

    private byte[] compressToJpeg(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private void sendFrameChunks(byte[] jpegBytes) throws IOException {
        int frameId = ++frameCounter;
        byte[] userBytes = username.getBytes("UTF-8");
        int payloadSize = VideoConfig.MAX_CHUNK_PAYLOAD;
        int totalChunks = (int) Math.ceil((double) jpegBytes.length / payloadSize);
        if (totalChunks <= 0) totalChunks = 1;

        for (int i = 0; i < totalChunks; i++) {
            int offset = i * payloadSize;
            int length = Math.min(payloadSize, jpegBytes.length - offset);

            // Packet Structure:
            // [MAGIC: 2][FRAME_ID: 4][CHUNK_INDEX: 2][TOTAL_CHUNKS: 2][USER_LEN: 1][USER_BYTES][JPEG_CHUNK]
            ByteBuffer buffer = ByteBuffer.allocate(11 + userBytes.length + length);
            buffer.putShort(VideoConfig.PROTOCOL_MAGIC);
            buffer.putInt(frameId);
            buffer.putShort((short) i);
            buffer.putShort((short) totalChunks);
            buffer.put((byte) userBytes.length);
            buffer.put(userBytes);
            buffer.put(jpegBytes, offset, length);

            byte[] packetBytes = buffer.array();
            DatagramPacket packet = new DatagramPacket(
                    packetBytes,
                    packetBytes.length,
                    serverAddress,
                    serverPort
            );
            udpSocket.send(packet);
        }
    }

    private void receiveVideoPackets() {
        byte[] buffer = new byte[VideoConfig.MAX_PACKET_SIZE];

        while (isRunning) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                if (packet.getLength() < 12) continue;

                ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
                short magic = byteBuffer.getShort();
                if (magic != VideoConfig.PROTOCOL_MAGIC) continue;

                int frameId = byteBuffer.getInt();
                int chunkIndex = byteBuffer.getShort();
                int totalChunks = byteBuffer.getShort();
                int userLen = byteBuffer.get() & 0xFF;

                if (byteBuffer.remaining() < userLen) continue;
                byte[] userBytes = new byte[userLen];
                byteBuffer.get(userBytes);
                String sender = new String(userBytes, "UTF-8");

                byte[] chunkData = new byte[byteBuffer.remaining()];
                byteBuffer.get(chunkData);

                // Add to frame assembly buffer
                String key = sender + ":" + frameId;
                FrameAssemblyBuffer assembly = assemblyMap.computeIfAbsent(
                        key,
                        k -> new FrameAssemblyBuffer(totalChunks)
                );

                boolean complete = assembly.addChunk(chunkIndex, chunkData);
                if (complete) {
                    assemblyMap.remove(key);
                    byte[] fullJpeg = assembly.assemble();
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(fullJpeg)) {
                        BufferedImage img = ImageIO.read(bais);
                        if (img != null && frameListener != null) {
                            frameListener.onFrameReceived(sender, img);
                        }
                    }
                }

                // Periodic buffer cleanup
                if (assemblyMap.size() > 50) {
                    long now = System.currentTimeMillis();
                    assemblyMap.entrySet().removeIf(e -> (now - e.getValue().createdAt) > 2000);
                }

            } catch (IOException e) {
                if (!isRunning) break;
            }
        }
    }

    private void sendHeartbeats() {
        byte[] ping = new byte[]{0x05, 0x06, 0x07, 0x08};
        while (isRunning) {
            try {
                DatagramPacket pingPacket = new DatagramPacket(ping, ping.length, serverAddress, serverPort);
                udpSocket.send(pingPacket);
                Thread.sleep(5000);
            } catch (Exception e) {
                if (!isRunning) break;
            }
        }
    }

    public synchronized void stop() {
        isRunning = false;
        currentMode = VideoMode.OFF;

        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        assemblyMap.clear();
        System.out.println("[Video Client] Stopped.");
    }
}
