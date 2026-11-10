import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Upgraded Modern Multi-modal Chat Client GUI.
 * Features real-time text chat, online member list, interactive voice chat
 * with microphone/deafen controls and VU meter, and multi-user video chat
 * supporting both camera avatars and live screen sharing.
 */
public class ChatClientGUI {

    private final String serverHost = "localhost";
    private final int serverPort = GroupChatServer.TEXT_PORT;

    private JFrame frame;
    private JTextPane chatPane;
    private JTextField inputField;
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    private JProgressBar micLevelBar;

    private JButton micBtn;
    private JButton deafenBtn;
    private JButton videoBtn;
    private JButton screenBtn;

    private VideoGridPanel videoGridPanel;

    private PrintWriter writer;
    private Socket socket;
    private String username;

    private VoiceClient voiceClient;
    private VideoClient videoClient;

    public ChatClientGUI() {
        askUsername();
        initClients();
        buildGUI();
        connectToServer();
    }

    private void askUsername() {
        String input = JOptionPane.showInputDialog(
                null,
                "Enter your username for Group Chat:",
                "Join Chat Room",
                JOptionPane.PLAIN_MESSAGE
        );
        if (input == null || input.trim().isEmpty()) {
            username = "User" + (int) (Math.random() * 1000);
        } else {
            username = input.trim();
        }
    }

    private void initClients() {
        // Initialize voice client subsystem
        voiceClient = new VoiceClient(serverHost, AudioConfig.VOICE_PORT, username);
        voiceClient.setAudioLevelListener(level -> {
            if (micLevelBar != null) {
                int value = (int) (level * 100);
                SwingUtilities.invokeLater(() -> micLevelBar.setValue(value));
            }
            if (videoClient != null) {
                videoClient.setVoiceLevel(level);
            }
        });

        // Initialize video client subsystem
        videoClient = new VideoClient(serverHost, VideoConfig.VIDEO_PORT, username);
        videoClient.setFrameListener((sender, frame) -> {
            if (videoGridPanel != null) {
                videoGridPanel.updateFrame(sender, frame);
            }
        });
    }

