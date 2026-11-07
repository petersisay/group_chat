import javax.sound.sampled.*;
import java.io.IOException;
import java.net.*;
import java.util.function.Consumer;

/**
 * Client-side Voice Manager.
 * Handles microphone audio recording and streaming to the server via UDP,
 * and receives incoming peer audio datagrams for playback through speakers/headphones.
 */
public class VoiceClient {

    private final String serverHost;
    private final int serverPort;
    private final String username;

    private DatagramSocket udpSocket;
    private InetAddress serverAddress;

    private TargetDataLine microphone;
    private SourceDataLine speaker;

    private volatile boolean isRunning = false;
    private volatile boolean isMicMuted = false;
    private volatile boolean isDeafened = false;

    // Callback to notify GUI about microphone input volume level (0.0 to 1.0)
    private Consumer<Double> audioLevelListener;

    public VoiceClient(String serverHost, int serverPort, String username) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.username = username;
    }

    public VoiceClient(String serverHost, String username) {
        this(serverHost, AudioConfig.VOICE_PORT, username);
    }

    public void setAudioLevelListener(Consumer<Double> listener) {
        this.audioLevelListener = listener;
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;

        try {
            udpSocket = new DatagramSocket();
            serverAddress = InetAddress.getByName(serverHost);

            initSpeaker();
            initMicrophone();

            // Start audio receiving thread (playback)
            Thread playbackThread = new Thread(this::receiveAndPlayAudio, "VoiceClient-Playback");
            playbackThread.setDaemon(true);
            playbackThread.start();

            // Start audio recording thread (capture)
            Thread recordThread = new Thread(this::recordAndSendAudio, "VoiceClient-Recorder");
            recordThread.setDaemon(true);
            recordThread.start();

            // Start keep-alive heartbeats to register with server
            Thread heartbeatThread = new Thread(this::sendHeartbeats, "VoiceClient-Heartbeat");
            heartbeatThread.setDaemon(true);
            heartbeatThread.start();

            System.out.println("[Voice Client] Voice service started for " + username);

        } catch (Exception e) {
            System.err.println("[Voice Client] Failed to initialize voice client: " + e.getMessage());
        }
    }

    private void initMicrophone() {
        try {
            AudioFormat format = AudioConfig.getFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            if (AudioSystem.isLineSupported(info)) {
                microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(format, AudioConfig.BUFFER_SIZE * 4);
                microphone.start();
                System.out.println("[Voice Client] Microphone opened successfully.");
            } else {
                System.out.println("[Voice Client] Microphone line not supported on this device.");
            }
        } catch (Exception e) {
            System.err.println("[Voice Client] Microphone error: " + e.getMessage());
            microphone = null;
        }
    }

    private void initSpeaker() {
        try {
            AudioFormat format = AudioConfig.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

            if (AudioSystem.isLineSupported(info)) {
                speaker = (SourceDataLine) AudioSystem.getLine(info);
                speaker.open(format, AudioConfig.BUFFER_SIZE * 4);
                speaker.start();
                System.out.println("[Voice Client] Speaker opened successfully.");
            } else {
                System.out.println("[Voice Client] Speaker line not supported on this device.");
            }
        } catch (Exception e) {
            System.err.println("[Voice Client] Speaker error: " + e.getMessage());
            speaker = null;
        }
    }

    private void recordAndSendAudio() {
        byte[] audioBuffer = new byte[AudioConfig.BUFFER_SIZE];

        while (isRunning) {
            if (microphone != null && microphone.isOpen()) {
                int bytesRead = microphone.read(audioBuffer, 0, audioBuffer.length);

                if (bytesRead > 0) {
                    double level = calculateRMS(audioBuffer, bytesRead);
                    if (audioLevelListener != null) {
                        audioLevelListener.accept(isMicMuted ? 0.0 : level);
                    }

                    if (!isMicMuted) {
                        try {
                            DatagramPacket packet = new DatagramPacket(
                                    audioBuffer,
                                    bytesRead,
                                    serverAddress,
                                    serverPort
                            );
                            udpSocket.send(packet);
                        } catch (IOException ignored) {}
                    }
                }
            } else {
                // If microphone hardware unavailable, sleep briefly to avoid busy loop
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private void receiveAndPlayAudio() {
        byte[] receiveBuffer = new byte[AudioConfig.MAX_PACKET_SIZE];

        while (isRunning) {
            try {
                DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                udpSocket.receive(packet);

                // Ignore empty or heartbeat packets
                if (packet.getLength() <= 4) continue;

                if (!isDeafened && speaker != null && speaker.isOpen()) {
                    speaker.write(packet.getData(), packet.getOffset(), packet.getLength());
                }
            } catch (IOException e) {
                if (!isRunning) break;
            }
        }
    }

    private void sendHeartbeats() {
        byte[] ping = new byte[]{0x01, 0x02, 0x03, 0x04};
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

    private double calculateRMS(byte[] pcmData, int length) {
        long sum = 0;
        int sampleCount = length / 2;
        for (int i = 0; i < length - 1; i += 2) {
            short sample = (short) ((pcmData[i + 1] << 8) | (pcmData[i] & 0xFF));
            sum += (long) sample * sample;
        }
        if (sampleCount == 0) return 0.0;
        double rms = Math.sqrt((double) sum / sampleCount);
        return Math.min(1.0, rms / 15000.0);
    }

    public void setMicMuted(boolean muted) {
        this.isMicMuted = muted;
        if (muted && audioLevelListener != null) {
            audioLevelListener.accept(0.0);
        }
    }

    public boolean isMicMuted() {
        return isMicMuted;
    }

    public void setDeafened(boolean deafened) {
        this.isDeafened = deafened;
    }

    public boolean isDeafened() {
        return isDeafened;
    }

    public synchronized void stop() {
        isRunning = false;

        if (microphone != null) {
            microphone.stop();
            microphone.close();
            microphone = null;
        }

        if (speaker != null) {
            speaker.stop();
            speaker.close();
            speaker = null;
        }

        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }

        if (audioLevelListener != null) {
            audioLevelListener.accept(0.0);
        }

        System.out.println("[Voice Client] Voice service stopped.");
    }
}
