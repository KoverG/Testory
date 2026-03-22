package app.domain.reports.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

/**
 * Кольцевая диаграмма на Canvas.
 * Цвета совпадают с rp-cap-* / rp-badge-* из report-card.css.
 */
public final class DonutChart extends Canvas {

    public record Slice(String colorKey, double value) {}

    private static final double SIZE      = 110;
    private static final double THICKNESS = 20;
    private static final double GAP_DEG   = 3.0;

    // Цвета совпадают с capsule/badge цветами
    private static final Color C_PASSED   = Color.web("#27ae60");
    private static final Color C_BUGS     = Color.web("#f39c12");
    private static final Color C_FAILED   = Color.web("#e74c3c");
    private static final Color C_CRITICAL = Color.web("#c0392b");
    private static final Color C_SKIPPED  = Color.web("#7f8c8d");
    private static final Color C_PROGRESS = Color.web("#2980b9");
    private static final Color C_UNKNOWN  = Color.web("#888888");

    public DonutChart() {
        super(SIZE, SIZE);
    }

    public void draw(List<Slice> slices, int total) {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, SIZE, SIZE);

        double cx = SIZE / 2.0;
        double cy = SIZE / 2.0;
        double r  = (SIZE - THICKNESS) / 2.0 - 2;

        // Линейные концы BUTT — без наложения соседних сегментов
        gc.setLineCap(StrokeLineCap.BUTT);
        gc.setLineWidth(THICKNESS);

        double sum = slices.stream().mapToDouble(Slice::value).sum();
        if (sum <= 0) {
            drawEmpty(gc, cx, cy, r);
            return;
        }

        int count = slices.size();
        double startAngle = -90.0;

        for (Slice slice : slices) {
            double fullSweep = (slice.value() / sum) * 360.0;
            double gap = count > 1 ? GAP_DEG : 0.0;
            double drawSweep = Math.max(1.0, fullSweep - gap);

            gc.setStroke(sliceColor(slice.colorKey()));
            gc.strokeArc(
                    cx - r, cy - r, r * 2, r * 2,
                    startAngle + gap / 2.0,
                    drawSweep,
                    javafx.scene.shape.ArcType.OPEN
            );
            startAngle += fullSweep;
        }

        // Число в центре
        gc.setFill(Color.rgb(210, 210, 210));
        gc.setFont(Font.font("System", FontWeight.BOLD, 15));
        String text = String.valueOf(total);
        double approxWidth = text.length() * 8.5;
        gc.fillText(text, cx - approxWidth / 2.0, cy + 5.5);
    }

    private static void drawEmpty(GraphicsContext gc, double cx, double cy, double r) {
        gc.setStroke(Color.rgb(80, 80, 80, 0.3));
        gc.strokeArc(cx - r, cy - r, r * 2, r * 2, 0, 360, javafx.scene.shape.ArcType.OPEN);
    }

    private static Color sliceColor(String colorKey) {
        if (colorKey == null) return C_UNKNOWN;
        return switch (colorKey) {
            case "passed"   -> C_PASSED;
            case "bugs"     -> C_BUGS;
            case "failed"   -> C_FAILED;
            case "critical" -> C_CRITICAL;
            case "skipped"  -> C_SKIPPED;
            case "progress" -> C_PROGRESS;
            default         -> C_UNKNOWN;
        };
    }
}
