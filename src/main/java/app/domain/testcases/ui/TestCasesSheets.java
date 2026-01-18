package app.domain.testcases.ui;

import app.core.I18n;
import app.domain.testcases.LabelStore;
import app.domain.testcases.TagStore;
import app.ui.UiBlur;
import app.ui.UiSvg;
import app.ui.UiScroll;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
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
import java.util.function.BooleanSupplier;

public final class TestCasesSheets {

    private static final Duration ANIM = Duration.millis(220);
    private static final double SHEET_EXTRA_FROM_BOTTOM = 22.0;

    private static final double FILTER_OVERLAY_MARGIN_BOTTOM = 12.0;
    private static final double FILTER_OVERLAY_EXTRA_SCROLL = 18.0;

    // Скругление для клипа модалки фильтра (чтобы vbar/ползунок не выходили за нижние углы)
    private static final double FILTER_SHEET_RADIUS = 18.0;

    // CSS-класс, который ты добавил в styles.css
    private static final String CASES_SCROLL_HIDDEN_CLASS = "tc-cases-scroll-hidden";

    // ✅ имитируем CSS :pressed/:armed при программном fire()
    private static final PseudoClass PC_PRESSED = PseudoClass.getPseudoClass("pressed");
    private static final PseudoClass PC_ARMED   = PseudoClass.getPseudoClass("armed");
    private static final Duration   SIM_PRESS_MS = Duration.millis(120);

    private enum FilterTab { LABELS, TAGS }

    private final StackPane leftStack;
    private final StackPane filterSheet;
    private final StackPane sortSheet;
    private final Node blurTarget;

    // список кейсов (ListView или любой Node), чтобы вешать/снимать класс скрытия скроллбара
    private final Node casesList;

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

    // overlay должен уметь скрываться/показываться
    private VBox filterApplyOverlay;

    // ✅ реальный корень "карточки" модалки внутри sheet (включает и content, и overlay)
    private Node filterCardRoot;
    private Node sortCardRoot;

    private Runnable onApplyFilters = () -> {};
    private Runnable onSortChanged = () -> {};

    // ✅ Узлы-тогглы, по которым мы умеем "пробросить клик" / или не дать reopen (press->release)
    private Node filterToggleNode;
    private Node sortToggleNode;
    private Node extraToggleNode; // ✅ третий: btnTrash (и т.п.)

    // ✅ дополнительные "оверлеи/карточки", клики внутри которых НЕ считаются outside
    private final List<Node> extraInsideRoots = new ArrayList<>();

    // ✅ дополнительные closers: закрыть trash overlay "тем же триггером"
    private final List<BooleanSupplier> outsideClosers = new ArrayList<>();

    public TestCasesSheets(
            StackPane leftStack,
            StackPane filterSheet,
            StackPane sortSheet,
            Node blurTarget,
            Node casesList,
            SmoothScrollSupport smoothScroll,
            List<String> sortKeys
    ) {
        this.leftStack = leftStack;
        this.filterSheet = filterSheet;
        this.sortSheet = sortSheet;
        this.blurTarget = blurTarget;
        this.casesList = casesList;
        this.smoothScroll = smoothScroll;

        this.sortKeys = (sortKeys == null) ? List.of() : sortKeys;

        this.blur = new UiBlur(Duration.millis(120), 10.0);
        if (blurTarget != null) this.blur.setTargets(blurTarget);
    }

    // порядок как (filterBtn, sortBtn, extraBtnTrash)
    public void setOutsideCloseConsumeTargets(Node... nodes) {
        filterToggleNode = null;
        sortToggleNode = null;
        extraToggleNode = null;

        if (nodes == null) return;
        if (nodes.length > 0) filterToggleNode = nodes[0];
        if (nodes.length > 1) sortToggleNode = nodes[1];
        if (nodes.length > 2) extraToggleNode = nodes[2];
    }

    // ✅ регистрируем "ещё один корень", клики внутри которого не должны закрывать
    public void addOutsideInsideRoot(Node root) {
        if (root == null) return;
        extraInsideRoots.add(root);
    }

    // ✅ регистрируем "что закрыть" на outside-click/ESC/переключениях
    // возвращает true если реально что-то закрыли (важно для btnTrash: чтобы не словить reopen)
    public void addOutsideCloser(BooleanSupplier closer) {
        if (closer == null) return;
        outsideClosers.add(closer);
    }

    private boolean runOutsideClosers() {
        boolean changed = false;
        for (BooleanSupplier r : outsideClosers) {
            try {
                if (r.getAsBoolean()) changed = true;
            } catch (Exception ignored) {}
        }
        return changed;
    }

