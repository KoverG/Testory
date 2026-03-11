package app.domain.cycles.ui.right;

import app.ui.UiSvg;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.awt.Desktop;
import java.net.URI;

/**
 * Build link chip used inside EnvironmentModal (Builds).
 *
 * Visual style is the same as TaskLinkChip (cy-task-link-chip),
 * but behavior is simpler: click opens URL in browser.
 */
final class BuildLinkChip extends HBox {

    private static final double ICON_W = 28.0;

    /**
     * ВАЖНО для FlowPane:
     * если у чипа будет "бесконечная" prefWidth из-за длинного текста,
     * он начнет ломать раскладку (вылезать/неправильно считать перенос).
     * Поэтому задаем ограничение на ширину чипа и текста.
     */
    private static final double MAX_CHIP_W = 190.0;
    private static final double MAX_TEXT_W = MAX_CHIP_W - ICON_W - 10.0;

    private final Button iconBtn = new Button();
    private final Label text = new Label();
    private final Region spacer = new Region();

    private Tooltip urlTooltip;

    private final String url;

    BuildLinkChip(String title, String url) {
        this.url = safe(url);

        getStyleClass().add("cy-task-link-chip");
        setAlignment(Pos.CENTER_LEFT);

        // ограничиваем ширину чипа, чтобы длинный текст не вылезал и правильно работал перенос в FlowPane
        setMaxWidth(MAX_CHIP_W);

        iconBtn.getStyleClass().addAll("icon-btn", "xs");
        iconBtn.getStyleClass().add("cy-task-link-icon");

        iconBtn.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-background-insets: 0;" +
                        "-fx-border-color: transparent;" +
                        "-fx-border-width: 0;"
        );

        iconBtn.setFocusTraversable(false);
        iconBtn.setCursor(Cursor.HAND);

        iconBtn.setMinWidth(ICON_W);
        iconBtn.setPrefWidth(ICON_W);
        iconBtn.setMaxWidth(ICON_W);

        text.getStyleClass().add("cy-task-link-text");

        // ✅ ellipsis вместо выхода за границы
        text.setWrapText(false);
        text.setTextOverrun(OverrunStyle.ELLIPSIS);

        // важно: чтобы label мог сжиматься внутри HBox
        text.setMinWidth(0.0);
        text.setMaxWidth(MAX_TEXT_W);
        HBox.setHgrow(text, Priority.ALWAYS);

        spacer.setMinWidth(0);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(text, spacer, iconBtn);

        setLink(title, this.url);

        // click anywhere opens url (not only icon)
        addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (e.getClickCount() != 1) return;
            openUrlSafe(this.url);
            e.consume();
        });

        iconBtn.setOnAction(e -> {
            openUrlSafe(this.url);
            e.consume();
        });
    }

    private void setLink(String title, String url) {
        String t = safe(title);
        String u = safe(url);

        text.setText(t.isBlank() ? u : t);

        UiSvg.setButtonSvg(iconBtn, "link-open.svg", 12);

        installTooltip(u);
    }

    // ===================== TOOLTIP =====================

    private void installTooltip(String url) {
        uninstallTooltip();

        String u = safe(url);
        if (u.isBlank()) return;

        urlTooltip = new Tooltip(u);

        Tooltip.install(this, urlTooltip);
        Tooltip.uninstall(iconBtn, urlTooltip);
    }

    private void uninstallTooltip() {
        if (urlTooltip != null) {
            try { Tooltip.uninstall(this, urlTooltip); } catch (Exception ignore) {}
            try { Tooltip.uninstall(iconBtn, urlTooltip); } catch (Exception ignore) {}
        }
        urlTooltip = null;
    }

    // ===================== URL OPEN =====================

    private static void openUrlSafe(String url) {
        try {
            String u = safe(url);
            if (u.isBlank()) return;
            if (!Desktop.isDesktopSupported()) return;
            Desktop.getDesktop().browse(new URI(u));
        } catch (Exception ignore) {
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
