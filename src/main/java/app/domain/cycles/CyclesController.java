package app.domain.cycles;

import app.core.CardNavigationBridge;
import app.domain.cycles.ui.CyclesScreen;
import app.domain.cycles.ui.CyclesViewRefs;
import app.domain.cycles.ui.right.CycleCardMenuButton;
import app.domain.cycles.ui.right.EnvironmentChip;
import app.domain.cycles.ui.right.TaskLinkChip;
import app.ui.UiScroll;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class CyclesController {

    private static final double CASES_SHEET_RADIUS = 18.0;

    @FXML private StackPane root;
    @FXML private BorderPane contentRoot;

    @FXML private StackPane leftStack;
    @FXML private VBox leftMain;

    @FXML private TextField tfSearch;
    @FXML private Button btnSearch;
    @FXML private Button btnFolder;
    @FXML private Button btnCreate;

    @FXML private Object tgThemeLeft;

    @FXML private Button btnTrash;
    @FXML private Button btnFilter;
    @FXML private Button btnSort;
    @FXML private Label lblSortSummary;

    @FXML private VBox casesSheet;
    @FXML private ListView<Object> lvLeft;

    @FXML private StackPane filterSheet;
    @FXML private StackPane sortSheet;
    @FXML private StackPane trashOverlay;

    @FXML private StackPane rightRoot;
    @FXML private VBox rightPlaceholder;
    @FXML private StackPane floatingOverlayRoot;

    @FXML private Label lblCycleCreatedAt;
    @FXML private TaskLinkChip chipTaskLink;
    @FXML private EnvironmentChip chipEnvironment;
    @FXML private TextField tfCycleTitle;
    @FXML private TextField tfCycleCategory;
    @FXML private Button btnAddCycleCategory;
    @FXML private Label lblCycleCategoryDisplay;
    @FXML private Label lblCycleCategoryGhost;
    @FXML private FlowPane fpCycleCategorySuggestions;
    @FXML private Label lblRightHint;
    @FXML private CycleCardMenuButton btnMenuRight;
    @FXML private Button btnProfileRight;
    @FXML private Button btnCloseRight;
    @FXML private Button btnRightAddCases;
    @FXML private Button btnRightTrashCases;
    @FXML private Label lblAddedCasesCount;
    @FXML private VBox vbAddedCases;
    @FXML private Button btnDeleteRight;
    @FXML private StackPane deleteLayer;
    @FXML private VBox deleteModal;
    @FXML private Button btnDeleteCancel;
    @FXML private Button btnDeleteConfirm;
    @FXML private Button btnSaveRight;

    private CyclesScreen screen;

    @FXML
    private void initialize() {
        attachCyclesStylesheet();
        installCasesSheetClip();

        CyclesViewRefs refs = new CyclesViewRefs(
                root,
                leftStack,
                leftMain,
                tfSearch,
                btnSearch,
                btnFolder,
                btnCreate,
                tgThemeLeft,
                btnTrash,
                btnFilter,
                btnSort,
                lblSortSummary,
                casesSheet,
                lvLeft,
                filterSheet,
                sortSheet,
                trashOverlay,
                floatingOverlayRoot,
                rightRoot,
                rightPlaceholder,
                lblCycleCreatedAt,
                chipTaskLink,
                chipEnvironment,
                tfCycleTitle,
                tfCycleCategory,
                btnAddCycleCategory,
                lblCycleCategoryDisplay,
                lblCycleCategoryGhost,
                fpCycleCategorySuggestions,
                lblRightHint,
                btnMenuRight,
                btnProfileRight,
                btnCloseRight,
                btnRightAddCases,
                btnRightTrashCases,
                lblAddedCasesCount,
                vbAddedCases,
                btnDeleteRight,
                deleteLayer,
                deleteModal,
                btnDeleteCancel,
                btnDeleteConfirm,
                btnSaveRight
        );

        screen = new CyclesScreen(refs);
        screen.init();

        CardNavigationBridge.PendingCycleHistoryNavigation navigation =
                CardNavigationBridge.consumePendingCycleHistoryNavigation();
        if (navigation != null) {
            Platform.runLater(() -> screen.openCycleFromHistory(navigation.cycleId(), navigation.sourceCaseId()));
        }
    }

    private void installCasesSheetClip() {
        if (casesSheet == null) return;
        UiScroll.clipRounded(casesSheet, CASES_SHEET_RADIUS);
    }

    private void attachCyclesStylesheet() {
        if (root == null) return;

        var url = getClass().getResource("/ui/cycles.css");
        if (url == null) return;

        String css = url.toExternalForm();
        if (!root.getStylesheets().contains(css)) {
            root.getStylesheets().add(css);
        }
    }
}