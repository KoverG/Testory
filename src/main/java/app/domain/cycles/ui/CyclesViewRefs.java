package app.domain.cycles.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class CyclesViewRefs {

    public final BorderPane root;

    public final StackPane leftStack;
    public final VBox leftMain;

    public final TextField tfSearch;
    public final Button btnSearch;
    public final Button btnFolder;
    public final Button btnCreate;
    public final Button btnTrash;
    public final Button btnFilter;
    public final Button btnSort;

    // ✅ как в TestCasesController: нужно, чтобы trash-overlay считал ширину/спейсер
    public final VBox casesSheet;

    public final ListView<Object> lvLeft;

    public final StackPane filterSheet;
    public final StackPane sortSheet;
    public final StackPane trashOverlay;

    public final StackPane rightRoot;
    public final VBox rightPlaceholder;
    public final Label lblRightTitle;
    public final Label lblRightHint;
    public final Button btnCloseRight;

    // ✅ новая кнопка по задаче
    public final Button btnToggleLeftList;

    public CyclesViewRefs(
            BorderPane root,
            StackPane leftStack,
            VBox leftMain,
            TextField tfSearch,
            Button btnSearch,
            Button btnFolder,
            Button btnCreate,
            Button btnTrash,
            Button btnFilter,
            Button btnSort,
            VBox casesSheet,
            ListView<Object> lvLeft,
            StackPane filterSheet,
            StackPane sortSheet,
            StackPane trashOverlay,
            StackPane rightRoot,
            VBox rightPlaceholder,
            Label lblRightTitle,
            Label lblRightHint,
            Button btnCloseRight,
            Button btnToggleLeftList
    ) {
        this.root = root;
        this.leftStack = leftStack;
        this.leftMain = leftMain;
        this.tfSearch = tfSearch;
        this.btnSearch = btnSearch;
        this.btnFolder = btnFolder;
        this.btnCreate = btnCreate;
        this.btnTrash = btnTrash;
        this.btnFilter = btnFilter;
        this.btnSort = btnSort;
        this.casesSheet = casesSheet;
        this.lvLeft = lvLeft;
        this.filterSheet = filterSheet;
        this.sortSheet = sortSheet;
        this.trashOverlay = trashOverlay;
        this.rightRoot = rightRoot;
        this.rightPlaceholder = rightPlaceholder;
        this.lblRightTitle = lblRightTitle;
        this.lblRightHint = lblRightHint;
        this.btnCloseRight = btnCloseRight;
        this.btnToggleLeftList = btnToggleLeftList;
    }
}
