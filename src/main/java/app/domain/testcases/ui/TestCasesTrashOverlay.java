// FILE: src/main/java/app/domain/testcases/ui/TestCasesTrashOverlay.java
package app.domain.testcases.ui;

import app.core.I18n;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public final class TestCasesTrashOverlay {

    private static final double OVERLAY_MARGIN_BOTTOM = 12.0;
    private static final double EXTRA_SCROLL = 18.0;

    private static final double OVERLAY_SIDE_INSET = 12.0;

    private static final double DELETE_BTN_W = 220.0;

    // ✅ общий модификатор disabled (универсально по проекту)
    private static final String DISABLED_BASE_CLASS = "tc-disabled-base";

    private final StackPane leftStack;

    // ✅ anchor: контейнер, ширину которого повторяем (tc-cases-sheet)
    private Region anchor;

    private boolean open = false;

    private StackPane overlayRoot;

    private VBox overlay;

    private StackPane row;
    private CheckBox cbSelect;
    private Button btnDelete;

    private double scrollSpacerPx = 0.0;

    private Runnable onSpacerChanged = () -> {};

    private Consumer<Boolean> onOpenChanged = v -> {};
    private boolean lastNotifiedOpen = false;

    private Runnable onDelete = () -> {};

    // ===================== LISTENERS (архитектурно) =====================
    private boolean leftStackListenersInstalled = false;
    private boolean anchorListenersInstalled = false;

    private final ChangeListener<Number> onLeftStackSize = (o, a, b) -> {
        if (!open) return;
        Platform.runLater(() -> {
            applyAnchorWidthNow();
            recalcSpacerNow();
        });
    };

    private final ChangeListener<Number> onAnchorSize = (o, a, b) -> {
        if (!open) return;
        Platform.runLater(this::applyAnchorWidthNow);
    };
    // ================================================================

    public TestCasesTrashOverlay(StackPane leftStack) {
        this.leftStack = leftStack;
    }

    /**
     * ✅ Архитектурно правильный вариант:
     * можно вызывать в любом порядке относительно init()/open(),
     * listeners снимутся со старого anchor и повесится на новый.
     */
    public void setAnchor(Region newAnchor) {
        if (this.anchor == newAnchor) {
            // если уже тот же объект — просто подтянуть ширину при открытом режиме
            if (open) Platform.runLater(this::applyAnchorWidthNow);
            return;
        }

        // снять listeners со старого anchor
        uninstallAnchorListeners();

        this.anchor = newAnchor;

        // если UI уже создан — ставим listeners на новый anchor
        // если UI ещё не создан — ensureUi() позже всё равно установит
        installAnchorListenersIfPossible();

        if (open) {
            Platform.runLater(this::applyAnchorWidthNow);
        }
    }

    public void init(Button btnTrash) {
        ensureUi();

        if (btnTrash != null) {
            btnTrash.setOnAction(e -> toggle());
        }

        Platform.runLater(this::recalcSpacerNow);
    }

    public void setOnSpacerChanged(Runnable r) {
        this.onSpacerChanged = (r == null) ? () -> {} : r;
    }

    public void setOnOpenChanged(Consumer<Boolean> c) {
        this.onOpenChanged = (c == null) ? (v) -> {} : c;
        notifyOpenIfNeeded();
    }

    public void setOnDelete(Runnable r) {
        this.onDelete = (r == null) ? () -> {} : r;
    }

    public boolean isOpen() {
        return open;
    }

    public double scrollSpacerPx() {
        return open ? scrollSpacerPx : 0.0;
    }

    public Node overlayRoot() {
        return overlayRoot;
    }

    public CheckBox selectAllCheckBox() {
        return cbSelect;
    }

    // ✅ канон: enabled/disabled + общий модификатор-класс (без inline-style)
    public void setDeleteEnabled(boolean enabled) {
        ensureUi();
        if (btnDelete == null) return;

        btnDelete.setDisable(!enabled);

        var classes = btnDelete.getStyleClass();
        if (!enabled) {
            if (!classes.contains(DISABLED_BASE_CLASS)) classes.add(DISABLED_BASE_CLASS);
        } else {
            classes.remove(DISABLED_BASE_CLASS);
        }
    }

    public void open() {
        if (open) return;
        open = true;
        applyState();
    }

    public void close() {
        if (!open) return;
        open = false;
        applyState();
    }

    public void toggle() {
        open = !open;
        applyState();
    }

    private void ensureUi() {
        if (leftStack == null) return;

        if (overlayRoot == null) {

            cbSelect = new CheckBox();
            cbSelect.getStyleClass().add("tc-trash-check");
            cbSelect.setFocusTraversable(false);

            btnDelete = new Button(I18n.t("tc.trash.delete"));
            btnDelete.getStyleClass().add("tc-filter-apply");
            btnDelete.getStyleClass().add("tc-trash-delete");
            btnDelete.setFocusTraversable(false);

            btnDelete.setMinWidth(DELETE_BTN_W);
            btnDelete.setPrefWidth(DELETE_BTN_W);
            btnDelete.setMaxWidth(DELETE_BTN_W);

            btnDelete.setOnAction(e -> onDelete.run());

            row = new StackPane();
            row.setPickOnBounds(false);

            StackPane.setAlignment(cbSelect, Pos.CENTER_LEFT);
            StackPane.setAlignment(btnDelete, Pos.CENTER);
            StackPane.setMargin(cbSelect, new Insets(0, 0, 0, 2));

            row.getChildren().addAll(cbSelect, btnDelete);

            overlay = new VBox(row);
            overlay.setAlignment(Pos.CENTER);
            overlay.setPickOnBounds(false);

            overlay.getStyleClass().add("tc-filter-overlay");
            overlay.getStyleClass().add("tc-trash-glass");

            overlayRoot = new StackPane(overlay);
            overlayRoot.setPickOnBounds(false);

            overlayRoot.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            overlayRoot.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
            overlayRoot.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

            overlay.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            overlay.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
            overlay.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

            StackPane.setAlignment(overlayRoot, Pos.BOTTOM_CENTER);

            overlayRoot.setVisible(false);
            overlayRoot.setManaged(false);

            leftStack.getChildren().add(overlayRoot);

            // ✅ listeners: ставим один раз и без зависимости от порядка вызовов
            installLeftStackListenersIfNeeded();
            installAnchorListenersIfPossible();
        }
    }

    private void installLeftStackListenersIfNeeded() {
        if (leftStackListenersInstalled) return;
        if (leftStack == null) return;

        leftStackListenersInstalled = true;
        leftStack.widthProperty().addListener(onLeftStackSize);
        leftStack.heightProperty().addListener(onLeftStackSize);
    }

    private void installAnchorListenersIfPossible() {
        // ставим listeners только если UI уже создан (overlayRoot != null),
        // иначе смысла нет: applyAnchorWidthNow будет позже
        if (anchorListenersInstalled) return;
        if (overlayRoot == null) return;
        if (anchor == null) return;

        anchorListenersInstalled = true;
        anchor.widthProperty().addListener(onAnchorSize);
        anchor.heightProperty().addListener(onAnchorSize);
    }

    private void uninstallAnchorListeners() {
        if (!anchorListenersInstalled) return;

        try {
            if (anchor != null) {
                anchor.widthProperty().removeListener(onAnchorSize);
                anchor.heightProperty().removeListener(onAnchorSize);
            }
        } catch (Exception ignored) {
        } finally {
            anchorListenersInstalled = false;
        }
    }

    private void applyState() {
        ensureUi();

        if (overlayRoot == null) return;

        overlayRoot.setVisible(open);
        overlayRoot.setManaged(open);

        if (open) {
            Platform.runLater(() -> {
                installAnchorListenersIfPossible();

                applyAnchorWidthNow();
                recalcSpacerNow();
                notifyOpenIfNeeded();
            });
        } else {
            scrollSpacerPx = 0.0;
            onSpacerChanged.run();
            notifyOpenIfNeeded();
        }
    }

    private void notifyOpenIfNeeded() {
        if (lastNotifiedOpen == open) return;
        lastNotifiedOpen = open;
        onOpenChanged.accept(open);
    }

    private void recalcSpacerNow() {
        double before = scrollSpacerPx;

        if (!open || overlayRoot == null || !overlayRoot.isVisible() || !overlayRoot.isManaged() || row == null) {
            scrollSpacerPx = 0.0;
        } else {
            double h = overlayRoot.getHeight();
            if (h <= 0) h = overlayRoot.prefHeight(-1);
            if (h <= 0) h = 52;

            scrollSpacerPx = h + OVERLAY_MARGIN_BOTTOM + EXTRA_SCROLL;
        }

        if (Math.abs(scrollSpacerPx - before) > 0.5) {
            onSpacerChanged.run();
        }
    }

    private void applyAnchorWidthNow() {
        if (overlayRoot == null || overlay == null || leftStack == null) return;

        double sideInset = OVERLAY_SIDE_INSET;

        double targetW;
        if (anchor != null) {
            targetW = anchor.getWidth();
            if (targetW <= 0) targetW = anchor.prefWidth(-1);
        } else {
            targetW = leftStack.getWidth();
        }

        if (targetW <= 0) return;

        double w = Math.max(1, targetW - sideInset * 2);

        overlay.setPrefWidth(w);
        overlay.setMinWidth(w);
        overlay.setMaxWidth(w);

        StackPane.setMargin(overlayRoot, new Insets(0, 0, OVERLAY_MARGIN_BOTTOM, 0));

        Platform.runLater(this::recalcSpacerNow);
    }
}
