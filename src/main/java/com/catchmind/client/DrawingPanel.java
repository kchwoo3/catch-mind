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
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import javax.swing.JButton;
import javax.swing.JToggleButton;

public class DrawingPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private record Segment(Point from, Point to, Color color, int width) {
	}

	private final List<Segment> segments = new ArrayList<>();
	private Point previousPoint;
	private Color penColor = Color.BLACK;
	private int penWidth = 2;
	private boolean eraserMode;

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
				Color strokeColor = eraserMode ? getBackground() : penColor;
				int strokeWidth = eraserMode ? 16 : penWidth;
				segments.add(new Segment(previousPoint, currentPoint, strokeColor, strokeWidth));
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

		Graphics2D pen = (Graphics2D) graphics.create();
		try {
			for (Segment segment : segments) {
				pen.setColor(segment.color());
				pen.setStroke(new BasicStroke(segment.width(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

				Point from = segment.from();
				Point to = segment.to();
				pen.drawLine(from.x, from.y, to.x, to.y);
			}
		} finally {
			pen.dispose();
		}
	}

	public void clearDrawing() {
		segments.clear();
		previousPoint = null;
		repaint();
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("낙서한판 - 그림판 미리보기");
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			DrawingPanel drawingPanel = new DrawingPanel();

			JComboBox<String> colorSelect = new JComboBox<>(new String[] { "검정", "빨강", "파랑" });
			colorSelect.addActionListener(event -> {
				switch (colorSelect.getSelectedIndex()) {
				case 1 -> drawingPanel.penColor = Color.RED;
				case 2 -> drawingPanel.penColor = Color.BLUE;
				default -> drawingPanel.penColor = Color.BLACK;
				}
			});

			JPanel toolsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			toolsPanel.add(new JLabel("색상"));
			toolsPanel.add(colorSelect);

			JComboBox<Integer> widthSelect = new JComboBox<>(new Integer[] { 2, 4, 8 });
			widthSelect.addActionListener(event -> {
				Integer width = (Integer) widthSelect.getSelectedItem();
				if (width != null) {
					drawingPanel.penWidth = width;
				}
			});

			JButton clearButton = new JButton("전체 지우기");
			clearButton.addActionListener(event -> drawingPanel.clearDrawing());

			toolsPanel.add(new JLabel("선 굵기 (px)"));
			toolsPanel.add(widthSelect);

			JToggleButton eraserButton = new JToggleButton("지우개");
			eraserButton.addActionListener(event -> {
				drawingPanel.eraserMode = eraserButton.isSelected();
			});

			toolsPanel.add(eraserButton);
			toolsPanel.add(clearButton);

			frame.add(drawingPanel, BorderLayout.CENTER);
			frame.add(toolsPanel, BorderLayout.SOUTH);
			frame.setSize(720, 480);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}
}