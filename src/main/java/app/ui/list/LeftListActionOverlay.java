package app.ui.list;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Универсальный оверлей для левого списка:
 * - select-all checkbox + action button
 * - scrollSpacerPx для spacer-row, чтобы низ списка не перекрывался
 * - open/close/toggle + openChanged/spacerChanged callbacks
 *
 * Разметка/стили сделаны по "канону" оверлея тесткейсов:
 * tc-filter-overlay + tc-trash-glass, чекбокс tc-trash-check, кнопка tc-filter-apply + tc-trash-delete.
 *
 * IMPORTANT: sizing должен быть 1-в-1 как TestCasesTrashOverlay, иначе overlayRoot может растягиваться
 * на всю высоту StackPane и ломать кликабельность/верстку.
 */
public final class LeftListActionOverlay {

    private static final double OVERLAY_MARGIN_BOTTOM = 12.0;
    private static final double EXTRA_SCROLL = 18.0;

    private static final double OVERLAY_SIDE_INSET = 12.0;
    private static final double ACTION_BTN_W = 220.0;

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
    private Button btnAction;

    private double scrollSpacerPx = 0.0;

    private Runnable onSpacerChanged = () -> {};
    private Consumer<Boolean> onOpenChanged = v -> {};
    private boolean lastNotifiedOpen = false;

    private Runnable onAction = () -> {};

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
        Platform.runLater(() -> {
            applyAnchorWidthNow();
            recalcSpacerNow();
        });
    };
    // ================================================================

    private String buttonText;

    public LeftListActionOverlay(StackPane leftStack, Region anchor, String buttonText) {
        this.leftStack = leftStack;
        this.anchor = anchor;
        this.buttonText = buttonText == null ? "" : buttonText;
    }

    /**
     * Можно вызывать в любом порядке относительно init()/open():
     * listeners снимутся со старого anchor и повесится на новый.
     */
    public void setAnchor(Region newAnchor) {
        if (this.anchor == newAnchor) {
            if (open) Platform.runLater(this::applyAnchorWidthNow);
            return;
        }

        uninstallAnchorListeners();
        this.anchor = newAnchor;
        installAnchorListenersIfPossible();

        if (open) Platform.runLater(this::applyAnchorWidthNow);
    }

    public void init(Button btnToggle) {
        ensureUi();

        if (btnToggle != null) {
            btnToggle.setOnAction(e -> toggle());
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

    public boolean isOpen() {
        return open;
    }

    public double scrollSpacerPx() {
        return open ? scrollSpacerPx : 0.0;
    }

    public StackPane overlayRoot() {
        ensureUi();
        return overlayRoot;
    }

    public CheckBox selectAllCheckBox() {
        ensureUi();
        return cbSelect;
    }

    public void setButtonText(String text) {
        this.buttonText = text == null ? "" : text;
        ensureUi();
        if (btnAction != null) btnAction.setText(this.buttonText);
    }

    public void setOnAction(Runnable r) {
        this.onAction = (r == null) ? () -> {} : r;
        ensureUi();
        if (btnAction != null) btnAction.setOnAction(e -> this.onAction.run());
    }

    // алиасы (чтобы не плодить разные API по проекту)
    public void setOnDelete(Runnable r) { setOnAction(r); }
    public void setOnAdd(Runnable r) { setOnAction(r); }
    public void setDeleteEnabled(boolean enabled) { setActionEnabled(enabled); }
    public void setAddEnabled(boolean enabled) { setActionEnabled(enabled); }

    public void setActionEnabled(boolean enabled) {
        ensureUi();
        if (btnAction == null) return;

        btnAction.setDisable(!enabled);

        if (!enabled) {
            if (!btnAction.getStyleClass().contains(DISABLED_BASE_CLASS)) {
                btnAction.getStyleClass().add(DISABLED_BASE_CLASS);
            }
        } else {
            btnAction.getStyleClass().remove(DISABLED_BASE_CLASS);
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

    // ===================== INTERNAL =====================

    private void ensureUi() {
        if (leftStack == null) return;
        if (overlayRoot != null) return;

        cbSelect = new CheckBox();
        cbSelect.getStyleClass().add("tc-trash-check");
        cbSelect.setFocusTraversable(false);

        btnAction = new Button(buttonText);
        btnAction.getStyleClass().add("tc-filter-apply");
        btnAction.getStyleClass().add("tc-trash-delete");
        btnAction.setFocusTraversable(false);

        btnAction.setMinWidth(ACTION_BTN_W);
        btnAction.setPrefWidth(ACTION_BTN_W);
        btnAction.setMaxWidth(ACTION_BTN_W);

        btnAction.setOnAction(e -> onAction.run());

        row = new StackPane();
        row.setPickOnBounds(false);

        StackPane.setAlignment(cbSelect, Pos.CENTER_LEFT);
        StackPane.setAlignment(btnAction, Pos.CENTER);
        StackPane.setMargin(cbSelect, new Insets(0, 0, 0, 2));

        row.getChildren().addAll(cbSelect, btnAction);

        // ✅ 1-в-1 как TestCasesTrashOverlay
        overlay = new VBox(row);
        overlay.setAlignment(Pos.CENTER);
        overlay.setPickOnBounds(false);

        overlay.getStyleClass().add("tc-filter-overlay");
        overlay.getStyleClass().add("tc-trash-glass");

        // ✅ КРИТИЧНО: overlayRoot должен быть StackPane(overlay), а не пустой StackPane,
        // иначе его Skin может тянуться на всю высоту родителя.
        overlayRoot = new StackPane(overlay);
        overlayRoot.setPickOnBounds(false);

        // ✅ КРИТИЧНО: sizing как в TestCasesTrashOverlay
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

        installLeftStackListenersIfNeeded();
        installAnchorListenersIfPossible();
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
            notifyOpenIfNeeded();

            // не трогаем сами чекбоксы строк — только select-all
            try { if (cbSelect != null) cbSelect.setSelected(false); } catch (Exception ignored) {}

            onSpacerChanged.run();
        }
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

        if (Math.abs(scrollSpacerPx - before) > 0.5) onSpacerChanged.run();
    }

    private void applyAnchorWidthNow() {
        if (overlayRoot == null || overlay == null || leftStack == null) return;

        double targetW;
        if (anchor != null) {
            targetW = anchor.getWidth();
            if (targetW <= 0) targetW = anchor.prefWidth(-1);
        } else {
            targetW = leftStack.getWidth();
        }

        if (targetW <= 0) return;

        double w = Math.max(1, targetW - OVERLAY_SIDE_INSET * 2);

        overlay.setPrefWidth(w);
        overlay.setMinWidth(w);
        overlay.setMaxWidth(w);

        StackPane.setMargin(overlayRoot, new Insets(0, 0, OVERLAY_MARGIN_BOTTOM, 0));

        Platform.runLater(this::recalcSpacerNow);
    }

    private void notifyOpenIfNeeded() {
        if (lastNotifiedOpen == open) return;
        lastNotifiedOpen = open;
        onOpenChanged.accept(open);
    }

    private void installLeftStackListenersIfNeeded() {
        if (leftStackListenersInstalled) return;
        if (leftStack == null) return;

        leftStackListenersInstalled = true;
        leftStack.widthProperty().addListener(onLeftStackSize);
        leftStack.heightProperty().addListener(onLeftStackSize);
    }

    private void installAnchorListenersIfPossible() {
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
}
