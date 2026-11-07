import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modern responsive Video Grid UI Component.
 * Automatically arranges participant video feeds in an adaptive grid layout,
 * rendering high-resolution frames with name overlays and speaking highlights.
 */
public class VideoGridPanel extends JPanel {

    private final Map<String, ParticipantTile> tiles = new ConcurrentHashMap<>();
    private final JPanel gridContainer;
    private final JLabel emptyLabel;

    public VideoGridPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(20, 24, 33));

        gridContainer = new JPanel();
        gridContainer.setBackground(new Color(20, 24, 33));
        gridContainer.setLayout(new GridLayout(1, 1, 8, 8));
        gridContainer.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        emptyLabel = new JLabel("No active video streams. Turn on Camera or Share Screen.", SwingConstants.CENTER);
        emptyLabel.setForeground(new Color(140, 150, 170));
        emptyLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        add(emptyLabel, BorderLayout.CENTER);
    }

    /**
     * Updates or creates a video tile for the given participant.
     */
    public void updateFrame(String username, BufferedImage frame) {
        SwingUtilities.invokeLater(() -> {
            ParticipantTile tile = tiles.computeIfAbsent(username, name -> {
                ParticipantTile newTile = new ParticipantTile(name);
                gridContainer.add(newTile);
                updateGridLayout();
                return newTile;
            });

            tile.setFrame(frame);

            if (emptyLabel.getParent() != null) {
                remove(emptyLabel);
                add(gridContainer, BorderLayout.CENTER);
                revalidate();
                repaint();
            }
        });
    }

    /**
     * Removes a participant's tile when they disconnect or stop video.
     */
    public void removeParticipant(String username) {
        SwingUtilities.invokeLater(() -> {
            ParticipantTile tile = tiles.remove(username);
            if (tile != null) {
                gridContainer.remove(tile);
                updateGridLayout();

                if (tiles.isEmpty()) {
                    remove(gridContainer);
                    add(emptyLabel, BorderLayout.CENTER);
                }
                revalidate();
                repaint();
            }
        });
    }

    private void updateGridLayout() {
        int count = tiles.size();
        if (count == 0) return;

        int cols = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((double) count / cols);
        gridContainer.setLayout(new GridLayout(rows, cols, 8, 8));
        gridContainer.revalidate();
    }

    /**
     * Individual Video Tile for a participant.
     */
    private static class ParticipantTile extends JPanel {
        private final String username;
        private BufferedImage currentFrame;
        private long lastFrameTime = System.currentTimeMillis();

        ParticipantTile(String username) {
            this.username = username;
            setBackground(new Color(28, 33, 46));
            setBorder(BorderFactory.createLineBorder(new Color(48, 56, 74), 1));
            setPreferredSize(new Dimension(320, 240));
        }

        void setFrame(BufferedImage frame) {
            this.currentFrame = frame;
            this.lastFrameTime = System.currentTimeMillis();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int width = getWidth();
            int height = getHeight();

            if (currentFrame != null) {
                // Letterbox rendering maintaining frame aspect ratio
                double imgAspect = (double) currentFrame.getWidth() / currentFrame.getHeight();
                double panelAspect = (double) width / height;

                int drawW, drawH, drawX, drawY;
                if (panelAspect > imgAspect) {
                    drawH = height;
                    drawW = (int) (height * imgAspect);
                    drawX = (width - drawW) / 2;
                    drawY = 0;
                } else {
                    drawW = width;
                    drawH = (int) (width / imgAspect);
                    drawX = 0;
                    drawY = (height - drawH) / 2;
                }

                g2.drawImage(currentFrame, drawX, drawY, drawW, drawH, null);
            } else {
                // Placeholder
                g2.setColor(new Color(35, 42, 58));
                g2.fillRect(0, 0, width, height);
            }

            // Bottom name banner
            int bannerHeight = 28;
            g2.setColor(new Color(15, 18, 26, 210));
            g2.fillRoundRect(8, height - bannerHeight - 8, Math.min(width - 16, 180), bannerHeight, 8, 8);

            // Active dot
            g2.setColor(new Color(46, 204, 113));
            g2.fillOval(16, height - bannerHeight / 2 - 12, 8, 8);

            // Username text
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2.drawString(username, 32, height - bannerHeight / 2 - 5);

            g2.dispose();
        }
    }
}
