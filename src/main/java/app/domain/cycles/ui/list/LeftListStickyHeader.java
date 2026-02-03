package app.ui.list;

import app.core.I18n;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * Sticky header for left ListView (Cycles/Cases).
 *
 * Requirements:
 * - stays fixed while list scrolls (overlay)
 * - i18n via key
 * - background style must match LeftListActionOverlay "glass" (tc-filter-overlay + tc-trash-glass)
 *
 * NOTE: header is typically set as mouseTransparent=true so it doesn't block scroll/selection.
 */
public final class LeftListStickyHeader extends StackPane {

    private final Label title = new Label();

    private String titleKey = "";

    public LeftListStickyHeader() {
        getStyleClass().addAll(
                // ✅ same glass style as delete overlay in left list
                "tc-filter-overlay",
                "tc-trash-glass",

                // ✅ local fine-tuning
                "cy-left-list-header"
        );

        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(10, 12, 10, 12));

        // stable height so we can reserve space in ListView padding
        setMinHeight(42);
        setPrefHeight(42);
        setMaxHeight(42);

        title.getStyleClass().add("cy-left-list-header-title");
        title.setText("");

        getChildren().add(title);
    }

    public void setTitleKey(String i18nKey) {
        this.titleKey = (i18nKey == null) ? "" : i18nKey.trim();
        title.setText(I18n.t(this.titleKey));
    }

    public String titleKey() {
        return titleKey;
    }

    public double stableHeightPx() {
        // we intentionally keep stable pref height
        return getPrefHeight();
    }
}
