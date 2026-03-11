package app.ui;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

/**
 * Shared helper for auto-growing TextArea (no scrollbars, height grows with wrapped content).
 *
 * Based on the proven implementation from TestCaseRightPane.taRightDescription.
 *
 * Notes:
 * - Uses Text measurer with wrappingWidth to estimate visual line count.
 * - Updates prefHeight only when delta is meaningful (avoids jitter).
 * - Optionally calls afterRecalc (scheduled via Platform.runLater) so callers can relayout containers/overlays.
 */
public final class UiAutoGrowTextArea {

    private static final String PROP_BASE_PREF_H = "autoGrowBasePrefHeight";

    private UiAutoGrowTextArea() {}

    /**
     * Installs "wrap-aware" auto-grow for a TextArea.
     *
     * @param ta             target TextArea
     * @param basePrefHeight minimal preferred height (usually initial prefHeight or 32 for one-line)
     * @param afterRecalc    optional callback (e.g. requestRelayout for overlays); called after recalculation
     */
    public static void installWrapAutoGrow(TextArea ta, double basePrefHeight, Runnable afterRecalc) {
        if (ta == null) return;

        // store base pref height
        ta.getProperties().put(PROP_BASE_PREF_H, basePrefHeight);

        // ensure control follows prefHeight (important for real height changes)
        ta.setMinHeight(Region.USE_PREF_SIZE);
        ta.setMaxHeight(Region.USE_PREF_SIZE);

        // one measurer per instance
        Text measurer = new Text();
        measurer.setFont(ta.getFont());

        Runnable recalc = () -> {
            Object v = ta.getProperties().get(PROP_BASE_PREF_H);
            double basePref = (v instanceof Number) ? ((Number) v).doubleValue() : basePrefHeight;

            String txt = ta.getText();
            if (txt == null) txt = "";
            if (txt.isEmpty()) txt = " "; // keep non-zero height

            double w = ta.getWidth();
            if (w <= 0) return;

            double inW = ta.snappedLeftInset() + ta.snappedRightInset();
            double inH = ta.snappedTopInset() + ta.snappedBottomInset();

            double wrapW = Math.max(0.0, w - inW - 2.0);
            measurer.setFont(ta.getFont());
            measurer.setWrappingWidth(wrapW);

            double lineH = computeLineHeight(measurer);

            int visualLines = countVisualLines(txt, measurer, wrapW);
            double textH = Math.max(1, visualLines) * lineH;

            double target = Math.max(basePref, Math.ceil(textH + inH + 2.0));

            double cur = ta.getPrefHeight();
            if (Math.abs(cur - target) > 0.5) {
                ta.setPrefHeight(target);

                // guard: if TextArea internally scrolled due to height change, return to top
                Platform.runLater(() -> {
                    try { ta.setScrollTop(0); } catch (Exception ignore) {}
                });
            }

            if (afterRecalc != null) {
                Platform.runLater(afterRecalc);
            }
        };

        ta.textProperty().addListener((obs, ov, nv) -> recalc.run());
        ta.widthProperty().addListener((obs, ov, nv) -> recalc.run());
        ta.fontProperty().addListener((obs, ov, nv) -> recalc.run());

        Platform.runLater(recalc);
    }

    public static void setBasePrefHeight(TextArea ta, double basePrefHeight) {
        if (ta == null) return;
        ta.getProperties().put(PROP_BASE_PREF_H, basePrefHeight);
    }

    private static double computeLineHeight(Text measurer) {
        measurer.setText("Ay");
        return measurer.getLayoutBounds().getHeight();
    }

    /**
     * Counts number of "visual lines" with wrap, based on Text layout bounds.
     */
    private static int countVisualLines(String txt, Text measurer, double wrapW) {
        String[] hard = txt.split("\n", -1);

        int lines = 0;
        for (String part : hard) {
            if (part.isEmpty()) {
                lines += 1;
                continue;
            }

            measurer.setText(part);

            double h = measurer.getLayoutBounds().getHeight();
            double lineH = computeLineHeight(measurer);

            int wrapped = (int) Math.ceil(h / Math.max(1.0, lineH));
            lines += Math.max(1, wrapped);
        }

        return Math.max(1, lines);
    }
}
