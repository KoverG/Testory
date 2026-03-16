package app.domain.history.ui;

import app.domain.history.model.HistoryCalendarDayModel;
import app.domain.history.service.HistoryMonthDataService;
import javafx.animation.FadeTransition;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
import java.util.Locale;
import java.util.Map;

public final class HistoryScreen {

    private static final double LEFT_RATIO = 0.40;
    private static final double RIGHT_RATIO = 0.60;
    private static final double HORIZONTAL_INSETS = 42.0;
    private static final double LEFT_PANEL_MAX_HEIGHT = 760.0;
    private static final double LEFT_PANEL_RESERVED = 28.0;
    private static final double CALENDAR_VIEWPORT_RESERVED = 226.0;
    private static final Duration SWITCH_ANIM = Duration.millis(180);
    private static final PseudoClass SELECTED_SCALE = PseudoClass.getPseudoClass("selected-scale");
    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter DAY_TITLE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", RU);
    private static final DateTimeFormatter MONTH_TITLE_FMT =
            DateTimeFormatter.ofPattern("LLLL yyyy", RU);

    private final HistoryViewRefs v;
    private final ToggleGroup scaleGroup = new ToggleGroup();
    private final HistoryMonthDataService monthDataService = new HistoryMonthDataService();

    private HistoryScale scale = HistoryScale.MONTH;
    private LocalDate anchorDate = LocalDate.now();
    private LocalDate selectedDate = LocalDate.now();
    private Map<LocalDate, HistoryCalendarDayModel> monthData = Map.of();

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
        if (v.root() == null || v.leftSurface() == null || v.calendarViewport() == null) return;

        var panelHeight = Bindings.min(v.root().heightProperty().subtract(LEFT_PANEL_RESERVED), LEFT_PANEL_MAX_HEIGHT);
        v.leftSurface().prefHeightProperty().bind(panelHeight);
        v.leftSurface().maxHeightProperty().bind(panelHeight);
        v.leftSurface().minHeightProperty().bind(panelHeight);

