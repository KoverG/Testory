package app.domain.cycles.ui;

import app.domain.cycles.ui.right.CycleCardMenuButton;
import app.domain.cycles.ui.right.EnvironmentChip;
import app.domain.cycles.ui.right.TaskLinkChip;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class CyclesViewRefs {

    public final StackPane root;

    public final StackPane leftStack;
    public final VBox leftMain;

    public final TextField tfSearch;
    public final Button btnSearch;
    public final Button btnFolder;
    public final Button btnCreate;

    // Р В Р’В Р В РІР‚В Р В Р Р‹Р РЋРІвЂћСћР В Р вЂ Р В РІР‚С™Р вЂ™Р’В¦ left toggle (Cycle/Cases)
    public final Object tgThemeLeft;

    public final Button btnTrash;
    public final Button btnFilter;
    public final Button btnSort;
    public final Label lblSortSummary;

    public final VBox casesSheet;
    public final ListView<Object> lvLeft;

    public final StackPane filterSheet;
    public final StackPane sortSheet;
    public final StackPane trashOverlay;
    public final StackPane floatingOverlayRoot;

    public final StackPane rightRoot;
    public final VBox rightPlaceholder;

    public final Label lblCycleCreatedAt;
    public final TaskLinkChip chipTaskLink;

    // Р В Р’В Р В РІР‚В Р В Р Р‹Р РЋРІвЂћСћР В Р вЂ Р В РІР‚С™Р вЂ™Р’В¦ NEW: environment chip (desktop + mobile icons)
    public final EnvironmentChip chipEnvironment;

    public final TextField tfCycleTitle;
    public final TextField tfCycleCategory;
    public final Button btnAddCycleCategory;
    public final Label lblCycleCategoryDisplay;
    public final Label lblCycleCategoryGhost;
    public final FlowPane fpCycleCategorySuggestions;

    public final Label lblRightHint;

    // Р В Р’В Р В РІР‚В Р В Р Р‹Р РЋРІвЂћСћР В Р вЂ Р В РІР‚С™Р вЂ™Р’В¦ Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎСљР В Р’В Р вЂ™Р’В Р В Р’В Р Р†Р вЂљР’В¦Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎС›Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРІР‚СњР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎСљР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В° Р В Р’В Р вЂ™Р’В Р В Р Р‹Р вЂ™Р’ВР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’ВµР В Р’В Р вЂ™Р’В Р В Р’В Р Р†Р вЂљР’В¦Р В Р’В Р В Р вЂ№Р В Р’В Р Р†Р вЂљРІвЂћвЂ“ (Р В Р’В Р В Р вЂ№Р В Р’В Р РЋРІР‚СљР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В°Р В Р’В Р вЂ™Р’В Р В Р Р‹Р вЂ™Р’ВР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В° Р В Р’В Р вЂ™Р’В Р В РЎС›Р Р†Р вЂљР’ВР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’ВµР В Р’В Р В Р вЂ№Р В Р’В Р Р†Р вЂљРЎв„ўР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В¶Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљР’ВР В Р’В Р В Р вЂ№Р В Р вЂ Р В РІР‚С™Р РЋРІвЂћСћ Р В Р’В Р вЂ™Р’В Р В Р Р‹Р вЂ™Р’ВР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎС›Р В Р’В Р вЂ™Р’В Р В РЎС›Р Р†Р вЂљР’ВР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В°Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В»Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎСљР В Р’В Р В Р вЂ№Р В Р Р‹Р Р†Р вЂљРЎС™/overlay Р В Р’В Р вЂ™Р’В Р В Р’В Р Р†Р вЂљР’В Р В Р’В Р вЂ™Р’В Р В Р’В Р Р†Р вЂљР’В¦Р В Р’В Р В Р вЂ№Р В Р Р‹Р Р†Р вЂљРЎС™Р В Р’В Р В Р вЂ№Р В Р вЂ Р В РІР‚С™Р РЋРІвЂћСћР В Р’В Р В Р вЂ№Р В Р’В Р Р†Р вЂљРЎв„ўР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљР’В Р В Р’В Р В Р вЂ№Р В Р’В Р РЋРІР‚СљР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’ВµР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В±Р В Р’В Р В Р вЂ№Р В Р’В Р В Р РЏ)
    public final CycleCardMenuButton btnMenuRight;

    // Р В Р’В Р В РІР‚В Р В Р Р‹Р РЋРІвЂћСћР В Р вЂ Р В РІР‚С™Р вЂ™Р’В¦ NEW: profile button in first row
    public final Button btnProfileRight;

    public final Button btnCloseRight;

    public final Button btnRightAddCases;

    // Р В Р’В Р В РІР‚В Р В Р Р‹Р РЋРІвЂћСћР В Р вЂ Р В РІР‚С™Р вЂ™Р’В¦ NEW: top trash toggle (delete-mode for rows)
    public final Button btnRightTrashCases;

    public final Label lblAddedCasesCount;

    public final VBox vbAddedCases;

    // Р В Р’В Р В РІР‚В Р В Р Р‹Р РЋРІвЂћСћР В Р вЂ Р В РІР‚С™Р вЂ™Р’В¦ DELETE (right)
    public final Button btnDeleteRight;
    public final StackPane deleteLayer;
    public final VBox deleteModal;
    public final Button btnDeleteCancel;
    public final Button btnDeleteConfirm;

    public final Button btnSaveRight;

    public CyclesViewRefs(
            StackPane root,
            StackPane leftStack,
            VBox leftMain,
            TextField tfSearch,
            Button btnSearch,
            Button btnFolder,
            Button btnCreate,
            Object tgThemeLeft,
            Button btnTrash,
            Button btnFilter,
            Button btnSort,
            Label lblSortSummary,
            VBox casesSheet,
            ListView<Object> lvLeft,
            StackPane filterSheet,
            StackPane sortSheet,
            StackPane trashOverlay,
            StackPane floatingOverlayRoot,
            StackPane rightRoot,
            VBox rightPlaceholder,
            Label lblCycleCreatedAt,
            TaskLinkChip chipTaskLink,
            EnvironmentChip chipEnvironment,
            TextField tfCycleTitle,
            TextField tfCycleCategory,
            Button btnAddCycleCategory,
            Label lblCycleCategoryDisplay,
            Label lblCycleCategoryGhost,
            FlowPane fpCycleCategorySuggestions,
            Label lblRightHint,
            CycleCardMenuButton btnMenuRight,
            Button btnProfileRight,
            Button btnCloseRight,
            Button btnRightAddCases,
            Button btnRightTrashCases,
            Label lblAddedCasesCount,
            VBox vbAddedCases,
            Button btnDeleteRight,
            StackPane deleteLayer,
            VBox deleteModal,
            Button btnDeleteCancel,
            Button btnDeleteConfirm,
            Button btnSaveRight
    ) {
        this.root = root;
        this.leftStack = leftStack;
        this.leftMain = leftMain;

        this.tfSearch = tfSearch;
        this.btnSearch = btnSearch;
        this.btnFolder = btnFolder;
        this.btnCreate = btnCreate;

        this.tgThemeLeft = tgThemeLeft;

        this.btnTrash = btnTrash;
        this.btnFilter = btnFilter;
        this.btnSort = btnSort;
        this.lblSortSummary = lblSortSummary;

        this.casesSheet = casesSheet;
        this.lvLeft = lvLeft;

        this.filterSheet = filterSheet;
        this.sortSheet = sortSheet;
        this.trashOverlay = trashOverlay;
        this.floatingOverlayRoot = floatingOverlayRoot;

        this.rightRoot = rightRoot;
        this.rightPlaceholder = rightPlaceholder;

        this.lblCycleCreatedAt = lblCycleCreatedAt;
        this.chipTaskLink = chipTaskLink;

        this.chipEnvironment = chipEnvironment;

        this.tfCycleTitle = tfCycleTitle;
        this.tfCycleCategory = tfCycleCategory;
        this.btnAddCycleCategory = btnAddCycleCategory;
        this.lblCycleCategoryDisplay = lblCycleCategoryDisplay;
        this.lblCycleCategoryGhost = lblCycleCategoryGhost;
        this.fpCycleCategorySuggestions = fpCycleCategorySuggestions;

        this.lblRightHint = lblRightHint;

        this.btnMenuRight = btnMenuRight;
        this.btnProfileRight = btnProfileRight;

        this.btnCloseRight = btnCloseRight;

        this.btnRightAddCases = btnRightAddCases;
        this.btnRightTrashCases = btnRightTrashCases;

        this.lblAddedCasesCount = lblAddedCasesCount;

        this.vbAddedCases = vbAddedCases;

        this.btnDeleteRight = btnDeleteRight;
        this.deleteLayer = deleteLayer;
        this.deleteModal = deleteModal;
        this.btnDeleteCancel = btnDeleteCancel;
        this.btnDeleteConfirm = btnDeleteConfirm;

        this.btnSaveRight = btnSaveRight;
    }
}
