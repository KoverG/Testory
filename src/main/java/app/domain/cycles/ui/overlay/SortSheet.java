package app.domain.cycles.ui.overlay;

import app.core.I18n;
import app.ui.UiBlur;
import app.ui.UiScroll;
import app.ui.UiSvg;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public final class SortSheet {

    public enum Mode {
        CYCLES,
        CASES
    }

    private static final Duration ANIM = Duration.millis(220);
    private static final double SHEET_EXTRA_FROM_BOTTOM = 22.0;
    private static final double SORT_SHEET_RADIUS = 18.0;
    private static final Duration SIM_PRESS_MS = Duration.millis(120.0);

    private final StackPane leftStack;
    private final StackPane sortSheet;
    private final Node heightTarget;
    private final UiBlur blur = new UiBlur(Duration.millis(120), 10.0);

    private final List<String> cycleSortKeys = new ArrayList<>();
    private final List<String> caseSortKeys = new ArrayList<>();

    private Runnable onBeforeOpen = () -> {};
    private Runnable onSortChanged = () -> {};

    private boolean open = false;
    private ParallelTransition sortAnim;
    private Mode mode = Mode.CYCLES;

    private int appliedCycleSortIndex = 0;
    private int appliedCaseSortIndex = 0;

    private Node cardRoot;
    private Node sortToggleNode;

    public SortSheet(StackPane leftStack, StackPane sortSheet, Node blurTarget) {
        this.leftStack = leftStack;
        this.sortSheet = sortSheet;
        this.heightTarget = blurTarget;
        if (blurTarget != null) {
            blur.setTargets(blurTarget);
        }
    }

    public void init() {
        if (sortSheet == null) return;

        if (!sortSheet.getStyleClass().contains("tc-sheet-filter-sort")) {
            sortSheet.getStyleClass().add("tc-sheet-filter-sort");
        }

        sortSheet.setVisible(false);
        sortSheet.setManaged(false);
        sortSheet.setOpacity(0.0);
        sortSheet.setTranslateY(0.0);
        sortSheet.setPickOnBounds(false);

        if (leftStack != null) {
            leftStack.heightProperty().addListener((obs, oldV, newV) -> {
                if (open) applySheetHeightNow();
            });

            Platform.runLater(() -> {
                if (leftStack.getScene() == null) return;
                leftStack.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
                leftStack.getScene().addEventFilter(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
            });
        }
    }

    public void setOnBeforeOpen(Runnable onBeforeOpen) {
        this.onBeforeOpen = onBeforeOpen == null ? () -> {} : onBeforeOpen;
    }

    public void setOnSortChanged(Runnable onSortChanged) {
        this.onSortChanged = onSortChanged == null ? () -> {} : onSortChanged;
    }

    public void setOutsideCloseConsumeTarget(Node sortToggleNode) {
        this.sortToggleNode = sortToggleNode;
    }

    public void toggleForCycles(List<String> sortKeys) {
        cycleSortKeys.clear();
        if (sortKeys != null) cycleSortKeys.addAll(sortKeys);
        toggle(Mode.CYCLES);
    }

    public void toggleForCases(List<String> sortKeys) {
        caseSortKeys.clear();
        if (sortKeys != null) caseSortKeys.addAll(sortKeys);
        toggle(Mode.CASES);
    }

    public void close() {
        if (!open) return;
        hideAnimated();
    }

    public void dismissImmediately() {
        stopAnimation();
        open = false;
        sortSheet.setVisible(false);
        sortSheet.setManaged(false);
        sortSheet.setTranslateY(0.0);
        sortSheet.setOpacity(0.0);
        blur.setActive(false);
    }

    public boolean isOpen() {
        return open;
    }

    public int appliedCycleSortIndex() {
        return normalizeIndex(appliedCycleSortIndex, cycleSortKeys);
    }

    public int appliedCaseSortIndex() {
        return normalizeIndex(appliedCaseSortIndex, caseSortKeys);
    }

    public String currentSortTextForCycles() {
        return currentSortText(Mode.CYCLES);
    }

    public String currentSortTextForCases() {
        return currentSortText(Mode.CASES);
    }

    private void toggle(Mode targetMode) {
        if (sortSheet == null || leftStack == null) return;

        if (open && mode == targetMode) {
            hideAnimated();
            return;
        }

        mode = targetMode;

        if (!open) {
            onBeforeOpen.run();
            showAnimated();
            return;
        }

        buildUi();
    }

    private void showAnimated() {
        open = true;
        buildUi();

        sortSheet.setVisible(true);
        sortSheet.setManaged(true);
        sortSheet.setOpacity(0.0);
        applySheetHeightNow();
        blur.setActive(true);

        Platform.runLater(() -> {
            double h = leftStack.getHeight();
            if (h <= 0) h = sortSheet.getHeight();
            if (h <= 0) h = 1.0;

            double fromY = h + SHEET_EXTRA_FROM_BOTTOM;
            sortSheet.setTranslateY(fromY);
            sortSheet.setOpacity(0.0);

            stopAnimation();

            TranslateTransition tt = new TranslateTransition(ANIM, sortSheet);
            tt.setFromY(fromY);
            tt.setToY(0.0);
            tt.setInterpolator(Interpolator.EASE_OUT);

            FadeTransition ft = new FadeTransition(ANIM, sortSheet);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            ft.setInterpolator(Interpolator.EASE_OUT);

            sortAnim = new ParallelTransition(tt, ft);
            sortAnim.play();
        });
    }

    private void hideAnimated() {
        double h = leftStack == null ? 0.0 : leftStack.getHeight();
        if (h <= 0) h = sortSheet.getHeight();
        if (h <= 0) h = 1.0;

        double toY = h + SHEET_EXTRA_FROM_BOTTOM;
        stopAnimation();

        TranslateTransition tt = new TranslateTransition(ANIM, sortSheet);
        tt.setFromY(sortSheet.getTranslateY());
        tt.setToY(toY);
        tt.setInterpolator(Interpolator.EASE_IN);

        FadeTransition ft = new FadeTransition(ANIM, sortSheet);
        ft.setFromValue(sortSheet.getOpacity());
        ft.setToValue(0.0);
        ft.setInterpolator(Interpolator.EASE_IN);

        sortAnim = new ParallelTransition(tt, ft);
        sortAnim.setOnFinished(e -> {
            open = false;
            sortSheet.setVisible(false);
            sortSheet.setManaged(false);
            sortSheet.setTranslateY(0.0);
            sortSheet.setOpacity(0.0);
            blur.setActive(false);
        });
        sortAnim.play();
    }

    private void buildUi() {
        sortSheet.getChildren().clear();

        VBox root = new VBox(10.0);
        root.setPadding(new Insets(12, 12, 12, 12));
        root.setFillWidth(true);
        root.setPickOnBounds(true);
        root.setOnMousePressed(e -> e.consume());

        StackPane titleCapsule = buildTitleCapsule();
        HBox topHeader = new HBox(titleCapsule);
        topHeader.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleCapsule, Priority.ALWAYS);

        VBox list = new VBox(8.0);
        List<String> keys = activeSortKeys();
        int selectedIndex = selectedIndex();

        for (int i = 0; i < keys.size(); i++) {
            final int idx = i;

            Label lbl = new Label(I18n.t(keys.get(i)));

            Label check = new Label();
            check.getStyleClass().add("tc-sort-check");
            Node checkIcon = UiSvg.createSvg("check.svg", 22);
            if (checkIcon != null) check.setGraphic(checkIcon);
            if (idx != selectedIndex) check.getStyleClass().add("off");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(10.0, lbl, spacer, check);
            row.setAlignment(Pos.CENTER_LEFT);

            Button item = new Button();
            item.setMaxWidth(Double.MAX_VALUE);
            item.getStyleClass().add("tc-sort-item");
            if (idx == selectedIndex) item.getStyleClass().add("selected");
            item.setGraphic(row);
            item.setText("");
            item.setFocusTraversable(false);
            item.setOnAction(e -> {
                applySelectedIndex(idx);
                onSortChanged.run();
                close();
            });

            list.getChildren().add(item);
        }

        root.getChildren().addAll(topHeader, list);

        StackPane card = new StackPane(root);
        StackPane.setAlignment(root, Pos.TOP_LEFT);

        sortSheet.getChildren().add(card);
        StackPane.setAlignment(card, Pos.BOTTOM_CENTER);
        cardRoot = card;

        UiScroll.clipRoundedSheet(sortSheet, SORT_SHEET_RADIUS);
    }

    private StackPane buildTitleCapsule() {
        Label title = new Label(mode == Mode.CYCLES ? I18n.t("cy.btn.toggle.cycles") : I18n.t("cy.btn.toggle.cases"));
        title.getStyleClass().add("cy-left-list-header-title");
        title.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");

        StackPane capsule = new StackPane(title);
        capsule.getStyleClass().addAll("tc-filter-overlay", "tc-trash-glass", "cy-left-list-header");
        capsule.setAlignment(Pos.CENTER_LEFT);
        capsule.setPadding(new Insets(0, 10, 0, 10));
        capsule.setStyle("-fx-min-height: 32px; -fx-pref-height: 32px; -fx-max-height: 32px;");
        capsule.setMaxWidth(Double.MAX_VALUE);
        return capsule;
    }
    private List<String> activeSortKeys() {
        return mode == Mode.CASES ? caseSortKeys : cycleSortKeys;
    }

    private int selectedIndex() {
        return mode == Mode.CASES
                ? normalizeIndex(appliedCaseSortIndex, caseSortKeys)
                : normalizeIndex(appliedCycleSortIndex, cycleSortKeys);
    }

    private void applySelectedIndex(int idx) {
        if (mode == Mode.CASES) {
            appliedCaseSortIndex = normalizeIndex(idx, caseSortKeys);
        } else {
            appliedCycleSortIndex = normalizeIndex(idx, cycleSortKeys);
        }
    }

    private String currentSortText(Mode targetMode) {
        List<String> keys = targetMode == Mode.CASES ? caseSortKeys : cycleSortKeys;
        if (keys.isEmpty()) return "";

        int idx = targetMode == Mode.CASES
                ? normalizeIndex(appliedCaseSortIndex, caseSortKeys)
                : normalizeIndex(appliedCycleSortIndex, cycleSortKeys);
        return I18n.t(keys.get(idx));
    }

    private static int normalizeIndex(int idx, List<String> keys) {
        if (keys == null || keys.isEmpty()) return 0;
        if (idx < 0) return 0;
        if (idx >= keys.size()) return keys.size() - 1;
        return idx;
    }

    private void applySheetHeightNow() {
        double base = 0.0;
        if (heightTarget instanceof Region region) {
            base = region.getHeight();
        } else if (heightTarget != null) {
            base = heightTarget.getLayoutBounds().getHeight();
        }
        if (base <= 0.0 && leftStack != null) base = leftStack.getHeight();
        if (base <= 0.0) return;

        double sheetHeight = Math.max(0.0, base - 14.0);
        sortSheet.setPrefHeight(sheetHeight);
        sortSheet.setMaxHeight(sheetHeight);
    }

    private void stopAnimation() {
        if (sortAnim != null) {
            sortAnim.stop();
            sortAnim = null;
        }
    }

    private void onKeyPressed(KeyEvent event) {
        if (!open) return;
        if (event.getCode() != KeyCode.ESCAPE) return;
        event.consume();
        close();
    }

    private void onMousePressed(MouseEvent event) {
        if (!open) return;
        if (!(event.getTarget() instanceof Node target)) return;
        if (isInside(target, cardRoot)) return;

        close();

        if (isInside(target, sortToggleNode)) {
            event.consume();
            pressFxOnlyLater(sortToggleNode);
        }
    }

    private static void pressFxOnlyLater(Node node) {
        if (!(node instanceof ButtonBase button)) return;

        Platform.runLater(() -> {
            button.arm();
            button.requestFocus();

            PauseTransition pause = new PauseTransition(SIM_PRESS_MS);
            pause.setOnFinished(ev -> button.disarm());
            pause.play();
        });
    }

    private static boolean isInside(Node target, Node container) {
        if (target == null || container == null) return false;

        Node current = target;
        while (current != null) {
            if (current == container) return true;
            current = current.getParent();
        }
        return false;
    }
}
