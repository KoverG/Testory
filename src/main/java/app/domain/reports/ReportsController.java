package app.domain.reports;

import app.domain.cycles.ui.left.CyclesLeftViewRefs;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class ReportsController {

    @FXML private StackPane root;

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

    @FXML private StackPane rightRoot;
    @FXML private Button btnCloseRight;

    private ReportsScreen screen;

    @FXML
    private void initialize() {
        attachCyclesStylesheet();

        CyclesLeftViewRefs leftRefs = new CyclesLeftViewRefs(
                leftStack,
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
                sortSheet
        );

        screen = new ReportsScreen(leftRefs, rightRoot, btnCloseRight);
        screen.init();
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
