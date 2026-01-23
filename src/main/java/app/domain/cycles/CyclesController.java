package app.domain.cycles;

import app.domain.cycles.ui.CyclesScreen;
import app.domain.cycles.ui.CyclesViewRefs;
import app.ui.UiScroll;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class CyclesController {

    private static final double CASES_SHEET_RADIUS = 18.0; // как на экране TestCases

    @FXML private BorderPane root;

    // left
    @FXML private StackPane leftStack;
    @FXML private VBox leftMain;

    @FXML private TextField tfSearch;
    @FXML private Button btnSearch;
    @FXML private Button btnFolder;
    @FXML private Button btnCreate;
    @FXML private Button btnTrash;
    @FXML private Button btnFilter;
    @FXML private Button btnSort;

    @FXML private VBox casesSheet;          // ✅ нужно для 1-в-1 trash overlay как в TestCases
    @FXML private ListView<Object> lvLeft;

    @FXML private StackPane filterSheet;
    @FXML private StackPane sortSheet;
    @FXML private StackPane trashOverlay;

    // right
    @FXML private StackPane rightRoot;
    @FXML private VBox rightPlaceholder;
    @FXML private Label lblRightTitle;
    @FXML private Label lblRightHint;
    @FXML private Button btnCloseRight;

    // ✅ новая кнопка по задаче
    @FXML private Button btnToggleLeftList;

    private CyclesScreen screen;

    @FXML
    private void initialize() {
        // ✅ ВАЖНО: Cycles имеет отдельный css (/ui/cycles.css). Подключаем его к корню экрана.
        // MainApp добавляет только /ui/styles.css, поэтому без этого cy-стили могут "пропасть".
        attachCyclesStylesheet();

        // ✅ 1-в-1 как на экране TestCases:
        // клип нужен, чтобы и скроллбар, и контент списка обрезались по скруглению листа.
        installCasesSheetClip();

        CyclesViewRefs refs = new CyclesViewRefs(
                root,
                leftStack,
                leftMain,
                tfSearch,
                btnSearch,
                btnFolder,
                btnCreate,
                btnTrash,
                btnFilter,
                btnSort,
                casesSheet,
                lvLeft,
                filterSheet,
                sortSheet,
                trashOverlay,
                rightRoot,
                rightPlaceholder,
                lblRightTitle,
                lblRightHint,
                btnCloseRight,
                btnToggleLeftList
        );

        screen = new CyclesScreen(refs);
        screen.init();
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

        // не дублируем
        if (!root.getStylesheets().contains(css)) {
            root.getStylesheets().add(css);
        }
    }
}
