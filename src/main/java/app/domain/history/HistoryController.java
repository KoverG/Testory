package app.domain.history;

import app.domain.history.ui.HistoryScreen;
import app.domain.history.ui.HistoryViewRefs;
import app.ui.UiSvg;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class HistoryController {

    @FXML private StackPane root;
    @FXML private HBox contentBox;

    @FXML private VBox leftColumn;
    @FXML private StackPane leftSurface;
    @FXML private Label lblPeriodMetric;
    @FXML private HBox scaleSwitcher;
    @FXML private ToggleButton btnScaleWeek;
    @FXML private ToggleButton btnScaleMonth;
    @FXML private ToggleButton btnScaleYear;
    @FXML private Button btnPrev;
    @FXML private Button btnToday;
    @FXML private Button btnNext;
    @FXML private Label lblCalendarTitle;
    @FXML private Label lblCalendarFootnote;
    @FXML private StackPane calendarViewport;

    @FXML private VBox rightColumn;
    @FXML private StackPane rightSurface;
    @FXML private Label lblSelectionTitle;
    @FXML private Button btnSelectionPrev;
    @FXML private Button btnSelectionNext;
    @FXML private HBox summaryRow;
    @FXML private Label lblSummaryCycles;
    @FXML private Label lblSummaryProblems;
    @FXML private Label lblSummaryActive;
    @FXML private VBox timelineContent;

    @FXML
    private void initialize() {
        attachHistoryStylesheet();
        installNavIcons();

        HistoryViewRefs refs = new HistoryViewRefs(
                root,
                contentBox,
                leftColumn,
                leftSurface,
                lblPeriodMetric,
                scaleSwitcher,
                btnScaleWeek,
                btnScaleMonth,
                btnScaleYear,
                btnPrev,
                btnToday,
                btnNext,
                lblCalendarTitle,
                lblCalendarFootnote,
                calendarViewport,
                rightColumn,
                rightSurface,
                lblSelectionTitle,
                btnSelectionPrev,
                btnSelectionNext,
                summaryRow,
                lblSummaryCycles,
                lblSummaryProblems,
                lblSummaryActive,
                timelineContent
        );

        new HistoryScreen(refs).init();
    }

    private void attachHistoryStylesheet() {
        if (root == null) return;

        var url = getClass().getResource("/ui/history.css");
        if (url == null) return;

        String css = url.toExternalForm();
        if (!root.getStylesheets().contains(css)) {
            root.getStylesheets().add(css);
        }
    }

    private void installNavIcons() {
        installBackIcon(btnPrev, false);
        installBackIcon(btnNext, true);
        installBackIcon(btnSelectionPrev, false);
        installBackIcon(btnSelectionNext, true);
    }

    private void installBackIcon(Button button, boolean rotate) {
        if (button == null) {
            return;
        }
        if (!rotate) {
            UiSvg.setButtonSvg(button, "back.svg", 12);
            button.setText("");
            return;
        }

        Node icon = UiSvg.createSvg("back.svg", 12);
        if (icon != null) {
            icon.setRotate(180.0);
            button.setGraphic(icon);
        }
        button.setText("");
    }
}