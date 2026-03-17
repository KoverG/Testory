package app.domain.history.ui;

import app.core.CardNavigationBridge;
import app.core.Router;
import app.domain.history.model.HistoryCalendarDayModel;
import app.domain.history.model.HistoryDayDataModel;
import app.domain.history.model.HistoryTimelineItemModel;
import app.domain.history.service.HistoryDayDataService;
import app.domain.history.service.HistoryMonthDataService;
import javafx.animation.FadeTransition;
import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
import java.util.HashMap;
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
    private final HistoryDayDataService dayDataService = new HistoryDayDataService();

    private HistoryScale scale = HistoryScale.MONTH;
    private LocalDate anchorDate = LocalDate.now();
    private LocalDate selectedDate = LocalDate.now();
    private Map<LocalDate, HistoryCalendarDayModel> monthData = Map.of();
    private HistoryDayDataModel dayData = new HistoryDayDataModel(null, java.util.List.of());

    private YearMonth builtMonthViewYM = null;
    private final Map<LocalDate, StackPane> monthCells = new HashMap<>();

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
        if (v.root() == null || v.leftColumn() == null || v.rightColumn() == null) return;

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
        // Static max-height cap — no bindings to root.heightProperty() to avoid transient layout jitter
        v.leftSurface().setMaxHeight(LEFT_PANEL_MAX_HEIGHT);
        VBox.setVgrow(v.leftSurface(), Priority.ALWAYS);
        VBox.setVgrow(v.calendarViewport(), Priority.ALWAYS);
    }

    private void installScaleSwitcher() {
        v.btnScaleDay().setToggleGroup(scaleGroup);
        v.btnScaleWeek().setToggleGroup(scaleGroup);
        v.btnScaleMonth().setToggleGroup(scaleGroup);
        v.btnScaleYear().setToggleGroup(scaleGroup);

        v.btnScaleDay().setOnAction(event -> changeScale(HistoryScale.DAY));
        v.btnScaleWeek().setOnAction(event -> changeScale(HistoryScale.WEEK));
        v.btnScaleMonth().setOnAction(event -> changeScale(HistoryScale.MONTH));
        v.btnScaleYear().setOnAction(event -> changeScale(HistoryScale.YEAR));

        v.btnScaleMonth().setSelected(true);
        markSelectedScale();
    }

    private void installNavigation() {
        v.btnPrev().setOnAction(event -> {
            anchorDate = shift(anchorDate, -1);
            refresh();
        });
        v.btnNext().setOnAction(event -> {
            anchorDate = shift(anchorDate, 1);
            refresh();
        });
        v.btnToday().setOnAction(event -> {
            anchorDate = LocalDate.now();
            selectedDate = LocalDate.now();
            refresh();
        });
    }

    private void changeScale(HistoryScale newScale) {
        if (newScale == null || newScale == scale) {
            markSelectedScale();
            return;
        }
        scale = newScale;
        markSelectedScale();
        refresh();
    }

    private void markSelectedScale() {
        v.btnScaleDay().pseudoClassStateChanged(SELECTED_SCALE, scale == HistoryScale.DAY);
        v.btnScaleWeek().pseudoClassStateChanged(SELECTED_SCALE, scale == HistoryScale.WEEK);
        v.btnScaleMonth().pseudoClassStateChanged(SELECTED_SCALE, scale == HistoryScale.MONTH);
        v.btnScaleYear().pseudoClassStateChanged(SELECTED_SCALE, scale == HistoryScale.YEAR);
    }

    private void refresh() {
        normalizeSelection();
        if (scale == HistoryScale.MONTH) {
            monthData = monthDataService.readMonth(anchorDate);
        } else {
            monthData = Map.of();
        }
        dayData = hasActiveSelection()
                ? dayDataService.readDay(focusDate())
                : new HistoryDayDataModel(null, java.util.List.of());

        v.lblCalendarTitle().setText(periodTitle(scale, anchorDate));
        v.lblCalendarFootnote().setText(footnote(scale));
        v.lblSelectionTitle().setText(selectionTitle(scale));
        refreshSummary();
        boolean sameMonthView = scale == HistoryScale.MONTH
                && builtMonthViewYM != null
                && builtMonthViewYM.equals(YearMonth.from(anchorDate))
                && !monthCells.isEmpty();
        if (sameMonthView) {
            refreshMonthCellStates();
        } else {
            monthCells.clear();
            builtMonthViewYM = scale == HistoryScale.MONTH ? YearMonth.from(anchorDate) : null;
            replaceCalendarView(buildCalendarView(scale, anchorDate));
        }
        refreshTimeline();
    }

    private void normalizeSelection() {
        if (scale != HistoryScale.MONTH && selectedDate == null) {
            selectedDate = anchorDate;
        }
    }

    /** Returns true when selectedDate should be treated as active (visible in current month/scale). */
    private boolean hasActiveSelection() {
        if (selectedDate == null) return false;
        if (scale != HistoryScale.MONTH) return true;
        return YearMonth.from(selectedDate).equals(YearMonth.from(anchorDate));
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
            case DAY -> buildDayView(date);
            case WEEK -> buildWeekView(date);
            case MONTH -> buildMonthView(date);
            case YEAR -> buildYearView(date);
        };
    }

    private Node buildMonthView(LocalDate date) {
        VBox root = new VBox(10);
        root.getStyleClass().add("hy-calendar-body");

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
                    new HistoryCalendarDayModel(current, 0, 0, 0));

            StackPane cell = new StackPane();
            cell.setMinHeight(52);
            cell.setPrefHeight(52);
            cell.setMaxHeight(52);
            cell.getStyleClass().add("hy-month-cell");

            if (col == 5 || col == 6) {
                cell.getStyleClass().add("is-weekend");
                if (col == 6) cell.getStyleClass().add("is-sunday");
            }

            if (!current.getMonth().equals(date.getMonth())) cell.getStyleClass().add("is-outside");
            if (current.equals(LocalDate.now())) cell.getStyleClass().add("is-today");
            if (current.equals(selectedDate)) cell.getStyleClass().add("is-selected");
            if (model.hasProblems()) cell.getStyleClass().add("is-problem");
            if (model.hasActivity()) cell.getStyleClass().add("has-activity");
            if (model.hasActive()) cell.getStyleClass().add("is-active");

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
                selectedDate = current;
                if (!current.getMonth().equals(anchorDate.getMonth())
                        || current.getYear() != anchorDate.getYear()) {
                    anchorDate = current;
                }
                refresh();
            });

            monthCells.put(current, cell);
            days.add(cell, col, row);
        }

        root.getChildren().addAll(headers, days);
        return root;
    }

    private Node buildWeekView(LocalDate date) {
        VBox root = new VBox(10);
        root.getStyleClass().add("hy-calendar-body");

        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        HBox row = new HBox(8);
        row.setFillHeight(true);

        for (int i = 0; i < 7; i++) {
            LocalDate current = weekStart.plusDays(i);
            VBox card = new VBox(8);
            card.getStyleClass().add("hy-week-card");
            HBox.setHgrow(card, Priority.ALWAYS);

            Label dayName = new Label(current.getDayOfWeek().getDisplayName(TextStyle.SHORT, RU));
            dayName.getStyleClass().add("hy-week-card-title");
            Label dayNumber = new Label(current.format(DateTimeFormatter.ofPattern("d MMM", RU)));
            dayNumber.getStyleClass().add("hy-week-card-date");

            VBox bars = new VBox(6,
                    skeletonBar(0.92),
                    skeletonBar(0.76),
                    skeletonBar(0.58)
            );

            Region spacer = new Region();
            VBox.setVgrow(spacer, Priority.ALWAYS);

            Label footer = new Label(current.equals(LocalDate.now()) ? "Сегодня" : "Каркас недели");
            footer.getStyleClass().add("hy-week-card-footer");

            card.getChildren().addAll(dayName, dayNumber, bars, spacer, footer);
            if (current.equals(LocalDate.now())) {
                card.getStyleClass().add("is-today");
            }
            row.getChildren().add(card);
        }

        root.getChildren().add(row);
        VBox.setVgrow(row, Priority.ALWAYS);
        return root;
    }

    private Node buildDayView(LocalDate date) {
        VBox root = new VBox(10);
        root.getStyleClass().add("hy-calendar-body");

        Label title = new Label(capitalize(date.format(DAY_TITLE_FMT)));
        title.getStyleClass().add("hy-day-title");

        VBox timeline = new VBox(8);
        timeline.getStyleClass().add("hy-day-timeline");

        String[] hours = {"09:00", "11:00", "13:00", "15:00", "17:00"};
        for (String hour : hours) {
            HBox row = new HBox(10);
            row.getStyleClass().add("hy-day-slot");

            Label hourLabel = new Label(hour);
            hourLabel.getStyleClass().add("hy-day-slot-hour");

            VBox content = new VBox(6);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.getChildren().addAll(
                    skeletonBar(0.78),
                    skeletonBar(0.44)
            );

            row.getChildren().addAll(hourLabel, content);
            timeline.getChildren().add(row);
        }

        root.getChildren().addAll(title, timeline);
        VBox.setVgrow(timeline, Priority.ALWAYS);
        return root;
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

        Month currentMonth = date.getMonth();
        for (int month = 1; month <= 12; month++) {
            VBox card = new VBox(8);
            card.getStyleClass().add("hy-year-card");
            card.setPrefHeight(116);
            card.setMinHeight(116);
            card.setMaxHeight(116);
            if (month == currentMonth.getValue()) {
                card.getStyleClass().add("is-current-month");
            }

            Label label = new Label(capitalize(Month.of(month).getDisplayName(TextStyle.FULL, RU)));
            label.getStyleClass().add("hy-year-card-title");

            FlowPane dots = new FlowPane();
            dots.setHgap(6);
            dots.setVgap(6);
            for (int dot = 0; dot < 10; dot++) {
                Region marker = new Region();
                marker.getStyleClass().add("hy-year-dot");
                if (dot == 2 || dot == 6) {
                    marker.getStyleClass().add("is-strong");
                }
                dots.getChildren().add(marker);
            }

            Label footer = new Label("Месячный срез");
            footer.getStyleClass().add("hy-year-card-footer");

            card.getChildren().addAll(label, dots, footer);
            grid.add(card, (month - 1) % 4, (month - 1) / 4);
        }

        return grid;
    }

    private void refreshMonthCellStates() {
        for (Map.Entry<LocalDate, StackPane> entry : monthCells.entrySet()) {
            StackPane cell = entry.getValue();
            boolean selected = entry.getKey().equals(selectedDate);
            if (selected) {
                if (!cell.getStyleClass().contains("is-selected")) cell.getStyleClass().add("is-selected");
            } else {
                cell.getStyleClass().remove("is-selected");
            }
        }
    }

    private void refreshSummary() {
        var s = dayData != null ? dayData.summary() : null;
        boolean hasData = s != null && s.hasActivity();
        String cycles   = hasData ? String.valueOf(s.cycleCount())       : "—";
        String problems = hasData ? String.valueOf(s.problematicCount())  : "—";
        String active   = hasData ? String.valueOf(s.activeCount())       : "—";
        if (v.lblSummaryCycles()   != null) v.lblSummaryCycles().setText(cycles);
        if (v.lblSummaryProblems() != null) v.lblSummaryProblems().setText(problems);
        if (v.lblSummaryActive()   != null) v.lblSummaryActive().setText(active);
    }

    private void refreshTimeline() {
        v.timelineContent().getChildren().clear();

        if (scale != HistoryScale.MONTH && scale != HistoryScale.DAY) {
            showTimelinePlaceholder(
                    "Хронология этого масштаба будет добавлена позже",
                    "Сейчас основной пользовательский сценарий строится вокруг выбранного дня: календарь месяца, сводка дня и список запусков циклов."
            );
            return;
        }

        if (scale == HistoryScale.MONTH && !hasActiveSelection()) {
            return;
        }

        if (dayData == null || dayData.timeline().isEmpty()) {
            showTimelinePlaceholder(
                    "За выбранный день запусков нет",
                    "Когда в эту дату есть прохождения циклов, здесь показывается хронология запусков с переходом в нужный цикл."
            );
            return;
        }

        for (HistoryTimelineItemModel item : dayData.timeline()) {
            v.timelineContent().getChildren().add(buildTimelineCard(item));
        }
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
        VBox card = new VBox(12);
        card.getStyleClass().add("hy-timeline-card");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label time = new Label(item.startedAt().format(TIME_FMT));
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
        chips.getChildren().add(buildTimelineChip(normalizeRunState(item.runState())));
        chips.getChildren().add(buildTimelineChip(caseCountLabel(item.totalCases())));
        appendStatusChips(chips, item);
        if (item.hasResponsible()) {
            chips.getChildren().add(buildTimelineChip("QA: " + item.qaResponsible()));
        }
        if (item.hasEnvironment()) {
            chips.getChildren().add(buildTimelineChip(item.environment()));
        }

        Button openButton = new Button("Открыть цикл");
        openButton.getStyleClass().addAll("tc-btn", "hy-timeline-open-btn");
        openButton.setFocusTraversable(false);
        openButton.setOnAction(event -> openCycle(item.cycleId()));

        card.getChildren().addAll(header, chips, openButton);
        return card;
    }

    private Label buildTimelineChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("hy-timeline-chip");
        return chip;
    }

    private void appendStatusChips(FlowPane chips, HistoryTimelineItemModel item) {
        addStatusChip(chips, "PASSED", item.passedCount());
        addStatusChip(chips, "FAILED", item.failedCount());
        addStatusChip(chips, "CRITICAL", item.criticalFailedCount());
        addStatusChip(chips, "BUGS", item.passedWithBugsCount());
        addStatusChip(chips, "SKIPPED", item.skippedCount());
        addStatusChip(chips, "IN_PROGRESS", item.inProgressCount());
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
            meta.append(" • Проблемных ").append(item.problemCount());
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

    private String normalizeRunState(String runState) {
        String value = runState == null ? "" : runState.trim().toLowerCase(RU);
        return switch (value) {
            case "finished" -> "Завершён";
            case "in_progress" -> "Выполняется";
            case "paused" -> "Пауза";
            case "idle" -> "Черновик";
            default -> value.isBlank() ? "Без статуса" : runState;
        };
    }

    private void openCycle(String cycleId) {
        if (cycleId == null || cycleId.isBlank()) {
            return;
        }
        CardNavigationBridge.requestCycleHistoryNavigation(cycleId, "");
        Router.get().cycles();
    }

    private LocalDate focusDate() {
        if (scale == HistoryScale.MONTH) return selectedDate != null ? selectedDate : anchorDate;
        return anchorDate;
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

    private Region skeletonBar(double factor) {
        Region bar = new Region();
        bar.getStyleClass().add("hy-skeleton-bar");
        bar.setPrefHeight(10);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefWidth(240 * factor);
        return bar;
    }

    private LocalDate shift(LocalDate date, int direction) {
        return switch (scale) {
            case DAY -> date.plusDays(direction);
            case WEEK -> date.plusWeeks(direction);
            case MONTH -> date.plusMonths(direction);
            case YEAR -> date.plusYears(direction);
        };
    }

    private String periodTitle(HistoryScale currentScale, LocalDate date) {
        return switch (currentScale) {
            case DAY -> capitalize(date.format(DAY_TITLE_FMT));
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
            case MONTH -> hasActiveSelection() ? formatDay(selectedDate) : "Выберите день";
            case DAY -> "Детальный день";
            case WEEK -> "Обзор недели";
            case YEAR -> "Годовой обзор";
        };
    }

    private String footnote(HistoryScale currentScale) {
        return switch (currentScale) {
            case MONTH -> "";
            case DAY, WEEK, YEAR -> "";
        };
    }

    private static String formatDay(LocalDate date) {
        return capitalize(date.format(DAY_TITLE_FMT));
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