        var viewportHeight = Bindings.max(360.0, panelHeight.subtract(CALENDAR_VIEWPORT_RESERVED));
        v.calendarViewport().prefHeightProperty().bind(viewportHeight);
        v.calendarViewport().maxHeightProperty().bind(viewportHeight);
        v.calendarViewport().minHeightProperty().bind(viewportHeight);
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
            if (scale == HistoryScale.MONTH) {
                selectedDate = shift(selectedDate, -1);
            }
            refresh();
        });
        v.btnNext().setOnAction(event -> {
            anchorDate = shift(anchorDate, 1);
            if (scale == HistoryScale.MONTH) {
                selectedDate = shift(selectedDate, 1);
            }
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

        v.lblCalendarTitle().setText(periodTitle(scale, anchorDate));
        v.lblCalendarFootnote().setText(footnote(scale));
        v.lblSelectionTitle().setText(selectionTitle(scale));
        v.lblSelectionHint().setText(selectionHint(scale));
        if (v.lblSummaryScale() != null) v.lblSummaryScale().setText(scale.title());
        if (v.lblSummaryPeriod() != null) v.lblSummaryPeriod().setText(summaryPeriod(scale));
        if (v.lblSummaryState() != null) v.lblSummaryState().setText(summaryState(scale));
        replaceCalendarView(buildCalendarView(scale, anchorDate));
        refreshTimelinePlaceholder();
    }

    private void normalizeSelection() {
        if (selectedDate == null) {
            selectedDate = anchorDate;
        }
        if (scale == HistoryScale.MONTH && !YearMonth.from(selectedDate).equals(YearMonth.from(anchorDate))) {
            LocalDate today = LocalDate.now();
            selectedDate = YearMonth.from(today).equals(YearMonth.from(anchorDate)) ? today : anchorDate.withDayOfMonth(1);
        }
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

        GridPane headers = createSevenColumnGrid(false);
        String[] dayNames = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        for (int i = 0; i < dayNames.length; i++) {
            Label label = new Label(dayNames[i]);
            label.getStyleClass().add("hy-weekday-label");
            if (i >= 5) {
                label.getStyleClass().add("is-weekend");
            }
            GridPane.setHalignment(label, HPos.CENTER);
            headers.add(label, i, 0);
        }

        StackPane monthLayer = new StackPane();
        monthLayer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        GridPane weekendBackdrop = createSevenColumnGrid(true);
        weekendBackdrop.setMouseTransparent(true);
        Region saturdayStripe = new Region();
        saturdayStripe.getStyleClass().add("hy-weekend-column");
        Region sundayStripe = new Region();
        sundayStripe.getStyleClass().addAll("hy-weekend-column", "is-sunday");
        weekendBackdrop.add(saturdayStripe, 5, 0, 1, 6);
        weekendBackdrop.add(sundayStripe, 6, 0, 1, 6);

        GridPane days = createSevenColumnGrid(true);
        days.setVgap(8);

        LocalDate firstOfMonth = date.withDayOfMonth(1);
        int firstColumn = firstOfMonth.getDayOfWeek().getValue() - 1;
        LocalDate gridStart = firstOfMonth.minusDays(firstColumn);

        for (int index = 0; index < 42; index++) {
            LocalDate current = gridStart.plusDays(index);
            HistoryCalendarDayModel model = monthData.getOrDefault(current, new HistoryCalendarDayModel(current, 0, 0, 0));

            StackPane cell = new StackPane();
            cell.setMinHeight(52);
            cell.setPrefHeight(52);
            cell.setMaxHeight(52);
            cell.getStyleClass().add("hy-month-cell");

            if (!current.getMonth().equals(date.getMonth())) {
                cell.getStyleClass().add("is-outside");
            }
            if (current.equals(LocalDate.now())) {
                cell.getStyleClass().add("is-today");
            }
            if (current.equals(selectedDate)) {
                cell.getStyleClass().add("is-selected");
            }
            if (model.hasProblems()) {
                cell.getStyleClass().add("is-problem");
            }
            if (model.hasActivity()) {
                cell.getStyleClass().add("has-activity");
            }
            if (model.hasActive()) {
                cell.getStyleClass().add("is-active");
            }

            Label number = new Label(String.valueOf(current.getDayOfMonth()));
            number.getStyleClass().add("hy-month-day-number");
            StackPane.setAlignment(number, Pos.TOP_CENTER);
            StackPane.setMargin(number, new Insets(8, 0, 0, 0));

            cell.getChildren().add(number);
            cell.setOnMouseClicked(event -> {
                if (event.getButton() != MouseButton.PRIMARY) return;
                selectedDate = current;
                if (!current.getMonth().equals(anchorDate.getMonth()) || current.getYear() != anchorDate.getYear()) {
                    anchorDate = current;
                }
                refresh();
            });

            days.add(cell, index % 7, index / 7);
        }

        monthLayer.getChildren().addAll(weekendBackdrop, days);
        root.getChildren().addAll(headers, monthLayer);
        VBox.setVgrow(monthLayer, Priority.ALWAYS);
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

    private void refreshTimelinePlaceholder() {
        v.timelineContent().getChildren().clear();

        VBox card = new VBox(8);
        card.getStyleClass().add("hy-empty-state");

        Label title = new Label(timelineTitle());
        title.getStyleClass().add("hy-empty-title");

        Label body = new Label(timelineText());
        body.setWrapText(true);
        body.getStyleClass().add("hy-empty-text");

        card.getChildren().addAll(title, body);
        v.timelineContent().getChildren().add(card);
    }

    private String timelineTitle() {
        if (scale == HistoryScale.MONTH) {
            HistoryCalendarDayModel model = monthData.getOrDefault(selectedDate, new HistoryCalendarDayModel(selectedDate, 0, 0, 0));
            if (!model.hasActivity()) {
                return "За выбранный день запусков пока нет";
            }
            return "Выбран день с активностью: " + formatDay(selectedDate);
        }
        return "Хронология будет добавлена на следующем этапе";
    }

    private String timelineText() {
        if (scale == HistoryScale.MONTH) {
            HistoryCalendarDayModel model = monthData.getOrDefault(selectedDate, new HistoryCalendarDayModel(selectedDate, 0, 0, 0));
            if (!model.hasActivity()) {
                return "Месячный календарь уже показывает реальные дни активности, но список конкретных запусков цикла появится на следующем этапе вместе с правой зоной дня.";
            }
            return "Календарь уже использует данные циклов: всего " + model.cycleCount()
                    + ", проблемных " + model.problematicCount()
                    + ", незавершённых " + model.activeCount()
                    + ". Следующим шагом здесь появится список запусков за выбранную дату и переход в цикл.";
        }
        return "Сейчас основной рабочий режим сосредоточен на месяце. Остальные масштабы сохранены как согласованный каркас, чтобы не смешивать этапы реализации.";
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
            case MONTH -> formatDay(selectedDate);
            case DAY -> "Детальный день";
            case WEEK -> "Обзор недели";
            case YEAR -> "Годовой обзор";
        };
    }

    private String selectionHint(HistoryScale currentScale) {
        return switch (currentScale) {
            case MONTH -> "Основной режим месяца уже использует реальные данные циклов. Пользователь может выбрать день и сразу увидеть базовую сводку по нему справа.";
            case DAY -> "Дневной режим пока остаётся каркасом. После завершения месяца сюда будет перенесена детальная таймлайн-лента событий.";
            case WEEK -> "Неделя пока сохранена как каркасный масштаб, чтобы не размывать реализацию до завершения рабочего режима месяца.";
            case YEAR -> "Годовой обзор пока возвращён к нейтральной обзорной сетке и не трогается до следующих этапов навигации.";
        };
    }

    private String summaryPeriod(HistoryScale currentScale) {
        return switch (currentScale) {
            case MONTH -> formatDay(selectedDate);
            case DAY -> capitalize(anchorDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", RU)));
            case WEEK -> {
                LocalDate start = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate end = start.plusDays(6);
                yield start.format(DateTimeFormatter.ofPattern("d.MM", RU))
                        + " - "
                        + end.format(DateTimeFormatter.ofPattern("d.MM.yyyy", RU));
            }
            case YEAR -> String.valueOf(anchorDate.getYear());
        };
    }

    private String summaryState(HistoryScale currentScale) {
        if (currentScale == HistoryScale.MONTH) {
            HistoryCalendarDayModel model = monthData.getOrDefault(selectedDate, new HistoryCalendarDayModel(selectedDate, 0, 0, 0));
            if (!model.hasActivity()) {
                return "Без запусков";
            }
            return model.cycleCount() + " циклов | проблемных " + model.problematicCount() + " | незавершённых " + model.activeCount();
        }
        return "Каркас этапа 2";
    }

    private String footnote(HistoryScale currentScale) {
        return switch (currentScale) {
            case MONTH -> "Месяц остаётся основным рабочим режимом истории: ячейки стали чище, а активность теперь читается через заливку и состояние дня.";
            case DAY -> "Режим дня ещё не наполнен данными по времени, но уже согласован как отдельный масштаб.";
            case WEEK -> "Недельный режим пока показывает только композицию будущей плотной навигации.";
            case YEAR -> "Годовой режим пока снова служит только обзорной сеткой без дополнительных усложнений.";
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
