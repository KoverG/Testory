package app.domain.history.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public record HistoryViewRefs(
        StackPane root,
        HBox contentBox,
        VBox leftColumn,
        StackPane leftSurface,
        Label lblPeriodMetric,
        HBox scaleSwitcher,
        ToggleButton btnScaleWeek,
        ToggleButton btnScaleMonth,
        ToggleButton btnScaleYear,
        Button btnPrev,
        Button btnToday,
        Button btnNext,
        Label lblCalendarTitle,
        Label lblCalendarFootnote,
        StackPane calendarViewport,
        VBox rightColumn,
        StackPane rightSurface,
        Label lblSelectionTitle,
        Button btnSelectionPrev,
        Button btnSelectionNext,
        HBox summaryRow,
        Label lblSummaryCycles,
        Label lblSummaryProblems,
        Label lblSummaryActive,
        VBox timelineContent
) {
}