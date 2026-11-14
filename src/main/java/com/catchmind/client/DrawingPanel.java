package com.catchmind.client;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class DrawingPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private record Segment(Point from, Point to) {}

    private final List<Segment> segments = new ArrayList<>();
    private Point previousPoint;

    public DrawingPanel() {
        setBackground(Color.WHITE);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    previousPoint = event.getPoint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (previousPoint == null) {
                    return;
                }

                Point currentPoint = event.getPoint();
                segments.add(new Segment(previousPoint, currentPoint));
                previousPoint = currentPoint;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    previousPoint = null;
                }
            }
        };

        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        graphics.setColor(Color.BLACK);

        for (Segment segment : segments) {
            Point from = segment.from();
            Point to = segment.to();
            graphics.drawLine(from.x, from.y, to.x, to.y);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("낙서한판 - 그림판 미리보기");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(new DrawingPanel());
            frame.setSize(720, 480);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}