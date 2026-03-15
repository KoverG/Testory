// FILE: src/main/java/app/domain/cycles/ui/left/LeftPaneCoordinator.java
package app.domain.cycles.ui.left;

import app.core.I18n;
import app.core.PrivateRootConfig;
import app.domain.cycles.repo.CaseHistoryIndexStore;
import app.domain.cycles.repo.CycleCardJsonReader;
import app.domain.cycles.ui.CyclesViewRefs;
import app.domain.cycles.ui.list.CaseListItem;
import app.domain.cycles.ui.list.CycleListItem;
import app.domain.cycles.ui.list.ListPresenter;
import app.domain.cycles.ui.overlay.FilterSheet;
import app.domain.cycles.ui.right.RightPaneCoordinator;
import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.cycles.usecase.CycleDraft;
import app.domain.cycles.usecase.CycleRunState;
import app.domain.testcases.LabelStore;
import app.domain.testcases.TagStore;
import app.domain.testcases.TestCase;
import app.domain.testcases.query.TestCaseFilter;
import app.domain.testcases.repo.TestCaseCardStore;
import app.ui.UiSvg;
import app.ui.confirm.LeftDeleteConfirm;
import app.domain.cycles.ui.overlay.LeftListActionOverlay;
import app.ui.list.LeftListStickyHeader;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.awt.Desktop;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LeftPaneCoordinator {

    private enum CasesPickerDiffType {
        NONE,
        ADD,
        CHANGE
    }

    private static final String ICON_SEARCH = "close.svg";
    private static final String ICON_FOLDER = "folder.svg";

    // Cycles mode
    private static final String ICON_TRASH  = "trash.svg";

    // Cases picker mode
    private static final String ICON_CASES_PICK = "plus.svg";

    // вњ… РџР°РїРєР° С†РёРєР»РѕРІ. JSON-С„Р°Р№Р»С‹ РІРЅСѓС‚СЂРё = РёСЃС‚РѕС‡РЅРёРє РґР°РЅРЅС‹С… СЃРїРёСЃРєР° С†РёРєР»РѕРІ.
    private static final Path CYCLES_ROOT = Path.of("test_resources", "cycles");

    // вњ… РёРјСЏ РїРѕРґРїР°РїРєРё-РјСѓСЃРѕСЂРєРё (РєР°Рє РІ TestCases: "_trash")
    private static final String TRASH_DIR_NAME = "_trash";

    // ===================== I18N =====================

    // вњ… delete confirm texts for cycles (NOT tc.*)
    private static final String I18N_CY_TRASH_TITLE = "cy.trash.delete.title";
    private static final String I18N_CY_TRASH_HINT  = "cy.trash.delete.hint";

    // вњ… fallback title for empty cycle title (but never show id)
    private static final String I18N_CY_UNTITLED = "cy.title.untitled";

    // вњ… NEW: sticky header titles
    private static final String I18N_CY_LEFT_LIST_CYCLES = "cy.left.list.cycles";
    private static final String I18N_CY_LEFT_LIST_CASES  = "cy.left.list.cases";
    // ===============================================

    // ===================== SEARCH UX =====================
    private double searchIdleDelayMs = 1000.0;
    // ===================================

    // ===================== TRASH MODE (shared) =====================
    private static final String TRASH_SPACER_ID = "__TRASH_SPACER__";

    // вњ… NEW: top spacer that offsets sticky header (scrolls away)
    private static final String TOP_SPACER_ID = "__TOP_SPACER__";

    private static final double TRASH_SHIFT_PX = 26.0;
    private static final double TRASH_ANIM_MS  = 170.0;

    private final DoubleProperty trashShiftPx = new SimpleDoubleProperty(0.0);
    private Timeline trashShiftAnim;

    // вњ… close overlay only on click OUTSIDE leftStack (like TestCasesController)
    private boolean trashOutsideCloseInstalled = false;
    // ===============================================================

    // ===================== STICKY HEADER (left list) =====================
    private static final double LIST_HEADER_GAP_PX = 8.0; // extra gap below header
    private static final double LIST_HEADER_MARGIN_TOP_PX = 10.0; // MUST match StackPane.setMargin(top)
    private LeftListStickyHeader stickyHeader;
    private StackPane listStack; // ListView + header overlay
    // ===============================================================

    private final CyclesViewRefs v;
    private final RightPaneCoordinator right;
    private final ListPresenter list;
    private final FilterSheet leftFilterSheet;
    private final CaseHistoryIndexStore caseHistoryIndexStore = new CaseHistoryIndexStore();

    private LeftMode mode = LeftMode.CYCLES_LIST;
    private LeftPaneActions actions;

    // вњ… СѓРІРµРґРѕРјР»РµРЅРёРµ РґР»СЏ РІРЅРµС€РЅРёС… Р±РёРЅРґРёРЅРіРѕРІ (tgThemeLeft)
    private Runnable onModeChanged;

    private final PauseTransition searchIdleTimer = new PauseTransition();
    private boolean searchProgrammaticChange = false;
    private String appliedSearch = "";

    // ====== DATA ======
    // вњ… cycles: С‚РѕР»СЊРєРѕ РёР· С„Р°Р№Р»РѕРІРѕР№ СЃРёСЃС‚РµРјС‹
    private final ObservableList<CycleListItem> cycleAll = FXCollections.observableArrayList();
    private final ObservableList<CycleListItem> cycleView = FXCollections.observableArrayList();
    private final Map<String, CycleFilterSnapshot> cycleFilterById = new HashMap<>();

    // cases: РєР°Рє Р±С‹Р»Рѕ
    private final ObservableList<CaseListItem> caseAll = FXCollections.observableArrayList();
    private final ObservableList<CaseListItem> caseView = FXCollections.observableArrayList();
    private final Map<String, TestCase> caseById = new HashMap<>();

    // ====== CHECKS ======
    private final Map<String, BooleanProperty> trashChecks = new HashMap<>();
    private final Map<String, BooleanProperty> cycleTrashChecks = new HashMap<>();

    // вњ… РїРѕСЂСЏРґРѕРє РѕС‚РјРµС‚РєРё С‡РµРєР±РѕРєСЃРѕРІ (РґР»СЏ "РїРѕСЂСЏРґРєР° РґРѕР±Р°РІР»РµРЅРёСЏ")
    private final Map<String, Long> casePickOrder = new HashMap<>();
    private long casePickSeq = 0L;
    private boolean suppressPickOrder = false;

    // ====== UNIVERSAL overlay (cases + cycles) ======
    private LeftListActionOverlay casesAddOverlay;
    private LeftListActionOverlay cyclesTrashOverlay;

    // ====== confirm delete (same mechanic as TestCases, but texts differ) ======
    private LeftDeleteConfirm cyclesDeleteConfirm;

    public LeftPaneCoordinator(CyclesViewRefs v, RightPaneCoordinator right) {
        this.v = v;
        this.right = right;
        this.list = new ListPresenter(v.lvLeft);
        this.leftFilterSheet = new FilterSheet(v.leftStack, v.filterSheet, v.casesSheet);
    }

    public void init() {
        // вњ… install sticky header overlay inside list container (casesSheet)
        installStickyListHeader();

        if (v.btnFolder != null) UiSvg.setButtonSvg(v.btnFolder, ICON_FOLDER, getIconSizeFromFxml(v.btnFolder, 14));
        if (v.btnSearch != null) {
            int base = getIconSizeFromFxml(v.btnSearch, 14);
            int scaled = Math.max(1, Math.round(base / 1.5f));
            UiSvg.setButtonSvg(v.btnSearch, ICON_SEARCH, scaled);
        }
        if (v.btnTrash != null)  UiSvg.setButtonSvg(v.btnTrash, ICON_TRASH,  getIconSizeFromFxml(v.btnTrash, 14));

        v.btnCreate.setOnAction(e -> { if (actions != null) actions.onCreate(); });
        v.btnFilter.setOnAction(e -> toggleFilterSheet());
        v.btnSort.setOnAction(e -> { if (actions != null) actions.onSort(); });

        v.lvLeft.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && actions != null) actions.onOpenSelected();
        });

        // keep existing selection UX
        if (v.lvLeft != null) {
            v.lvLeft.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                if (mode == LeftMode.CYCLES_LIST) refreshCyclesDeleteAvailability();
            });
        }

        v.btnFolder.setOnAction(e -> openCyclesFolder());

        if (v.rightRoot != null) {
            v.rightRoot.visibleProperty().addListener((obs, oldV, newV) -> refreshLeftActionButtonVisibility());
        }
        if (right != null) {
            right.setOnUiStateChanged(this::onRightUiStateChanged);
        }

        // ====== CASES PICKER overlay (universal) ======
        casesAddOverlay = new LeftListActionOverlay(v.leftStack, v.casesSheet, "Р”РѕР±Р°РІРёС‚СЊ");
        casesAddOverlay.setOnOpenChanged(open -> {
            animateTrashShift(open);
            updateCasesTrashSpacerItem();
            // вњ… РїСЂРё РѕС‚РєСЂС‹С‚РёРё overlay СЃРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµРј С‡РµРєР±РѕРєСЃС‹ СЃ РїСЂР°РІРѕР№ Р·РѕРЅРѕР№
            syncCasesPickerChecksFromRight();
            refreshAddAvailability();
            refreshCasesAddButtonText();
        });
        casesAddOverlay.setOnSpacerChanged(this::updateCasesTrashSpacerItem);

        casesAddOverlay.selectAllCheckBox().selectedProperty().addListener((obs, oldV, newV) -> {
            setAllTrashChecks(newV != null && newV);
            refreshAddAvailability();
            refreshCasesAddButtonText();
        });

        // вњ… РїСЂРёРјРµРЅРёС‚СЊ diff РІ РїСЂР°РІСѓСЋ Р·РѕРЅСѓ, РќР• Р·Р°РєСЂС‹РІР°СЏ СЂРµР¶РёРј
        casesAddOverlay.setOnAdd(this::addSelectedCasesToRight);

        // ====== CYCLES trash overlay (delete) ======
        cyclesTrashOverlay = new LeftListActionOverlay(v.leftStack, v.casesSheet, "РЈРґР°Р»РёС‚СЊ");
        cyclesTrashOverlay.setOnOpenChanged(open -> {
            // РµСЃР»Рё РїРѕРґС‚РІРµСЂР¶РґРµРЅРёРµ Р±С‹Р»Рѕ РѕС‚РєСЂС‹С‚Рѕ вЂ” Р·Р°РєСЂС‹РІР°РµРј РІРјРµСЃС‚Рµ СЃ overlay
            if (!open && cyclesDeleteConfirm != null) {
                cyclesDeleteConfirm.close();
            }

            animateTrashShift(open);
            updateCyclesTrashSpacerItem();
            refreshCyclesDeleteAvailability();
        });
        cyclesTrashOverlay.setOnSpacerChanged(this::updateCyclesTrashSpacerItem);
        cyclesTrashOverlay.selectAllCheckBox().selectedProperty().addListener((obs, oldV, newV) -> {
            setAllCycleTrashChecks(newV != null && newV);
            refreshCyclesDeleteAvailability();
        });

        // ====== confirm delete (РєР°Рє РІ TestCases), РЅРѕ СЃ i18n РґР»СЏ Cycles ======
        cyclesDeleteConfirm = new LeftDeleteConfirm(v.leftStack, this::deleteSelectedCyclesChecked);
        cyclesDeleteConfirm.setCanOpenSupplier(() ->
                cyclesTrashOverlay != null
                        && cyclesTrashOverlay.isOpen()
                        && hasAnyCycleTrashChecked()
        );
        // вњ… texts for cycles (not tc.*)
        cyclesDeleteConfirm.setTextKeys(I18N_CY_TRASH_TITLE, I18N_CY_TRASH_HINT);

        // РєРЅРѕРїРєР° "РЈРґР°Р»РёС‚СЊ" РІ overlay -> РѕС‚РєСЂС‹РІР°РµРј confirm
        cyclesTrashOverlay.setOnDelete(() -> {
            if (cyclesDeleteConfirm != null) cyclesDeleteConfirm.open();
        });

        leftFilterSheet.setOnBeforeOpen(this::closeLeftOverlaysForFilter);
        leftFilterSheet.setOnApply(() -> {
            applyFiltersToList();
            updateFilterButtonText();
        });
        leftFilterSheet.init();

        installSearchBehavior();
        applyMode(LeftMode.CYCLES_LIST);

        // вњ… after scene ready: install outside-close for trash overlay (cycles)
        Platform.runLater(this::installTrashOutsideClose);
    }

    public void setMode(LeftMode newMode) {
        if (this.mode == newMode) return;
        applyMode(newMode);
    }

    public LeftMode mode() {
        return mode;
    }

    // вњ… РґР»СЏ ThemeToggleUiInstaller
    public void setOnModeChanged(Runnable r) {
        this.onModeChanged = r;
    }

    /**
     * вњ… Р±РµР·РѕРїР°СЃРЅС‹Р№ refresh cycles РїРѕСЃР»Рµ СЃРѕС…СЂР°РЅРµРЅРёСЏ С„Р°Р№Р»Р° РЅР° РґРёСЃРє.
     * РќРµ РјРµРЅСЏРµС‚ mode, РЅРµ Р»РѕРјР°РµС‚ overlays вЂ” РїСЂРѕСЃС‚Рѕ РїРµСЂРµС‡РёС‚С‹РІР°РµС‚ РїР°РїРєСѓ Рё РїРµСЂРµСЂРёСЃРѕРІС‹РІР°РµС‚ СЃРїРёСЃРѕРє РµСЃР»Рё РјС‹ РІ CYCLES_LIST.
     */
    public void refreshCyclesFromDisk() {
        reloadCyclesFromDisk();
        applyFiltersToList();

        if (mode == LeftMode.CYCLES_LIST) {
            list.showCycles(cycleView);
            installCyclesListCellFactory();
            updateCyclesTrashSpacerItem();
            refreshCyclesDeleteAvailability();
        }

        // header text is still correct, but keep it safe
        updateStickyHeaderTitle();
    }

    /**
     * UX shortcut:
     * - switch left list to CASES_PICKER
     * - and open "Р”РѕР±Р°РІРёС‚СЊ" overlay (same as pressing the plus/trash button in cases picker mode).
     */
    public void openCasesPickerAddOverlay() {
        // switch list to cases picker (reloads items + sets btnTrash to plus)
        setMode(LeftMode.CASES_PICKER);

        // вњ… СЃРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµРј С‡РµРєР±РѕРєСЃС‹ СЃ РїСЂР°РІРѕР№ Р·РѕРЅРѕР№ Рё РѕР±РЅРѕРІР»СЏРµРј РґРѕСЃС‚СѓРїРЅРѕСЃС‚СЊ РєРЅРѕРїРєРё
        syncCasesPickerChecksFromRight();
        refreshAddAvailability();
        refreshCasesAddButtonText();
        

        // open overlay (shift list + show checkboxes + action button)
        if (casesAddOverlay != null && !casesAddOverlay.isOpen()) {
            casesAddOverlay.open();
        }
    }

    /**
     * вњ… Р—Р°РґР°С‡Р°: РєРЅРѕРїРєР° "Р”РѕР±Р°РІРёС‚СЊ РєРµР№СЃС‹" СЂР°Р±РѕС‚Р°РµС‚ РєР°Рє С‚СѓРјР±Р»РµСЂ.
     * РџРѕРІС‚РѕСЂРЅРѕРµ РЅР°Р¶Р°С‚РёРµ РІРѕР·РІСЂР°С‰Р°РµС‚ РёСЃС…РѕРґРЅРѕРµ СЃРѕСЃС‚РѕСЏРЅРёРµ СЃРїРёСЃРєР°:
     * - РµСЃР»Рё СЃРµР№С‡Р°СЃ СѓР¶Рµ РІ СЂРµР¶РёРјРµ РґРѕР±Р°РІР»РµРЅРёСЏ (CASES_PICKER) вЂ” РІРѕР·РІСЂР°С‰Р°РµРјСЃСЏ РІ CYCLES_LIST
     * - РёРЅР°С‡Рµ вЂ” РѕС‚РєСЂС‹РІР°РµРј СЂРµР¶РёРј РґРѕР±Р°РІР»РµРЅРёСЏ (openCasesPickerAddOverlay)
     */
    public void toggleCasesPickerAddOverlay() {
        if (mode == LeftMode.CASES_PICKER) {
            // applyMode(CYCLES_LIST) СЃР°Рј Р·Р°РєСЂРѕРµС‚ casesAddOverlay РµСЃР»Рё РѕРЅ РѕС‚РєСЂС‹С‚
            setMode(LeftMode.CYCLES_LIST);
            return;
        }
        openCasesPickerAddOverlay();
    }

    private void applyMode(LeftMode newMode) {
        this.mode = newMode;

        // close other overlay when leaving its mode
        if (newMode != LeftMode.CASES_PICKER && casesAddOverlay != null && casesAddOverlay.isOpen()) {
            casesAddOverlay.close();
        }
        if (newMode != LeftMode.CYCLES_LIST && cyclesTrashOverlay != null && cyclesTrashOverlay.isOpen()) {
            cyclesTrashOverlay.close();
        }
        if (newMode != LeftMode.CYCLES_LIST && cyclesDeleteConfirm != null && cyclesDeleteConfirm.isOpen()) {
            cyclesDeleteConfirm.close();
        }
        if (leftFilterSheet != null && leftFilterSheet.isOpen()) {
            leftFilterSheet.close();
        }

        animateTrashShift(false);
        trashShiftPx.set(0.0);

        // вњ… FIX: СЃР±СЂРѕСЃРёС‚СЊ items РїРµСЂРµРґ setItems РґСЂСѓРіРѕРіРѕ С‚РёРїР° (С‡С‚РѕР±С‹ СЃС‚Р°СЂС‹Рµ cells СѓС€Р»Рё РІ empty=true)
        if (v.lvLeft != null) {
            v.lvLeft.setItems(FXCollections.observableArrayList());
            v.lvLeft.getSelectionModel().clearSelection();
        }

        if (newMode == LeftMode.CYCLES_LIST) {
            actions = new CyclesListActions(v, right, () -> applyMode(LeftMode.CASES_PICKER));

            if (v.btnTrash != null) {
                UiSvg.setButtonSvg(v.btnTrash, ICON_TRASH, getIconSizeFromFxml(v.btnTrash, 14));
                v.btnTrash.setDisable(false);
                if (cyclesTrashOverlay != null) {
                    cyclesTrashOverlay.init(v.btnTrash);
                    cyclesTrashOverlay.setButtonText("Удалить");
                }
                refreshCyclesDeleteAvailability();
            }

            reloadCyclesFromDisk();
            applyFiltersToList();

            list.showCycles(cycleView);

            installCyclesListCellFactory();
            updateCyclesTrashSpacerItem();

            refreshCyclesDeleteAvailability();

        } else {
            actions = new CasesPickerActions(v, right, () -> applyMode(LeftMode.CYCLES_LIST));

            if (v.btnTrash != null) {
                UiSvg.setButtonSvg(v.btnTrash, ICON_CASES_PICK, getIconSizeFromFxml(v.btnTrash, 14));
                v.btnTrash.setDisable(false);

                if (casesAddOverlay != null) {
                    casesAddOverlay.init(v.btnTrash);
                    casesAddOverlay.setButtonText("Добавить");
                }
            }

            // вњ… РїСЂРё РІС…РѕРґРµ РІ СЂРµР¶РёРј РІС‹Р±РѕСЂР° РєРµР№СЃРѕРІ вЂ” РЅР°С‡РёРЅР°РµРј РЅРѕРІСѓСЋ СЃРµСЃСЃРёСЋ РїРѕСЂСЏРґРєР° РІС‹Р±РѕСЂР°
            casePickOrder.clear();
            casePickSeq = 0L;

            // вњ… Р’РђР–РќРћ: РѕСЃС‚Р°РІР»СЏРµРј С‚РІРѕСЋ СЂР°Р±РѕС‡СѓСЋ Р·Р°РіСЂСѓР·РєСѓ РєРµР№СЃРѕРІ РёР· TestCaseCardStore
            reloadCasesFromDisk();
            applyFiltersToList();

            list.showCases(caseView);

            installCasesPickerCellFactory();
            updateCasesTrashSpacerItem();

            // вњ… РѕС‚РјРµС‡Р°РµРј СѓР¶Рµ РґРѕР±Р°РІР»РµРЅРЅС‹Рµ СЃРїСЂР°РІР° РєРµР№СЃС‹
            syncCasesPickerChecksFromRight();
            refreshAddAvailability();
            refreshCasesAddButtonText();
        }

        refreshLeftActionButtonVisibility();
        updateSearchButtonVisibility();
        updateFilterButtonText();

        // вњ… update sticky header title per current mode (i18n)
        updateStickyHeaderTitle();

        // вњ… notify external binders (tgThemeLeft)
        if (onModeChanged != null) {
            try { onModeChanged.run(); } catch (Exception ignore) {}
        }
    }

    // ===================== STICKY HEADER (left list) =====================

    /**
     * Builds a StackPane overlay inside v.casesSheet:
     * - ListView at bottom
     * - sticky header on top
     *
     * Header background MUST match delete overlay:
     * tc-filter-overlay + tc-trash-glass.
     */
    private void installStickyListHeader() {
        if (v == null || v.casesSheet == null || v.lvLeft == null) return;

        // already installed?
        if (stickyHeader != null && listStack != null) return;

        stickyHeader = new LeftListStickyHeader();
        // вњ… important: allow scroll/clicks to pass through header overlay
        stickyHeader.setMouseTransparent(true);
        stickyHeader.setPickOnBounds(false);

        listStack = new StackPane();
        listStack.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // вњ… CRITICAL: casesSheet is VBox, stack must grow (fixes bottom clipping)
        VBox.setVgrow(listStack, Priority.ALWAYS);

        // move ListView into stack
        try {
            v.casesSheet.getChildren().remove(v.lvLeft);
        } catch (Exception ignored) {}

        // ensure ListView can fill stack
        v.lvLeft.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane.setAlignment(v.lvLeft, Pos.TOP_LEFT);

        listStack.getChildren().add(v.lvLeft);

        // overlay header on top
        StackPane.setAlignment(stickyHeader, Pos.TOP_CENTER);
        StackPane.setMargin(stickyHeader, new Insets(LIST_HEADER_MARGIN_TOP_PX, 10, 0, 10));
        listStack.getChildren().add(stickyHeader);

        // replace sheet content with the stack (single child)
        v.casesSheet.getChildren().clear();
        v.casesSheet.getChildren().add(listStack);

        // вњ… IMPORTANT: do NOT use ListView padding (it causes "scroll to header and stop").
        // We use a TOP spacer item that scrolls away.
        ensureTopSpacers();

        // initial title
        updateStickyHeaderTitle();
    }

    private void updateStickyHeaderTitle() {
        if (stickyHeader == null) return;

        String key = (mode == LeftMode.CASES_PICKER)
                ? I18N_CY_LEFT_LIST_CASES
                : I18N_CY_LEFT_LIST_CYCLES;

        stickyHeader.setTitleKey(key);

        // title may affect header height в†’ update spacer height lazily by using supplier
        // but also ensure spacer exists after mode switches/filters
        ensureTopSpacers();
    }

    private double topSpacerHeightPx() {
        if (stickyHeader == null) return 1.0;

        double h = stickyHeader.getHeight();
        if (h <= 0.0) h = stickyHeader.prefHeight(-1);

        // if component provides stable height, use it as fallback
        if (h <= 0.0) {
            try { h = stickyHeader.stableHeightPx(); } catch (Exception ignore) {}
        }

        if (h <= 0.0) h = 1.0;

        double top = LIST_HEADER_MARGIN_TOP_PX + h + LIST_HEADER_GAP_PX;
        return Math.max(1.0, top);
    }

    private void ensureTopSpacers() {
        // cycles view
        ensureTopSpacerRow(
                cycleView,
                TOP_SPACER_ID,
                CycleListItem::id,
                () -> stickyHeader != null,
                () -> new CycleListItem(TOP_SPACER_ID, "", "")
        );
        moveTopSpacerToStart(cycleView, CycleListItem::id);

        // cases view
        ensureTopSpacerRow(
                caseView,
                TOP_SPACER_ID,
                CaseListItem::id,
                () -> stickyHeader != null,
                () -> new CaseListItem(TOP_SPACER_ID, "")
        );
        moveTopSpacerToStart(caseView, CaseListItem::id);
    }

    private static <T> void ensureTopSpacerRow(
            ObservableList<T> viewItems,
            String spacerId,
            java.util.function.Function<T, String> idGetter,
            java.util.function.Supplier<Boolean> wantSpacer,
            java.util.function.Supplier<T> spacerFactory
    ) {
        if (viewItems == null) return;

        boolean need = wantSpacer != null && Boolean.TRUE.equals(wantSpacer.get());
        int idx = indexOfSpacer(viewItems, spacerId, idGetter);

        if (need) {
            if (idx < 0 && spacerFactory != null) viewItems.add(0, spacerFactory.get());
        } else {
            if (idx >= 0) viewItems.remove(idx);
        }
    }

    private static <T> void moveTopSpacerToStart(ObservableList<T> items, java.util.function.Function<T, String> idGetter) {
        if (items == null || items.isEmpty()) return;

        int idx = -1;
        for (int i = 0; i < items.size(); i++) {
            T it = items.get(i);
            if (it == null) continue;
            String id = idGetter.apply(it);
            if (TOP_SPACER_ID.equals(id)) { idx = i; break; }
        }

        if (idx > 0) {
            T spacer = items.remove(idx);
            items.add(0, spacer);
        }
    }

    // ===================== OPEN CYCLE CARD (single click) =====================

    private void onCycleClicked(CycleListItem c) {
        if (c == null) return;

        String id = safeTrim(c.id());
        if (id.isEmpty() || TRASH_SPACER_ID.equals(id) || TOP_SPACER_ID.equals(id)) return;

        // toggle-close if clicking currently opened cycle (like TestCases)
        if (right != null && right.isOpen()) {
            String openedId = safeTrim(right.openedCycleId());
            if (!openedId.isEmpty() && openedId.equals(id)) {
                right.close();
                try {
                    if (v.lvLeft != null) v.lvLeft.getSelectionModel().clearSelection();
                } catch (Exception ignored) {}
                return;
            }
        }

        // open existing card
        Path file = CYCLES_ROOT.resolve(id + ".json");
        if (right != null) right.openExistingCard(file);
    }

    // ===================== ADD SELECTED CASES -> RIGHT (UI-only) =====================

    private void addSelectedCasesToRight() {
        if (right == null) return;
        if (!right.isOpen()) return;

        LinkedHashSet<String> desiredIds = getCheckedCaseIds();
        LinkedHashSet<String> currentIds = new LinkedHashSet<>(right.getAddedCaseIds());

        // toRemove: Р±С‹Р»Рѕ СЃРїСЂР°РІР°, РЅРѕ СЃРµР№С‡Р°СЃ РЅРµ РѕС‚РјРµС‡РµРЅРѕ
        List<String> toRemove = new ArrayList<>();
        for (String id : currentIds) {
            if (id == null || id.isBlank()) continue;
            if (!desiredIds.contains(id)) toRemove.add(id);
        }

        // toAdd: РѕС‚РјРµС‡РµРЅРѕ, РЅРѕ РµС‰С‘ РЅРµС‚ СЃРїСЂР°РІР°
        List<String> toAddIds = new ArrayList<>();
        for (String id : desiredIds) {
            if (id == null || id.isBlank()) continue;
            if (!currentIds.contains(id)) toAddIds.add(id);
        }

        // 1) remove unchecked (СЃРѕС…СЂР°РЅСЏРµРј РїРѕСЂСЏРґРѕРє РѕСЃС‚Р°РІС€РёС…СЃСЏ)
        if (!toRemove.isEmpty()) {
            right.removeAddedCasesByIds(toRemove);
        }

        // 2) add new вЂ” РІ РїРѕСЂСЏРґРєРµ "РїРѕ РјРµСЂРµ РІС‹Р±РѕСЂР°" (casePickOrder), РёРЅР°С‡Рµ fallback РЅР° РїРѕСЂСЏРґРѕРє СЃРїРёСЃРєР°
        if (!toAddIds.isEmpty()) {
            List<CycleCaseRef> toAdd = buildToAddRefsOrdered(toAddIds);
            if (!toAdd.isEmpty()) right.addAddedCases(toAdd);
        }

        right.closePickerPreviewCaseCard();

        // вњ… СЂРµР¶РёРј Рё overlay РѕСЃС‚Р°СЋС‚СЃСЏ Р°РєС‚РёРІРЅС‹РјРё, РїСЂРѕСЃС‚Рѕ РѕР±РЅРѕРІР»СЏРµРј РґРѕСЃС‚СѓРїРЅРѕСЃС‚СЊ РєРЅРѕРїРєРё
        refreshAddAvailability();
        refreshCasesAddButtonText();
    }

    private LinkedHashSet<String> getCheckedCaseIds() {
        LinkedHashSet<String> out = new LinkedHashSet<>();

        for (var e : trashChecks.entrySet()) {
            if (e == null) continue;

            String id = safeTrim(e.getKey());
            if (id.isEmpty() || TRASH_SPACER_ID.equals(id) || TOP_SPACER_ID.equals(id)) continue;

            BooleanProperty p = e.getValue();
            if (p != null && p.get()) out.add(id);
        }

        return out;
    }

    private boolean hasCasesPickerDiff() {
        return casesPickerDiffType() != CasesPickerDiffType.NONE;
    }

    private CasesPickerDiffType casesPickerDiffType() {
        if (right == null || !right.isOpen()) return CasesPickerDiffType.NONE;

        LinkedHashSet<String> desired = getCheckedCaseIds();
        LinkedHashSet<String> current = new LinkedHashSet<>(right.getAddedCaseIds());
        if (desired.equals(current)) return CasesPickerDiffType.NONE;

        if (current.isEmpty()) return CasesPickerDiffType.ADD;

        for (String id : current) {
            if (!desired.contains(id)) {
                return CasesPickerDiffType.CHANGE;
            }
        }

        return CasesPickerDiffType.ADD;
    }

    private List<CycleCaseRef> buildToAddRefsOrdered(List<String> toAddIds) {
        // 1) РµСЃР»Рё РµСЃС‚СЊ order РїРѕ С‡РµРєР±РѕРєСЃР°Рј вЂ” СЃРѕСЂС‚РёСЂСѓРµРј РїРѕ РЅРµРјСѓ
        List<String> ids = new ArrayList<>();
        for (String id : toAddIds) {
            if (id == null || id.isBlank()) continue;
            ids.add(id);
        }

        ids.sort((a, b) -> {
            Long oa = casePickOrder.get(a);
            Long ob = casePickOrder.get(b);
            if (oa == null && ob == null) return 0;
            if (oa == null) return 1;
            if (ob == null) return -1;
            return Long.compare(oa, ob);
        });

        // title lookup РёР· caseAll/caseView
        Map<String, String> titleById = new HashMap<>();
        for (CaseListItem it : caseAll) {
            if (it == null) continue;
            String id = safeTrim(it.id());
            if (id.isEmpty() || TRASH_SPACER_ID.equals(id) || TOP_SPACER_ID.equals(id)) continue;
            titleById.putIfAbsent(id, safeTrim(it.title()));
        }
        for (CaseListItem it : caseView) {
            if (it == null) continue;
            String id = safeTrim(it.id());
            if (id.isEmpty() || TRASH_SPACER_ID.equals(id) || TOP_SPACER_ID.equals(id)) continue;
            titleById.putIfAbsent(id, safeTrim(it.title()));
        }

        List<CycleCaseRef> out = new ArrayList<>();
        for (String id : ids) {
            String title = titleById.getOrDefault(id, "");
            out.add(new CycleCaseRef(id, title));
        }
        return out;
    }

    /**
     * РџСЂРё РІС…РѕРґРµ/РѕС‚РєСЂС‹С‚РёРё overlay РІ CASES_PICKER:
     * вњ… РѕС‚РјРµС‡Р°РµРј СѓР¶Рµ РґРѕР±Р°РІР»РµРЅРЅС‹Рµ СЃРїСЂР°РІР° РєРµР№СЃС‹ Рё РќР• РїРёС€РµРј РёС… РІ order (СЌС‚Рѕ РЅРµ "РЅРѕРІРѕРµ РґРѕР±Р°РІР»РµРЅРёРµ")
     */
    private void syncCasesPickerChecksFromRight() {
        if (right == null || !right.isOpen()) return;

        suppressPickOrder = true;
        try {
            for (String idRaw : right.getAddedCaseIds()) {
                String id = safeTrim(idRaw);
                if (id.isEmpty()) continue;

                BooleanProperty p = getOrCreateTrashCheck(id);
                p.set(true);
            }
        } finally {
            suppressPickOrder = false;
        }
    }

    private BooleanProperty getOrCreateTrashCheck(String id) {
        String key = safeTrim(id);
        if (key.isEmpty()) return new SimpleBooleanProperty(false);

        BooleanProperty p = trashChecks.get(key);
        if (p != null) return p;

        SimpleBooleanProperty prop = new SimpleBooleanProperty(false);
        // вњ… С„РёРєСЃРёСЂСѓРµРј РїРѕСЂСЏРґРѕРє РІС‹Р±РѕСЂР° РїРѕР»СЊР·РѕРІР°С‚РµР»РµРј
        prop.addListener((obs, oldV, newV) -> {
            if (suppressPickOrder) return;

            boolean v = newV != null && newV;
            if (v) {
                casePickOrder.put(key, ++casePickSeq);
            } else {
                casePickOrder.remove(key);
            }
            refreshAddAvailability();
            refreshCasesAddButtonText();
        });

        trashChecks.put(key, prop);
        return prop;
    }

    // ===================== OUTSIDE CLOSE (cycles trash) =====================

    private void installTrashOutsideClose() {
        if (trashOutsideCloseInstalled) return;
        if (v == null || v.leftStack == null) return;

        var scene = v.leftStack.getScene();
        if (scene == null) return;

        trashOutsideCloseInstalled = true;

        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            // only in cycles list mode
            if (mode != LeftMode.CYCLES_LIST) return;

            if (cyclesTrashOverlay == null || !cyclesTrashOverlay.isOpen()) return;

            Object t = e.getTarget();
            if (!(t instanceof Node n)) return;

            // click on trash button should not close mode on PRESS
            if (v.btnTrash != null && isDescendantOf(n, v.btnTrash)) return;

            // click inside leftStack should NOT close (including list / overlay)
            if (isDescendantOf(n, v.leftStack)) return;

            // click outside leftStack -> close overlay + confirm
            if (cyclesDeleteConfirm != null) cyclesDeleteConfirm.close();
            cyclesTrashOverlay.close();
        });
    }

    private static boolean isDescendantOf(Node node, Node root) {
        if (node == null || root == null) return false;
        Node cur = node;
        while (cur != null) {
            if (cur == root) return true;
            cur = cur.getParent();
        }
        return false;
    }

    // ===================== LIST CELLS (trash-mode) =====================

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void installCyclesListCellFactory() {
        if (v.lvLeft == null) return;

        TrashModeListSupport.install(
                v.lvLeft,
                (ObservableList) cycleView,
                trashShiftPx,
                (Map) cycleTrashChecks,
                TOP_SPACER_ID,
                this::topSpacerHeightPx,
                TRASH_SPACER_ID,
                () -> cyclesTrashOverlay != null ? cyclesTrashOverlay.scrollSpacerPx() : 1.0,
                it -> ((CycleListItem) it).id(),
                it -> {
                    CycleListItem c = (CycleListItem) it;

                    String t = safeTrim(c.safeTitle());
                    String d = safeTrim(c.safeCreatedAtUi());

                    if (t.isBlank()) t = I18n.t(I18N_CY_UNTITLED);
                    if (d.isBlank()) return t;

                    return t + " " + d;
                },
                this::refreshCyclesDeleteAvailability,
                it -> {
                    if (!(it instanceof CycleListItem c)) return;
                    onCycleClicked(c);
                }
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void installCasesPickerCellFactory() {
        if (v.lvLeft == null) return;

        TrashModeListSupport.install(
                v.lvLeft,
                (ObservableList) caseView,
                trashShiftPx,
                (Map) trashChecks,
                TOP_SPACER_ID,
                this::topSpacerHeightPx,
                TRASH_SPACER_ID,
                () -> casesAddOverlay != null ? casesAddOverlay.scrollSpacerPx() : 1.0,
                it -> ((CaseListItem) it).id(),
                it -> ((CaseListItem) it).title(),
                () -> {
                    refreshAddAvailability();
                    refreshCasesAddButtonText();
                },
                it -> {
                    if (!(it instanceof CaseListItem c)) return;
                    right.openTestCaseCardFromList(c.id(), currentCasePickerIds());
                },
                it -> {
                    if (!(it instanceof CaseListItem c)) return;
                    right.openTestCaseCardFromList(c.id(), currentCasePickerIds());
                },
                (it, id) -> {
                    BooleanProperty p = trashChecks.get(id);
                    return p != null && p.get();
                }
        );
    }

    // ===================== SPACER ROWS =====================

    private void updateCasesTrashSpacerItem() {
        ensureSpacerRow(
                caseView,
                TRASH_SPACER_ID,
                CaseListItem::id,
                () -> casesAddOverlay != null && casesAddOverlay.isOpen(),
                () -> new CaseListItem(TRASH_SPACER_ID, "")
        );
        moveSpacerToEnd(caseView, CaseListItem::id);
        moveTopSpacerToStart(caseView, CaseListItem::id);
    }

    private List<String> currentCasePickerIds() {
        List<String> ids = new ArrayList<>();
        for (CaseListItem item : caseView) {
            if (item == null) continue;

            String id = safeTrim(item.id());
            if (id.isEmpty() || TRASH_SPACER_ID.equals(id) || TOP_SPACER_ID.equals(id)) continue;
            ids.add(id);
        }
        return ids;
    }

    private void updateCyclesTrashSpacerItem() {
        ensureSpacerRow(
                cycleView,
                TRASH_SPACER_ID,
                CycleListItem::id,
                () -> cyclesTrashOverlay != null && cyclesTrashOverlay.isOpen(),
                () -> new CycleListItem(TRASH_SPACER_ID, "", "")
        );
        moveSpacerToEnd(cycleView, CycleListItem::id);
        moveTopSpacerToStart(cycleView, CycleListItem::id);
    }

    public static <T> void ensureSpacerRow(
            ObservableList<T> viewItems,
            String spacerId,
            java.util.function.Function<T, String> idGetter,
            java.util.function.Supplier<Boolean> wantSpacer,
            java.util.function.Supplier<T> spacerFactory
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

    public static <T> int indexOfSpacer(ObservableList<T> list, String spacerId, java.util.function.Function<T, String> idGetter) {
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

    private static <T> void moveSpacerToEnd(ObservableList<T> items, java.util.function.Function<T, String> idGetter) {
        if (items == null || items.isEmpty()) return;

        int idx = -1;
        for (int i = 0; i < items.size(); i++) {
            T it = items.get(i);
            if (it == null) continue;
            String id = idGetter.apply(it);
            if (TRASH_SPACER_ID.equals(id)) { idx = i; break; }
        }

        if (idx >= 0 && idx != items.size() - 1) {
            T spacer = items.remove(idx);
            items.add(spacer);
        }
    }

    // ===================== ENABLE/DISABLE action buttons =====================

    private void refreshAddAvailability() {
        if (casesAddOverlay == null) return;

        // вњ… РєРЅРѕРїРєР° Р°РєС‚РёРІРЅР° С‚РѕР»СЊРєРѕ РµСЃР»Рё РµСЃС‚СЊ diff РѕС‚РЅРѕСЃРёС‚РµР»СЊРЅРѕ РїСЂР°РІРѕР№ Р·РѕРЅС‹
        boolean enable = hasCasesPickerDiff();
        casesAddOverlay.setAddEnabled(enable);
    }

    private void refreshCasesAddButtonText() {
        if (casesAddOverlay == null) return;

        boolean useAddText = right == null
                || !right.isOpen()
                || right.getAddedCaseIds().isEmpty()
                || casesPickerDiffType() == CasesPickerDiffType.ADD;
        String text = useAddText
                ? "Добавить"
                : "Изменить";
        casesAddOverlay.setButtonText(text);
    }

    private void onRightUiStateChanged() {
        if (mode == LeftMode.CASES_PICKER
                && right != null
                && right.isOpen()
                && !right.isEditModeEnabled()
                && casesAddOverlay != null
                && casesAddOverlay.isOpen()) {
            casesAddOverlay.close();
        }
        refreshLeftActionButtonVisibility();
    }

    private void refreshLeftActionButtonVisibility() {
        if (v == null || v.btnTrash == null) return;

        boolean show = mode != LeftMode.CASES_PICKER || (right != null && right.isOpen());
        boolean disable = mode == LeftMode.CASES_PICKER
                && (right == null || !right.isOpen() || !right.isEditModeEnabled());
        v.btnTrash.setVisible(show);
        v.btnTrash.setManaged(show);
        v.btnTrash.setDisable(disable);
    }

    private void refreshCyclesDeleteAvailability() {
        if (cyclesTrashOverlay == null) return;
        boolean enable = hasAnyCycleTrashChecked();
        cyclesTrashOverlay.setDeleteEnabled(enable);
    }
    private boolean hasAnyCycleTrashChecked() {
        for (var e : cycleTrashChecks.entrySet()) {
            if (e == null) continue;
            BooleanProperty p = e.getValue();
            if (p != null && p.get()) return true;
        }
        return false;
    }

    private void setAllTrashChecks(boolean v) {
        // вњ… select-all РЅРµ РґРѕР»Р¶РµРЅ Р»РѕРјР°С‚СЊ "РїРѕСЂСЏРґРѕРє РґРѕР±Р°РІР»РµРЅРёСЏ" вЂ” СЌС‚Рѕ РјР°СЃСЃРѕРІР°СЏ РѕРїРµСЂР°С†РёСЏ
        suppressPickOrder = true;
        try {
            for (var it : caseView) {
                if (it == null) continue;
                String id = it.id();
                if (id == null || id.isBlank() || TRASH_SPACER_ID.equals(id) || TOP_SPACER_ID.equals(id)) continue;

                BooleanProperty p = getOrCreateTrashCheck(id);
                p.set(v);
            }
        } finally {
            suppressPickOrder = false;
        }
    }

    private void setAllCycleTrashChecks(boolean v) {
        for (var it : cycleView) {
            if (it == null) continue;
            String id = it.id();
            if (id == null || id.isBlank() || TRASH_SPACER_ID.equals(id) || TOP_SPACER_ID.equals(id)) continue;
            cycleTrashChecks.computeIfAbsent(id, k -> new SimpleBooleanProperty(false)).set(v);
        }
    }

    // ===================== UI + SEARCH =====================

    private void installSearchBehavior() {
        if (v.tfSearch == null) return;

        searchIdleTimer.setDuration(Duration.millis(searchIdleDelayMs));
        searchIdleTimer.setOnFinished(e -> applySearchNow());

        v.tfSearch.setOnAction(e -> {
            searchIdleTimer.stop();
            applySearchNow();
        });

        v.tfSearch.textProperty().addListener((obs, oldV, newV) -> {
            if (searchProgrammaticChange) {
                updateSearchButtonVisibility();
                return;
            }

            updateSearchButtonVisibility();
            searchIdleTimer.stop();
            searchIdleTimer.playFromStart();
        });

        if (v.btnSearch != null) {
            v.btnSearch.setOnAction(e -> clearSearchAndReset());
        }

        updateSearchButtonVisibility();
    }

    private void updateSearchButtonVisibility() {
        if (v.btnSearch == null || v.tfSearch == null) return;

        String t = v.tfSearch.getText();
        boolean has = t != null && !t.isBlank();

        v.btnSearch.setVisible(has);
        v.btnSearch.setManaged(has);
        v.btnSearch.setDisable(!has);
    }

    private void clearSearchAndReset() {
        if (v.tfSearch == null) return;

        searchIdleTimer.stop();

        searchProgrammaticChange = true;
        try {
            v.tfSearch.setText("");
        } finally {
            searchProgrammaticChange = false;
        }

        appliedSearch = "";
        applyFiltersToList();

        updateSearchButtonVisibility();

        javafx.application.Platform.runLater(v.tfSearch::requestFocus);
    }

    private void applySearchNow() {
        if (v.tfSearch == null) return;

        String q = v.tfSearch.getText();
        q = q == null ? "" : q.trim();

        appliedSearch = q;

        applyFiltersToList();

        if (actions != null) actions.onSearch(q);
    }

    // ===================== ANIM (shared) =====================

    private void animateTrashShift(boolean open) {
        double target = open ? TRASH_SHIFT_PX : 0.0;

        if (trashShiftAnim != null) trashShiftAnim.stop();

        trashShiftAnim = new Timeline(
                new KeyFrame(Duration.millis(TRASH_ANIM_MS),
                        new KeyValue(trashShiftPx, target)
                )
        );
        trashShiftAnim.playFromStart();
    }

    // ===================== CASES picker data (fixed) =====================

    private void reloadCasesFromDisk() {
        caseAll.clear();
        caseById.clear();
        trashChecks.clear(); // вњ… РєР°Рє Р±С‹Р»Рѕ: РїСЂРё РІС…РѕРґРµ РІ СЂРµР¶РёРј РІС‹Р±РѕСЂРєРё вЂ” С‡РёСЃС‚С‹Р№ РІС‹Р±РѕСЂ
        casePickOrder.clear();
        casePickSeq = 0L;

        try {
            List<TestCase> all = TestCaseCardStore.loadAll();
            for (TestCase tc : all) {
                if (tc == null) continue;

                String id = safeTrim(tc.getId());
                if (id.isEmpty()) continue;

                // вњ… РєР°Рє Р±С‹Р»Рѕ РІ СЂР°Р±РѕС‡РµРј РІР°СЂРёР°РЅС‚Рµ: РїРѕРєР°Р·С‹РІР°РµРј "CODE-NUMBER Title" (РёР»Рё head РµСЃР»Рё title РїСѓСЃС‚РѕР№)
                String title = buildLeftTitle(tc);
                caseAll.add(new CaseListItem(id, title));
                caseById.put(id, tc);
            }

            // keep stable sort
            caseAll.sort(Comparator.comparing(CaseListItem::title, String.CASE_INSENSITIVE_ORDER));

        } catch (Exception ignore) {
            // ignore
        }
    }

    private String buildLeftTitle(TestCase tc) {
        if (tc == null) return "";

        String code = s(tc.getCode());
        String num  = s(tc.getNumber());
        String title = s(tc.getTitle());

        String head;
        if (!code.isEmpty() && !num.isEmpty()) head = code + "-" + num;
        else if (!code.isEmpty()) head = code;
        else head = safeTrim(tc.getId());

        if (!title.isEmpty()) return head + " " + title;
        return head;
    }

    // ===================== CYCLES data (from disk) =====================

    private void reloadCyclesFromDisk() {
        cycleAll.clear();
        cycleFilterById.clear();
        cycleTrashChecks.clear();

        try {
            if (!Files.exists(CYCLES_ROOT)) return;

            List<Path> jsons = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(CYCLES_ROOT, "*.json")) {
                for (Path p : ds) {
                    if (p == null) continue;
                    jsons.add(p);
                }
            }

            for (Path p : jsons) {
                CycleDraft draft = CycleCardJsonReader.readDraft(p);
                if (draft == null) continue;

                String id = safeTrim(draft.id);
                if (id.isEmpty()) {
                    String name = p.getFileName().toString();
                    id = safeTrim(name.replace(".json", ""));
                }
                if (id.isEmpty()) continue;

                cycleAll.add(new CycleListItem(id, safeTrim(draft.title), safeTrim(draft.createdAtUi)));
                cycleFilterById.put(id, CycleFilterSnapshot.from(draft));
            }

            cycleAll.sort(Comparator.comparing(CycleListItem::safeTitle, String.CASE_INSENSITIVE_ORDER));
        } catch (Exception ignore) {
            // ignore
        }
    }

    private void deleteSelectedCyclesChecked() {
        try {
            if (!Files.exists(CYCLES_ROOT)) return;

            Path trashDir = CYCLES_ROOT.resolve(TRASH_DIR_NAME);
            if (!Files.exists(trashDir)) Files.createDirectories(trashDir);

            List<String> ids = new ArrayList<>();
            for (var e : cycleTrashChecks.entrySet()) {
                if (e == null) continue;
                if (e.getValue() != null && e.getValue().get()) ids.add(e.getKey());
            }

            for (String id : ids) {
                if (id == null || id.isBlank()) continue;

                Path src = CYCLES_ROOT.resolve(id + ".json");
                if (!Files.exists(src)) continue;

                Path dst = trashDir.resolve(id + ".json");
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
                caseHistoryIndexStore.removeCycle(id);
            }

            cycleTrashChecks.clear();
            if (cyclesTrashOverlay != null) cyclesTrashOverlay.close();
            if (cyclesDeleteConfirm != null) cyclesDeleteConfirm.close();

            reloadCyclesFromDisk();
            applyFiltersToList();
            list.showCycles(cycleView);
            installCyclesListCellFactory();
            updateCyclesTrashSpacerItem();
            refreshCyclesDeleteAvailability();
        } catch (Exception ignore) {
            // ignore
        }
    }

    // ===================== FILTERS =====================

    private void applyFiltersToList() {
        FilterSheet.CycleFilters cycleFilters = leftFilterSheet.appliedCycleFilters();
        FilterSheet.CaseFilters caseFilters = leftFilterSheet.appliedCaseFilters();

        cycleView.clear();
        for (CycleListItem item : cycleAll) {
            if (item == null) continue;
            String id = safeTrim(item.id());
            if (id.isEmpty()) continue;
            if (!matchesCycleFilters(id, cycleFilters)) continue;
            cycleView.add(item);
        }

        caseView.clear();
        for (CaseListItem item : caseAll) {
            if (item == null) continue;
            String id = safeTrim(item.id());
            if (id.isEmpty()) continue;
            if (!matchesCaseFilters(id, caseFilters)) continue;
            caseView.add(item);
        }

        if (appliedSearch != null && !appliedSearch.isBlank()) {
            String q = appliedSearch.trim().toLowerCase();
            cycleView.removeIf(it -> it == null || it.id() == null || it.safeTitle() == null || !it.safeTitle().toLowerCase().contains(q));
            caseView.removeIf(it -> it == null || it.id() == null || it.title() == null || !it.title().toLowerCase().contains(q));
        }

        ensureTopSpacers();
        moveSpacerToEnd(cycleView, CycleListItem::id);
        moveSpacerToEnd(caseView, CaseListItem::id);
    }

    private boolean matchesCycleFilters(String cycleId, FilterSheet.CycleFilters filters) {
        if (filters == null) return true;
        CycleFilterSnapshot snapshot = cycleFilterById.get(cycleId);
        if (snapshot == null) return true;

        List<String> selectedStatuses = filters.statuses();
        if (!selectedStatuses.isEmpty() && !containsIgnoreCase(selectedStatuses, snapshot.runState)) return false;

        List<String> selectedResponsibles = filters.responsibles();
        if (!selectedResponsibles.isEmpty()) {
            String responsible = safeTrim(snapshot.qaResponsible);
            if (responsible.isEmpty()) return false;

            boolean found = false;
            for (String selected : selectedResponsibles) {
                if (responsible.equalsIgnoreCase(safeTrim(selected))) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }

        List<String> createdRanges = filters.createdDateRanges();
        if (!createdRanges.isEmpty()) {
            boolean createdMatches = false;
            for (String createdRange : createdRanges) {
                if (matchesCreatedDate(snapshot.createdDate, createdRange)) {
                    createdMatches = true;
                    break;
                }
            }
            if (!createdMatches) return false;
        }

        List<String> progresses = filters.progresses();
        if (!progresses.isEmpty()) {
            boolean progressMatches = false;
            for (String progress : progresses) {
                if (matchesProgressBucket(snapshot.progressPercent, progress)) {
                    progressMatches = true;
                    break;
                }
            }
            if (!progressMatches) return false;
        }

        for (String selectedStatusValue : filters.caseStatuses()) {
            String normalized = safeTrim(selectedStatusValue).toUpperCase();
            if (normalized.isEmpty()) continue;
            if (!snapshot.caseStatuses.contains(normalized)) return false;
        }

        return true;
    }

    private boolean matchesCaseFilters(String caseId, FilterSheet.CaseFilters filters) {
        if (filters == null) return true;
        TestCase testCase = caseById.get(caseId);
        if (testCase == null) return false;
        return TestCaseFilter.matches(testCase, filters.labels(), filters.tags());
    }

    private boolean matchesCreatedDate(LocalDate createdDate, String range) {
        if (createdDate == null) return false;

        LocalDate today = LocalDate.now();
        return switch (safeTrim(range)) {
            case "today" -> createdDate.equals(today);
            case "last7" -> !createdDate.isBefore(today.minusDays(6)) && !createdDate.isAfter(today);
            case "last30" -> !createdDate.isBefore(today.minusDays(29)) && !createdDate.isAfter(today);
            default -> true;
        };
    }

    private boolean matchesProgressBucket(double progressPercent, String bucket) {
        return switch (safeTrim(bucket)) {
            case "0" -> progressPercent == 0.0;
            case "1_50" -> progressPercent > 0.0 && progressPercent <= 50.0;
            case "51_90" -> progressPercent > 50.0 && progressPercent <= 90.0;
            case "91_99" -> progressPercent > 90.0 && progressPercent < 100.0;
            case "100" -> progressPercent == 100.0;
            default -> true;
        };
    }

    private void toggleFilterSheet() {
        if (mode == LeftMode.CASES_PICKER) {
            leftFilterSheet.toggleForCases(LabelStore.loadAll(), TagStore.loadAll());
        } else {
            List<String> responsibles = new ArrayList<>();
            addAllUniqueIgnoreCase(responsibles, PrivateRootConfig.qaUsers());
            addAllUniqueIgnoreCase(responsibles, collectAvailableResponsibles());
            addAllUniqueIgnoreCase(responsibles, leftFilterSheet.appliedCycleFilters().responsibles());
            leftFilterSheet.toggleForCycles(responsibles);
        }
    }

    private void closeLeftOverlaysForFilter() {
        if (casesAddOverlay != null && casesAddOverlay.isOpen()) {
            casesAddOverlay.close();
        }
        if (cyclesTrashOverlay != null && cyclesTrashOverlay.isOpen()) {
            cyclesTrashOverlay.close();
        }
        if (cyclesDeleteConfirm != null && cyclesDeleteConfirm.isOpen()) {
            cyclesDeleteConfirm.close();
        }
    }

    private void updateFilterButtonText() {
        if (v == null || v.btnFilter == null) return;

        String base = I18n.t("tc.btn.filter");
        int count = mode == LeftMode.CASES_PICKER
                ? leftFilterSheet.activeCountForCases()
                : leftFilterSheet.activeCountForCycles();

        v.btnFilter.setText(count > 0 ? base + " (" + count + ")" : base);
    }

    private List<String> collectAvailableResponsibles() {
        List<String> out = new ArrayList<>();
        Set<String> seen = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (CycleFilterSnapshot snapshot : cycleFilterById.values()) {
            if (snapshot == null) continue;
            String responsible = safeTrim(snapshot.qaResponsible);
            if (responsible.isEmpty()) continue;
            if (seen.add(responsible)) out.add(responsible);
        }

        return out;
    }

    private static void addAllUniqueIgnoreCase(List<String> target, List<String> source) {
        if (target == null || source == null) return;
        for (String value : source) {
            String normalized = safeTrim(value);
            if (normalized.isEmpty()) continue;
            if (!containsIgnoreCase(target, normalized)) target.add(normalized);
        }
    }
    private static final class CycleFilterSnapshot {
        private static final DateTimeFormatter UI_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        private final String runState;
        private final String qaResponsible;
        private final LocalDate createdDate;
        private final double progressPercent;
        private final Set<String> caseStatuses;

        private CycleFilterSnapshot(String runState, String qaResponsible, LocalDate createdDate, double progressPercent, Set<String> caseStatuses) {
            this.runState = runState;
            this.qaResponsible = qaResponsible;
            this.createdDate = createdDate;
            this.progressPercent = progressPercent;
            this.caseStatuses = caseStatuses;
        }

        private static CycleFilterSnapshot from(CycleDraft draft) {
            String runState = CycleRunState.normalize(draft.runState);
            String qaResponsible = safeTrim(draft.qaResponsible);
            LocalDate createdDate = parseCreatedDate(draft);

            int total = draft.cases == null ? 0 : draft.cases.size();
            int completed = 0;
            Set<String> statuses = new java.util.HashSet<>();

            if (draft.cases != null) {
                for (CycleCaseRef ref : draft.cases) {
                    if (ref == null) continue;
                    String status = safeTrim(ref.safeStatus()).toUpperCase();
                    if (status.isEmpty()) continue;
                    statuses.add(status);
                    if (!"IN_PROGRESS".equals(status)) completed++;
                }
            }

            double progressPercent = total <= 0 ? 0.0 : (completed * 100.0) / total;
            if (completed == total && total > 0) progressPercent = 100.0;

            return new CycleFilterSnapshot(runState, qaResponsible, createdDate, progressPercent, statuses);
        }

        private static LocalDate parseCreatedDate(CycleDraft draft) {
            String iso = safeTrim(draft.createdAtIso);
            if (!iso.isEmpty()) {
                try {
                    return LocalDateTime.parse(iso).toLocalDate();
                } catch (DateTimeParseException ignore) {
                    // ignore
                }
            }

            String ui = safeTrim(draft.createdAtUi);
            if (!ui.isEmpty()) {
                try {
                    return LocalDate.parse(ui, UI_DATE_FORMATTER);
                } catch (DateTimeParseException ignore) {
                    // ignore
                }
            }

            return null;
        }
    }
    // ===================== MISC =====================

    private void openCyclesFolder() {
        try {
            if (!Files.exists(CYCLES_ROOT)) Files.createDirectories(CYCLES_ROOT);
            if (!Desktop.isDesktopSupported()) return;
            Desktop.getDesktop().open(CYCLES_ROOT.toFile());
        } catch (Exception ignore) {
            // ignore
        }
    }

    private static int getIconSizeFromFxml(javafx.scene.Node node, int def) {
        if (node == null) return def;
        Object ud = node.getUserData();
        if (ud == null) return def;

        String s = String.valueOf(ud).trim();
        if (s.isEmpty()) return def;

        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : def;
        } catch (Exception ignore) {
            return def;
        }
    }

    private static String s(String v) {
        return v == null ? "" : v.trim();
    }

    public void toggleCasesPickerAddMode() {
        if (mode != LeftMode.CASES_PICKER) {
            openCasesPickerAddOverlay();
            return;
        }

        if (casesAddOverlay == null) return;

        if (casesAddOverlay.isOpen()) {
            casesAddOverlay.close();
            return;
        }

        syncCasesPickerChecksFromRight();
        refreshAddAvailability();
        refreshCasesAddButtonText();
        casesAddOverlay.open();
    }

    public static String safeTrim(String v) {
        return v == null ? "" : v.trim();
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        if (values == null || candidate == null) return false;
        String normalizedCandidate = safeTrim(candidate);
        for (String value : values) {
            if (normalizedCandidate.equalsIgnoreCase(safeTrim(value))) return true;
        }
        return false;
    }
}




