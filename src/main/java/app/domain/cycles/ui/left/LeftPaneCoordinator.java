// FILE: src/main/java/app/domain/cycles/ui/left/LeftPaneCoordinator.java
package app.domain.cycles.ui.left;

import app.core.I18n;
import app.core.PrivateRootConfig;
import app.domain.cycles.CycleCategoryStore;
import app.domain.cycles.query.CycleListSorter;
import app.domain.cycles.repo.CaseHistoryIndexStore;
import app.domain.cycles.repo.CycleCardJsonReader;
import app.domain.cycles.ui.list.CaseListItem;
import app.domain.cycles.ui.list.CycleListItem;
import app.domain.cycles.ui.list.ListPresenter;
import app.domain.cycles.ui.overlay.FilterSheet;
import app.domain.cycles.ui.overlay.SortSheet;
import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.cycles.usecase.CycleDraft;
import app.domain.cycles.usecase.CycleRunState;
import app.domain.testcases.LabelStore;
import app.domain.testcases.TagStore;
import app.domain.testcases.TestCase;
import app.domain.testcases.query.TestCaseFilter;
import app.domain.testcases.query.TestCaseSorter;
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
import javafx.scene.control.OverrunStyle;
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

    // Р Р†РЎС™РІР‚В¦ Р В РЎСџР В Р’В°Р В РЎвЂ”Р В РЎвЂќР В Р’В° Р РЋРІР‚В Р В РЎвЂР В РЎвЂќР В Р’В»Р В РЎвЂўР В Р вЂ . JSON-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В Р вЂ Р В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™Р РЋР вЂљР В РЎвЂ = Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В РўвЂР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ Р РЋР С“Р В РЎвЂ”Р В РЎвЂР РЋР С“Р В РЎвЂќР В Р’В° Р РЋРІР‚В Р В РЎвЂР В РЎвЂќР В Р’В»Р В РЎвЂўР В Р вЂ .
    private static final Path CYCLES_ROOT = Path.of("test_resources", "cycles");

    // Р Р†РЎС™РІР‚В¦ Р В РЎвЂР В РЎВР РЋР РЏ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР В РЎвЂ-Р В РЎВР РЋРЎвЂњР РЋР С“Р В РЎвЂўР РЋР вЂљР В РЎвЂќР В РЎвЂ (Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р В Р вЂ  TestCases: "_trash")
    private static final String TRASH_DIR_NAME = "_trash";

    // ===================== I18N =====================

    // Р Р†РЎС™РІР‚В¦ delete confirm texts for cycles (NOT tc.*)
    private static final String I18N_CY_TRASH_TITLE = "cy.trash.delete.title";
    private static final String I18N_CY_TRASH_HINT  = "cy.trash.delete.hint";

    // Р Р†РЎС™РІР‚В¦ fallback title for empty cycle title (but never show id)
    private static final String I18N_CY_UNTITLED = "cy.title.untitled";

    // Р Р†РЎС™РІР‚В¦ NEW: sticky header titles
    private static final String I18N_CY_LEFT_LIST_CYCLES = "cy.left.list.cycles";
    private static final String I18N_CY_LEFT_LIST_CASES  = "cy.left.list.cases";
    private static final List<String> CASE_SORT_KEYS = List.of(
            "tc.sort.createdNewest",
            "tc.sort.createdOldest",
            "tc.sort.savedRecent",
            "tc.sort.code",
            "tc.sort.numberAsc",
            "tc.sort.numberDesc",
            "tc.sort.titleAsc",
            "tc.sort.titleDesc"
    );

    private static final List<String> CYCLE_SORT_KEYS = List.of(
            "cy.sort.createdNewest",
            "cy.sort.createdOldest",
            "cy.sort.titleAsc",
            "cy.sort.titleDesc",
            "cy.sort.progressDesc",
            "cy.sort.progressAsc",
            "cy.sort.caseCountDesc",
            "cy.sort.caseCountAsc",
            "cy.sort.criticalCountDesc",
            "cy.sort.criticalCountAsc"
    );
    // ===============================================

    // ===================== SEARCH UX =====================
    private double searchIdleDelayMs = 1000.0;
    // ===================================

    // ===================== TRASH MODE (shared) =====================
    private static final String TRASH_SPACER_ID = "__TRASH_SPACER__";

    // Р Р†РЎС™РІР‚В¦ NEW: top spacer that offsets sticky header (scrolls away)
    private static final String TOP_SPACER_ID = "__TOP_SPACER__";

    private static final double TRASH_SHIFT_PX = 26.0;
    private static final double TRASH_ANIM_MS  = 170.0;

    private final DoubleProperty trashShiftPx = new SimpleDoubleProperty(0.0);
    private Timeline trashShiftAnim;

    // Р Р†РЎС™РІР‚В¦ close overlay only on click OUTSIDE leftStack (like TestCasesController)
    private boolean trashOutsideCloseInstalled = false;
    // ===============================================================

    // ===================== STICKY HEADER (left list) =====================
    private static final double LIST_HEADER_GAP_PX = 8.0; // extra gap below header
    private static final double LIST_HEADER_MARGIN_TOP_PX = 10.0; // MUST match StackPane.setMargin(top)
    private LeftListStickyHeader stickyHeader;
    private StackPane listStack; // ListView + header overlay
    // ===============================================================

    private final CyclesLeftViewRefs v;
    private final CyclesLeftHost host;
    private final ListPresenter list;
    private final FilterSheet leftFilterSheet;
    private final SortSheet leftSortSheet;
    private final CaseHistoryIndexStore caseHistoryIndexStore = new CaseHistoryIndexStore();

    private LeftMode mode = LeftMode.CYCLES_LIST;
    private LeftPaneActions actions;

    // Р Р†РЎС™РІР‚В¦ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РўвЂР В Р’В»Р РЋР РЏ Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР РЋРІР‚В¦ Р В Р’В±Р В РЎвЂР В Р вЂ¦Р В РўвЂР В РЎвЂР В Р вЂ¦Р В РЎвЂ“Р В РЎвЂўР В Р вЂ  (tgThemeLeft)
    private Runnable onModeChanged;

    private final PauseTransition searchIdleTimer = new PauseTransition();
    private boolean searchProgrammaticChange = false;
    private String appliedSearch = "";

    // ====== DATA ======
    // Р Р†РЎС™РІР‚В¦ cycles: Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р В РЎвЂР В Р’В· Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В РЎвЂўР В Р вЂ Р В РЎвЂўР В РІвЂћвЂ“ Р РЋР С“Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В Р’ВµР В РЎВР РЋРІР‚в„–
    private final ObservableList<CycleListItem> cycleAll = FXCollections.observableArrayList();
    private final ObservableList<CycleListItem> cycleView = FXCollections.observableArrayList();
    private final Map<String, CycleFilterSnapshot> cycleFilterById = new HashMap<>();
    private final Map<String, CycleListSorter.Snapshot> cycleSortById = new HashMap<>();

    // cases: Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р В Р’В±Р РЋРІР‚в„–Р В Р’В»Р В РЎвЂў
    private final ObservableList<CaseListItem> caseAll = FXCollections.observableArrayList();
    private final ObservableList<CaseListItem> caseView = FXCollections.observableArrayList();
    private final Map<String, TestCase> caseById = new HashMap<>();

    // ====== CHECKS ======
    private final Map<String, BooleanProperty> trashChecks = new HashMap<>();
    private final Map<String, BooleanProperty> cycleTrashChecks = new HashMap<>();

    // Р Р†РЎС™РІР‚В¦ Р В РЎвЂ”Р В РЎвЂўР РЋР вЂљР РЋР РЏР В РўвЂР В РЎвЂўР В РЎвЂќ Р В РЎвЂўР РЋРІР‚С™Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂќР В РЎвЂ Р РЋРІР‚РЋР В Р’ВµР В РЎвЂќР В Р’В±Р В РЎвЂўР В РЎвЂќР РЋР С“Р В РЎвЂўР В Р вЂ  (Р В РўвЂР В Р’В»Р РЋР РЏ "Р В РЎвЂ”Р В РЎвЂўР РЋР вЂљР РЋР РЏР В РўвЂР В РЎвЂќР В Р’В° Р В РўвЂР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ")
    private final Map<String, Long> casePickOrder = new HashMap<>();
    private long casePickSeq = 0L;
    private boolean suppressPickOrder = false;

    // ====== UNIVERSAL overlay (cases + cycles) ======
    private LeftListActionOverlay casesAddOverlay;
    private LeftListActionOverlay cyclesTrashOverlay;

    // ====== confirm delete (same mechanic as TestCases, but texts differ) ======
    private LeftDeleteConfirm cyclesDeleteConfirm;

    public LeftPaneCoordinator(CyclesLeftViewRefs v, CyclesLeftHost host) {
        this.v = v;
        this.host = host;
        this.list = new ListPresenter(v.lvLeft);
        this.leftFilterSheet = new FilterSheet(v.leftStack, v.filterSheet, v.casesSheet);
        this.leftSortSheet = new SortSheet(v.leftStack, v.sortSheet, v.casesSheet);
    }

    public void init() {
        // Р Р†РЎС™РІР‚В¦ install sticky header overlay inside list container (casesSheet)
        installStickyListHeader();

        if (v.btnFolder != null) UiSvg.setButtonSvg(v.btnFolder, ICON_FOLDER, getIconSizeFromFxml(v.btnFolder, 14));
        if (v.btnSearch != null) {
            int base = getIconSizeFromFxml(v.btnSearch, 14);
            int scaled = Math.max(1, Math.round(base / 1.5f));
            UiSvg.setButtonSvg(v.btnSearch, ICON_SEARCH, scaled);
        }
        if (v.btnTrash != null)  UiSvg.setButtonSvg(v.btnTrash, ICON_TRASH,  getIconSizeFromFxml(v.btnTrash, 14));
        if (v.btnSort != null) UiSvg.setButtonSvg(v.btnSort, "sort.svg", getIconSizeFromFxml(v.btnSort, 14));
        if (v.lblSortSummary != null) {
            v.lblSortSummary.setWrapText(false);
            v.lblSortSummary.setTextOverrun(OverrunStyle.ELLIPSIS);
            v.lblSortSummary.setMaxWidth(Double.MAX_VALUE);
        }

        v.btnCreate.setOnAction(e -> { if (actions != null) actions.onCreate(); });
        v.btnFilter.setOnAction(e -> toggleFilterSheet());
        v.btnSort.setOnAction(e -> toggleSortSheet());

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

        if (host.rightVisibleProperty() != null) {
            host.rightVisibleProperty().addListener((obs, oldV, newV) -> refreshLeftActionButtonVisibility());
        }
        host.setOnUiStateChanged(this::onRightUiStateChanged);

        // ====== CASES PICKER overlay (universal) ======
        casesAddOverlay = new LeftListActionOverlay(v.leftStack, v.casesSheet, I18n.t("cy.right.addCases"));
        casesAddOverlay.setOnOpenChanged(open -> {
            animateTrashShift(open);
            updateCasesTrashSpacerItem();
            // Р Р†РЎС™РІР‚В¦ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂ Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р В РЎвЂР В РЎвЂ overlay Р РЋР С“Р В РЎвЂР В Р вЂ¦Р РЋРІР‚В¦Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р В РЎвЂР В Р’В·Р В РЎвЂР РЋР вЂљР РЋРЎвЂњР В Р’ВµР В РЎВ Р РЋРІР‚РЋР В Р’ВµР В РЎвЂќР В Р’В±Р В РЎвЂўР В РЎвЂќР РЋР С“Р РЋРІР‚в„– Р РЋР С“ Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В РЎвЂўР В РІвЂћвЂ“ Р В Р’В·Р В РЎвЂўР В Р вЂ¦Р В РЎвЂўР В РІвЂћвЂ“
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

        // Р Р†РЎС™РІР‚В¦ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ diff Р В Р вЂ  Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р РЋРЎвЂњР РЋР вЂ№ Р В Р’В·Р В РЎвЂўР В Р вЂ¦Р РЋРЎвЂњ, Р В РЎСљР В РІР‚Сћ Р В Р’В·Р В Р’В°Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р РЋР РЏ Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВ
        casesAddOverlay.setOnAdd(this::addSelectedCasesToRight);

        // ====== CYCLES trash overlay (delete) ======
        cyclesTrashOverlay = new LeftListActionOverlay(v.leftStack, v.casesSheet, I18n.t("tc.trash.delete"));
        cyclesTrashOverlay.setOnOpenChanged(open -> {
            // Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР РЋРІР‚С™Р В Р вЂ Р В Р’ВµР РЋР вЂљР В Р’В¶Р В РўвЂР В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В Р’В±Р РЋРІР‚в„–Р В Р’В»Р В РЎвЂў Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р В РЎвЂў Р Р†Р вЂљРІР‚Сњ Р В Р’В·Р В Р’В°Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВ Р В Р вЂ Р В РЎВР В Р’ВµР РЋР С“Р РЋРІР‚С™Р В Р’Вµ Р РЋР С“ overlay
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

        // ====== confirm delete (Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р В Р вЂ  TestCases), Р В Р вЂ¦Р В РЎвЂў Р РЋР С“ i18n Р В РўвЂР В Р’В»Р РЋР РЏ Cycles ======
        cyclesDeleteConfirm = new LeftDeleteConfirm(v.leftStack, this::deleteSelectedCyclesChecked);
        cyclesDeleteConfirm.setCanOpenSupplier(() ->
                cyclesTrashOverlay != null
                        && cyclesTrashOverlay.isOpen()
                        && hasAnyCycleTrashChecked()
        );
        // Р Р†РЎС™РІР‚В¦ texts for cycles (not tc.*)
        cyclesDeleteConfirm.setTextKeys(I18N_CY_TRASH_TITLE, I18N_CY_TRASH_HINT);

        // Р В РЎвЂќР В Р вЂ¦Р В РЎвЂўР В РЎвЂ”Р В РЎвЂќР В Р’В° "Р В Р в‚¬Р В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰" Р В Р вЂ  overlay -> Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВ confirm
        cyclesTrashOverlay.setOnDelete(() -> {
            if (cyclesDeleteConfirm != null) cyclesDeleteConfirm.open();
        });

        leftFilterSheet.setOnBeforeOpen(this::closeLeftOverlaysForSheet);
        leftFilterSheet.setOutsideCloseConsumeTarget(v.btnFilter);
        leftFilterSheet.setOnApply(() -> {
            applyFiltersToList();
            updateFilterButtonText();
        });
        leftFilterSheet.init();

        leftSortSheet.setOnBeforeOpen(this::closeLeftOverlaysForSheet);
        leftSortSheet.setOutsideCloseConsumeTarget(v.btnSort);
        leftSortSheet.setOnSortChanged(() -> {
            applyFiltersToList();
            updateSortButtonText();
        });
        leftSortSheet.init();

        installSearchBehavior();
        applyMode(LeftMode.CYCLES_LIST);

        // Р С—РЎР‚Р С‘Р СР ВµР Р…РЎРЏР ВµР С Р С•Р С–РЎР‚Р В°Р Р…Р С‘РЎвЂЎР ВµР Р…Р С‘РЎРЏ, Р ВµРЎРѓР В»Р С‘ Р В»Р ВµР Р†Р В°РЎРЏ Р В·Р С•Р Р…Р В° Р С‘РЎРѓР С—Р С•Р В»РЎРЉР В·РЎС“Р ВµРЎвЂљРЎРѓРЎРЏ Р Р…Р Вµ Р Р…Р В° РЎРЊР С”РЎР‚Р В°Р Р…Р Вµ Cycles
        if (host.leftZoneMode() == LeftZoneMode.REPORTS) {
            applyReportsModeUi();
        }

        // Р Р†РЎС™РІР‚В¦ after scene ready: install outside-close for trash overlay (cycles)
        Platform.runLater(this::installTrashOutsideClose);
    }

    private void applyReportsModeUi() {
        // РЎРѓР С”РЎР‚РЎвЂ№Р Р†Р В°Р ВµР С Р С”Р Р…Р С•Р С—Р С”РЎС“ РЎС“Р Т‘Р В°Р В»Р ВµР Р…Р С‘РЎРЏ/Р Т‘Р С•Р В±Р В°Р Р†Р В»Р ВµР Р…Р С‘РЎРЏ РЎР‚РЎРЏР Т‘Р С•Р С РЎРѓ toggle
        if (v.btnTrash != null) {
            v.btnTrash.setVisible(false);
            v.btnTrash.setManaged(false);
        }
        // РЎРѓР С”РЎР‚РЎвЂ№Р Р†Р В°Р ВµР С Р С”Р Р…Р С•Р С—Р С”РЎС“ "Р РЋР С•Р В·Р Т‘Р В°РЎвЂљРЎРЉ" РІР‚вЂќ Р Р…Р В° РЎРЊР С”РЎР‚Р В°Р Р…Р Вµ Reports Р С•Р Р…Р В° Р Р…Р Вµ Р Р…РЎС“Р В¶Р Р…Р В°
        if (v.btnCreate != null) {
            v.btnCreate.setVisible(false);
            v.btnCreate.setManaged(false);
        }
    }

    public void setMode(LeftMode newMode) {
        if (this.mode == newMode) return;
        applyMode(newMode);
    }

    public LeftMode mode() {
        return mode;
    }

    // Р Р†РЎС™РІР‚В¦ Р В РўвЂР В Р’В»Р РЋР РЏ ThemeToggleUiInstaller
    public void setOnModeChanged(Runnable r) {
        this.onModeChanged = r;
    }

    /**
     * Р Р†РЎС™РІР‚В¦ Р В Р’В±Р В Р’ВµР В Р’В·Р В РЎвЂўР В РЎвЂ”Р В Р’В°Р РЋР С“Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ refresh cycles Р В РЎвЂ”Р В РЎвЂўР РЋР С“Р В Р’В»Р В Р’Вµ Р РЋР С“Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В° Р В Р вЂ¦Р В Р’В° Р В РўвЂР В РЎвЂР РЋР С“Р В РЎвЂќ.
     * Р В РЎСљР В Р’Вµ Р В РЎВР В Р’ВµР В Р вЂ¦Р РЋР РЏР В Р’ВµР РЋРІР‚С™ mode, Р В Р вЂ¦Р В Р’Вµ Р В Р’В»Р В РЎвЂўР В РЎВР В Р’В°Р В Р’ВµР РЋРІР‚С™ overlays Р Р†Р вЂљРІР‚Сњ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂў Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР РЋРІР‚С™ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР РЋРЎвЂњ Р В РЎвЂ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР РЋР вЂљР В РЎвЂР РЋР С“Р В РЎвЂўР В Р вЂ Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР РЋРІР‚С™ Р РЋР С“Р В РЎвЂ”Р В РЎвЂР РЋР С“Р В РЎвЂўР В РЎвЂќ Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р В РЎВР РЋРІР‚в„– Р В Р вЂ  CYCLES_LIST.
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
     * - and open "Р В РІР‚СњР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰" overlay (same as pressing the plus/trash button in cases picker mode).
     */
    public void openCasesPickerAddOverlay() {
        // switch list to cases picker (reloads items + sets btnTrash to plus)
        setMode(LeftMode.CASES_PICKER);

        // Р Р†РЎС™РІР‚В¦ Р РЋР С“Р В РЎвЂР В Р вЂ¦Р РЋРІР‚В¦Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р В РЎвЂР В Р’В·Р В РЎвЂР РЋР вЂљР РЋРЎвЂњР В Р’ВµР В РЎВ Р РЋРІР‚РЋР В Р’ВµР В РЎвЂќР В Р’В±Р В РЎвЂўР В РЎвЂќР РЋР С“Р РЋРІР‚в„– Р РЋР С“ Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В РЎвЂўР В РІвЂћвЂ“ Р В Р’В·Р В РЎвЂўР В Р вЂ¦Р В РЎвЂўР В РІвЂћвЂ“ Р В РЎвЂ Р В РЎвЂўР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РўвЂР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋРЎвЂњР В РЎвЂ”Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂќР В Р вЂ¦Р В РЎвЂўР В РЎвЂ”Р В РЎвЂќР В РЎвЂ
        syncCasesPickerChecksFromRight();
        refreshAddAvailability();
        refreshCasesAddButtonText();
        

        // open overlay (shift list + show checkboxes + action button)
        if (casesAddOverlay != null && !casesAddOverlay.isOpen()) {
            casesAddOverlay.open();
        }
    }

    /**
     * Р Р†РЎС™РІР‚В¦ Р В РІР‚вЂќР В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В Р’В°: Р В РЎвЂќР В Р вЂ¦Р В РЎвЂўР В РЎвЂ”Р В РЎвЂќР В Р’В° "Р В РІР‚СњР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂќР В Р’ВµР В РІвЂћвЂ“Р РЋР С“Р РЋРІР‚в„–" Р РЋР вЂљР В Р’В°Р В Р’В±Р В РЎвЂўР РЋРІР‚С™Р В Р’В°Р В Р’ВµР РЋРІР‚С™ Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р РЋРІР‚С™Р РЋРЎвЂњР В РЎВР В Р’В±Р В Р’В»Р В Р’ВµР РЋР вЂљ.
     * Р В РЎСџР В РЎвЂўР В Р вЂ Р РЋРІР‚С™Р В РЎвЂўР РЋР вЂљР В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В Р’В¶Р В Р’В°Р РЋРІР‚С™Р В РЎвЂР В Р’Вµ Р В Р вЂ Р В РЎвЂўР В Р’В·Р В Р вЂ Р РЋР вЂљР В Р’В°Р РЋРІР‚В°Р В Р’В°Р В Р’ВµР РЋРІР‚С™ Р В РЎвЂР РЋР С“Р РЋРІР‚В¦Р В РЎвЂўР В РўвЂР В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋР С“Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋР РЏР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р РЋР С“Р В РЎвЂ”Р В РЎвЂР РЋР С“Р В РЎвЂќР В Р’В°:
     * - Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋР С“Р В Р’ВµР В РІвЂћвЂ“Р РЋРІР‚РЋР В Р’В°Р РЋР С“ Р РЋРЎвЂњР В Р’В¶Р В Р’Вµ Р В Р вЂ  Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВР В Р’Вµ Р В РўвЂР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ (CASES_PICKER) Р Р†Р вЂљРІР‚Сњ Р В Р вЂ Р В РЎвЂўР В Р’В·Р В Р вЂ Р РЋР вЂљР В Р’В°Р РЋРІР‚В°Р В Р’В°Р В Р’ВµР В РЎВР РЋР С“Р РЋР РЏ Р В Р вЂ  CYCLES_LIST
     * - Р В РЎвЂР В Р вЂ¦Р В Р’В°Р РЋРІР‚РЋР В Р’Вµ Р Р†Р вЂљРІР‚Сњ Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВ Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВ Р В РўвЂР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ (openCasesPickerAddOverlay)
     */
    public void toggleCasesPickerAddOverlay() {
        if (mode == LeftMode.CASES_PICKER) {
            // applyMode(CYCLES_LIST) Р РЋР С“Р В Р’В°Р В РЎВ Р В Р’В·Р В Р’В°Р В РЎвЂќР РЋР вЂљР В РЎвЂўР В Р’ВµР РЋРІР‚С™ casesAddOverlay Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р В РЎвЂўР В Р вЂ¦ Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™
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
            leftFilterSheet.dismissImmediately();
        }
        if (leftSortSheet != null && leftSortSheet.isOpen()) {
            leftSortSheet.dismissImmediately();
        }

        animateTrashShift(false);
        trashShiftPx.set(0.0);

        // Р Р†РЎС™РІР‚В¦ FIX: Р РЋР С“Р В Р’В±Р РЋР вЂљР В РЎвЂўР РЋР С“Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ items Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В РўвЂ setItems Р В РўвЂР РЋР вЂљР РЋРЎвЂњР В РЎвЂ“Р В РЎвЂўР В РЎвЂ“Р В РЎвЂў Р РЋРІР‚С™Р В РЎвЂР В РЎвЂ”Р В Р’В° (Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР РЋРІР‚в„–Р В Р’Вµ cells Р РЋРЎвЂњР РЋРІвЂљВ¬Р В Р’В»Р В РЎвЂ Р В Р вЂ  empty=true)
        if (v.lvLeft != null) {
            v.lvLeft.setItems(FXCollections.observableArrayList());
            v.lvLeft.getSelectionModel().clearSelection();
        }

        if (newMode == LeftMode.CYCLES_LIST) {
            actions = new CyclesListActions(v, host, () -> applyMode(LeftMode.CASES_PICKER));

            if (v.btnTrash != null) {
                UiSvg.setButtonSvg(v.btnTrash, ICON_TRASH, getIconSizeFromFxml(v.btnTrash, 14));
                v.btnTrash.setDisable(false);
                if (cyclesTrashOverlay != null) {
                    cyclesTrashOverlay.init(v.btnTrash);
                    cyclesTrashOverlay.setButtonText(I18n.t("tc.trash.delete"));
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
            actions = new CasesPickerActions(v, host, () -> applyMode(LeftMode.CYCLES_LIST));

            if (v.btnTrash != null) {
                UiSvg.setButtonSvg(v.btnTrash, ICON_CASES_PICK, getIconSizeFromFxml(v.btnTrash, 14));
                v.btnTrash.setDisable(false);

                if (casesAddOverlay != null) {
                    casesAddOverlay.init(v.btnTrash);
                    casesAddOverlay.setButtonText(I18n.t("cy.left.overlay.add"));
                }
            }

            // Р Р†РЎС™РІР‚В¦ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂ Р В Р вЂ Р РЋРІР‚В¦Р В РЎвЂўР В РўвЂР В Р’Вµ Р В Р вЂ  Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р В РЎвЂўР РЋР вЂљР В Р’В° Р В РЎвЂќР В Р’ВµР В РІвЂћвЂ“Р РЋР С“Р В РЎвЂўР В Р вЂ  Р Р†Р вЂљРІР‚Сњ Р В Р вЂ¦Р В Р’В°Р РЋРІР‚РЋР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р’ВµР В РЎВ Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р РЋРЎвЂњР РЋР вЂ№ Р РЋР С“Р В Р’ВµР РЋР С“Р РЋР С“Р В РЎвЂР РЋР вЂ№ Р В РЎвЂ”Р В РЎвЂўР РЋР вЂљР РЋР РЏР В РўвЂР В РЎвЂќР В Р’В° Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р В РЎвЂўР РЋР вЂљР В Р’В°
            casePickOrder.clear();
            casePickSeq = 0L;

            // Р Р†РЎС™РІР‚В¦ Р В РІР‚в„ўР В РЎвЂ™Р В РІР‚вЂњР В РЎСљР В РЎвЂє: Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В Р’В°Р В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р РЋРІР‚С™Р В Р вЂ Р В РЎвЂўР РЋР вЂ№ Р РЋР вЂљР В Р’В°Р В Р’В±Р В РЎвЂўР РЋРІР‚РЋР РЋРЎвЂњР РЋР вЂ№ Р В Р’В·Р В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В·Р В РЎвЂќР РЋРЎвЂњ Р В РЎвЂќР В Р’ВµР В РІвЂћвЂ“Р РЋР С“Р В РЎвЂўР В Р вЂ  Р В РЎвЂР В Р’В· TestCaseCardStore
            reloadCasesFromDisk();
            applyFiltersToList();

            list.showCases(caseView);

            installCasesPickerCellFactory();
            updateCasesTrashSpacerItem();

            // Р Р†РЎС™РІР‚В¦ Р В РЎвЂўР РЋРІР‚С™Р В РЎВР В Р’ВµР РЋРІР‚РЋР В Р’В°Р В Р’ВµР В РЎВ Р РЋРЎвЂњР В Р’В¶Р В Р’Вµ Р В РўвЂР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р РЋР С“Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В° Р В РЎвЂќР В Р’ВµР В РІвЂћвЂ“Р РЋР С“Р РЋРІР‚в„–
            syncCasesPickerChecksFromRight();
            refreshAddAvailability();
            refreshCasesAddButtonText();
        }

        refreshLeftActionButtonVisibility();
        updateSearchButtonVisibility();
        updateFilterButtonText();
        updateSortButtonText();

        // Р Р†РЎС™РІР‚В¦ update sticky header title per current mode (i18n)
        updateStickyHeaderTitle();

        // Р Р†РЎС™РІР‚В¦ notify external binders (tgThemeLeft)
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
        // Р Р†РЎС™РІР‚В¦ important: allow scroll/clicks to pass through header overlay
        stickyHeader.setMouseTransparent(true);
        stickyHeader.setPickOnBounds(false);

        listStack = new StackPane();
        listStack.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Р Р†РЎС™РІР‚В¦ CRITICAL: casesSheet is VBox, stack must grow (fixes bottom clipping)
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

        // Р Р†РЎС™РІР‚В¦ IMPORTANT: do NOT use ListView padding (it causes "scroll to header and stop").
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

        // title may affect header height Р Р†РІР‚В РІР‚в„ў update spacer height lazily by using supplier
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
        if (host.isRightOpen()) {
            String openedId = safeTrim(host.openedCycleId());
            if (!openedId.isEmpty() && openedId.equals(id)) {
                host.closeRight();
                try {
                    if (v.lvLeft != null) v.lvLeft.getSelectionModel().clearSelection();
                } catch (Exception ignored) {}
                return;
            }
        }

        // open existing card
        Path file = CYCLES_ROOT.resolve(id + ".json");
        host.openExistingCard(file);
    }

    // ===================== ADD SELECTED CASES -> RIGHT (UI-only) =====================

    private void addSelectedCasesToRight() {
        if (!host.isRightOpen()) return;

        LinkedHashSet<String> desiredIds = getCheckedCaseIds();
        LinkedHashSet<String> currentIds = new LinkedHashSet<>(host.getAddedCaseIds());

        // toRemove: Р В Р’В±Р РЋРІР‚в„–Р В Р’В»Р В РЎвЂў Р РЋР С“Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В°, Р В Р вЂ¦Р В РЎвЂў Р РЋР С“Р В Р’ВµР В РІвЂћвЂ“Р РЋРІР‚РЋР В Р’В°Р РЋР С“ Р В Р вЂ¦Р В Р’Вµ Р В РЎвЂўР РЋРІР‚С™Р В РЎВР В Р’ВµР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂў
        List<String> toRemove = new ArrayList<>();
        for (String id : currentIds) {
            if (id == null || id.isBlank()) continue;
            if (!desiredIds.contains(id)) toRemove.add(id);
        }

        // toAdd: Р В РЎвЂўР РЋРІР‚С™Р В РЎВР В Р’ВµР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂў, Р В Р вЂ¦Р В РЎвЂў Р В Р’ВµР РЋРІР‚В°Р РЋРІР‚В Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™ Р РЋР С“Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В°
        List<String> toAddIds = new ArrayList<>();
        for (String id : desiredIds) {
            if (id == null || id.isBlank()) continue;
            if (!currentIds.contains(id)) toAddIds.add(id);
        }

        // 1) remove unchecked (Р РЋР С“Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂ”Р В РЎвЂўР РЋР вЂљР РЋР РЏР В РўвЂР В РЎвЂўР В РЎвЂќ Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В Р’В°Р В Р вЂ Р РЋРІвЂљВ¬Р В РЎвЂР РЋРІР‚В¦Р РЋР С“Р РЋР РЏ)
        if (!toRemove.isEmpty()) {
            host.removeAddedCasesByIds(toRemove);
        }

        // 2) add new Р Р†Р вЂљРІР‚Сњ Р В Р вЂ  Р В РЎвЂ”Р В РЎвЂўР РЋР вЂљР РЋР РЏР В РўвЂР В РЎвЂќР В Р’Вµ РІР‚СљР В РЎвЂ”Р В РЎвЂў Р В РЎВР В Р’ВµР РЋР вЂљР В Р’Вµ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р В РЎвЂўР РЋР вЂљР В Р’В°РІР‚Сњ (casePickOrder), Р В РЎвЂР В Р вЂ¦Р В Р’В°Р РЋРІР‚РЋР В Р’Вµ fallback Р В Р вЂ¦Р В Р’В° Р В РЎвЂ”Р В РЎвЂўР РЋР вЂљР РЋР РЏР В РўвЂР В РЎвЂўР В РЎвЂќ Р РЋР С“Р В РЎвЂ”Р В РЎвЂР РЋР С“Р В РЎвЂќР В Р’В°
        if (!toAddIds.isEmpty()) {
            List<CycleCaseRef> toAdd = buildToAddRefsOrdered(toAddIds);
            if (!toAdd.isEmpty()) host.addAddedCases(toAdd);
        }

        host.closePickerPreviewCaseCard();

        // Р Р†РЎС™РІР‚В¦ Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВ Р В РЎвЂ overlay Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋР вЂ№Р РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р РЋРІР‚в„–Р В РЎВР В РЎвЂ, Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂў Р В РЎвЂўР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РўвЂР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋРЎвЂњР В РЎвЂ”Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂќР В Р вЂ¦Р В РЎвЂўР В РЎвЂ”Р В РЎвЂќР В РЎвЂ
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
        if (!host.isRightOpen()) return CasesPickerDiffType.NONE;

        LinkedHashSet<String> desired = getCheckedCaseIds();
        LinkedHashSet<String> current = new LinkedHashSet<>(host.getAddedCaseIds());
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
        // 1) Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р’ВµР РЋР С“Р РЋРІР‚С™Р РЋР Р‰ order Р В РЎвЂ”Р В РЎвЂў Р РЋРІР‚РЋР В Р’ВµР В РЎвЂќР В Р’В±Р В РЎвЂўР В РЎвЂќР РЋР С“Р В Р’В°Р В РЎВ Р Р†Р вЂљРІР‚Сњ Р РЋР С“Р В РЎвЂўР РЋР вЂљР РЋРІР‚С™Р В РЎвЂР РЋР вЂљР РЋРЎвЂњР В Р’ВµР В РЎВ Р В РЎвЂ”Р В РЎвЂў Р В Р вЂ¦Р В Р’ВµР В РЎВР РЋРЎвЂњ
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

        // title lookup Р В РЎвЂР В Р’В· caseAll/caseView
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
     * Р В РЎСџР РЋР вЂљР В РЎвЂ Р В Р вЂ Р РЋРІР‚В¦Р В РЎвЂўР В РўвЂР В Р’Вµ/Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р В РЎвЂР В РЎвЂ overlay Р В Р вЂ  CASES_PICKER:
     * Р Р†РЎС™РІР‚В¦ Р В РЎвЂўР РЋРІР‚С™Р В РЎВР В Р’ВµР РЋРІР‚РЋР В Р’В°Р В Р’ВµР В РЎВ Р РЋРЎвЂњР В Р’В¶Р В Р’Вµ Р В РўвЂР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р РЋР С“Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В° Р В РЎвЂќР В Р’ВµР В РІвЂћвЂ“Р РЋР С“Р РЋРІР‚в„– Р В РЎвЂ Р В РЎСљР В РІР‚Сћ Р В РЎвЂ”Р В РЎвЂР РЋРІвЂљВ¬Р В Р’ВµР В РЎВ Р В РЎвЂР РЋРІР‚В¦ Р В Р вЂ  order (Р РЋР РЉР РЋРІР‚С™Р В РЎвЂў Р В Р вЂ¦Р В Р’Вµ "Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В РЎвЂўР В Р’Вµ Р В РўвЂР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ")
     */
    private void syncCasesPickerChecksFromRight() {
        if (!host.isRightOpen()) return;

        suppressPickOrder = true;
        try {
            for (String idRaw : host.getAddedCaseIds()) {
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
        // Р Р†РЎС™РІР‚В¦ Р РЋРІР‚С›Р В РЎвЂР В РЎвЂќР РЋР С“Р В РЎвЂР РЋР вЂљР РЋРЎвЂњР В Р’ВµР В РЎВ Р В РЎвЂ”Р В РЎвЂўР РЋР вЂљР РЋР РЏР В РўвЂР В РЎвЂўР В РЎвЂќ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р В РЎвЂўР РЋР вЂљР В Р’В° Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р В Р’ВµР В Р’В»Р В Р’ВµР В РЎВ
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
                    host.openTestCaseCardFromList(c.id(), currentCasePickerIds());
                },
                it -> {
                    if (!(it instanceof CaseListItem c)) return;
                    host.openTestCaseCardFromList(c.id(), currentCasePickerIds());
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

        // Р Р†РЎС™РІР‚В¦ Р В РЎвЂќР В Р вЂ¦Р В РЎвЂўР В РЎвЂ”Р В РЎвЂќР В Р’В° Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В Р’В° Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р’ВµР РЋР С“Р РЋРІР‚С™Р РЋР Р‰ diff Р В РЎвЂўР РЋРІР‚С™Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р В РЎвЂР РЋРІР‚С™Р В Р’ВµР В Р’В»Р РЋР Р‰Р В Р вЂ¦Р В РЎвЂў Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В РЎвЂўР В РІвЂћвЂ“ Р В Р’В·Р В РЎвЂўР В Р вЂ¦Р РЋРІР‚в„–
        boolean enable = hasCasesPickerDiff();
        casesAddOverlay.setAddEnabled(enable);
    }

    private void refreshCasesAddButtonText() {
        if (casesAddOverlay == null) return;

        boolean useAddText = !host.isRightOpen()
                || host.getAddedCaseIds().isEmpty()
                || casesPickerDiffType() == CasesPickerDiffType.ADD;
        String text = useAddText
                ? I18n.t("cy.left.overlay.add")
                : I18n.t("cy.left.overlay.edit");
        casesAddOverlay.setButtonText(text);
    }

    private void onRightUiStateChanged() {
        if (mode == LeftMode.CASES_PICKER
                && host.isRightOpen()
                && !host.isEditModeEnabled()
                && casesAddOverlay != null
                && casesAddOverlay.isOpen()) {
            casesAddOverlay.close();
        }
        refreshLeftActionButtonVisibility();
    }

    private void refreshLeftActionButtonVisibility() {
        if (v == null || v.btnTrash == null) return;

        // Р Р† РЎР‚Р ВµР В¶Р С‘Р СР Вµ REPORTS Р С”Р Р…Р С•Р С—Р С”Р В° Р Р†РЎРѓР ВµР С–Р Т‘Р В° РЎРѓР С”РЎР‚РЎвЂ№РЎвЂљР В°
        if (host.leftZoneMode() == LeftZoneMode.REPORTS) {
            v.btnTrash.setVisible(false);
            v.btnTrash.setManaged(false);
            return;
        }

        boolean show = mode != LeftMode.CASES_PICKER || host.isRightOpen();
        boolean disable = mode == LeftMode.CASES_PICKER
                && (!host.isRightOpen() || !host.isEditModeEnabled());
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
        // Р Р†РЎС™РІР‚В¦ select-all Р В Р вЂ¦Р В Р’Вµ Р В РўвЂР В РЎвЂўР В Р’В»Р В Р’В¶Р В Р’ВµР В Р вЂ¦ Р В Р’В»Р В РЎвЂўР В РЎВР В Р’В°Р РЋРІР‚С™Р РЋР Р‰ "Р В РЎвЂ”Р В РЎвЂўР РЋР вЂљР РЋР РЏР В РўвЂР В РЎвЂўР В РЎвЂќ Р В РўвЂР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ" Р Р†Р вЂљРІР‚Сњ Р РЋР РЉР РЋРІР‚С™Р В РЎвЂў Р В РЎВР В Р’В°Р РЋР С“Р РЋР С“Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋР РЏ Р В РЎвЂўР В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’В°Р РЋРІР‚В Р В РЎвЂР РЋР РЏ
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
        trashChecks.clear(); // Р Р†РЎС™РІР‚В¦ Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р В Р’В±Р РЋРІР‚в„–Р В Р’В»Р В РЎвЂў: Р В РЎвЂ”Р РЋР вЂљР В РЎвЂ Р В Р вЂ Р РЋРІР‚В¦Р В РЎвЂўР В РўвЂР В Р’Вµ Р В Р вЂ  Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р В РЎвЂўР РЋР вЂљР В РЎвЂќР В РЎвЂ Р Р†Р вЂљРІР‚Сњ Р РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р В РЎвЂўР РЋР вЂљ
        casePickOrder.clear();
        casePickSeq = 0L;

        try {
            List<TestCase> all = TestCaseCardStore.loadAll();
            for (TestCase tc : all) {
                if (tc == null) continue;

                String id = safeTrim(tc.getId());
                if (id.isEmpty()) continue;

                // Р Р†РЎС™РІР‚В¦ Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р В Р’В±Р РЋРІР‚в„–Р В Р’В»Р В РЎвЂў Р В Р вЂ  Р РЋР вЂљР В Р’В°Р В Р’В±Р В РЎвЂўР РЋРІР‚РЋР В Р’ВµР В РЎВ Р В Р вЂ Р В Р’В°Р РЋР вЂљР В РЎвЂР В Р’В°Р В Р вЂ¦Р РЋРІР‚С™Р В Р’Вµ: Р В РЎвЂ”Р В РЎвЂўР В РЎвЂќР В Р’В°Р В Р’В·Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВ "CODE-NUMBER Title" (Р В РЎвЂР В Р’В»Р В РЎвЂ head Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ title Р В РЎвЂ”Р РЋРЎвЂњР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР В РІвЂћвЂ“)
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
        cycleSortById.clear();
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
                cycleSortById.put(id, toCycleSortSnapshot(draft));
            }

            CycleListSorter.sort(cycleAll, leftSortSheet.appliedCycleSortIndex(), cycleSortById::get);
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

        sortCyclesView();
        sortCasesView();

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

        List<String> selectedCategories = filters.categories();
        if (!selectedCategories.isEmpty()) {
            String category = safeTrim(snapshot.category);
            if (category.isEmpty()) return false;
            if (!containsIgnoreCase(selectedCategories, category)) return false;
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

            List<String> categories = new ArrayList<>();
            addAllUniqueIgnoreCase(categories, CycleCategoryStore.loadAll());
            addAllUniqueIgnoreCase(categories, collectAvailableCategories());
            addAllUniqueIgnoreCase(categories, leftFilterSheet.appliedCycleFilters().categories());

            leftFilterSheet.toggleForCycles(responsibles, categories);
        }
    }

    private void toggleSortSheet() {
        if (mode == LeftMode.CASES_PICKER) {
            leftSortSheet.toggleForCases(CASE_SORT_KEYS);
        } else {
            leftSortSheet.toggleForCycles(CYCLE_SORT_KEYS);
        }
    }

    private void closeLeftOverlaysForSheet() {
        if (casesAddOverlay != null && casesAddOverlay.isOpen()) {
            casesAddOverlay.close();
        }
        if (cyclesTrashOverlay != null && cyclesTrashOverlay.isOpen()) {
            cyclesTrashOverlay.close();
        }
        if (cyclesDeleteConfirm != null && cyclesDeleteConfirm.isOpen()) {
            cyclesDeleteConfirm.close();
        }
        if (leftFilterSheet != null && leftFilterSheet.isOpen()) {
            leftFilterSheet.dismissImmediately();
        }
        if (leftSortSheet != null && leftSortSheet.isOpen()) {
            leftSortSheet.dismissImmediately();
        }
    }

    private void updateSortButtonText() {
        if (v == null || v.btnSort == null) return;

        String base = I18n.t("tc.btn.sort");
        v.btnSort.setText(base);

        if (v.lblSortSummary == null) return;

        String text = currentSortText();
        boolean hasText = text != null && !text.isBlank();
        v.lblSortSummary.setText(hasText ? base + ": " + text : "");
        v.lblSortSummary.setManaged(hasText);
        v.lblSortSummary.setVisible(hasText);
    }

    private String currentSortText() {
        boolean casesMode = mode == LeftMode.CASES_PICKER;
        String text = casesMode
                ? leftSortSheet.currentSortTextForCases()
                : leftSortSheet.currentSortTextForCycles();

        if (text != null && !text.isBlank()) return text;

        List<String> keys = casesMode ? CASE_SORT_KEYS : CYCLE_SORT_KEYS;
        int idx = casesMode ? leftSortSheet.appliedCaseSortIndex() : leftSortSheet.appliedCycleSortIndex();
        return keys.isEmpty() ? "" : I18n.t(keys.get(idx));
    }
    private void updateFilterButtonText() {
        if (v == null || v.btnFilter == null) return;

        String base = I18n.t("tc.btn.filter");
        int count = mode == LeftMode.CASES_PICKER
                ? leftFilterSheet.activeCountForCases()
                : leftFilterSheet.activeCountForCycles();

        v.btnFilter.setText(count > 0 ? base + " (" + count + ")" : base);
    }

    private void sortCyclesView() {
        CycleListSorter.sort(cycleView, leftSortSheet.appliedCycleSortIndex(), cycleSortById::get);
    }

    private void sortCasesView() {
        if (caseView.size() <= 1) return;

        List<TestCase> orderedCases = new ArrayList<>();
        for (CaseListItem item : caseView) {
            if (item == null) continue;
            TestCase testCase = caseById.get(item.id());
            if (testCase != null) orderedCases.add(testCase);
        }

        TestCaseSorter.sort(orderedCases, leftSortSheet.appliedCaseSortIndex());

        Map<String, CaseListItem> byId = new HashMap<>();
        for (CaseListItem item : caseView) {
            if (item == null || item.id() == null) continue;
            byId.put(item.id(), item);
        }

        caseView.clear();
        for (TestCase testCase : orderedCases) {
            if (testCase == null || testCase.getId() == null) continue;
            CaseListItem item = byId.get(testCase.getId().trim());
            if (item != null) caseView.add(item);
        }
    }
    private List<String> collectAvailableCategories() {
        List<String> out = new ArrayList<>();
        Set<String> seen = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (CycleFilterSnapshot snapshot : cycleFilterById.values()) {
            if (snapshot == null) continue;
            String category = safeTrim(snapshot.category);
            if (category.isEmpty()) continue;
            if (seen.add(category)) out.add(category);
        }

        return out;
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
    private static CycleListSorter.Snapshot toCycleSortSnapshot(CycleDraft draft) {
        if (draft == null) return CycleListSorter.Snapshot.EMPTY;

        int caseCount = draft.cases == null ? 0 : draft.cases.size();
        int completed = 0;
        if (draft.cases != null) {
            for (CycleCaseRef ref : draft.cases) {
                if (ref == null) continue;
                String status = safeTrim(ref.safeStatus()).toUpperCase();
                if (status.isEmpty()) continue;
                if (!"IN_PROGRESS".equals(status)) completed++;
            }
        }

        double progressPercent = caseCount <= 0 ? 0.0 : (completed * 100.0) / caseCount;
        if (completed == caseCount && caseCount > 0) progressPercent = 100.0;

        return new CycleListSorter.Snapshot(
                buildCreatedSortKey(draft),
                progressPercent,
                caseCount,
                countCriticalCases(draft)
        );
    }

    private static String buildCreatedSortKey(CycleDraft draft) {
        if (draft == null) return "";

        String iso = safeTrim(draft.createdAtIso);
        if (!iso.isEmpty()) return iso;

        LocalDate createdDate = CycleFilterSnapshot.parseCreatedDate(draft);
        if (createdDate == null) return "";
        return createdDate.atStartOfDay().toString();
    }

    private static int countCriticalCases(CycleDraft draft) {
        if (draft == null || draft.cases == null || draft.cases.isEmpty()) return 0;

        int count = 0;
        for (CycleCaseRef ref : draft.cases) {
            if (ref == null) continue;
            String status = safeTrim(ref.safeStatus()).toUpperCase();
            if ("FAILED".equals(status) || "CRITICAL_FAILED".equals(status)) count++;
        }
        return count;
    }
    private static final class CycleFilterSnapshot {
        private static final DateTimeFormatter UI_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        private final String runState;
        private final String qaResponsible;
        private final String category;
        private final LocalDate createdDate;
        private final double progressPercent;
        private final Set<String> caseStatuses;

        private CycleFilterSnapshot(String runState, String qaResponsible, String category, LocalDate createdDate, double progressPercent, Set<String> caseStatuses) {
            this.runState = runState;
            this.qaResponsible = qaResponsible;
            this.category = category;
            this.createdDate = createdDate;
            this.progressPercent = progressPercent;
            this.caseStatuses = caseStatuses;
        }

        private static CycleFilterSnapshot from(CycleDraft draft) {
            String runState = CycleRunState.normalize(draft.runState);
            String qaResponsible = safeTrim(draft.qaResponsible);
            String category = safeTrim(draft.category);
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

            return new CycleFilterSnapshot(runState, qaResponsible, category, createdDate, progressPercent, statuses);
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




