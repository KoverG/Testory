package app.domain.cycles.ui.left;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Refs на UI-узлы левой зоны экрана Cycles.
 * Выделен из CyclesViewRefs, чтобы LeftPaneCoordinator не зависел от всего экрана.
 */
public final class CyclesLeftViewRefs {

    public final StackPane leftStack;

    public final TextField tfSearch;
    public final Button btnSearch;
    public final Button btnFolder;
    public final Button btnCreate;

    public final Object tgThemeLeft;

    public final Button btnTrash;
    public final Button btnFilter;
    public final Button btnSort;
    public final Label lblSortSummary;

    public final VBox casesSheet;
    public final ListView<Object> lvLeft;

    public final StackPane filterSheet;
    public final StackPane sortSheet;

    public CyclesLeftViewRefs(
            StackPane leftStack,
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
            StackPane sortSheet
    ) {
        this.leftStack = leftStack;
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
    }
}
