package com.catchmind.client;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

public class DrawingPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final int CANVAS_WIDTH = 800;
	private static final int CANVAS_HEIGHT = 500;

	private enum PenColor {
		RED("빨강", Color.RED), ORANGE("주황", new Color(0xFFA500)), YELLOW("노랑", Color.YELLOW),
		GREEN("초록", new Color(0x008000)), BLUE("파랑", Color.BLUE), NAVY("남색", new Color(0x000080)),
		PURPLE("보라", new Color(0x800080)), WHITE("흰색", Color.WHITE), BLACK("검정", Color.BLACK);

		private final String label;
		private final Color color;

		PenColor(String label, Color color) {
			this.label = label;
			this.color = color;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	record Segment(Point from, Point to, Color color, int width) {
	}

	private final List<Segment> segments = new ArrayList<>();
	private Point previousPoint;
	private boolean drawingStroke;
	private Color penColor = Color.BLACK;
	private int penWidth = 2;
	private boolean eraserMode;
	private boolean drawingAllowed = true;
	private Consumer<Segment> strokeAction = this::addSegment;
	private Runnable clearAction = this::clearDrawing;

	public DrawingPanel() {
		setBackground(new Color(0xEDF2F6));

		MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				if (drawingAllowed && SwingUtilities.isLeftMouseButton(event)) {
					previousPoint = toCanvasPoint(event.getPoint());
					drawingStroke = previousPoint != null;
				}
			}

			@Override
			public void mouseDragged(MouseEvent event) {
				if (!drawingStroke) {
					return;
				}

				Point currentPoint = toCanvasPoint(event.getPoint());

				if (previousPoint != null && currentPoint != null) {
					Color strokeColor = eraserMode ? Color.WHITE : penColor;
					int strokeWidth = eraserMode ? 16 : penWidth;
					strokeAction.accept(new Segment(previousPoint, currentPoint, strokeColor, strokeWidth));
				}

				previousPoint = currentPoint;
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				if (SwingUtilities.isLeftMouseButton(event)) {
					drawingStroke = false;
					previousPoint = null;
				}
			}
		};

		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	private Rectangle2D getCanvasBounds() {
		Insets insets = getInsets();
		int availableWidth = Math.max(0, getWidth() - insets.left - insets.right);
		int availableHeight = Math.max(0, getHeight() - insets.top - insets.bottom);

		double scale = Math.min((double) availableWidth / CANVAS_WIDTH, (double) availableHeight / CANVAS_HEIGHT);

		double width = CANVAS_WIDTH * scale;
		double height = CANVAS_HEIGHT * scale;

		return new Rectangle2D.Double(insets.left + (availableWidth - width) / 2,
				insets.top + (availableHeight - height) / 2, width, height);
	}

	private Point toCanvasPoint(Point panelPoint) {
		Rectangle2D bounds = getCanvasBounds();
		if (!bounds.contains(panelPoint)) {
			return null;
		}

		double scale = bounds.getWidth() / CANVAS_WIDTH;
		int x = Math.min(CANVAS_WIDTH - 1, (int) ((panelPoint.x - bounds.getX()) / scale));
		int y = Math.min(CANVAS_HEIGHT - 1, (int) ((panelPoint.y - bounds.getY()) / scale));
		return new Point(x, y);
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);

		Rectangle2D bounds = getCanvasBounds();
		if (bounds.isEmpty()) {
			return;
		}

		Graphics2D pen = (Graphics2D) graphics.create();
		try {
			pen.clip(bounds);
			pen.translate(bounds.getX(), bounds.getY());

			double scale = bounds.getWidth() / CANVAS_WIDTH;
			pen.scale(scale, scale);

			pen.setColor(Color.WHITE);
			pen.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

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

	void setDrawingAllowed(boolean drawingAllowed) {
		this.drawingAllowed = drawingAllowed;
		drawingStroke = false;
		previousPoint = null;
	}

	void setDrawingActions(Consumer<Segment> strokeAction, Runnable clearAction) {
	    this.strokeAction = strokeAction;
	    this.clearAction = clearAction;
	}

	void addSegment(Segment segment) {
	    segments.add(segment);
	    repaint();
	}

	void clearSegments() {
	    segments.clear();
	    repaint();
	}

	public void clearDrawing() {
	    drawingStroke = false;
	    previousPoint = null;
	    clearSegments();
	}

	JPanel createToolsPanel() {
		JPanel toolsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

		JComboBox<PenColor> colorSelect = new JComboBox<>(PenColor.values());
		colorSelect.setSelectedItem(PenColor.BLACK);

		colorSelect.addActionListener(event -> {
			PenColor selectedColor = (PenColor) colorSelect.getSelectedItem();
			if (selectedColor != null) {
				penColor = selectedColor.color;
			}
		});
		toolsPanel.add(new JLabel("색상"));
		toolsPanel.add(colorSelect);

		JComboBox<Integer> widthSelect = new JComboBox<>(new Integer[] { 2, 4, 8 });
		widthSelect.addActionListener(event -> {
			Integer width = (Integer) widthSelect.getSelectedItem();
			if (width != null) {
				penWidth = width;
			}
		});
		toolsPanel.add(new JLabel("선 굵기 (px)"));
		toolsPanel.add(widthSelect);

		JToggleButton eraserButton = new JToggleButton("지우개");
		eraserButton.addActionListener(event -> eraserMode = eraserButton.isSelected());
		toolsPanel.add(eraserButton);

		JButton clearButton = new JButton("전체 지우기");
		clearButton.addActionListener(event -> {
		    if (drawingAllowed) {
		        clearAction.run();
		    }
		});
		toolsPanel.add(clearButton);

		return toolsPanel;
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("낙서한판 - 그림판 미리보기");
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			DrawingPanel drawingPanel = new DrawingPanel();

			frame.add(drawingPanel, BorderLayout.CENTER);
			frame.add(drawingPanel.createToolsPanel(), BorderLayout.SOUTH);
			frame.setSize(720, 480);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}
}