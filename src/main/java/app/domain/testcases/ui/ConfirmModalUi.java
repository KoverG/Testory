// FILE: src/main/java/app/domain/testcases/ui/ConfirmModalUi.java
package app.domain.testcases.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

public final class ConfirmModalUi {

    private ConfirmModalUi() {}

    /**
     * Универсальная настройка модалки подтверждения:
     * - фикс ширина
     * - высота по контенту (prefHeight computed)
     * - НЕ растягивать на весь экран (maxHeight USE_PREF_SIZE)
     * - заголовок/подсказка: wrap + центр
     */
    public static void apply(VBox modal, double modalW, double minH) {
        if (modal == null) return;

        modal.setAlignment(Pos.CENTER);

        // ✅ фикс ширина
        modal.setMinWidth(modalW);
        modal.setPrefWidth(modalW);
        modal.setMaxWidth(modalW);

        // ✅ высота по контенту (и НЕ растягиваться до экрана)
        modal.setMinHeight(minH);
        modal.setPrefHeight(Region.USE_COMPUTED_SIZE);
        modal.setMaxHeight(Region.USE_PREF_SIZE);

        // wrap+центр для всех label внутри
        applyToLabels(modal, modalW);
    }

    public static void applyToLabels(Node root, double modalW) {
        if (root == null) return;

        if (root instanceof Label lb) {
            tuneLabel(lb, modalW);
            return;
        }

        if (root instanceof Region r) {
            for (Node ch : r.getChildrenUnmodifiable()) {
                applyToLabels(ch, modalW);
            }
        }
    }

    private static void tuneLabel(Label lb, double modalW) {
        lb.setMaxWidth(Math.max(1.0, modalW - 28.0));
        lb.setWrapText(true);

        lb.setAlignment(Pos.CENTER);
        lb.setTextAlignment(TextAlignment.CENTER);

        // ✅ без ellipsis — переносами
        lb.setTextOverrun(OverrunStyle.CLIP);
    }
}
