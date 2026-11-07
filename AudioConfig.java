import javax.sound.sampled.AudioFormat;

/**
 * Configuration parameters for the Voice Chat subsystem.
 * Standardizes audio capture, playback format, and network transport settings.
 */
public final class AudioConfig {

    // Network port for UDP audio broadcasting
    public static final int VOICE_PORT = 5001;

    // Audio sampling rate (16 kHz is standard for clear voice communication)
    public static final float SAMPLE_RATE = 16000.0f;

    // Bit depth per sample
    public static final int SAMPLE_SIZE_IN_BITS = 16;

    // Mono channel for voice
    public static final int CHANNELS = 1;

    // Signed PCM
    public static final boolean SIGNED = true;

    // Little-endian byte order
    public static final boolean BIG_ENDIAN = false;

    // Packet chunk buffer size in bytes (16000 * 2 bytes * 0.04s = ~1280 bytes = 40ms packets)
    public static final int BUFFER_SIZE = 1280;

    // Maximum datagram packet size including headers (ID + timestamp + payload)
    public static final int MAX_PACKET_SIZE = 2048;

    private AudioConfig() {
        // Prevent instantiation
    }

    /**
     * Returns the standardized AudioFormat used by both sender and receiver.
     */
    public static AudioFormat getFormat() {
        return new AudioFormat(
                SAMPLE_RATE,
                SAMPLE_SIZE_IN_BITS,
                CHANNELS,
                SIGNED,
                BIG_ENDIAN
        );
    }
}
