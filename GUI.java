import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;

import static javax.swing.JLayeredPane.FRAME_CONTENT_LAYER;

public class GUI {

    private final JFrame mFrame;
    private final Grid mGrid;
    private final InfoBox mInfoBox;

    /**
     * Default constructor of GUI
     *
     * @param playerName name of current player
     */
    public GUI(Point location, String playerName, int N) {
        mFrame = new JFrame(playerName);

        JPanel mainFrame = new JPanel();
        mainFrame.setLayout(new BoxLayout(mainFrame, BoxLayout.X_AXIS));

        mInfoBox = new InfoBox();
        mGrid = new Grid(N);
        mainFrame.add(mInfoBox);
        mainFrame.add(mGrid);

        mFrame.setContentPane(mainFrame);
        mFrame.setLocation(location.x, location.y);
        mFrame.pack();
        mFrame.setResizable(false);
        mFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public void updateScores(HashMap<String, String> scores) {
        mInfoBox.updateScores(scores);
    }

    public void updatePlayers(HashMap<String, Point> players) {
        mGrid.updatePlayers(players);
    }

    public void updateTreasures(List<Point> treasures) {
        mGrid.updateTreasures(treasures);
    }

    public void show() {
        mFrame.revalidate();
        mFrame.setVisible(true);
    }
    public void hide() {
        mFrame.setVisible(false);
        mFrame.dispose();
    }

    protected static class Grid extends JPanel {

        private static final String GRID_TITLE = "Game Area";
        private static final int GRID_CELL_SIZE = 40;
        private static final int GRID_CELL_INTERVAL = 1;
        private static final int GRID_PANEL_PADDINGS = 10;
        private static final int GRID_LEVEL_TREASURE = 4;
        private static final int GRID_LEVEL_PLAYER = 3;
        private static final int GRID_PLAYER_NAME_FONT_SIZE = 25;
        private final int mGridSize;

        private int mGridPixelWidth, mGridPixelHeight;
        private final Point mOriginPoint = new Point();
        private JLayeredPane mJLayerPane;
        private HashMap<Point, TreasureIcon> mTreasures = new HashMap<>();
        private HashMap<String, JLabel> mPlayers = new HashMap<>();

        Grid(int N) {
            mGridSize = N;
            setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
            createCellPanel();
        }

        void createCellPanel() {
            mJLayerPane = new JLayeredPane();
            int size = (GRID_CELL_SIZE * mGridSize) + (GRID_CELL_INTERVAL * (mGridSize - 1)) + (GRID_PANEL_PADDINGS * 2);
            mGridPixelWidth = size;
            mGridPixelHeight = size;
            mJLayerPane.setBorder(BorderFactory.createTitledBorder(GRID_TITLE));
            mJLayerPane.setPreferredSize(new Dimension(size, size));
            mOriginPoint.x = mJLayerPane.getBounds().x + 10;  // Calibrated value
            mOriginPoint.y = mJLayerPane.getBounds().y + 15; // Calibrated value
            JLayeredPane floor = new JLayeredPane();

            // Generate Floors
            int horizontalOffset;
            int verticalOffset = mOriginPoint.y;
            for (int i = 0; i < mGridSize; i++) {
                horizontalOffset = mOriginPoint.x;
                for (int j = 0; j < mGridSize; j++) {
                    Point cellPoint = new Point(horizontalOffset, verticalOffset);
                    mJLayerPane.add(createCell(cellPoint), FRAME_CONTENT_LAYER);
                    horizontalOffset += (GRID_CELL_SIZE + GRID_CELL_INTERVAL);
                }
                verticalOffset += (GRID_CELL_SIZE + GRID_CELL_INTERVAL);
            }
            this.add(mJLayerPane);
        }

        /**
         * List of players <PlayerName, Position>
         *
         * @param players player sets
         */
        void updatePlayers(HashMap<String, Point> players) {
            HashMap<String, JLabel> keepingList = new HashMap<>();
            for (String playerName : players.keySet()) {
                Point p = players.get(playerName);
                Point targetCell = new Point(
                        mOriginPoint.x + (p.x * GRID_CELL_SIZE) + (p.x) * GRID_CELL_INTERVAL,
                        mOriginPoint.y + (p.y * GRID_CELL_SIZE) + (p.y) * GRID_CELL_INTERVAL
                );

                // AS Required, we have to make sure that all username length is 2
                if (playerName.length() > 2) {
                    playerName = playerName.substring(0, 2);
                } else if (playerName.length() < 2) {
                    playerName = playerName + "a".repeat(2 - playerName.length());
                }

                if (mPlayers.containsKey(playerName)) {
                    // Update existing player's position
                    JLabel player = mPlayers.get(playerName);
                    player.setBounds(targetCell.x + (GRID_CELL_SIZE - GRID_PLAYER_NAME_FONT_SIZE) / 3,
                            targetCell.y - (GRID_CELL_SIZE - GRID_PLAYER_NAME_FONT_SIZE) / 3,
                            GRID_CELL_SIZE,
                            GRID_CELL_SIZE);
                    keepingList.put(playerName, player);
                    continue;
                }

                JLabel playerIcon = new JLabel(playerName);
                playerIcon.setFont(new Font("Default", Font.PLAIN, GRID_PLAYER_NAME_FONT_SIZE));
                playerIcon.setHorizontalTextPosition(SwingConstants.CENTER);
                playerIcon.setVerticalAlignment(SwingConstants.CENTER);
                playerIcon.setBounds(targetCell.x + (GRID_CELL_SIZE - GRID_PLAYER_NAME_FONT_SIZE) / 3,
                        targetCell.y - (GRID_CELL_SIZE - GRID_PLAYER_NAME_FONT_SIZE) / 3,
                        GRID_CELL_SIZE,
                        GRID_CELL_SIZE);
                mJLayerPane.add(playerIcon, GRID_LEVEL_PLAYER);
                keepingList.put(playerName, playerIcon);
            }

            for (String playerName : mPlayers.keySet()) {
                if (keepingList.containsKey(playerName)) {
                    continue;
                }
                mJLayerPane.remove(mPlayers.get(playerName));
            }
            mPlayers = keepingList;
            mJLayerPane.validate();
            mJLayerPane.repaint();
        }

        /**
         * List of points, e.g. x, y (0,0) (1,1)
         *
         * @param treasurePoints list
         */
        void updateTreasures(List<Point> treasurePoints) {
            HashMap<Point, TreasureIcon> keepingList = new HashMap<>();
            for (Point p : treasurePoints) {
                // No need to redraw the treasure that already in the cell
                if (mTreasures.containsKey(p)) {
                    keepingList.put(p, mTreasures.get(p));
                    continue;
                }

                Point targetCell = new Point(
                        mOriginPoint.x + (p.x * GRID_CELL_SIZE) + (p.x) * GRID_CELL_INTERVAL,
                        mOriginPoint.y + (p.y * GRID_CELL_SIZE) + (p.y) * GRID_CELL_INTERVAL
                );
                TreasureIcon treasureIcon = new TreasureIcon(targetCell);
                treasureIcon.setBounds((GRID_CELL_SIZE - treasureIcon.getBoundingBoxWidth()) / 2,
                        (GRID_CELL_SIZE - treasureIcon.getBoundingBoxHeight()) / 2,
                        mGridPixelWidth,
                        mGridPixelHeight);
                keepingList.put(p, treasureIcon);
                mJLayerPane.add(treasureIcon, GRID_LEVEL_TREASURE);
            }
            for (Point p : mTreasures.keySet()) {
                if (keepingList.containsKey(p)) {
                    continue;
                }
                mJLayerPane.remove(mTreasures.get(p));
            }
            mTreasures = keepingList;
            mJLayerPane.validate();
            mJLayerPane.repaint();
        }

        private JLabel createCell(Point point) {
            JLabel label = new JLabel();
            label.setOpaque(true);
            label.setBackground(Color.getColor("Cell", 0x39A2DB));
            label.setBorder(BorderFactory.createLineBorder(Color.getColor("Border", 0x053742)));
            label.setPreferredSize(new Dimension(GRID_CELL_SIZE, GRID_CELL_SIZE));
            label.setBounds(point.x, point.y, GRID_CELL_SIZE, GRID_CELL_SIZE);
            return label;
        }

        static class TreasureIcon extends JComponent {
            static final int TREASURE_PADDING_SIZE = 20;
            static final int TREASURE_SIZE = GRID_CELL_SIZE - TREASURE_PADDING_SIZE;
            private final Point mPos;
            private final int mOffsetX;
            private final int mOffsetY;

            TreasureIcon(Point pos) {
                mPos = pos;
                // Distance to original x; always be Used
                int rho = TREASURE_SIZE / 2 + TREASURE_SIZE / 4;
                mOffsetX = (int) (rho * Math.cos(-Math.PI / 5));
                mOffsetY = (int) (rho * Math.sin(-Math.PI / 5));
            }

            int getBoundingBoxWidth() {
                return TREASURE_SIZE + Math.abs(mOffsetX);
            }

            int getBoundingBoxHeight() {
                return TREASURE_SIZE + mOffsetY;
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D painter = (Graphics2D) g;
                drawCube(painter);
            }

            private void drawCube(Graphics2D painter) {
                // ShiningYellow
                painter.setColor(Color.getColor("ShiningYellow", 0xF7FD04));
                painter.fillRoundRect(mPos.x, mPos.y, TREASURE_SIZE, TREASURE_SIZE, 2, 2);
                painter.setColor(Color.getColor("DarkBorder", 0x345B63));
                painter.setStroke(new BasicStroke(1));
                painter.drawRoundRect(mPos.x, mPos.y, TREASURE_SIZE, TREASURE_SIZE, 2, 2);

                Polygon faceB = new Polygon();
                painter.setColor(Color.getColor("UpperFace", 0xF98404));
                faceB.addPoint(mPos.x, mPos.y);
                faceB.addPoint(mPos.x + mOffsetX, mPos.y + mOffsetY);
                faceB.addPoint(mPos.x + mOffsetX + TREASURE_SIZE, mPos.y + mOffsetY);
                faceB.addPoint(mPos.x + TREASURE_SIZE, mPos.y);
                painter.fillPolygon(faceB);
                drawStroke(painter, faceB);

                Polygon faceC = new Polygon();
                painter.setColor(Color.getColor("RightFace", 0xF9B208));
                faceC.addPoint(mPos.x + mOffsetX + TREASURE_SIZE, mPos.y + mOffsetY);
                faceC.addPoint(mPos.x + mOffsetX + TREASURE_SIZE, mPos.y + mOffsetY + TREASURE_SIZE);
                faceC.addPoint(mPos.x + TREASURE_SIZE, mPos.y + TREASURE_SIZE);
                faceC.addPoint(mPos.x + TREASURE_SIZE, mPos.y);
                painter.fillPolygon(faceC);
                drawStroke(painter, faceC);

                // Draw a little lock
                int middle = TREASURE_SIZE / 2;
                painter.setFont(new Font("Default", Font.PLAIN, TREASURE_SIZE));
                painter.drawString("*", mPos.x + (middle - middle / 3), mPos.y + TREASURE_SIZE);
            }

            private void drawStroke(Graphics2D painter, Polygon target) {
                painter.setColor(Color.getColor("DarkBorder", 0x345B63));
                painter.setStroke(new BasicStroke(1));
                painter.drawPolygon(target);
            }
        }
    }

    protected static class InfoBox extends JPanel {

        private static final int INFOBOX_WIDTH = 150;
        private static final String INFOBOX_TITLE = "Scores";
        private HashMap<String, JLabel> mScores = new HashMap<>();

        InfoBox() {
            setBorder(BorderFactory.createTitledBorder(INFOBOX_TITLE));
            setLayout(new GridLayout(10, 1));
            setPreferredSize(new Dimension(INFOBOX_WIDTH, 150));
            setBounds(0, 0, INFOBOX_WIDTH, 150);
            setAlignmentY(Component.TOP_ALIGNMENT);
        }

        void updateScores(HashMap<String, String> scores) {
            HashMap<String, JLabel> keepingList = new HashMap<>();
            for (String playerName : scores.keySet()) {
                String scoreText = scores.get(playerName);
                if (mScores.containsKey(playerName)) {
                    JLabel scoreLabel = mScores.get(playerName);
                    scoreLabel.setText(scores.get(playerName));
                    keepingList.put(playerName, scoreLabel);
                    continue;
                }
                JLabel scoreLabel = new JLabel(scoreText);
                this.add(scoreLabel);
                keepingList.put(playerName, scoreLabel);
            }

            for (String playerName : mScores.keySet()) {
                if (keepingList.containsKey(playerName)) {
                    continue;
                }
                this.remove(mScores.get(playerName));
            }
            mScores = keepingList;
            this.validate();
            this.repaint();
        }
    }
}
