package app.domain.history.ui;

import app.core.AppSettings;
import app.core.CardNavigationBridge;
import app.core.Router;
import app.domain.history.model.HistoryCalendarDayModel;
import app.domain.history.model.HistoryDayDataModel;
import app.domain.history.model.HistoryDaySummaryModel;
import app.domain.history.model.HistoryMonthSummaryModel;
import app.domain.history.model.HistoryTimelineItemModel;
import app.domain.history.service.HistoryDayDataService;
import app.domain.history.service.HistoryMonthDataService;
import app.domain.history.service.HistoryWeekDataService;
import app.domain.history.service.HistoryYearDataService;
import javafx.animation.FadeTransition;
import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HistoryScreen {

    private static final double LEFT_RATIO = 0.40;
    private static final double RIGHT_RATIO = 0.60;
    private static final double HORIZONTAL_INSETS = 42.0;
    private static final double LEFT_PANEL_MAX_HEIGHT = 760.0;
    private static final Duration SWITCH_ANIM = Duration.millis(180);
    private static final PseudoClass SELECTED_SCALE = PseudoClass.getPseudoClass("selected-scale");
    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter DAY_TITLE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", RU);
    private static final DateTimeFormatter MONTH_TITLE_FMT =
            DateTimeFormatter.ofPattern("LLLL yyyy", RU);
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm", RU);

    private final HistoryViewRefs v;
    private final ToggleGroup scaleGroup = new ToggleGroup();
    private final HistoryMonthDataService monthDataService = new HistoryMonthDataService();
    private final HistoryWeekDataService weekDataService = new HistoryWeekDataService();
    private final HistoryYearDataService yearDataService = new HistoryYearDataService();
    private final HistoryDayDataService dayDataService = new HistoryDayDataService();

    private HistoryScale scale = HistoryScale.fromSettings(AppSettings.historyScale());
    private LocalDate anchorDate = LocalDate.now();
    private LocalDate selectedDate = LocalDate.now();
    private Map<LocalDate, HistoryCalendarDayModel> monthData = Map.of();
    private Map<LocalDate, HistoryCalendarDayModel> weekData = Map.of();
    private Map<YearMonth, HistoryMonthSummaryModel> yearData = Map.of();
    private HistoryMonthSummaryModel selectedMonthSummary = null;
    private HistoryDayDataModel dayData = new HistoryDayDataModel(null, List.of());
    private SummaryFilter activeSummaryFilter = SummaryFilter.ALL;

    private YearMonth builtMonthViewYM = null;
    private LocalDate builtWeekViewStart = null;
    private int builtYearViewYear = -1;
    private final Map<LocalDate, StackPane> monthCells = new HashMap<>();
    private final Map<LocalDate, VBox> weekCards = new HashMap<>();
    private final Map<YearMonth, VBox> yearCards = new HashMap<>();

    public HistoryScreen(HistoryViewRefs refs) {
        this.v = refs;
    }

    public void init() {
        installRatioBinding();
        installLeftHeightBinding();
        installScaleSwitcher();
        installNavigation();
        refresh();
    }

    private void installRatioBinding() {
        if (v.root() == null || v.contentBox() == null || v.leftColumn() == null || v.rightColumn() == null) return;

        v.root().setAlignment(Pos.TOP_LEFT);
        v.contentBox().setMaxWidth(Double.MAX_VALUE);
        v.contentBox().setMaxHeight(Double.MAX_VALUE);
        v.contentBox().prefWidthProperty().bind(v.root().widthProperty());
        v.contentBox().minWidthProperty().bind(v.root().widthProperty());
        v.contentBox().prefHeightProperty().bind(v.root().heightProperty());
        v.contentBox().minHeightProperty().bind(v.root().heightProperty());
        var availableWidth = v.root().widthProperty().subtract(HORIZONTAL_INSETS);
        var leftWidth = availableWidth.multiply(LEFT_RATIO);
        var rightWidth = availableWidth.multiply(RIGHT_RATIO);

        v.leftColumn().prefWidthProperty().bind(leftWidth);
        v.leftColumn().minWidthProperty().bind(leftWidth);
        v.leftColumn().maxWidthProperty().bind(leftWidth);

        v.rightColumn().prefWidthProperty().bind(rightWidth);
        v.rightColumn().minWidthProperty().bind(rightWidth);
        v.rightColumn().maxWidthProperty().bind(rightWidth);
    }

    private void installLeftHeightBinding() {
        if (v.leftSurface() == null || v.calendarViewport() == null) return;
        v.leftSurface().setMaxHeight(LEFT_PANEL_MAX_HEIGHT);
        VBox.setVgrow(v.leftSurface(), Priority.ALWAYS);
        VBox.setVgrow(v.calendarViewport(), Priority.ALWAYS);
    }

    private void installScaleSwitcher() {
        installScaleToggle(v.btnScaleWeek(), HistoryScale.WEEK);
        installScaleToggle(v.btnScaleMonth(), HistoryScale.MONTH);
        installScaleToggle(v.btnScaleYear(), HistoryScale.YEAR);
        updateScaleToggleSelection();
    }

    private void installScaleToggle(ToggleButton button, HistoryScale toggleScale) {
        if (button == null) {
            return;
        }
        button.setToggleGroup(scaleGroup);
        button.setOnAction(event -> changeScale(toggleScale));
    }

    private void installNavigation() {
        if (v.btnPrev() != null) {
            v.btnPrev().setOnAction(event -> {
                anchorDate = shift(anchorDate, -1);
                refresh();
            });
        }
        if (v.btnNext() != null) {
            v.btnNext().setOnAction(event -> {
                anchorDate = shift(anchorDate, 1);
                refresh();
            });
        }
        if (v.btnToday() != null) {
            v.btnToday().setOnAction(event -> {
                anchorDate = LocalDate.now();
                selectedDate = LocalDate.now();
                refresh();
            });
        }
        if (v.btnSelectionPrev() != null) {
            v.btnSelectionPrev().setOnAction(event -> shiftSelectionDay(-1));
        }
        if (v.btnSelectionNext() != null) {
            v.btnSelectionNext().setOnAction(event -> shiftSelectionDay(1));
        }
    }

    private void changeScale(HistoryScale newScale) {
        if (newScale == null || newScale == scale) {
            updateScaleToggleSelection();
            return;
        }

        scale = newScale;
        AppSettings.setHistoryScale(newScale.settingsValue());
        updateScaleToggleSelection();
        refresh();
    }

    private void updateScaleToggleSelection() {
        updateScaleToggle(v.btnScaleWeek(), scale == HistoryScale.WEEK);
        updateScaleToggle(v.btnScaleMonth(), scale == HistoryScale.MONTH);
        updateScaleToggle(v.btnScaleYear(), scale == HistoryScale.YEAR);
    }

    private void updateScaleToggle(ToggleButton button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setSelected(selected);
        button.pseudoClassStateChanged(SELECTED_SCALE, selected);
    }

    private void shiftSelectionDay(int direction) {
        if (!canNavigateSelectionDay()) {
            return;
        }
        if (scale == HistoryScale.YEAR) {
            selectedDate = (selectedDate != null ? selectedDate : anchorDate).plusMonths(direction);
            anchorDate = selectedDate;
            refresh();
            return;
        }
        LocalDate base = selectedDate != null ? selectedDate : anchorDate;
        selectedDate = base.plusDays(direction);
        anchorDate = selectedDate;
        refresh();
    }

    private boolean canNavigateSelectionDay() {
        return scale == HistoryScale.YEAR || selectedDate != null;
    }

    private void refresh() {
        normalizeSelection();
        monthData = scale == HistoryScale.MONTH ? monthDataService.readMonth(anchorDate) : Map.of();
        weekData = scale == HistoryScale.WEEK ? weekDataService.readWeek(anchorDate) : Map.of();
        yearData = scale == HistoryScale.YEAR ? yearDataService.readYear(anchorDate) : Map.of();
        if (scale == HistoryScale.YEAR && selectedDate != null) {
            YearMonth selectedYM = YearMonth.from(selectedDate);
            if (yearData.containsKey(selectedYM)) {
                selectedMonthSummary = yearData.get(selectedYM);
            } else {
                selectedMonthSummary = yearDataService.readYear(selectedDate)
                        .getOrDefault(selectedYM, new HistoryMonthSummaryModel(selectedYM, 0, 0, 0, 0, 0));
            }
        } else {
            selectedMonthSummary = null;
        }
        dayData = hasActiveSelection()
                ? dayDataService.readDay(focusDate())
                : new HistoryDayDataModel(null, List.of());

        if (v.lblCalendarTitle() != null) {
            v.lblCalendarTitle().setText(periodTitle(scale, anchorDate));
        }
        if (v.lblCalendarFootnote() != null) {
            v.lblCalendarFootnote().setText(footnote(scale));
        }
        if (v.lblPeriodMetric() != null) {
            v.lblPeriodMetric().setText(periodMetricText());
        }
        if (v.lblSelectionTitle() != null) {
            v.lblSelectionTitle().setText(selectionTitle(scale));
        }
        updateSelectionNavigationState();
        refreshSummary();

        boolean sameMonthView = scale == HistoryScale.MONTH
                && builtMonthViewYM != null
                && builtMonthViewYM.equals(YearMonth.from(anchorDate))
                && !monthCells.isEmpty();
        boolean sameWeekView = scale == HistoryScale.WEEK
                && builtWeekViewStart != null
                && builtWeekViewStart.equals(anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                && !weekCards.isEmpty();
        boolean sameYearView = scale == HistoryScale.YEAR
                && builtYearViewYear == anchorDate.getYear()
                && !yearCards.isEmpty();
        if (sameMonthView) {
            refreshMonthCellStates();
        } else if (sameWeekView) {
            refreshWeekCardStates();
        } else if (sameYearView) {
            refreshYearCardStates();
        } else {
            monthCells.clear();
            weekCards.clear();
            yearCards.clear();
            builtMonthViewYM = scale == HistoryScale.MONTH ? YearMonth.from(anchorDate) : null;
            builtWeekViewStart = scale == HistoryScale.WEEK
                    ? anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    : null;
            builtYearViewYear = scale == HistoryScale.YEAR ? anchorDate.getYear() : -1;
            replaceCalendarView(buildCalendarView(scale, anchorDate));
        }
        refreshTimeline();
    }

    private void updateSelectionNavigationState() {
        boolean enabled = canNavigateSelectionDay();
        if (v.btnSelectionPrev() != null) {
            v.btnSelectionPrev().setDisable(!enabled);
        }
        if (v.btnSelectionNext() != null) {
            v.btnSelectionNext().setDisable(!enabled);
        }
    }

    private void normalizeSelection() {
        if (selectedDate == null) {
            selectedDate = anchorDate;
        }
    }

    private boolean hasActiveSelection() {
        if (selectedDate == null) return false;
        return switch (scale) {
            case MONTH, WEEK -> true;
            case YEAR -> false;
        };
    }

    private void replaceCalendarView(Node next) {
        if (next == null) return;

        v.calendarViewport().getChildren().setAll(next);
        next.setOpacity(0.0);

        FadeTransition fade = new FadeTransition(SWITCH_ANIM, next);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }

    private Node buildCalendarView(HistoryScale currentScale, LocalDate date) {
        return switch (currentScale) {
            case WEEK -> buildWeekView(date);
            case MONTH -> buildMonthView(date);
            case YEAR -> buildYearView(date);
        };
    }    private Node buildMonthView(LocalDate date) {
        VBox root = new VBox(10);
        root.getStyleClass().add("hy-calendar-body");
        root.setFillWidth(true);

        LocalDate firstOfMonth = date.withDayOfMonth(1);
        int firstColumn = firstOfMonth.getDayOfWeek().getValue() - 1;
        LocalDate gridStart = firstOfMonth.minusDays(firstColumn);
        LocalDate lastOfMonth = date.withDayOfMonth(date.lengthOfMonth());

        int visibleRows = 6;
        if (lastOfMonth.isBefore(gridStart.plusDays(35))) visibleRows = 5;
        if (lastOfMonth.isBefore(gridStart.plusDays(28))) visibleRows = 4;

        GridPane headers = createSevenColumnGrid(false);
        String[] dayNames = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        for (int i = 0; i < dayNames.length; i++) {
            Label label = new Label(dayNames[i]);
            label.getStyleClass().add("hy-weekday-label");
            if (i >= 5) label.getStyleClass().add("is-weekend");
            GridPane.setHalignment(label, HPos.CENTER);
            headers.add(label, i, 0);
        }

        GridPane days = createSevenColumnGrid(false);
        days.setVgap(8);
        for (int row = 0; row < visibleRows; row++) {
            days.getRowConstraints().add(new RowConstraints(52, 52, 52));
        }

        for (int index = 0; index < visibleRows * 7; index++) {
            int col = index % 7;
            int row = index / 7;
            LocalDate current = gridStart.plusDays(index);
            HistoryCalendarDayModel model = monthData.getOrDefault(current,
                    new HistoryCalendarDayModel(current, 0, 0, 0, 0, 0));

            StackPane cell = new StackPane();
            cell.setMinHeight(52);
            cell.setPrefHeight(52);
            cell.setMaxHeight(52);
            cell.getStyleClass().add("hy-month-cell");

            if (col == 5 || col == 6) {
                cell.getStyleClass().add("is-weekend");
                if (col == 6) cell.getStyleClass().add("is-sunday");
            }

            Label number = new Label(String.valueOf(current.getDayOfMonth()));
            number.getStyleClass().add("hy-month-day-number");
            StackPane.setAlignment(number, Pos.TOP_CENTER);
            StackPane.setMargin(number, new Insets(8, 0, 0, 0));
            cell.getChildren().add(number);

            if (model.hasActivity()) {
                Region dot = new Region();
                dot.getStyleClass().add("hy-month-dot");
                StackPane.setAlignment(dot, Pos.BOTTOM_CENTER);
                StackPane.setMargin(dot, new Insets(0, 0, 7, 0));
                cell.getChildren().add(dot);
            }

            cell.setOnMouseClicked(event -> {
                if (event.getButton() != MouseButton.PRIMARY) return;
                if (current.equals(selectedDate)) return;
                selectedDate = current;
                if (!current.getMonth().equals(anchorDate.getMonth())
                        || current.getYear() != anchorDate.getYear()) {
                    anchorDate = current;
                }
                refresh();
            });

            monthCells.put(current, cell);
            refreshMonthCellState(current, cell, date, model);
            days.add(cell, col, row);
        }

        root.getChildren().addAll(headers, days);
        return root;
    }

    private void refreshMonthCellStates() {
        for (Map.Entry<LocalDate, StackPane> entry : monthCells.entrySet()) {
            LocalDate current = entry.getKey();
            StackPane cell = entry.getValue();
            HistoryCalendarDayModel model = monthData.getOrDefault(current,
                    new HistoryCalendarDayModel(current, 0, 0, 0, 0, 0));
            refreshMonthCellState(current, cell, anchorDate, model);
        }
    }

    private void refreshMonthCellState(LocalDate current, StackPane cell, LocalDate date, HistoryCalendarDayModel model) {
        updateStyleClass(cell, "is-outside", !current.getMonth().equals(date.getMonth()));
        updateStyleClass(cell, "is-today", current.equals(LocalDate.now()));
        updateStyleClass(cell, "is-selected", current.equals(selectedDate));
        updateStyleClass(cell, "is-active", model.hasActive());
        applyTrafficTone(cell, model.hasFailures(), model.hasWarnings(), model.isAllPassed());
    }

    private Node buildWeekView(LocalDate date) {
        VBox root = new VBox(10);
        root.getStyleClass().add("hy-calendar-body");

        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        GridPane row = createSevenColumnGrid(false);
        RowConstraints weekRow = new RowConstraints();
        weekRow.setPercentHeight(100);
        weekRow.setVgrow(Priority.ALWAYS);
        weekRow.setFillHeight(true);
        row.getRowConstraints().add(weekRow);
        row.setVgap(0);
        row.setMinHeight(0);
        row.setMaxHeight(Double.MAX_VALUE);
        row.setMaxWidth(Double.MAX_VALUE);

        for (int i = 0; i < 7; i++) {
            LocalDate current = weekStart.plusDays(i);
            HistoryCalendarDayModel model = weekData.getOrDefault(current,
                    new HistoryCalendarDayModel(current, 0, 0, 0, 0, 0));

            VBox card = new VBox(8);
            card.getStyleClass().add("hy-week-card");
            if (current.getDayOfWeek() == DayOfWeek.SATURDAY || current.getDayOfWeek() == DayOfWeek.SUNDAY) {
                card.getStyleClass().add("is-weekend");
                if (current.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    card.getStyleClass().add("is-sunday");
                }
            }
            card.setMinWidth(0);
            card.setMinHeight(0);
            card.setMaxWidth(Double.MAX_VALUE);
            card.setMaxHeight(Double.MAX_VALUE);
            GridPane.setHgrow(card, Priority.ALWAYS);
            GridPane.setFillWidth(card, true);
            GridPane.setFillHeight(card, true);
            GridPane.setVgrow(card, Priority.ALWAYS);

            updateStyleClass(card, "is-today", current.equals(LocalDate.now()));
            updateStyleClass(card, "is-selected", current.equals(selectedDate));
            updateStyleClass(card, "is-active", model.hasActive());
            applyTrafficTone(card, model.hasFailures(), model.hasWarnings(), model.isAllPassed());

            Label dayName = new Label(current.getDayOfWeek().getDisplayName(TextStyle.SHORT, RU));
            dayName.getStyleClass().add("hy-week-card-title");

            Label dayNumber = new Label(current.format(DateTimeFormatter.ofPattern("d", RU)));
            dayNumber.getStyleClass().add("hy-week-card-date");

            Region spacer = new Region();
            VBox.setVgrow(spacer, Priority.ALWAYS);

            Node content;
            if (model.hasActivity()) {
                VBox activity = new VBox(4);
                activity.getStyleClass().add("hy-week-card-activity");
                activity.setAlignment(Pos.CENTER);
                activity.setMaxWidth(Double.MAX_VALUE);

                Label count = new Label(String.valueOf(model.cycleCount()));
                count.getStyleClass().add("hy-week-card-count");
                activity.getChildren().add(count);

                Region dot = new Region();
                dot.getStyleClass().add("hy-month-dot");
                activity.getChildren().add(dot);

                content = activity;
            } else {
                Label empty = new Label("—");
                empty.getStyleClass().add("hy-week-card-empty");
                content = empty;
            }

            card.getChildren().addAll(dayName, dayNumber, spacer, content);

            card.setOnMouseClicked(event -> {
                if (event.getButton() != MouseButton.PRIMARY) return;
                if (current.equals(selectedDate)) return;
                selectedDate = current;
                refresh();
            });

            weekCards.put(current, card);
            row.add(card, i, 0);
        }

        root.getChildren().add(row);
        VBox.setVgrow(row, Priority.ALWAYS);
        return root;
    }


    private void refreshWeekCardStates() {
        for (Map.Entry<LocalDate, VBox> entry : weekCards.entrySet()) {
            LocalDate current = entry.getKey();
            VBox card = entry.getValue();
            HistoryCalendarDayModel model = weekData.getOrDefault(current,
                    new HistoryCalendarDayModel(current, 0, 0, 0, 0, 0));
            refreshWeekCardState(current, card, model);
        }
    }

    private void refreshWeekCardState(LocalDate current, VBox card, HistoryCalendarDayModel model) {
        updateStyleClass(card, "is-today", current.equals(LocalDate.now()));
        updateStyleClass(card, "is-selected", current.equals(selectedDate));
        updateStyleClass(card, "is-active", model.hasActive());
        applyTrafficTone(card, model.hasFailures(), model.hasWarnings(), model.isAllPassed());
    }

    private void refreshYearCardStates() {
        YearMonth selectedYM = selectedDate != null ? YearMonth.from(selectedDate) : null;
        for (Map.Entry<YearMonth, VBox> entry : yearCards.entrySet()) {
            YearMonth ym = entry.getKey();
            VBox card = entry.getValue();
            HistoryMonthSummaryModel summary = yearData.getOrDefault(ym,
                    new HistoryMonthSummaryModel(ym, 0, 0, 0, 0, 0));
            updateStyleClass(card, "is-current-month", ym.equals(YearMonth.from(LocalDate.now())));
            updateStyleClass(card, "is-selected", ym.equals(selectedYM));
            updateStyleClass(card, "is-active", summary.activeCount() > 0);
            applyTrafficTone(card, summary.hasFailures(), summary.hasWarnings(), summary.isAllPassed());
        }
    }

    private Node buildYearView(LocalDate date) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getStyleClass().add("hy-year-grid");
        grid.setAlignment(Pos.TOP_LEFT);

        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            grid.getColumnConstraints().add(cc);
        }

        int year = date.getYear();
        YearMonth selectedYM = selectedDate != null ? YearMonth.from(selectedDate) : null;
        for (int month = 1; month <= 12; month++) {
            YearMonth ym = YearMonth.of(year, month);
            HistoryMonthSummaryModel summary = yearData.getOrDefault(ym,
                    new HistoryMonthSummaryModel(ym, 0, 0, 0, 0, 0));

            VBox card = new VBox(4);
            card.getStyleClass().add("hy-year-card");
            card.setPrefHeight(130);
            card.setMinHeight(130);
            card.setMaxHeight(130);

            updateStyleClass(card, "is-current-month", ym.equals(YearMonth.from(LocalDate.now())));
            updateStyleClass(card, "is-selected", ym.equals(selectedYM));
            updateStyleClass(card, "is-active", summary.activeCount() > 0);
            applyTrafficTone(card, summary.hasFailures(), summary.hasWarnings(), summary.isAllPassed());

            Label label = new Label(capitalize(Month.of(month).getDisplayName(TextStyle.FULL, RU)));
            label.getStyleClass().add("hy-year-card-title");

            Region spacer = new Region();
            VBox.setVgrow(spacer, Priority.ALWAYS);

            VBox content = new VBox(4);
            content.setAlignment(Pos.CENTER);
            content.setMaxWidth(Double.MAX_VALUE);
            if (summary.hasActivity()) {
                Label number = new Label(String.valueOf(summary.cycleCount()));
                number.getStyleClass().add("hy-year-card-count");

                Label plural = new Label(pluralCycles(summary.cycleCount()).trim());
                plural.getStyleClass().add("hy-year-card-footer");

                VBox countGroup = new VBox(0);
                countGroup.setAlignment(Pos.CENTER);
                countGroup.setMaxWidth(Double.MAX_VALUE);
                countGroup.getChildren().addAll(number, plural);
                content.getChildren().add(countGroup);
                if (summary.hasProblems()) {
                    Label prob = new Label("Проблем: " + summary.problematicCount());
                    prob.getStyleClass().add("hy-year-card-problem");
                    content.getChildren().add(prob);
                }
                Region dot = new Region();
                dot.getStyleClass().add("hy-month-dot");
                content.getChildren().add(dot);
            } else {
                Label empty = new Label("—");
                empty.getStyleClass().add("hy-year-card-empty");
                content.getChildren().add(empty);
            }

            card.getChildren().addAll(label, spacer, content);
            yearCards.put(ym, card);

            final int finalMonth = month;
            card.setOnMouseClicked(event -> {
                if (event.getButton() != MouseButton.PRIMARY) return;
                LocalDate today = LocalDate.now();
                boolean isCurrentMonth = year == today.getYear() && finalMonth == today.getMonthValue();
                anchorDate = isCurrentMonth ? today : LocalDate.of(year, finalMonth, 1);
                selectedDate = anchorDate;
                scale = HistoryScale.MONTH;
                AppSettings.setHistoryScale(scale.settingsValue());
                updateScaleToggleSelection();
                refresh();
            });

            grid.add(card, (month - 1) % 4, (month - 1) / 4);
        }

        return grid;
    }

    private void refreshSummary() {
        HBox row = summaryContainer();
        if (row == null) {
            return;
        }

        row.getChildren().clear();

        if (scale == HistoryScale.YEAR) {
            buildYearSummaryCards(row);
            return;
        }

        if (!hasActiveSelection()) {
            row.getChildren().add(buildSummaryEmptyCard(
                    "Выберите день в календаре",
                    "Чтобы увидеть сводку по циклам и фильтровать хронологию."
            ));
            return;
        }

        HistoryDaySummaryModel summary = dayData == null ? null : dayData.summary();
        if (summary == null || !summary.hasActivity()) {
            activeSummaryFilter = SummaryFilter.ALL;
            row.getChildren().add(buildSummaryEmptyCard(
                    "За текущий день нет данных по циклам",
                    ""
            ));
            return;
        }

        List<SummaryCardData> cards = new ArrayList<>();
        cards.add(new SummaryCardData(SummaryFilter.ALL, "Количество циклов", summary.cycleCount()));
        if (summary.problematicCount() > 0) {
            cards.add(new SummaryCardData(SummaryFilter.PROBLEMS, "Циклов с проблемами", summary.problematicCount()));
        }
        if (summary.notStartedCount() > 0) {
            cards.add(new SummaryCardData(SummaryFilter.NOT_STARTED, "Не начатых циклов", summary.notStartedCount()));
        }
        if (summary.pausedCount() > 0) {
            cards.add(new SummaryCardData(SummaryFilter.PAUSED, "Циклы на паузе", summary.pausedCount()));
        }

        if (cards.isEmpty()) {
            activeSummaryFilter = SummaryFilter.ALL;
            row.getChildren().add(buildSummaryEmptyCard(
                    "За текущий день нет данных по циклам",
                    ""
            ));
            return;
        }

        if (cards.stream().noneMatch(card -> card.filter() == activeSummaryFilter)) {
            activeSummaryFilter = SummaryFilter.ALL;
        }

        for (SummaryCardData card : cards) {
            row.getChildren().add(buildSummaryCard(card));
        }
    }

    private Node buildSummaryCard(SummaryCardData data) {
        VBox card = new VBox(4);
        card.getStyleClass().add("hy-summary-card");
        if (data.filter() == activeSummaryFilter) {
            card.getStyleClass().add("is-active-card");
        }
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(data.title());
        title.getStyleClass().add("hy-summary-label");
        title.setWrapText(true);

        Label value = new Label(String.valueOf(data.count()));
        value.getStyleClass().add("hy-summary-value");

        card.getChildren().addAll(title, value);
        card.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            activeSummaryFilter = data.filter();
            refreshSummary();
            refreshTimeline();
        });
        return card;
    }

    private Node buildSummaryEmptyCard(String titleText, String bodyText) {
        VBox card = new VBox(bodyText.isBlank() ? 0 : 6);
        card.getStyleClass().addAll("hy-empty-state", "hy-summary-empty-state");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);

        Label title = new Label(titleText);
        title.getStyleClass().addAll("hy-empty-title", "hy-summary-empty-title");
        title.setWrapText(true);
        card.getChildren().add(title);

        if (!bodyText.isBlank()) {
            Label body = new Label(bodyText);
            body.getStyleClass().addAll("hy-empty-text", "hy-summary-empty-text");
            body.setWrapText(true);
            card.getChildren().add(body);
        }

        return card;
    }

    private void buildYearSummaryCards(HBox row) {
        YearMonth selected = YearMonth.from(selectedDate != null ? selectedDate : anchorDate);
        HistoryMonthSummaryModel m = selectedMonthSummary != null
                ? selectedMonthSummary
                : new HistoryMonthSummaryModel(selected, 0, 0, 0, 0, 0);

        if (!m.hasActivity()) {
            row.getChildren().add(buildSummaryEmptyCard(
                    "За этот месяц данных нет", ""
            ));
            return;
        }

        row.getChildren().add(buildStaticSummaryCard("Количество циклов", m.cycleCount()));
        if (m.problematicCount() > 0) {
            row.getChildren().add(buildStaticSummaryCard("Циклов с проблемами", m.problematicCount()));
        }
        int notStarted = Math.max(0, m.unfinishedCount() - m.activeCount());
        if (notStarted > 0) {
            row.getChildren().add(buildStaticSummaryCard("Не начатых циклов", notStarted));
        }
        if (m.activeCount() > 0) {
            row.getChildren().add(buildStaticSummaryCard("Циклов на паузе", m.activeCount()));
        }
    }

    private Node buildStaticSummaryCard(String titleText, int count) {
        VBox card = new VBox(4);
        card.getStyleClass().add("hy-summary-card");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(titleText);
        title.getStyleClass().add("hy-summary-label");
        title.setWrapText(true);

        Label value = new Label(String.valueOf(count));
        value.getStyleClass().add("hy-summary-value");

        card.getChildren().addAll(title, value);
        return card;
    }

    private HBox summaryContainer() {
        return v.summaryRow();
    }

    private void refreshTimeline() {
        v.timelineContent().getChildren().clear();

        if (scale == HistoryScale.YEAR) {
            showTimelinePlaceholder(
                    "Выберите месяц",
                    "Нажмите на карточку месяца в календаре, чтобы перейти к его детальному просмотру."
            );
            return;
        }

        if ((scale == HistoryScale.MONTH || scale == HistoryScale.WEEK) && !hasActiveSelection()) {
            if (scale == HistoryScale.WEEK) {
                showTimelinePlaceholder(
                        "Выберите день недели",
                        "Нажмите на карточку дня слева, чтобы увидеть хронологию запусков."
                );
            }
            return;
        }

        if (dayData == null || dayData.timeline().isEmpty()) {
            showTimelinePlaceholder(
                    "За выбранный день событий нет",
                    "Когда в эту дату есть старт, пауза, завершение или не начатые циклы, здесь показывается хронология с переходом в нужный цикл."
            );
            return;
        }

        List<HistoryTimelineItemModel> filteredTimeline = dayData.timeline().stream()
                .filter(this::matchesActiveFilter)
                .toList();

        if (filteredTimeline.isEmpty()) {
            showTimelinePlaceholder(
                    "По выбранному фильтру событий нет",
                    "Переключите карточку в блоке сводки или выберите другой день."
            );
            return;
        }

        for (HistoryTimelineItemModel item : filteredTimeline) {
            v.timelineContent().getChildren().add(buildTimelineCard(item));
        }
    }

    private boolean matchesActiveFilter(HistoryTimelineItemModel item) {
        if (item == null) {
            return false;
        }
        return switch (activeSummaryFilter) {
            case ALL -> true;
            case PROBLEMS -> item.problematicCycle();
            case NOT_STARTED -> item.notStartedCycle();
            case PAUSED -> item.pausedCycle();
        };
    }

    private void showTimelinePlaceholder(String titleText, String bodyText) {
        VBox card = new VBox(8);
        card.getStyleClass().add("hy-empty-state");

        Label title = new Label(titleText);
        title.getStyleClass().add("hy-empty-title");

        Label body = new Label(bodyText);
        body.setWrapText(true);
        body.getStyleClass().add("hy-empty-text");

        card.getChildren().addAll(title, body);
        v.timelineContent().getChildren().add(card);
    }

    private Node buildTimelineCard(HistoryTimelineItemModel item) {
        VBox card = new VBox(10);
        card.getStyleClass().add("hy-timeline-card");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label time = new Label(item.occurredAt().format(TIME_FMT));
        time.getStyleClass().add("hy-timeline-time");

        VBox titleBox = new VBox(4);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Label title = new Label(item.title().isBlank() ? "Без названия" : item.title());
        title.getStyleClass().add("hy-timeline-title");

        Label meta = new Label(buildMetaLine(item));
        meta.getStyleClass().add("hy-timeline-meta");
        meta.setWrapText(true);

        titleBox.getChildren().addAll(title, meta);
        header.getChildren().addAll(time, titleBox);

        FlowPane chips = new FlowPane();
        chips.setHgap(8);
        chips.setVgap(8);
        appendEventChips(chips, item);
        appendStatusChips(chips, item);
        if (item.hasResponsible()) {
            chips.getChildren().add(buildTimelineChip("QA: " + item.qaResponsible()));
        }
        if (item.hasEnvironment()) {
            chips.getChildren().add(buildTimelineChip(item.environment()));
        }

        card.getChildren().addAll(header, chips);
        card.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            openCycle(item.cycleId());
        });
        return card;
    }

    private void appendEventChips(FlowPane chips, HistoryTimelineItemModel item) {
        String eventLabel = eventTypeLabel(item);
        if (!eventLabel.isBlank()) {
            chips.getChildren().add(buildTimelineChip(eventLabel));
        }
        if (!item.statusLabel().isBlank() && !item.statusLabel().equals(eventLabel)) {
            chips.getChildren().add(buildTimelineChip(item.statusLabel()));
        }
    }

    private String eventTypeLabel(HistoryTimelineItemModel item) {
        if (item.isFinishedEvent()) {
            return "Завершение";
        }
        if (item.isPausedEvent()) {
            return "Пауза";
        }
        if (item.isNotStartedEvent()) {
            return "Не начат";
        }
        if (item.isStartedEvent()) {
            return "Старт";
        }
        return "";
    }

    private Label buildTimelineChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("hy-timeline-chip");
        return chip;
    }

    private void appendStatusChips(FlowPane chips, HistoryTimelineItemModel item) {
        addStatusChip(chips, "Passed", item.passedCount());
        addStatusChip(chips, "Failed", item.failedCount());
        addStatusChip(chips, "Critical", item.criticalFailedCount());
        addStatusChip(chips, "With bugs", item.passedWithBugsCount());
        addStatusChip(chips, "Skipped", item.skippedCount());
        addStatusChip(chips, "In progress", item.inProgressCount());
    }

    private void addStatusChip(FlowPane chips, String label, int count) {
        if (count <= 0) {
            return;
        }
        chips.getChildren().add(buildTimelineChip(label + " " + count));
    }

    private String buildMetaLine(HistoryTimelineItemModel item) {
        StringBuilder meta = new StringBuilder();
        meta.append(caseCountLabel(item.totalCases()));
        if (item.problemCount() > 0) {
            meta.append(" • С проблемами ").append(item.problemCount());
        }
        if (item.inProgressCount() > 0) {
            meta.append(" • В работе ").append(item.inProgressCount());
        }
        return meta.toString();
    }

    private String caseCountLabel(int totalCases) {
        return totalCases + pluralCases(totalCases);
    }

    private static String pluralCases(int count) {
        int mod10 = count % 10;
        int mod100 = count % 100;
        if (mod10 == 1 && mod100 != 11) {
            return " кейс";
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return " кейса";
        }
        return " кейсов";
    }

    private static String pluralCycles(int count) {
        int mod10 = count % 10;
        int mod100 = count % 100;
        if (mod10 == 1 && mod100 != 11) return " цикл";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return " цикла";
        return " циклов";
    }

    private void openCycle(String cycleId) {
        if (cycleId == null || cycleId.isBlank()) {
            return;
        }
        CardNavigationBridge.requestCycleHistoryNavigation(cycleId, "");
        Router.get().cycles();
    }

    private LocalDate focusDate() {
        return selectedDate != null ? selectedDate : anchorDate;
    }

    private GridPane createSevenColumnGrid(boolean withRows) {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7.0);
            grid.getColumnConstraints().add(cc);
        }
        if (withRows) {
            for (int row = 0; row < 6; row++) {
                RowConstraints rc = new RowConstraints();
                rc.setPercentHeight(100.0 / 6.0);
                grid.getRowConstraints().add(rc);
            }
        }
        return grid;
    }

    private LocalDate shift(LocalDate date, int direction) {
        return switch (scale) {
            case WEEK -> date.plusWeeks(direction);
            case MONTH -> date.plusMonths(direction);
            case YEAR -> date.plusYears(direction);
        };
    }

    private String periodTitle(HistoryScale currentScale, LocalDate date) {
        return switch (currentScale) {
            case WEEK -> {
                LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate end = start.plusDays(6);
                yield "Неделя " + start.getDayOfMonth() + " - " + end.format(DateTimeFormatter.ofPattern("d MMMM yyyy", RU));
            }
            case MONTH -> capitalize(date.format(MONTH_TITLE_FMT));
            case YEAR -> date.getYear() + " год";
        };
    }

    private String selectionTitle(HistoryScale currentScale) {
        return switch (currentScale) {
            case MONTH -> hasActiveSelection() ? labeledDay(selectedDate) : "Выберите день";
            case WEEK -> hasActiveSelection() ? labeledDay(selectedDate) : weekRangeTitle(anchorDate);
            case YEAR -> capitalize((selectedDate != null ? selectedDate : anchorDate).format(MONTH_TITLE_FMT));
        };
    }

    private String periodMetricText() {
        PeriodStats stats = switch (scale) {
            case MONTH -> collectDayStats(monthData.values());
            case WEEK -> collectDayStats(weekData.values());
            case YEAR -> collectMonthStats(yearData.values());
        };

        String title = switch (scale) {
            case WEEK -> "Циклов на этой неделе:";
            case MONTH -> "Циклов в этом месяце:";
            case YEAR -> "Циклов в этом году:";
        };

        if (!stats.hasData()) {
            return title + "\nНет данных.";
        }

        List<String> parts = new ArrayList<>();
        if (stats.finished() > 0) {
            parts.add("пройдено - " + stats.finished() + " шт");
        }
        if (stats.unfinished() > 0) {
            parts.add("не пройдено - " + stats.unfinished() + " шт");
        }

        if (parts.isEmpty()) {
            return title + "\nНет данных.";
        }
        return title + "\n" + String.join(", ", parts);
    }

    private PeriodStats collectDayStats(Iterable<HistoryCalendarDayModel> models) {
        int total = 0;
        int finished = 0;
        int unfinished = 0;
        for (HistoryCalendarDayModel model : models) {
            if (model == null) continue;
            total += model.cycleCount();
            finished += model.finishedCount();
            unfinished += model.unfinishedCount();
        }
        return new PeriodStats(total, finished, unfinished);
    }

    private PeriodStats collectMonthStats(Iterable<HistoryMonthSummaryModel> summaries) {
        int total = 0;
        int finished = 0;
        int unfinished = 0;
        for (HistoryMonthSummaryModel summary : summaries) {
            if (summary == null) continue;
            total += summary.cycleCount();
            finished += summary.finishedCount();
            unfinished += summary.unfinishedCount();
        }
        return new PeriodStats(total, finished, unfinished);
    }

    private String labeledDay(LocalDate date) {
        if (date == null) {
            return "Выберите день";
        }
        String relative = relativeDayLabel(date);
        String day = formatDay(date);
        return relative.isBlank() ? day : relative + ": " + day;
    }

    private String relativeDayLabel(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return "Сегодня";
        }
        if (date.equals(today.minusDays(1))) {
            return "Вчера";
        }
        if (date.equals(today.plusDays(1))) {
            return "Завтра";
        }
        return "";
    }

    private String weekRangeTitle(LocalDate date) {
        LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        return start.format(DateTimeFormatter.ofPattern("d", RU))
                + " - "
                + end.format(DateTimeFormatter.ofPattern("d MMMM yyyy", RU));
    }

    private String footnote(HistoryScale currentScale) {
        return switch (currentScale) {
            case MONTH, WEEK, YEAR -> "";
        };
    }

    private void applyTrafficTone(Node node, boolean danger, boolean warning, boolean success) {
        if (node == null) {
            return;
        }
        node.getStyleClass().removeAll("tone-danger", "tone-warning", "tone-success");
        if (danger) {
            node.getStyleClass().add("tone-danger");
        } else if (warning) {
            node.getStyleClass().add("tone-warning");
        } else if (success) {
            node.getStyleClass().add("tone-success");
        }
    }

    private void updateStyleClass(Node node, String styleClass, boolean enabled) {
        if (node == null || styleClass == null || styleClass.isBlank()) {
            return;
        }
        if (enabled) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        } else {
            node.getStyleClass().remove(styleClass);
        }
    }

    private static String formatDay(LocalDate date) {
        return capitalize(date.format(DAY_TITLE_FMT));
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record PeriodStats(int total, int finished, int unfinished) {
        private boolean hasData() {
            return total > 0;
        }
    }

    private record SummaryCardData(SummaryFilter filter, String title, int count) {
    }

    private enum SummaryFilter {
        ALL,
        PROBLEMS,
        NOT_STARTED,
        PAUSED
    }
}
