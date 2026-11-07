/**
 * Configuration parameters for the Video Chat subsystem.
 * Standardizes video frame dimensions, target frame rates, compression,
 * and network transport packetization settings.
 */
public final class VideoConfig {

    // Network port for UDP video broadcasting
    public static final int VIDEO_PORT = 5002;

    // Standard video stream resolution
    public static final int FRAME_WIDTH = 480;
    public static final int FRAME_HEIGHT = 360;

    // Target frames per second
    public static final int TARGET_FPS = 15;
    public static final int FRAME_INTERVAL_MS = 1000 / TARGET_FPS;

    // JPEG compression quality (0.0 to 1.0)
    public static final float JPEG_QUALITY = 0.65f;

    // Application-level UDP chunk size (safe within standard network MTUs)
    public static final int MAX_CHUNK_PAYLOAD = 8192;

    // Datagram packet buffer size
    public static final int MAX_PACKET_SIZE = 9216;

    // Protocol magic bytes to identify valid video packets
    public static final short PROTOCOL_MAGIC = (short) 0x5649; // "VI"

    private VideoConfig() {
        // Prevent instantiation
    }
}
