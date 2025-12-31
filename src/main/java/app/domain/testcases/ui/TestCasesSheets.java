// FILE: src/main/java/app/domain/testcases/ui/TestCasesSheets.java
package app.domain.testcases.ui;

import app.core.I18n;
import app.domain.testcases.LabelStore;
import app.domain.testcases.TagStore;
import app.ui.UiBlur;
import app.ui.UiSvg;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public final class TestCasesSheets {

    private static final Duration ANIM = Duration.millis(220);
    private static final double SHEET_EXTRA_FROM_BOTTOM = 22.0;

    private static final double FILTER_OVERLAY_MARGIN_BOTTOM = 12.0;
    private static final double FILTER_OVERLAY_EXTRA_SCROLL = 18.0;

    private enum FilterTab { LABELS, TAGS }

    private final StackPane leftStack;
    private final StackPane filterSheet;
    private final StackPane sortSheet;
    private final Node blurTarget;

    private final SmoothScrollSupport smoothScroll;
    private final UiBlur blur;

    private final List<String> sortKeys;
    private int selectedSortIndex = 0;

    private boolean filterOpen = false;
    private ParallelTransition filterAnim;

    private boolean sortOpen = false;
    private ParallelTransition sortAnim;

    private FilterTab activeTab = FilterTab.LABELS;

    private final List<String> appliedLabels = new ArrayList<>();
    private final List<String> appliedTags   = new ArrayList<>();

    private final List<String> draftLabels = new ArrayList<>();
    private final List<String> draftTags   = new ArrayList<>();

    private double labelsScrollV = 0.0;
    private double tagsScrollV   = 0.0;

    private ToggleButton tabLabelsBtn;
    private ToggleButton tabTagsBtn;
    private Button resetBtn;
    private Button applyBtn;
    private FlowPane chipsPane;
    private ScrollPane chipsScroll;

    private VBox chipsScrollContent;
    private Region chipsBottomSpacer;

    // ВАЖНО: overlay должен уметь скрываться/показываться (как в старом контроллере)
    private VBox filterApplyOverlay;

    private Runnable onApplyFilters = () -> {};
    private Runnable onSortChanged = () -> {};

    public TestCasesSheets(
            StackPane leftStack,
            StackPane filterSheet,
            StackPane sortSheet,
            Node blurTarget,
            SmoothScrollSupport smoothScroll,
            List<String> sortKeys
    ) {
        this.leftStack = leftStack;
        this.filterSheet = filterSheet;
        this.sortSheet = sortSheet;
        this.blurTarget = blurTarget;
        this.smoothScroll = smoothScroll;

        this.sortKeys = (sortKeys == null) ? List.of() : sortKeys;

        this.blur = new UiBlur(Duration.millis(120), 10.0);
        if (blurTarget != null) this.blur.setTargets(blurTarget);
    }

    public void init() {
        if (filterSheet != null) {
            filterSheet.setVisible(false);
            filterSheet.setManaged(false);
            filterSheet.setOpacity(0.0);
            filterSheet.setTranslateY(0);
        }
        if (sortSheet != null) {
            sortSheet.setVisible(false);
            sortSheet.setManaged(false);
            sortSheet.setOpacity(0.0);
            sortSheet.setTranslateY(0);
        }

        if (leftStack != null) {
            leftStack.heightProperty().addListener((obs, ov, nv) -> {
                if (filterOpen || sortOpen) applySheetHeightNow();
            });
        }
    }

    public void setOnApplyFilters(Runnable r) {
        this.onApplyFilters = (r == null) ? () -> {} : r;
    }

    public void setOnSortChanged(Runnable r) {
        this.onSortChanged = (r == null) ? () -> {} : r;
    }

    public boolean isFilterOpen() { return filterOpen; }
    public boolean isSortOpen() { return sortOpen; }

    public List<String> appliedLabels() { return appliedLabels; }
    public List<String> appliedTags() { return appliedTags; }

    public int selectedSortIndex() { return selectedSortIndex; }

    public void toggleFilter() {
        if (filterSheet == null || leftStack == null) return;

        if (!filterOpen && sortOpen) closeSortInstant();

        boolean show = !filterOpen;
        filterOpen = show;

        if (filterAnim != null) {
            filterAnim.stop();
            filterAnim = null;
        }

        if (show) {
            blur.setActive(true);
            showFilterAnimated();
        } else {
            if (!sortOpen) blur.setActive(false);
            hideFilterAnimated();
        }
    }

    public void toggleSort() {
        if (sortSheet == null || leftStack == null) return;

        if (!sortOpen && filterOpen) closeFilterInstant();

        boolean show = !sortOpen;
        sortOpen = show;

        if (sortAnim != null) {
            sortAnim.stop();
            sortAnim = null;
        }

        if (show) {
            blur.setActive(true);
            showSortAnimated();
        } else {
            if (!filterOpen) blur.setActive(false);
            hideSortAnimated();
        }
    }

    public String currentSortText() {
        if (sortKeys.isEmpty()) return "";
        int idx = selectedSortIndex;
        if (idx < 0) idx = 0;
        if (idx >= sortKeys.size()) idx = sortKeys.size() - 1;
        return I18n.t(sortKeys.get(idx));
    }

    private void showFilterAnimated() {
        syncDraftFromApplied();
        buildFilterUi();
        rebuildChipsAndRestoreScroll();

        filterSheet.setVisible(true);
        filterSheet.setManaged(true);
        filterSheet.setOpacity(0.0);

        applySheetHeightNow();

        Platform.runLater(() -> {
            double h = leftStack.getHeight();
            if (h <= 0) h = filterSheet.getHeight();
            if (h <= 0) h = 1;

            double fromY = h + SHEET_EXTRA_FROM_BOTTOM;

            filterSheet.setTranslateY(fromY);
            filterSheet.setOpacity(0.0);

            TranslateTransition tt = new TranslateTransition(ANIM, filterSheet);
            tt.setFromY(fromY);
            tt.setToY(0.0);
            tt.setInterpolator(Interpolator.EASE_OUT);

            FadeTransition ft = new FadeTransition(ANIM, filterSheet);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            ft.setInterpolator(Interpolator.EASE_OUT);

            filterAnim = new ParallelTransition(tt, ft);
            filterAnim.play();
        });
    }

    private void hideFilterAnimated() {
        discardDraft();

        double h = leftStack.getHeight();
        if (h <= 0) h = filterSheet.getHeight();
        if (h <= 0) h = 1;

        double toY = h + SHEET_EXTRA_FROM_BOTTOM;

        TranslateTransition tt = new TranslateTransition(ANIM, filterSheet);
        tt.setFromY(filterSheet.getTranslateY());
        tt.setToY(toY);
        tt.setInterpolator(Interpolator.EASE_IN);

        FadeTransition ft = new FadeTransition(ANIM, filterSheet);
        ft.setFromValue(filterSheet.getOpacity());
        ft.setToValue(0.0);
        ft.setInterpolator(Interpolator.EASE_IN);

        filterAnim = new ParallelTransition(tt, ft);
        filterAnim.setOnFinished(e -> {
            filterSheet.setVisible(false);
            filterSheet.setManaged(false);
            filterSheet.setTranslateY(0);
            filterSheet.setOpacity(0.0);
        });
        filterAnim.play();
    }

    private void showSortAnimated() {
        buildSortUi();

        sortSheet.setVisible(true);
        sortSheet.setManaged(true);
        sortSheet.setOpacity(0.0);

        applySheetHeightNow();

        Platform.runLater(() -> {
            double h = leftStack.getHeight();
            if (h <= 0) h = sortSheet.getHeight();
            if (h <= 0) h = 1;

            double fromY = h + SHEET_EXTRA_FROM_BOTTOM;

            sortSheet.setTranslateY(fromY);
            sortSheet.setOpacity(0.0);

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

    private void hideSortAnimated() {
        double h = leftStack.getHeight();
        if (h <= 0) h = sortSheet.getHeight();
        if (h <= 0) h = 1;

        double toY = h + SHEET_EXTRA_FROM_BOTTOM;

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
            sortSheet.setVisible(false);
            sortSheet.setManaged(false);
            sortSheet.setTranslateY(0);
            sortSheet.setOpacity(0.0);
        });
        sortAnim.play();
    }

    private void closeFilterInstant() {
        if (!filterOpen) return;

        if (filterAnim != null) {
            filterAnim.stop();
            filterAnim = null;
        }

        discardDraft();
        filterOpen = false;

        filterSheet.setVisible(false);
        filterSheet.setManaged(false);
        filterSheet.setTranslateY(0);
        filterSheet.setOpacity(0.0);

        if (!sortOpen) blur.setActive(false);
    }

    private void closeSortInstant() {
        if (!sortOpen) return;

        if (sortAnim != null) {
            sortAnim.stop();
            sortAnim = null;
        }

        sortOpen = false;

        sortSheet.setVisible(false);
        sortSheet.setManaged(false);
        sortSheet.setTranslateY(0);
        sortSheet.setOpacity(0.0);

        if (!filterOpen) blur.setActive(false);
    }

    private void applySheetHeightNow() {
        if (leftStack == null) return;

        double base = leftStack.getHeight();
        if (base <= 0) return;

        if (filterSheet != null && filterSheet.isVisible()) filterSheet.setMaxHeight(base - 14);
        if (sortSheet != null && sortSheet.isVisible()) sortSheet.setMaxHeight(base - 14);
    }

    // ===================== FILTER UI (как в старом контроллере) =====================

    private void buildFilterUi() {
        filterSheet.getChildren().clear();

        filterApplyOverlay = null;
        chipsScrollContent = null;
        chipsBottomSpacer = null;

        HBox header = new HBox(10);
        header.getStyleClass().add("tc-filter-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 12, 10, 12));

        ToggleButton labels = new ToggleButton(I18n.t("tc.filter.labels"));
        ToggleButton tags = new ToggleButton(I18n.t("tc.filter.tags"));

        labels.getStyleClass().add("tc-filter-tab");
        tags.getStyleClass().add("tc-filter-tab");
        labels.setFocusTraversable(false);
        tags.setFocusTraversable(false);

        tabLabelsBtn = labels;
        tabTagsBtn = tags;

        labels.setSelected(activeTab == FilterTab.LABELS);
        tags.setSelected(activeTab == FilterTab.TAGS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        resetBtn = new Button(I18n.t("tc.filter.reset"));
        resetBtn.getStyleClass().add("tc-filter-reset");
        resetBtn.setFocusTraversable(false);
        resetBtn.setOnAction(e -> {
            draftLabels.clear();
            draftTags.clear();
            labelsScrollV = 0.0;
            tagsScrollV = 0.0;
            updateFilterButtonsState();
            rebuildChipsAndRestoreScroll();
        });

        header.getChildren().addAll(labels, tags, spacer, resetBtn);

        chipsPane = new FlowPane(8, 8);
        chipsPane.getStyleClass().add("tc-filter-chips");
        chipsPane.setPadding(new Insets(10, 12, 10, 12));

        chipsBottomSpacer = new Region();
        chipsBottomSpacer.setMinHeight(0);
        chipsBottomSpacer.setPrefHeight(0);
        chipsBottomSpacer.setMaxHeight(0);

        chipsScrollContent = new VBox(chipsPane, chipsBottomSpacer);
        chipsScrollContent.setFillWidth(true);
        chipsScrollContent.setSpacing(0);

        chipsScroll = new ScrollPane(chipsScrollContent);
        chipsScroll.setFitToWidth(true);
        chipsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chipsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        chipsScroll.getStyleClass().addAll("tc-scroll", "tc-scroll-left", "tc-filter-scroll");
        smoothScroll.install(chipsScroll);

        VBox content = new VBox(header, chipsScroll);
        content.setFillWidth(true);
        VBox.setVgrow(chipsScroll, Priority.ALWAYS);

        applyBtn = new Button(I18n.t("tc.filter.apply"));
        applyBtn.getStyleClass().add("tc-filter-apply");
        applyBtn.setMaxWidth(Double.MAX_VALUE);
        applyBtn.setFocusTraversable(false);
        applyBtn.setOnAction(e -> {
            if (!isDraftDirty()) {
                toggleFilter();
                return;
            }
            commitDraftToApplied();
            onApplyFilters.run();
            toggleFilter();
        });

        VBox overlay = new VBox(applyBtn);
        overlay.setAlignment(Pos.BOTTOM_CENTER);

        overlay.setMinHeight(Region.USE_PREF_SIZE);
        overlay.setPrefHeight(Region.USE_COMPUTED_SIZE);
        overlay.setMaxHeight(Region.USE_PREF_SIZE);

        overlay.setMaxWidth(Double.MAX_VALUE);
        overlay.setPickOnBounds(false);

        StackPane.setAlignment(overlay, Pos.BOTTOM_CENTER);
        StackPane.setMargin(overlay, new Insets(0, 12, FILTER_OVERLAY_MARGIN_BOTTOM, 12));

        filterApplyOverlay = overlay;

        filterSheet.getChildren().addAll(content, overlay);
        StackPane.setAlignment(content, Pos.TOP_LEFT);

        labels.setOnAction(e -> {
            saveCurrentScrollPosition();
            activeTab = FilterTab.LABELS;
            tabLabelsBtn.setSelected(true);
            tabTagsBtn.setSelected(false);
            rebuildChipsAndRestoreScroll();
        });

        tags.setOnAction(e -> {
            saveCurrentScrollPosition();
            activeTab = FilterTab.TAGS;
            tabTagsBtn.setSelected(true);
            tabLabelsBtn.setSelected(false);
            rebuildChipsAndRestoreScroll();
        });

        // если высота кнопки станет известна позже — пересчитаем spacer
        applyBtn.heightProperty().addListener((obs, ov, nv) -> updateFilterBottomSpacerNow());

        updateFilterButtonsState();
        Platform.runLater(this::updateFilterBottomSpacerNow);
    }

    private void updateFilterBottomSpacerNow() {
        if (chipsBottomSpacer == null) return;

        boolean overlayVisible = filterApplyOverlay != null && filterApplyOverlay.isVisible() && filterApplyOverlay.isManaged();
        boolean applyVisible = applyBtn != null && applyBtn.isVisible() && applyBtn.isManaged();

        if (!overlayVisible || !applyVisible) {
            chipsBottomSpacer.setMinHeight(0);
            chipsBottomSpacer.setPrefHeight(0);
            chipsBottomSpacer.setMaxHeight(0);
            return;
        }

        double btnH = applyBtn.getHeight();
        if (btnH <= 0) btnH = applyBtn.prefHeight(-1);
        if (btnH <= 0) btnH = 44;

        double spacerH = btnH + FILTER_OVERLAY_MARGIN_BOTTOM + FILTER_OVERLAY_EXTRA_SCROLL;

        chipsBottomSpacer.setMinHeight(spacerH);
        chipsBottomSpacer.setPrefHeight(spacerH);
        chipsBottomSpacer.setMaxHeight(spacerH);
    }

    private void updateFilterButtonsState() {
        if (resetBtn == null || applyBtn == null) return;

        boolean dirty = isDraftDirty();
        boolean hasAny = !(draftLabels.isEmpty() && draftTags.isEmpty());

        // Reset: только когда реально есть выбранные фильтры
        resetBtn.setVisible(hasAny);
        resetBtn.setManaged(hasAny);
        resetBtn.setDisable(!hasAny);

        // Apply: только когда есть изменения относительно applied
        applyBtn.setVisible(dirty);
        applyBtn.setManaged(dirty);
        applyBtn.setDisable(!dirty);

        if (filterApplyOverlay != null) {
            filterApplyOverlay.setVisible(dirty);
            filterApplyOverlay.setManaged(dirty);
        }

        updateFilterBottomSpacerNow();
    }

    private void syncDraftFromApplied() {
        draftLabels.clear();
        draftTags.clear();

        draftLabels.addAll(appliedLabels);
        draftTags.addAll(appliedTags);

        updateFilterButtonsState();
    }

    private void discardDraft() {
        // поведение как в старом контроллере: просто сброс черновика и позиции скролла
        draftLabels.clear();
        draftTags.clear();
        labelsScrollV = 0.0;
        tagsScrollV = 0.0;
        updateFilterButtonsState();
    }

    private boolean isDraftDirty() {
        if (draftLabels.size() != appliedLabels.size()) return true;
        if (draftTags.size() != appliedTags.size()) return true;

        for (String s : draftLabels) if (!containsIgnoreCase(appliedLabels, s)) return true;
        for (String s : draftTags) if (!containsIgnoreCase(appliedTags, s)) return true;

        return false;
    }

    private void commitDraftToApplied() {
        appliedLabels.clear();
        appliedTags.clear();

        appliedLabels.addAll(draftLabels);
        appliedTags.addAll(draftTags);
    }

    private void saveCurrentScrollPosition() {
        if (chipsScroll == null) return;
        if (activeTab == FilterTab.LABELS) labelsScrollV = chipsScroll.getVvalue();
        else tagsScrollV = chipsScroll.getVvalue();
    }

    private void rebuildChipsAndRestoreScroll() {
        if (chipsPane == null || chipsScroll == null) return;

        chipsPane.getChildren().clear();

        List<String> source = (activeTab == FilterTab.LABELS) ? LabelStore.loadAll() : TagStore.loadAll();
        List<String> selected = (activeTab == FilterTab.LABELS) ? draftLabels : draftTags;

        for (String v : source) {
            if (v == null) continue;

            boolean on = containsIgnoreCase(selected, v);

            ToggleButton chip = new ToggleButton(v);
            chip.getStyleClass().add("tc-filter-chip");
            chip.setSelected(on);
            chip.setFocusTraversable(false);

            chip.setOnAction(e -> {
                boolean now = chip.isSelected();
                if (now) addUnique(selected, v);
                else removeIgnoreCase(selected, v);

                updateFilterButtonsState();
            });

            chipsPane.getChildren().add(chip);
        }

        double restore = (activeTab == FilterTab.LABELS) ? labelsScrollV : tagsScrollV;

        Platform.runLater(() -> {
            chipsScroll.setVvalue(restore);
            updateFilterButtonsState();
        });
    }

    // ===================== SORT UI =====================

    private void buildSortUi() {
        sortSheet.getChildren().clear();

        VBox root = new VBox(8);
        root.setPadding(new Insets(12, 12, 12, 12));
        root.setFillWidth(true);

        VBox list = new VBox(8);

        for (int i = 0; i < sortKeys.size(); i++) {
            final int idx = i;

            javafx.scene.control.Label lbl = new javafx.scene.control.Label(I18n.t(sortKeys.get(i)));

            javafx.scene.control.Label check = new javafx.scene.control.Label();
            check.getStyleClass().add("tc-sort-check");

            Node checkIcon = UiSvg.createSvg("check.svg", 22);
            if (checkIcon != null) check.setGraphic(checkIcon);

            if (idx != selectedSortIndex) check.getStyleClass().add("off");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(10, lbl, spacer, check);
            row.setAlignment(Pos.CENTER_LEFT);

            Button b = new Button();
            b.setMaxWidth(Double.MAX_VALUE);
            b.getStyleClass().add("tc-sort-item");
            if (idx == selectedSortIndex) b.getStyleClass().add("selected");
            b.setGraphic(row);
            b.setText("");
            b.setFocusTraversable(false);

            b.setOnAction(ev -> {
                selectedSortIndex = idx;
                onSortChanged.run();
                toggleSort();
            });

            list.getChildren().add(b);
        }

        root.getChildren().add(list);

        sortSheet.getChildren().add(root);
        StackPane.setAlignment(root, Pos.TOP_LEFT);
    }

    // ===================== helpers =====================

    private static boolean containsIgnoreCase(List<String> list, String v) {
        if (list == null || list.isEmpty()) return false;
        if (v == null) return false;

        for (String s : list) {
            if (s == null) continue;
            if (s.equalsIgnoreCase(v)) return true;
        }
        return false;
    }

    private static void addUnique(List<String> list, String v) {
        if (list == null) return;
        if (v == null) return;

        if (!containsIgnoreCase(list, v)) list.add(v);
    }

    private static void removeIgnoreCase(List<String> list, String v) {
        if (list == null || list.isEmpty()) return;
        if (v == null) return;

        for (int i = list.size() - 1; i >= 0; i--) {
            String s = list.get(i);
            if (s == null) continue;
            if (s.equalsIgnoreCase(v)) list.remove(i);
        }
    }

    public void refreshFilterChipsIfOpen() {
        if (!filterOpen) return;
        rebuildChipsAndRestoreScroll();
        updateFilterButtonsState();
    }
}