    private void buildGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        frame = new JFrame("Group Chat Studio - " + username);
        frame.setSize(1000, 720);
        frame.setMinimumSize(new Dimension(800, 600));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });

        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBackground(new Color(24, 28, 38));

        // 1. Top Header Bar
        JPanel headerPanel = buildHeaderPanel();
        rootPanel.add(headerPanel, BorderLayout.NORTH);

        // 2. Center Content: SplitPane with Video Grid on Left and Text Chat + Users on Right
        videoGridPanel = new VideoGridPanel();
        JPanel chatPanel = buildChatPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, videoGridPanel, chatPanel);
        splitPane.setResizeWeight(0.60);
        splitPane.setDividerSize(6);
        splitPane.setBackground(new Color(32, 38, 50));
        splitPane.setBorder(null);

        rootPanel.add(splitPane, BorderLayout.CENTER);

        // 3. Bottom Media Control Dock
        JPanel mediaBar = buildMediaControlBar();
        rootPanel.add(mediaBar, BorderLayout.SOUTH);

        frame.setContentPane(rootPanel);
        frame.setVisible(true);
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(18, 22, 30));
        header.setBorder(new EmptyBorder(10, 16, 10, 16));

        JLabel titleLabel = new JLabel("Group Chat Studio  |  Room: General");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 15));

        JLabel userBadge = new JLabel("Logged in as: " + username + " [Online]");
        userBadge.setForeground(new Color(46, 204, 113));
        userBadge.setFont(new Font("SansSerif", Font.BOLD, 13));

        header.add(titleLabel, BorderLayout.WEST);
        header.add(userBadge, BorderLayout.EAST);
        return header;
    }

    private JPanel buildChatPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(24, 28, 38));

        // Chat message history
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setBackground(new Color(30, 35, 48));
        chatPane.setForeground(new Color(230, 235, 245));
        chatPane.setFont(new Font("SansSerif", Font.PLAIN, 13));
        chatPane.setBorder(new EmptyBorder(8, 8, 8, 8));

        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(BorderFactory.createMatteBorder(0, 1, 1, 0, new Color(45, 52, 70)));

        // User list sidebar
        userListModel = new DefaultListModel<>();
        userListModel.addElement(username + " (You)");

        userList = new JList<>(userListModel);
        userList.setBackground(new Color(22, 26, 35));
        userList.setForeground(new Color(180, 195, 215));
        userList.setFont(new Font("SansSerif", Font.PLAIN, 12));
        userList.setBorder(new EmptyBorder(6, 6, 6, 6));

        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(140, 0));
        userScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(0, 1, 1, 0, new Color(45, 52, 70)),
                "Online Members",
                0,
                0,
                new Font("SansSerif", Font.BOLD, 11),
                new Color(140, 155, 180)
        ));

        JSplitPane chatAndUsers = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScroll, userScroll);
        chatAndUsers.setResizeWeight(0.75);
        chatAndUsers.setDividerSize(4);
        chatAndUsers.setBorder(null);

        // Input field & Send button
        JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
        inputPanel.setBackground(new Color(24, 28, 38));
        inputPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        inputField = new JTextField();
        inputField.setBackground(new Color(38, 44, 60));
        inputField.setForeground(Color.WHITE);
        inputField.setCaretColor(Color.WHITE);
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 70, 95), 1),
                new EmptyBorder(6, 10, 6, 10)
        ));

        JButton sendBtn = new JButton("Send");
        sendBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        sendBtn.setBackground(new Color(52, 152, 219));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setFocusPainted(false);
        sendBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        sendBtn.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);

        panel.add(chatAndUsers, BorderLayout.CENTER);
        panel.add(inputPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildMediaControlBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(16, 20, 28));
        bar.setBorder(new EmptyBorder(8, 16, 8, 16));

        // Center Button Controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        controls.setOpaque(false);

        micBtn = createMediaButton("[Mic On]", new Color(46, 204, 113));
        micBtn.addActionListener(e -> toggleMicrophone());

        deafenBtn = createMediaButton("[Audio On]", new Color(52, 152, 219));
        deafenBtn.addActionListener(e -> toggleDeafen());

        videoBtn = createMediaButton("[Camera: Off]", new Color(120, 130, 145));
        videoBtn.addActionListener(e -> toggleCamera());

        screenBtn = createMediaButton("[Screen: Off]", new Color(120, 130, 145));
        screenBtn.addActionListener(e -> toggleScreenShare());

        controls.add(micBtn);
        controls.add(deafenBtn);
        controls.add(videoBtn);
        controls.add(screenBtn);

        // Left Mic Level VU Meter
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        leftPanel.setOpaque(false);

        JLabel micLabel = new JLabel("Mic Level:");
        micLabel.setForeground(new Color(160, 175, 195));
        micLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        micLevelBar = new JProgressBar(0, 100);
        micLevelBar.setPreferredSize(new Dimension(100, 12));
        micLevelBar.setForeground(new Color(46, 204, 113));
        micLevelBar.setBackground(new Color(40, 48, 65));
        micLevelBar.setBorderPainted(false);

        leftPanel.add(micLabel);
        leftPanel.add(micLevelBar);

        bar.add(leftPanel, BorderLayout.WEST);
        bar.add(controls, BorderLayout.CENTER);
        return bar;
    }

    private JButton createMediaButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(130, 34));
        return btn;
    }

    private void toggleMicrophone() {
        boolean willMute = !voiceClient.isMicMuted();
        voiceClient.setMicMuted(willMute);
        if (willMute) {
            micBtn.setText("[Mic Muted]");
            micBtn.setBackground(new Color(231, 76, 60));
        } else {
            micBtn.setText("[Mic On]");
            micBtn.setBackground(new Color(46, 204, 113));
        }
    }

    private void toggleDeafen() {
        boolean willDeafen = !voiceClient.isDeafened();
        voiceClient.setDeafened(willDeafen);
        if (willDeafen) {
            deafenBtn.setText("[Deafened]");
            deafenBtn.setBackground(new Color(231, 76, 60));
        } else {
            deafenBtn.setText("[Audio On]");
            deafenBtn.setBackground(new Color(52, 152, 219));
        }
    }

    private void toggleCamera() {
        if (videoClient.getMode() == VideoClient.VideoMode.CAMERA) {
            videoClient.setMode(VideoClient.VideoMode.OFF);
            videoBtn.setText("[Camera: Off]");
            videoBtn.setBackground(new Color(120, 130, 145));
            videoGridPanel.removeParticipant(username);
        } else {
            videoClient.setMode(VideoClient.VideoMode.CAMERA);
            videoBtn.setText("[Camera: On]");
            videoBtn.setBackground(new Color(46, 204, 113));

            // If screen share was active, turn it off
            screenBtn.setText("[Screen: Off]");
            screenBtn.setBackground(new Color(120, 130, 145));
        }
    }

    private void toggleScreenShare() {
        if (videoClient.getMode() == VideoClient.VideoMode.SCREEN_SHARE) {
            videoClient.setMode(VideoClient.VideoMode.OFF);
            screenBtn.setText("[Screen: Off]");
            screenBtn.setBackground(new Color(120, 130, 145));
            videoGridPanel.removeParticipant(username);
        } else {
            videoClient.setMode(VideoClient.VideoMode.SCREEN_SHARE);
            screenBtn.setText("[Screen: Sharing]");
            screenBtn.setBackground(new Color(155, 89, 182));

            // If camera was active, turn it off
            videoBtn.setText("[Camera: Off]");
            videoBtn.setBackground(new Color(120, 130, 145));
        }
    }

    private void connectToServer() {
        try {
            socket = new Socket(serverHost, serverPort);
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            // Send username handshake
            writer.println(username);

            // Start voice and video streaming engines
            voiceClient.start();
            videoClient.start();

            // Background thread to listen for incoming chat messages and system events
            new Thread(this::listenForServerMessages, "ChatClient-Listener").start();

        } catch (Exception e) {
            appendSystemMessage("Unable to connect to server at " + serverHost + ":" + serverPort);
        }
    }

    private void listenForServerMessages() {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8")
            );

            String msg;
            while ((msg = reader.readLine()) != null) {
                if (msg.startsWith("SYS_USER_LIST:")) {
                    String[] users = msg.substring("SYS_USER_LIST:".length()).split(",");
                    updateOnlineUsers(users);
                } else {
                    appendMessage(msg);
                }
            }
        } catch (Exception ignored) {
        } finally {
            appendSystemMessage("Disconnected from chat server.");
        }
    }

    private void updateOnlineUsers(String[] users) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (String u : users) {
                if (!u.trim().isEmpty()) {
                    if (u.equals(username)) {
                        userListModel.addElement(u + " (You)");
                    } else {
                        userListModel.addElement(u);
                    }
                }
            }
        });
    }

    private void sendMessage() {
        String msg = inputField.getText().trim();
        if (!msg.isEmpty() && writer != null) {
            writer.println(msg);
            inputField.setText("");
        }
    }

    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm").format(new Date());
            String formatted = "[" + time + "] " + message + "\n";
            try {
                javax.swing.text.Document doc = chatPane.getDocument();
                doc.insertString(doc.getLength(), formatted, null);
                chatPane.setCaretPosition(doc.getLength());
            } catch (Exception ignored) {}
        });
    }

    private void appendSystemMessage(String text) {
        appendMessage("[System] " + text);
    }

    private void disconnect() {
        if (voiceClient != null) voiceClient.stop();
        if (videoClient != null) videoClient.stop();
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClientGUI::new);
    }
}
