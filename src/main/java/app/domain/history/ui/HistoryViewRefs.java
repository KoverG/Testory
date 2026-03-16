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
        HBox scaleSwitcher,
        ToggleButton btnScaleDay,
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
        Label lblSelectionHint,
        Label lblSummaryScale,
        Label lblSummaryPeriod,
        Label lblSummaryState,
        VBox timelineContent
) {
}