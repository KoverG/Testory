package app.domain.cycles.ui.overlay;

import app.core.I18n;
import app.domain.cycles.usecase.CycleRunState;
import app.ui.UiBlur;
import app.ui.UiScroll;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public final class FilterSheet {

    public enum Mode {
        CYCLES,
        CASES
    }

    public static final class CycleFilters {
        private final List<String> statuses = new ArrayList<>();
        private final List<String> responsibles = new ArrayList<>();
        private final List<String> createdDateRanges = new ArrayList<>();
        private final List<String> progresses = new ArrayList<>();
        private final List<String> caseStatuses = new ArrayList<>();

        public CycleFilters copy() {
            CycleFilters copy = new CycleFilters();
            copy.statuses.addAll(statuses);
            copy.responsibles.addAll(responsibles);
            copy.createdDateRanges.addAll(createdDateRanges);
            copy.progresses.addAll(progresses);
            copy.caseStatuses.addAll(caseStatuses);
            return copy;
        }

        public List<String> statuses() {
            return List.copyOf(statuses);
        }

        public List<String> responsibles() {
            return List.copyOf(responsibles);
        }

        public List<String> createdDateRanges() {
            return List.copyOf(createdDateRanges);
        }

        public List<String> progresses() {
            return List.copyOf(progresses);
        }

        public List<String> caseStatuses() {
            return List.copyOf(caseStatuses);
        }

        public void clear() {
            statuses.clear();
            responsibles.clear();
            createdDateRanges.clear();
            progresses.clear();
            caseStatuses.clear();
        }

        public int activeCount() {
            int count = 0;
            if (!statuses.isEmpty()) count++;
            if (!responsibles.isEmpty()) count++;
            if (!createdDateRanges.isEmpty()) count++;
            if (!progresses.isEmpty()) count++;
            if (!caseStatuses.isEmpty()) count++;
            return count;
        }
    }

    public static final class CaseFilters {
        private final List<String> labels = new ArrayList<>();
        private final List<String> tags = new ArrayList<>();

        public CaseFilters copy() {
            CaseFilters copy = new CaseFilters();
            copy.labels.addAll(labels);
            copy.tags.addAll(tags);
            return copy;
        }

        public List<String> labels() {
            return List.copyOf(labels);
        }

        public List<String> tags() {
            return List.copyOf(tags);
        }

        public void clear() {
            labels.clear();
            tags.clear();
        }

        public int activeCount() {
            int count = 0;
            if (!labels.isEmpty()) count++;
            if (!tags.isEmpty()) count++;
            return count;
        }
    }

    private static final Duration ANIM = Duration.millis(220);
    private static final double SHEET_EXTRA_FROM_BOTTOM = 22.0;
    private static final double FILTER_SHEET_RADIUS = 18.0;
    private static final double FILTER_OVERLAY_MARGIN_BOTTOM = 12.0;
    private static final double FILTER_OVERLAY_EXTRA_SCROLL = 18.0;
    private static final Duration SIM_PRESS_MS = Duration.millis(120.0);

    private static final String DATE_TODAY = "today";
    private static final String DATE_7_DAYS = "last7";
    private static final String DATE_30_DAYS = "last30";

    private static final String PROGRESS_ZERO = "0";
    private static final String PROGRESS_1_50 = "1_50";
    private static final String PROGRESS_51_90 = "51_90";
    private static final String PROGRESS_91_99 = "91_99";
    private static final String PROGRESS_100 = "100";

    private static final List<String> CYCLE_CASE_STATUS_OPTIONS = List.of(
            "PASSED",
            "PASSED_WITH_BUGS",
            "FAILED",
            "CRITICAL_FAILED",
            "IN_PROGRESS",
            "SKIPPED"
    );

    private final StackPane leftStack;
    private final StackPane filterSheet;
    private final Node heightTarget;
    private final UiBlur blur = new UiBlur(Duration.millis(120), 10.0);

    private Runnable onApply = () -> {};
    private Runnable onBeforeOpen = () -> {};

    private boolean open = false;
    private ParallelTransition filterAnim;
    private Mode mode = Mode.CYCLES;

    private final CycleFilters appliedCycle = new CycleFilters();
    private final CycleFilters draftCycle = new CycleFilters();
    private final CaseFilters appliedCase = new CaseFilters();
    private final CaseFilters draftCase = new CaseFilters();

    private final List<String> availableResponsibles = new ArrayList<>();
    private final List<String> availableLabels = new ArrayList<>();
    private final List<String> availableTags = new ArrayList<>();

    private double caseLabelsScrollV = 0.0;
    private double caseTagsScrollV = 0.0;
    private boolean caseLabelsTabActive = true;

    private Button resetBtn;
    private Button applyBtn;
    private VBox applyOverlay;
    private ScrollPane scrollPane;
    private Region bottomSpacer;
    private Node cardRoot;
    private Node filterToggleNode;

    public FilterSheet(StackPane leftStack, StackPane filterSheet, Node blurTarget) {
        this.leftStack = leftStack;
        this.filterSheet = filterSheet;
        this.heightTarget = blurTarget;
        if (blurTarget != null) {
            blur.setTargets(blurTarget);
        }
    }

    public void init() {
        if (filterSheet == null) return;

        filterSheet.setVisible(false);
        filterSheet.setManaged(false);
        filterSheet.setOpacity(0.0);
        filterSheet.setTranslateY(0.0);
        filterSheet.setPickOnBounds(false);

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

    public void setOnApply(Runnable onApply) {
        this.onApply = onApply == null ? () -> {} : onApply;
    }

    public void setOnBeforeOpen(Runnable onBeforeOpen) {
        this.onBeforeOpen = onBeforeOpen == null ? () -> {} : onBeforeOpen;
    }

    public void setOutsideCloseConsumeTarget(Node filterToggleNode) {
        this.filterToggleNode = filterToggleNode;
    }

    public void toggleForCycles(List<String> responsibles) {
        availableResponsibles.clear();
        if (responsibles != null) availableResponsibles.addAll(responsibles);
        toggle(Mode.CYCLES);
    }

    public void toggleForCases(List<String> labels, List<String> tags) {
        availableLabels.clear();
        if (labels != null) availableLabels.addAll(labels);

        availableTags.clear();
        if (tags != null) availableTags.addAll(tags);

        toggle(Mode.CASES);
    }

    public void close() {
        if (!open) return;
        hideAnimated();
    }

    public boolean isOpen() {
        return open;
    }

    public CycleFilters appliedCycleFilters() {
        return appliedCycle.copy();
    }

    public CaseFilters appliedCaseFilters() {
        return appliedCase.copy();
    }

    public int activeCountForCycles() {
        return appliedCycle.activeCount();
    }

    public int activeCountForCases() {
        return appliedCase.activeCount();
    }

    private void toggle(Mode targetMode) {
        if (filterSheet == null || leftStack == null) return;

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
        syncDraftFromApplied();
        buildUi();

        filterSheet.setVisible(true);
        filterSheet.setManaged(true);
        filterSheet.setOpacity(0.0);
        applySheetHeightNow();
        blur.setActive(true);

        Platform.runLater(() -> {
            double h = leftStack.getHeight();
            if (h <= 0) h = filterSheet.getHeight();
            if (h <= 0) h = 1.0;

            double fromY = h + SHEET_EXTRA_FROM_BOTTOM;
            filterSheet.setTranslateY(fromY);
            filterSheet.setOpacity(0.0);

            stopAnimation();

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

    private void hideAnimated() {
        discardDraft();

        double h = leftStack == null ? 0.0 : leftStack.getHeight();
        if (h <= 0) h = filterSheet.getHeight();
        if (h <= 0) h = 1.0;

        double toY = h + SHEET_EXTRA_FROM_BOTTOM;
        stopAnimation();

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
            open = false;
            filterSheet.setVisible(false);
            filterSheet.setManaged(false);
            filterSheet.setTranslateY(0.0);
            filterSheet.setOpacity(0.0);
            blur.setActive(false);
        });
        filterAnim.play();
    }

    private void buildUi() {
        filterSheet.getChildren().clear();

        Node card = buildCard();
        filterSheet.getChildren().add(card);
        StackPane.setAlignment(card, Pos.BOTTOM_CENTER);
        cardRoot = card;
    }

    private Node buildCard() {
        resetBtn = new Button(I18n.t("tc.filter.reset"));
        resetBtn.getStyleClass().add("tc-filter-reset");
        resetBtn.setFocusTraversable(false);
        resetBtn.setMinWidth(Region.USE_PREF_SIZE);
        resetBtn.setPrefWidth(Region.USE_COMPUTED_SIZE);
        resetBtn.setOnAction(e -> {
            resetDraft();
            buildUi();
        });

        VBox contentBody = new VBox(12.0);
        contentBody.setFillWidth(true);
        contentBody.setPadding(new Insets(10, 12, 10, 12));

        if (mode == Mode.CYCLES) {
            buildCyclesContent(contentBody);
        } else {
            buildCasesContent(contentBody);
        }

        bottomSpacer = new Region();
        bottomSpacer.setMinHeight(0.0);
        bottomSpacer.setPrefHeight(0.0);
        bottomSpacer.setMaxHeight(0.0);
        contentBody.getChildren().add(bottomSpacer);

        scrollPane = new ScrollPane(contentBody);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().addAll("tc-scroll", "tc-scroll-left", "tc-filter-scroll");

        StackPane titleCapsule = buildTitleCapsule();
        HBox topHeader = new HBox(8.0, titleCapsule, resetBtn);
        topHeader.setAlignment(Pos.CENTER_LEFT);
        topHeader.setPadding(new Insets(10, 12, 0, 12));
        HBox.setHgrow(titleCapsule, Priority.ALWAYS);
        Node headerControls = buildHeaderControls();

        VBox content = headerControls == null
                ? new VBox(10.0, topHeader, scrollPane)
                : new VBox(10.0, topHeader, headerControls, scrollPane);
        content.setFillWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        content.setPickOnBounds(true);
        content.setOnMousePressed(e -> e.consume());

        applyBtn = new Button(I18n.t("tc.filter.apply"));
        applyBtn.getStyleClass().add("tc-filter-apply");
        applyBtn.setMaxWidth(Double.MAX_VALUE);
        applyBtn.setFocusTraversable(false);
        applyBtn.setOnAction(e -> applyDraft());

        applyOverlay = new VBox(applyBtn);
        applyOverlay.setAlignment(Pos.BOTTOM_CENTER);
        applyOverlay.setPickOnBounds(false);
        applyOverlay.setOnMousePressed(e -> e.consume());
        StackPane.setAlignment(applyOverlay, Pos.BOTTOM_CENTER);
        StackPane.setMargin(applyOverlay, new Insets(0, 12, FILTER_OVERLAY_MARGIN_BOTTOM, 12));

        StackPane card = new StackPane(content, applyOverlay);
        StackPane.setAlignment(content, Pos.TOP_LEFT);

        UiScroll.clipRoundedSheet(filterSheet, FILTER_SHEET_RADIUS);

        updateButtonsState();
        Platform.runLater(this::updateBottomSpacer);

        return card;
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

    private Node buildHeaderControls() {
        if (mode != Mode.CASES) return null;

        HBox tabs = new HBox(8.0);
        tabs.setPadding(new Insets(0, 12, 0, 12));

        ToggleButton labelsTab = new ToggleButton(I18n.t("tc.filter.labels"));
        labelsTab.getStyleClass().add("tc-filter-tab");
        labelsTab.setFocusTraversable(false);
        labelsTab.setSelected(caseLabelsTabActive);
        labelsTab.setOnAction(e -> {
            saveCaseScrollPosition();
            caseLabelsTabActive = true;
            buildUi();
        });

        ToggleButton tagsTab = new ToggleButton(I18n.t("tc.filter.tags"));
        tagsTab.getStyleClass().add("tc-filter-tab");
        tagsTab.setFocusTraversable(false);
        tagsTab.setSelected(!caseLabelsTabActive);
        tagsTab.setOnAction(e -> {
            saveCaseScrollPosition();
            caseLabelsTabActive = false;
            buildUi();
        });

        tabs.getChildren().addAll(labelsTab, tagsTab);
        return tabs;
    }

    private void buildCyclesContent(VBox root) {
        root.getChildren().add(buildSection(
                I18n.t("cy.filter.status"),
                true,
                false,
                buildMultiSelectChips(
                        List.of(
                                new ChipOption(CycleRunState.IDLE, I18n.t("cy.filter.status.idle")),
                                new ChipOption(CycleRunState.RUNNING, I18n.t("cy.filter.status.running")),
                                new ChipOption(CycleRunState.PAUSED, I18n.t("cy.filter.status.paused")),
                                new ChipOption(CycleRunState.FINISHED, I18n.t("cy.filter.status.finished"))
                        ),
                        draftCycle.statuses
                )
        ));

        root.getChildren().add(buildSection(
                I18n.t("cy.filter.responsible"),
                true,
                false,
                buildMultiSelectChips(toOptions(availableResponsibles), draftCycle.responsibles)
        ));

        root.getChildren().add(buildSection(
                I18n.t("cy.filter.createdAt"),
                true,
                false,
                buildExclusiveChips(
                        List.of(
                                new ChipOption(DATE_TODAY, I18n.t("cy.filter.createdAt.today")),
                                new ChipOption(DATE_7_DAYS, I18n.t("cy.filter.createdAt.7days")),
                                new ChipOption(DATE_30_DAYS, I18n.t("cy.filter.createdAt.30days"))
                        ),
                        draftCycle.createdDateRanges
                )
        ));

        root.getChildren().add(buildSection(
                I18n.t("cy.filter.progress"),
                false,
                false,
                buildMultiSelectChips(
                        List.of(
                                new ChipOption(PROGRESS_ZERO, I18n.t("cy.filter.progress.zero")),
                                new ChipOption(PROGRESS_1_50, I18n.t("cy.filter.progress.1_50")),
                                new ChipOption(PROGRESS_51_90, I18n.t("cy.filter.progress.51_90")),
                                new ChipOption(PROGRESS_91_99, I18n.t("cy.filter.progress.91_99")),
                                new ChipOption(PROGRESS_100, I18n.t("cy.filter.progress.100"))
                        ),
                        draftCycle.progresses
                )
        ));

        root.getChildren().add(buildSection(
                I18n.t("cy.filter.caseStatuses"),
                false,
                false,
                buildMultiSelectChips(toOptions(CYCLE_CASE_STATUS_OPTIONS), draftCycle.caseStatuses)
        ));
    }

    private void buildCasesContent(VBox root) {
        List<String> source = caseLabelsTabActive ? availableLabels : availableTags;
        List<String> selected = caseLabelsTabActive ? draftCase.labels : draftCase.tags;
        root.getChildren().add(buildMultiSelectChips(toOptions(source), selected));

        Platform.runLater(() -> {
            if (scrollPane == null) return;
            scrollPane.setVvalue(caseLabelsTabActive ? caseLabelsScrollV : caseTagsScrollV);
        });
    }

    private VBox buildSection(String title, FlowPane chips) {
        return buildSection(title, false, false, chips);
    }

    private VBox buildSection(String title, boolean capsuleTitle, boolean includeReset, FlowPane chips) {
        Node titleNode = capsuleTitle ? buildSectionCapsule(title) : buildSectionLabel(title);
        Node headerNode = includeReset ? buildSectionHeader(titleNode) : titleNode;

        VBox box = new VBox(8.0, headerNode, chips);
        box.setFillWidth(true);
        return box;
    }

    private ToggleButton buildSectionCapsule(String title) {
        ToggleButton capsule = new ToggleButton(title);
        capsule.getStyleClass().add("tc-filter-tab");
        capsule.setSelected(true);
        capsule.setFocusTraversable(false);
        capsule.setMouseTransparent(true);
        return capsule;
    }

    private Label buildSectionLabel(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("cy-meta-label");
        return label;
    }

    private HBox buildSectionHeader(Node titleNode) {
        HBox header = new HBox(8.0, titleNode);
        header.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, resetBtn);
        return header;
    }

    private FlowPane buildExclusiveChips(List<ChipOption> options, List<String> selected) {
        FlowPane pane = new FlowPane(8.0, 8.0);
        String selectedValue = selected.isEmpty() ? "" : selected.get(0);
        for (ChipOption option : options) {
            ToggleButton chip = new ToggleButton(option.label());
            chip.getStyleClass().add("tc-filter-chip");
            chip.setFocusTraversable(false);
            chip.setSelected(option.value().equalsIgnoreCase(normalizeValue(selectedValue)));
            chip.setOnAction(e -> {
                selected.clear();
                if (chip.isSelected()) selected.add(option.value());
                buildUi();
            });
            pane.getChildren().add(chip);
        }
        return pane;
    }

    private FlowPane buildMultiSelectChips(List<ChipOption> options, List<String> selected) {
        FlowPane pane = new FlowPane(8.0, 8.0);
        for (ChipOption option : options) {
            ToggleButton chip = new ToggleButton(option.label());
            chip.getStyleClass().add("tc-filter-chip");
            chip.setFocusTraversable(false);
            chip.setSelected(containsIgnoreCase(selected, option.value()));
            chip.setOnAction(e -> {
                if (chip.isSelected()) addUnique(selected, option.value());
                else removeIgnoreCase(selected, option.value());
                updateButtonsState();
            });
            pane.getChildren().add(chip);
        }
        return pane;
    }

    private void applyDraft() {
        if (mode == Mode.CYCLES) {
            copyCycle(draftCycle, appliedCycle);
        } else {
            copyCase(draftCase, appliedCase);
        }
        onApply.run();
        close();
    }

    private void resetDraft() {
        if (mode == Mode.CYCLES) draftCycle.clear();
        else draftCase.clear();
    }

    private void syncDraftFromApplied() {
        copyCycle(appliedCycle, draftCycle);
        copyCase(appliedCase, draftCase);
    }

    private void discardDraft() {
        draftCycle.clear();
        draftCase.clear();
        caseLabelsScrollV = 0.0;
        caseTagsScrollV = 0.0;
    }

    private boolean isDraftDirty() {
        if (mode == Mode.CYCLES) {
            if (!sameValues(appliedCycle.statuses, draftCycle.statuses)) return true;
            if (!sameValues(appliedCycle.createdDateRanges, draftCycle.createdDateRanges)) return true;
            if (!sameValues(appliedCycle.progresses, draftCycle.progresses)) return true;
            if (!sameValues(appliedCycle.responsibles, draftCycle.responsibles)) return true;
            return !sameValues(appliedCycle.caseStatuses, draftCycle.caseStatuses);
        }

        if (!sameValues(appliedCase.labels, draftCase.labels)) return true;
        return !sameValues(appliedCase.tags, draftCase.tags);
    }

    private void updateButtonsState() {
        if (resetBtn == null || applyBtn == null) return;

        boolean dirty = isDraftDirty();
        boolean hasAny = mode == Mode.CYCLES
                ? draftCycle.activeCount() > 0
                : draftCase.activeCount() > 0;

        resetBtn.setVisible(true);
        resetBtn.setManaged(true);
        resetBtn.setDisable(!hasAny);
        resetBtn.setOpacity(hasAny ? 1.0 : 0.0);
        resetBtn.setMouseTransparent(!hasAny);

        applyBtn.setVisible(dirty);
        applyBtn.setManaged(dirty);
        applyBtn.setDisable(!dirty);

        if (applyOverlay != null) {
            applyOverlay.setVisible(dirty);
            applyOverlay.setManaged(dirty);
        }

        updateBottomSpacer();
    }

    private void updateBottomSpacer() {
        if (bottomSpacer == null || applyBtn == null || applyOverlay == null) return;

        if (!applyOverlay.isVisible() || !applyOverlay.isManaged()) {
            bottomSpacer.setMinHeight(0.0);
            bottomSpacer.setPrefHeight(0.0);
            bottomSpacer.setMaxHeight(0.0);
            return;
        }

        double btnH = applyBtn.getHeight();
        if (btnH <= 0.0) btnH = applyBtn.prefHeight(-1);
        if (btnH <= 0.0) btnH = 44.0;

        double spacerH = btnH + FILTER_OVERLAY_MARGIN_BOTTOM + FILTER_OVERLAY_EXTRA_SCROLL;
        bottomSpacer.setMinHeight(spacerH);
        bottomSpacer.setPrefHeight(spacerH);
        bottomSpacer.setMaxHeight(spacerH);
    }

    private void saveCaseScrollPosition() {
        if (scrollPane == null || mode != Mode.CASES) return;
        if (caseLabelsTabActive) caseLabelsScrollV = scrollPane.getVvalue();
        else caseTagsScrollV = scrollPane.getVvalue();
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
        filterSheet.setPrefHeight(sheetHeight);
        filterSheet.setMaxHeight(sheetHeight);
    }

    private void stopAnimation() {
        if (filterAnim != null) {
            filterAnim.stop();
            filterAnim = null;
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

        if (isInside(target, filterToggleNode)) {
            event.consume();
            pressFxOnlyLater(filterToggleNode);
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

    private static void copyCycle(CycleFilters from, CycleFilters to) {
        to.statuses.clear();
        to.statuses.addAll(from.statuses);
        to.responsibles.clear();
        to.responsibles.addAll(from.responsibles);
        to.createdDateRanges.clear();
        to.createdDateRanges.addAll(from.createdDateRanges);
        to.progresses.clear();
        to.progresses.addAll(from.progresses);
        to.caseStatuses.clear();
        to.caseStatuses.addAll(from.caseStatuses);
    }

    private static void copyCase(CaseFilters from, CaseFilters to) {
        to.labels.clear();
        to.labels.addAll(from.labels);
        to.tags.clear();
        to.tags.addAll(from.tags);
    }

    private static List<ChipOption> toOptions(List<String> values) {
        List<ChipOption> out = new ArrayList<>();
        if (values == null) return out;
        for (String value : values) {
            String normalized = normalizeValue(value);
            if (normalized.isBlank()) continue;
            out.add(new ChipOption(normalized, normalized));
        }
        return out;
    }

    private static String normalizeValue(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        if (values == null || candidate == null) return false;
        for (String value : values) {
            if (equalsIgnoreCase(value, candidate)) return true;
        }
        return false;
    }

    private static void addUnique(List<String> values, String candidate) {
        if (values == null) return;
        String normalized = normalizeValue(candidate);
        if (normalized.isBlank()) return;
        if (!containsIgnoreCase(values, normalized)) values.add(normalized);
    }

    private static void removeIgnoreCase(List<String> values, String candidate) {
        if (values == null || candidate == null) return;
        for (int i = values.size() - 1; i >= 0; i--) {
            if (equalsIgnoreCase(values.get(i), candidate)) values.remove(i);
        }
    }

    private static boolean sameValues(List<String> left, List<String> right) {
        if (left == null || right == null) return left == right;
        if (left.size() != right.size()) return false;
        for (String value : left) {
            if (!containsIgnoreCase(right, value)) return false;
        }
        return true;
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return normalizeValue(left).equalsIgnoreCase(normalizeValue(right));
    }

    private record ChipOption(String value, String label) {
    }
}
