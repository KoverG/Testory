// FILE: src/main/java/app/domain/cycles/ui/left/TrashModeListSupport.java
package app.domain.cycles.ui.left;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Small UI helper to keep LeftPaneCoordinator lean:
 * - installs list cell factory with "trash-mode" checkbox behavior identical to TestCases list.
 * - supports spacer row(s).
 *
 * IMPORTANT:
 * ListView может коротко оставлять старые cells при смене items/cellFactory.
 * Поэтому здесь есть fail-safe от ClassCastException (не тот тип item).
 */
public final class TrashModeListSupport {

    private TrashModeListSupport() {}

    // copied from LeftPaneCoordinator canonical layout constants
    public static final double LEFT_SCROLL_RESERVE_PX = 10.0;
    public static final double LEFT_CONTENT_INSET_PX = 4.0;
    public static final double LEFT_EFFECTIVE_RESERVE_PX = LEFT_SCROLL_RESERVE_PX + LEFT_CONTENT_INSET_PX;

    public static final double TRASH_SHIFT_PX = 26.0;

    /**
     * ✅ NEW overload: supports TWO spacers:
     * - topSpacerId: offsets sticky header (scrolls away)
     * - spacerId: bottom spacer for overlay (existing behavior)
     */
    public static <T> void install(
            ListView<T> lv,
            ObservableList<T> viewItems,
            DoubleProperty trashShiftPx,
            Map<String, BooleanProperty> checks,
            String topSpacerId,
            Supplier<Double> topSpacerHeightSupplier,
            String spacerId,
            Supplier<Double> spacerHeightSupplier,
            Function<T, String> idGetter,
            Function<T, String> titleGetter,
            Runnable onSelectionChanged,
            Consumer<T> onNormalClick,
            Consumer<T> onTrashModeClick,
            BiPredicate<T, String> keepSelectionInTrashMode
    ) {
        if (lv == null) return;

        lv.setCellFactory(list -> new ListCell<>() {

            private final StackPane cellRoot = new StackPane();

            private final HBox row = new HBox();
            private final Label title = new Label();

            private final CheckBox cbTrashRow = new CheckBox();

            private String boundId = null;
            private BooleanProperty boundProp = null;

            private final ChangeListener<Boolean> onTrashChecked = (o, a, b) -> {
                if (onSelectionChanged != null) onSelectionChanged.run();
            };

            {
                cellRoot.setPickOnBounds(false);

                row.getStyleClass().add("tc-case-row");

                title.getStyleClass().add("tc-case-title");
                title.setMinWidth(0.0);
                title.setPrefWidth(0.0);
                title.setMaxWidth(Double.MAX_VALUE);
                title.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);

                HBox.setHgrow(title, Priority.ALWAYS);
                row.getChildren().add(title);

                var w = list.widthProperty()
                        .subtract(LEFT_EFFECTIVE_RESERVE_PX)
                        .subtract(trashShiftPx);

                row.minWidthProperty().bind(w);
                row.prefWidthProperty().bind(w);
                row.maxWidthProperty().bind(w);

                row.translateXProperty().bind(trashShiftPx.add(LEFT_CONTENT_INSET_PX));

                cbTrashRow.getStyleClass().add("tc-trash-check");
                cbTrashRow.setFocusTraversable(false);

                cbTrashRow.managedProperty().bind(trashShiftPx.greaterThan(0.5));
                cbTrashRow.visibleProperty().bind(trashShiftPx.greaterThan(0.5));

                cbTrashRow.opacityProperty().bind(
                        Bindings.when(trashShiftPx.lessThanOrEqualTo(0.5))
                                .then(0.0)
                                .otherwise(Bindings.min(1.0, trashShiftPx.divide(TRASH_SHIFT_PX)))
                );

                StackPane.setAlignment(cbTrashRow, javafx.geometry.Pos.CENTER_LEFT);
                StackPane.setAlignment(row, javafx.geometry.Pos.CENTER_LEFT);

                StackPane.setMargin(cbTrashRow, new Insets(0, 0, 0, 2));

                cellRoot.getChildren().addAll(cbTrashRow, row);

                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

                // click row toggles checkbox in trash-mode (same UX as cases)
                // + in normal mode: delegate click to opener (Cycles requirement)

                setOnMouseClicked(ev -> {
                    if (ev.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
                    if (ev.getClickCount() != 1) return;

                    Object t = ev.getTarget();
                    if (t instanceof Node n && isDescendantOf(n, cbTrashRow)) return;

                    T raw = getItem();
                    if (raw == null || isEmpty()) return;

                    // ✅ fail-safe (mode-switch may feed "wrong" item type into old cell)
                    final String id;
                    try {
                        id = safeTrim(idGetter.apply(raw));
                    } catch (Exception ex) {
                        return;
                    }

                    if (id.isEmpty()) return;
                    if (spacerId != null && spacerId.equals(id)) return;
                    if (topSpacerId != null && topSpacerId.equals(id)) return;

                    boolean inTrashMode = trashShiftPx.get() > 0.5;

                    if (inTrashMode) {
                        boolean keepSelection = keepSelectionInTrashMode != null
                                && keepSelectionInTrashMode.test(raw, id);
                        BooleanProperty p = checks.computeIfAbsent(id, k -> new SimpleBooleanProperty(false));
                        if (!keepSelection) {
                            p.set(!p.get());
                        }

                        if (onSelectionChanged != null) onSelectionChanged.run();
                        if (onTrashModeClick != null) onTrashModeClick.accept(raw);
                        return;
                    }

                    // normal mode click → open card
                    if (onNormalClick != null) {
                        try {
                            // держим selection синхронным (ListView не всегда успевает)
                            list.getSelectionModel().select(getIndex());
                        } catch (Exception ignored) {}
                        onNormalClick.accept(raw);
                    }
                });
            }

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    unbindTrashRow();
                    setGraphic(null);
                    title.setText("");
                    title.setTooltip(null);
                    setDisable(false);
                    return;
                }

                // ✅ fail-safe: если сюда прилетит "не тот" item, мы не падаем
                final String id;
                try {
                    id = safeTrim(idGetter.apply(item));
                } catch (Exception ex) {
                    unbindTrashRow();
                    setGraphic(null);
                    title.setText("");
                    title.setTooltip(null);
                    setDisable(false);
                    return;
                }

                // top spacer OR bottom spacer
                boolean isTopSpacer = topSpacerId != null && topSpacerId.equals(id);
                boolean isBottomSpacer = spacerId != null && spacerId.equals(id);

                if (isTopSpacer || isBottomSpacer) {
                    unbindTrashRow();

                    Region r = new Region();
                    double h = 1.0;

                    try {
                        Supplier<Double> sup = isTopSpacer ? topSpacerHeightSupplier : spacerHeightSupplier;
                        h = sup == null ? 1.0 : sup.get();
                    } catch (Exception ignored) {}

                    if (h <= 0) h = 1;

                    r.setMinHeight(h);
                    r.setPrefHeight(h);
                    r.setMaxHeight(h);

                    title.setTooltip(null);
                    setGraphic(r);
                    setDisable(true);
                    return;
                }

                setDisable(false);

                bindTrashRow(id);

                final String text;
                try {
                    text = safeTrim(titleGetter.apply(item));
                } catch (Exception ex) {
                    title.setText("");
                    title.setTooltip(null);
                    setGraphic(cellRoot);
                    return;
                }

                title.setText(text);
                setGraphic(cellRoot);

                if (text.isBlank()) {
                    title.setTooltip(null);
                    return;
                }

                final String shownText = text;

                Platform.runLater(() -> {
                    if (isLabelTextClipped(title)) {
                        Tooltip tt = title.getTooltip();
                        if (tt == null) tt = new Tooltip();
                        tt.setText(shownText);
                        title.setTooltip(tt);
                    } else {
                        title.setTooltip(null);
                    }
                });
            }

            private void bindTrashRow(String id) {
                if (id == null) return;

                if (id.equals(boundId) && boundProp != null) return;

                unbindTrashRow();

                boundId = id;

                BooleanProperty p = checks.computeIfAbsent(id, k -> new SimpleBooleanProperty(false));
                boundProp = p;

                cbTrashRow.selectedProperty().bindBidirectional(p);
                p.addListener(onTrashChecked);
            }

            private void unbindTrashRow() {
                if (boundProp != null) {
                    try { boundProp.removeListener(onTrashChecked); } catch (Exception ignored) {}
                }

                try { cbTrashRow.selectedProperty().unbindBidirectional(boundProp); } catch (Exception ignored) {}

                boundId = null;
                boundProp = null;

                try { cbTrashRow.setSelected(false); } catch (Exception ignored) {}
            }
        });

        // keep original items
        if (viewItems != null) lv.setItems(viewItems);
    }

    /**
     * ✅ Backward compatible overload for the full signature without trash-mode click callback.
     */
    public static <T> void install(
            ListView<T> lv,
            ObservableList<T> viewItems,
            DoubleProperty trashShiftPx,
            Map<String, BooleanProperty> checks,
            String topSpacerId,
            Supplier<Double> topSpacerHeightSupplier,
            String spacerId,
            Supplier<Double> spacerHeightSupplier,
            Function<T, String> idGetter,
            Function<T, String> titleGetter,
            Runnable onSelectionChanged,
            Consumer<T> onNormalClick
    ) {
        install(
                lv,
                viewItems,
                trashShiftPx,
                checks,
                topSpacerId,
                topSpacerHeightSupplier,
                spacerId,
                spacerHeightSupplier,
                idGetter,
                titleGetter,
                onSelectionChanged,
                onNormalClick,
                null,
                null
        );
    }

    /**
     * ✅ Backward compatible overload (old signature).
     * Treats single spacerId as bottom spacer only.
     */
    public static <T> void install(
            ListView<T> lv,
            ObservableList<T> viewItems,
            DoubleProperty trashShiftPx,
            Map<String, BooleanProperty> checks,
            String spacerId,
            Supplier<Double> spacerHeightSupplier,
            Function<T, String> idGetter,
            Function<T, String> titleGetter,
            Runnable onSelectionChanged,
            Consumer<T> onNormalClick
    ) {
        install(
                lv,
                viewItems,
                trashShiftPx,
                checks,
                null,
                null,
                spacerId,
                spacerHeightSupplier,
                idGetter,
                titleGetter,
                onSelectionChanged,
                onNormalClick,
                null,
                null
        );
    }
    public static <T> void ensureSpacerRow(
            ObservableList<T> viewItems,
            String spacerId,
            Function<T, String> idGetter,
            Supplier<Boolean> wantSpacer,
            Supplier<T> spacerFactory
    ) {
        if (viewItems == null) return;

        boolean need = wantSpacer != null && Boolean.TRUE.equals(wantSpacer.get());
        int idx = indexOfSpacer(viewItems, spacerId, idGetter);

        if (need) {
            if (idx < 0 && spacerFactory != null) viewItems.add(spacerFactory.get());
        } else {
            if (idx >= 0) viewItems.remove(idx);
        }
    }

    public static <T> int indexOfSpacer(ObservableList<T> list, String spacerId, Function<T, String> idGetter) {
        for (int i = 0; i < list.size(); i++) {
            T it = list.get(i);
            if (it == null) continue;

            final String id;
            try {
                id = safeTrim(idGetter.apply(it));
            } catch (Exception ex) {
                continue;
            }

            if (spacerId.equals(id)) return i;
        }
        return -1;
    }

    public static boolean isDescendantOf(Node target, Node container) {
        if (target == null || container == null) return false;

        Node cur = target;
        while (cur != null) {
            if (cur == container) return true;
            cur = cur.getParent();
        }
        return false;
    }

    public static boolean isLabelTextClipped(Label lbl) {
        if (lbl == null) return false;

        String s = lbl.getText();
        if (s == null || s.isEmpty()) return false;

        double w = lbl.getWidth();
        if (w <= 0) return false;

        Insets ins = lbl.getInsets();
        double avail = w - (ins == null ? 0.0 : (ins.getLeft() + ins.getRight())) - 2.0;
        if (avail <= 0) return false;

        Text t = new Text(s);
        t.setFont(lbl.getFont());
        double tw = t.getLayoutBounds().getWidth();

        return tw > avail + 0.5;
    }

    public static String safeTrim(String v) {
        return v == null ? "" : v.trim();
    }
}