    public void init() {
        if (filterSheet != null) {
            filterSheet.setVisible(false);
            filterSheet.setManaged(false);
            filterSheet.setOpacity(0.0);
            filterSheet.setTranslateY(0);
            filterSheet.setPickOnBounds(true);
        }
        if (sortSheet != null) {
            sortSheet.setVisible(false);
            sortSheet.setManaged(false);
            sortSheet.setOpacity(0.0);
            sortSheet.setTranslateY(0);
            sortSheet.setPickOnBounds(true);
        }

        if (leftStack != null) {
            leftStack.heightProperty().addListener((obs, ov, nv) -> {
                if (filterOpen || sortOpen) applySheetHeightNow();
            });
        }

        Platform.runLater(() -> {
            if (leftStack == null) return;
            var scene = leftStack.getScene();
            if (scene == null) return;

            // ✅ ESC: закрываем sheet и/или trash overlay теми же триггерами
            scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() != KeyCode.ESCAPE) return;

                boolean any = false;

                if (filterOpen) {
                    toggleFilter();
                    any = true;
                } else if (sortOpen) {
                    toggleSort();
                    any = true;
                }

                // trash overlay и прочее
                if (runOutsideClosers()) any = true;

                if (any) e.consume();
            });

            // ✅ ГЛОБАЛЬНОЕ закрытие по клику вне модалки/оверлея
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {

                Node t = (e.getTarget() instanceof Node n) ? n : null;
                if (t == null) return;

                // 1) если открыт FILTER/SORT — твоя логика (плюс закрытие extra overlay тем же триггером)
                if (filterOpen || sortOpen) {

                    // внутри карточки — не закрываем
                    if (filterOpen && isInside(t, filterCardRoot)) return;
                    if (sortOpen && isInside(t, sortCardRoot)) return;

                    Node hitToggle = hitTestToggleAt(e.getSceneX(), e.getSceneY());

                    if (filterOpen) {
                        toggleFilter();

                        // ✅ закрываем доп.оверлеи тем же триггером
                        boolean extraClosed = runOutsideClosers();

                        if (hitToggle != null && hitToggle == sortToggleNode) {
                            e.consume();
                            fireLaterWithPressFx(hitToggle);
                        } else if (hitToggle != null && hitToggle == filterToggleNode) {
                            e.consume();
                            pressFxOnlyLater(hitToggle);
                        } else if (hitToggle != null && hitToggle == extraToggleNode) {
                            // если клик был по btnTrash, и мы что-то закрыли "на press",
                            // нужно не дать его onAction открыть обратно на release
                            if (extraClosed) {
                                e.consume();
                                pressFxOnlyLater(hitToggle);
                            }
                        }
                        return;
                    }

                    if (sortOpen) {
                        toggleSort();

                        boolean extraClosed = runOutsideClosers();

                        if (hitToggle != null && hitToggle == filterToggleNode) {
                            e.consume();
                            fireLaterWithPressFx(hitToggle);
                        } else if (hitToggle != null && hitToggle == sortToggleNode) {
                            e.consume();
                            pressFxOnlyLater(hitToggle);
                        } else if (hitToggle != null && hitToggle == extraToggleNode) {
                            if (extraClosed) {
                                e.consume();
                                pressFxOnlyLater(hitToggle);
                            }
                        }
                        return;
                    }

                    return;
                }

                // 2) если sheet'ы НЕ открыты — возможно открыт trash overlay.
                if (outsideClosers.isEmpty()) return;

                // если клик внутри зарегистрированных "оверлеев" — не закрываем
                for (Node root : extraInsideRoots) {
                    if (isInside(t, root)) return;
                }

                Node hitToggle = hitTestToggleAt(e.getSceneX(), e.getSceneY());

                // клики по btnTrash: если overlay закрываем "на press", не даём reopen на release
                boolean closed = runOutsideClosers();

                if (hitToggle != null && hitToggle == extraToggleNode && closed) {
                    e.consume();
                    pressFxOnlyLater(hitToggle);
                }
            });
        });
    }

    private Node hitTestToggleAt(double sceneX, double sceneY) {
        Node a = filterToggleNode;
        if (isHit(a, sceneX, sceneY)) return a;

        Node b = sortToggleNode;
        if (isHit(b, sceneX, sceneY)) return b;

        Node c = extraToggleNode;
        if (isHit(c, sceneX, sceneY)) return c;

        return null;
    }

    private static boolean isHit(Node n, double sceneX, double sceneY) {
        if (n == null) return false;
        if (!n.isVisible()) return false;

        Bounds b = n.localToScene(n.getBoundsInLocal());
        if (b == null) return false;

        return b.contains(sceneX, sceneY);
    }

    private static void pressFxOnlyLater(Node n) {
        if (n == null) return;

        Platform.runLater(() -> {
            if (n instanceof ButtonBase bb) {
                bb.pseudoClassStateChanged(PC_ARMED, true);
                bb.pseudoClassStateChanged(PC_PRESSED, true);

                bb.requestFocus();

                PauseTransition pt = new PauseTransition(SIM_PRESS_MS);
                pt.setOnFinished(ev -> {
                    bb.pseudoClassStateChanged(PC_PRESSED, false);
                    bb.pseudoClassStateChanged(PC_ARMED, false);
                });
                pt.play();
            }
        });
    }

    private static void fireLaterWithPressFx(Node n) {
        if (n == null) return;

        Platform.runLater(() -> {
            if (n instanceof ButtonBase bb) {
                bb.pseudoClassStateChanged(PC_ARMED, true);
                bb.pseudoClassStateChanged(PC_PRESSED, true);

                bb.requestFocus();

                bb.fire();

                PauseTransition pt = new PauseTransition(SIM_PRESS_MS);
                pt.setOnFinished(ev -> {
                    bb.pseudoClassStateChanged(PC_PRESSED, false);
                    bb.pseudoClassStateChanged(PC_ARMED, false);
                });
                pt.play();
            }
        });
    }

    private void clearSceneFocusToLeftStack() {
        if (leftStack == null) return;

        Platform.runLater(() -> {
            if (leftStack.getScene() != null) {
                leftStack.requestFocus();
                return;
            }
            var p = leftStack.getParent();
            if (p != null) p.requestFocus();
        });
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

        // ✅ открываем фильтр -> закрываем доп.оверлеи тем же триггером
        if (!filterOpen) runOutsideClosers();

        if (!filterOpen && sortOpen) closeSortInstant();

        boolean show = !filterOpen;
        filterOpen = show;

        updateCasesScrollHidden();

        if (filterAnim != null) {
            filterAnim.stop();
            filterAnim = null;
        }

        if (show) {
            clearSceneFocusToLeftStack();

            blur.setActive(true);
            showFilterAnimated();
        } else {
            if (!sortOpen) blur.setActive(false);
            hideFilterAnimated();
        }
    }

    public void toggleSort() {
        if (sortSheet == null || leftStack == null) return;

        // ✅ открываем сорт -> закрываем доп.оверлеи тем же триггером
        if (!sortOpen) runOutsideClosers();

        if (!sortOpen && filterOpen) closeFilterInstant();

        boolean show = !sortOpen;
        sortOpen = show;

        updateCasesScrollHidden();

        if (sortAnim != null) {
            sortAnim.stop();
            sortAnim = null;
        }

        if (show) {
            clearSceneFocusToLeftStack();

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

            updateCasesScrollHidden();
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

            updateCasesScrollHidden();
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

        updateCasesScrollHidden();

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

        updateCasesScrollHidden();

        if (!filterOpen) blur.setActive(false);
    }

    private void applySheetHeightNow() {
        if (leftStack == null) return;

        double base = leftStack.getHeight();
        if (base <= 0) return;

        if (filterSheet != null && filterSheet.isVisible()) filterSheet.setMaxHeight(base - 14);
        if (sortSheet != null && sortSheet.isVisible()) sortSheet.setMaxHeight(base - 14);
    }

    // ===================== FILTER UI =====================

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

        StackPane card = new StackPane(content, overlay);
        StackPane.setAlignment(content, Pos.TOP_LEFT);

        filterSheet.getChildren().add(card);
        filterCardRoot = card;

        UiScroll.clipRoundedSheet(filterSheet, FILTER_SHEET_RADIUS);

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

        resetBtn.setVisible(hasAny);
        resetBtn.setManaged(hasAny);
        resetBtn.setDisable(!hasAny);

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

        StackPane card = new StackPane(root);
        StackPane.setAlignment(root, Pos.TOP_LEFT);

        sortSheet.getChildren().add(card);
        sortCardRoot = card;
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

    private void updateCasesScrollHidden() {
        if (casesList == null) return;

        boolean hide = filterOpen || sortOpen;
        var classes = casesList.getStyleClass();

        if (hide) {
            if (!classes.contains(CASES_SCROLL_HIDDEN_CLASS)) classes.add(CASES_SCROLL_HIDDEN_CLASS);
        } else {
            classes.remove(CASES_SCROLL_HIDDEN_CLASS);
        }
    }

    private static boolean isInside(Node target, Node container) {
        if (target == null || container == null) return false;

        Node cur = target;
        while (cur != null) {
            if (cur == container) return true;
            cur = cur.getParent();
        }
        return false;
    }
}
