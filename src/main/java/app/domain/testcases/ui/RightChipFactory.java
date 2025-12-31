// FILE: src/main/java/app/domain/testcases/ui/RightChipFactory.java
package app.domain.testcases.ui;

import app.ui.UiSvg;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public final class RightChipFactory {

    private final ScrollPane spRight;
    private final String iconClose;

    public RightChipFactory(ScrollPane spRight, String iconClose) {
        this.spRight = spRight;
        this.iconClose = iconClose;
    }

    public HBox createChipEllipsis(FlowPane host, String text) {
        HBox chip = new HBox(8);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("tc-tag-chip");

        chip.setMinWidth(0);

        Label t = new Label(text);
        t.getStyleClass().add("tc-tag-chip-text");

        t.setWrapText(false);
        t.setTextOverrun(OverrunStyle.ELLIPSIS);
        t.setMinWidth(0);
        HBox.setHgrow(t, Priority.ALWAYS);

        if (text != null && !text.isBlank()) {
            t.setTooltip(new Tooltip(text));
        }

        Button close = new Button();
        close.setFocusTraversable(false);
        close.getStyleClass().addAll("icon-btn", "tc-tag-chip-close");
        UiSvg.setButtonSvg(close, iconClose, 12);
        close.setText("");

        close.setOnAction(e -> host.getChildren().remove(chip));

        chip.getChildren().addAll(t, close);

        installChipEllipsisSizing(host, chip, t, close);

        return chip;
    }

    private void installChipEllipsisSizing(FlowPane host, HBox chip, Label textLabel, Button closeBtn) {
        if (host == null || chip == null || textLabel == null) return;

        Runnable applyWidth = () -> {
            double w = 0.0;

            if (spRight != null && spRight.getViewportBounds() != null) {
                w = spRight.getViewportBounds().getWidth();
            }
            if (w <= 0.0) {
                w = host.getWidth();
            }
            if (w <= 0.0) return;

            host.setPrefWrapLength(w);

            double available = Math.max(140.0, w - 24.0);

            chip.setMaxWidth(available);

            double closeW = 22.0;
            if (closeBtn != null) {
                double cw = closeBtn.getWidth();
                if (cw <= 0.0) cw = closeBtn.prefWidth(-1);
                if (cw > 0.0) closeW = cw;
            }

            double labelMax = Math.max(40.0, available - closeW - 8.0 - 18.0);
            textLabel.setMaxWidth(labelMax);
        };

        if (spRight != null) {
            spRight.viewportBoundsProperty().addListener((obs, ov, nv) -> applyWidth.run());
        }
        host.widthProperty().addListener((obs, ov, nv) -> applyWidth.run());

        Platform.runLater(applyWidth);
    }
}
