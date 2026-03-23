package app.domain.reports.ui;

import app.core.CardNavigationBridge;
import app.core.Router;
import app.domain.cycles.repo.CycleCardJsonReader;
import app.domain.cycles.repo.CycleJson;
import app.domain.cycles.usecase.CycleDraft;
import app.domain.reports.ReportsScreen;
import app.domain.reports.export.HtmlReportExporter;
import app.domain.reports.model.HistorySection;
import app.domain.reports.model.ReportData;
import app.domain.reports.model.ReportTarget;
import app.domain.reports.model.ReportTargetType;
import app.domain.reports.model.StatusSummarySection;
import app.domain.reports.model.TrendSection;
import app.domain.reports.model.TrendSection.TrendCapsule;
import app.domain.reports.service.ReportDataService;
import app.ui.UiSvg;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Правая зона экрана Reports.
 * Структура: StackPane = VBox(шапка + скролл) + оверлей с кнопкой Отчёт внизу.
 * view() возвращает StackPane, который добавляется в rightPlaceholder.
 */
public final class ReportCardView {

    private static final int MAX_HISTORY_ROWS = 20;

    // Порядок статусов для сводки
    private static final List<String> STATUS_ORDER = List.of(
            "PASSED", "PASSED_WITH_BUGS", "FAILED", "CRITICAL_FAILED", "IN_PROGRESS", "SKIPPED"
    );
    private static final Map<String, String> STATUS_LABELS = Map.of(
            "PASSED",           "Passed",
            "PASSED_WITH_BUGS", "Passed with bugs",
            "FAILED",           "Failed",
            "CRITICAL_FAILED",  "Critical failed",
            "IN_PROGRESS",      "In progress",
            "SKIPPED",          "Skipped"
    );

    private final ReportDataService dataService = new ReportDataService();
    private final HtmlReportExporter exporter   = new HtmlReportExporter();
    private final Runnable onClose;

    // корневой StackPane: VBox с контентом + оверлей с кнопкой Отчёт
    private final StackPane root;
    // шапка
    private final Label lblTypeBadge = new Label();
    private final Label lblTitle     = new Label();
    private final Button btnNavigate = new Button();
    private final Button btnReport;
    private final Label hintReport  = new Label(" ");
    // scroll content
    private final VBox scrollContent = new VBox(12);
    private final ScrollPane scroll;
    private final Region bottomSpacer;

    public ReportCardView(Runnable onClose) {
        this.onClose = onClose;

        // --- Кнопка закрытия ---
        Button btnClose = new Button();
        btnClose.getStyleClass().addAll("icon-btn", "sm");
        btnClose.setFocusTraversable(false);
        UiSvg.setButtonSvg(btnClose, "close.svg", 14);
        btnClose.setOnAction(e -> { if (onClose != null) onClose.run(); });

        // --- Шапка ---
        lblTypeBadge.getStyleClass().addAll("rp-type-badge");
        lblTitle.getStyleClass().add("rp-title");
        lblTitle.setWrapText(true);

        btnNavigate.getStyleClass().addAll("icon-btn", "sm", "rp-navigate-btn");
        btnNavigate.setFocusTraversable(false);
        UiSvg.setButtonSvg(btnNavigate, "navigate.svg", 14);

        // Верхняя строка шапки: шильдик + пространство + кнопка закрытия
        Region badgeSpacer = new Region();
        HBox.setHgrow(badgeSpacer, Priority.ALWAYS);
        HBox badgeRow = new HBox(8);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        badgeRow.getChildren().addAll(lblTypeBadge, badgeSpacer, btnClose);

        // Нижняя строка шапки: кнопка навигации + заголовок
        HBox.setHgrow(lblTitle, Priority.ALWAYS);
        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.getChildren().addAll(btnNavigate, lblTitle);

        VBox header = new VBox(4, badgeRow, titleRow);
        header.setPadding(new Insets(0, 0, 12, 0));

        // --- Scroll content ---
        scrollContent.setPadding(new Insets(0, 0, 8, 0));

        // Спейсер в конце скролла — чтобы контент можно было проскролить мимо кнопки-оверлея
        bottomSpacer = new Region();
        bottomSpacer.setMinHeight(80);
        bottomSpacer.setPrefHeight(80);
        bottomSpacer.setMaxHeight(80);

        scroll = new ScrollPane(scrollContent);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().addAll("tc-filter-scroll", "rp-card-scroll");
        scroll.setFocusTraversable(false);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox contentBox = new VBox(0, header, scroll);
        contentBox.setMaxWidth(Double.MAX_VALUE);
        contentBox.setMaxHeight(Double.MAX_VALUE);

        // --- Кнопка Отчёт (оверлей внизу) ---
        btnReport = new Button("Отчёт");
        btnReport.getStyleClass().addAll("tc-filter-apply", "tc-save-btn", "tc-disabled-base");
        btnReport.setPrefWidth(250);
        btnReport.setFocusTraversable(false);

        // Подсказка под кнопкой (показывается при блокировке)
        hintReport.getStyleClass().add("tc-save-hint");
        hintReport.setWrapText(true);
        hintReport.setMouseTransparent(true);
        hintReport.setAlignment(Pos.CENTER);
        hintReport.setMaxWidth(Double.MAX_VALUE);
        hintReport.setOpacity(0.0);

        VBox btnHintBox = new VBox(4, btnReport, hintReport);
        btnHintBox.setAlignment(Pos.CENTER);
        btnHintBox.setPickOnBounds(false);
        btnHintBox.setMaxWidth(Double.MAX_VALUE);

        // Внутренний StackPane — занимает полную ширину, внутри позиционирует кнопку+подсказку
        StackPane innerOverlay = new StackPane(btnHintBox);
        innerOverlay.setPickOnBounds(false);
        innerOverlay.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(btnHintBox, Pos.BOTTOM_CENTER);
        StackPane.setMargin(btnHintBox, new Insets(0, 0, 12, 0));

        // Внешний VBox — занимает весь StackPane, выравнивает внутренний StackPane к низу
        VBox overlay = new VBox(innerOverlay);
        overlay.setAlignment(Pos.BOTTOM_CENTER);
        overlay.setPickOnBounds(false);

        // --- Корень ---
        root = new StackPane(contentBox, overlay);
        root.setMaxWidth(Double.MAX_VALUE);
        root.setMaxHeight(Double.MAX_VALUE);

        String cssUrl = getClass().getResource("/ui/report-card.css").toExternalForm();
        root.getStylesheets().add(cssUrl);
    }

    public StackPane view() { return root; }

    public void load(ReportTarget target) {
        scrollContent.getChildren().clear();
        lblTitle.setText("...");
        lblTypeBadge.setText("");

        ReportData data = dataService.build(target);
        applyHeader(data);
        buildScrollContent(data);
    }

    // ===== шапка =====

    private void applyHeader(ReportData data) {
        String typeText = data.target().type() == ReportTargetType.TEST_CASE ? "КЕЙС" : "ЦИКЛ";
        lblTypeBadge.setText(typeText);
        lblTitle.setText(data.title().isBlank() ? data.target().id() : data.title());
        btnReport.setOnAction(e -> exporter.export(data,
                msg -> Platform.runLater(() -> btnReport.setText("!"))));

        if (data.target().type() == ReportTargetType.CYCLE) {
            btnNavigate.setOnAction(e -> {
                ReportsScreen.setPendingRestore(data.target());
                CardNavigationBridge.requestCycleHistoryNavigation(data.target().id(), "");
                Router.get().cycles();
            });
            boolean blocked = readRecommendation(data.target()).isEmpty();
            btnReport.setDisable(blocked);
            updateReportHint(blocked);
        } else {
            btnNavigate.setOnAction(e -> {
                ReportsScreen.setPendingRestore(data.target());
                CardNavigationBridge.requestCaseRestore(data.target().id());
                Router.get().testCases();
            });
            btnReport.setDisable(false);
            updateReportHint(false);
        }
    }

    // ===== scroll content =====

    private void buildScrollContent(ReportData data) {
        // subtitle (теги/описание)
        if (!data.subtitle().isBlank()) {
            Label sub = new Label(data.subtitle());
            sub.getStyleClass().add("rp-subtitle");
            sub.setWrapText(true);
            scrollContent.getChildren().add(sub);
        }

        // блок дат
        scrollContent.getChildren().add(buildDateBlock(data));

        // тренд ВЫШЕ сводки
        data.<TrendSection>section(TrendSection.TYPE)
                .ifPresent(s -> scrollContent.getChildren().add(buildTrendSection(s)));

        // сводка (donut)
        data.<StatusSummarySection>section(StatusSummarySection.TYPE)
                .ifPresent(s -> scrollContent.getChildren().add(buildSummarySection(s)));

        // история
        data.<HistorySection>section(HistorySection.TYPE)
                .ifPresent(s -> scrollContent.getChildren().add(buildHistorySection(s)));

        // рекомендация (только для цикла)
        if (data.target().type() == ReportTargetType.CYCLE) {
            String rec = readRecommendation(data.target());
            scrollContent.getChildren().add(buildRecommendationSection(rec, data.target()));
        }

        // спейсер для прокрутки мимо кнопки-оверлея
        scrollContent.getChildren().add(bottomSpacer);
    }

    // ===== блок дат =====

    private Node buildDateBlock(ReportData data) {
        VBox box = new VBox(4);

        if (data.target().type() == ReportTargetType.CYCLE) {
            if (!data.startedAt().isBlank()) {
                box.getChildren().add(dateRow("Начат:", data.startedAt()));
            }
            if (!data.finishedAt().isBlank()) {
                box.getChildren().add(dateRow("Завершён:", data.finishedAt()));
            }
        } else {
            if (!data.lastRunDate().isBlank()) {
                box.getChildren().add(dateRow("Последнее прохождение:", data.lastRunDate()));
            }
        }

        return box;
    }

    private static Node dateRow(String label, String value) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("rp-date-label");
        Label val = new Label(value);
        val.getStyleClass().add("rp-date-value");
        row.getChildren().addAll(lbl, val);
        return row;
    }

    // ===== тренд (капсулы) =====

    private Node buildTrendSection(TrendSection section) {
        if (section.capsules().isEmpty()) return new Region();

        VBox box = sectionBox("Тренд");

        HBox capsuleRow = new HBox(8);
        capsuleRow.setAlignment(Pos.CENTER_LEFT);

        for (TrendCapsule cap : section.capsules()) {
            capsuleRow.getChildren().add(buildCapsule(cap));
        }

        box.getChildren().add(capsuleRow);
        return box;
    }

    private Node buildCapsule(TrendCapsule cap) {
        Label lbl = new Label(String.valueOf(cap.count()));
        lbl.setAlignment(Pos.CENTER);

        String colorClass = capsuleColorClass(cap.colorKey());

        if (cap.wide()) {
            lbl.getStyleClass().addAll("rp-capsule", "rp-capsule-wide", colorClass);
        } else {
            lbl.getStyleClass().addAll("rp-capsule", "rp-capsule-round", colorClass);
        }

        String tip = capsuleLabel(cap.colorKey()) + ": " + cap.count();
        Tooltip.install(lbl, new Tooltip(tip));
        return lbl;
    }

    private static String capsuleColorClass(String colorKey) {
        return switch (colorKey == null ? "" : colorKey) {
            case "passed" -> "rp-cap-passed";
            case "bugs"   -> "rp-cap-bugs";
            case "failed" -> "rp-cap-failed";
            default       -> "rp-cap-unknown";
        };
    }

    private static String capsuleLabel(String colorKey) {
        return switch (colorKey == null ? "" : colorKey) {
            case "passed" -> "Passed";
            case "bugs"   -> "With bugs";
            case "failed" -> "Failed";
            default       -> colorKey;
        };
    }

    // ===== сводка (donut) =====

    private Node buildSummarySection(StatusSummarySection section) {
        VBox box = sectionBox("Сводка");

        // Donut chart
        DonutChart donut = new DonutChart();
        List<DonutChart.Slice> slices = new ArrayList<>();
        for (String status : STATUS_ORDER) {
            int count = section.countsByStatus().getOrDefault(status, 0);
            if (count > 0) {
                slices.add(new DonutChart.Slice(statusToColorKey(status), count));
            }
        }
        donut.draw(slices, section.total());

        // Легенда: [бейдж] [····лидер-линия····] [count] [pct-muted]
        VBox legend = new VBox(0);
        for (String status : STATUS_ORDER) {
            int count = section.countsByStatus().getOrDefault(status, 0);
            if (count == 0) continue;

            double pct = section.total() > 0 ? (count * 100.0 / section.total()) : 0;

            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(5, 0, 5, 0));

            Label badge = statusBadge(status);
            badge.setMinWidth(108);

            Region leader = new Region();
            leader.getStyleClass().add("rp-legend-leader");
            HBox.setHgrow(leader, Priority.ALWAYS);

            Label countLbl = new Label(String.valueOf(count));
            countLbl.getStyleClass().add("rp-stat-val");
            countLbl.setMinWidth(24);
            countLbl.setAlignment(Pos.CENTER_RIGHT);

            Label pctLbl = new Label(String.format("%.0f%%", pct));
            pctLbl.getStyleClass().addAll("rp-stat-val", "rp-stat-pct");
            pctLbl.setMinWidth(36);
            pctLbl.setAlignment(Pos.CENTER_RIGHT);

            row.getChildren().addAll(badge, leader, countLbl, pctLbl);
            legend.getChildren().add(row);
        }

        // Компоновка: donut слева, легенда справа
        HBox inner = new HBox(16);
        inner.setAlignment(Pos.CENTER_LEFT);
        inner.getChildren().addAll(donut, legend);
        HBox.setHgrow(legend, Priority.ALWAYS);  // легенда занимает остаток

        box.getChildren().add(inner);
        return box;
    }

    // ===== история =====

    private Node buildHistorySection(HistorySection section) {
        if (section.rows().isEmpty()) return new Region();

        VBox box = sectionBox("История");

        int shown = Math.min(section.rows().size(), MAX_HISTORY_ROWS);
        for (int i = 0; i < shown; i++) {
            box.getChildren().add(buildHistoryRow(section.rows().get(i)));
        }

        if (section.rows().size() > MAX_HISTORY_ROWS) {
            Label more = new Label("... ещё " + (section.rows().size() - MAX_HISTORY_ROWS));
            more.getStyleClass().add("rp-subtitle");
            box.getChildren().add(more);
        }

        return box;
    }

    private Node buildHistoryRow(HistorySection.HistoryRow row) {
        VBox box = new VBox(3);
        box.getStyleClass().add("rp-history-row");

        // BorderPane: badge слева, дата справа, название по центру (обрезается с ...)
        Label badge = statusBadge(row.status());

        String titleText = row.title().isBlank() ? row.contextLabel() : row.title();
        Label title = new Label(titleText);
        title.getStyleClass().add("rp-history-title");
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setMinWidth(0);

        Label date = new Label(row.dateLabel());
        date.getStyleClass().add("rp-history-context");
        date.setMinWidth(Region.USE_PREF_SIZE);
        date.setAlignment(Pos.CENTER_RIGHT);

        BorderPane top = new BorderPane();
        top.setLeft(badge);
        top.setCenter(title);
        top.setRight(date);
        BorderPane.setAlignment(badge, Pos.CENTER_LEFT);
        BorderPane.setAlignment(title, Pos.CENTER_LEFT);
        BorderPane.setAlignment(date, Pos.CENTER_RIGHT);
        BorderPane.setMargin(title, new Insets(0, 8, 0, 8));

        box.getChildren().add(top);

        String context = row.title().isBlank() ? "" : row.contextLabel();
        if (!context.isBlank()) {
            Label ctx = new Label(context);
            ctx.getStyleClass().add("rp-history-context");
            box.getChildren().add(ctx);
        }

        if (!row.comment().isBlank()) {
            Label comment = new Label(row.comment());
            comment.getStyleClass().add("rp-history-comment");
            box.getChildren().add(comment);
        }

        return box;
    }

    // ===== утилиты =====

    private static VBox sectionBox(String sectionTitle) {
        VBox box = new VBox(8);
        box.getStyleClass().add("rp-section");

        Label lbl = new Label(sectionTitle.toUpperCase());
        lbl.getStyleClass().add("rp-section-title");
        box.getChildren().add(lbl);

        return box;
    }

    private static Label statusBadge(String status) {
        boolean blank = status == null || status.isBlank();
        String label = blank ? "Не начат" : STATUS_LABELS.getOrDefault(status, status);
        Label badge = new Label(label);
        badge.getStyleClass().addAll("rp-badge", badgeCssClass(status));
        return badge;
    }

    private static String badgeCssClass(String status) {
        if (status == null || status.isBlank()) return "rp-badge-none";
        return switch (status) {
            case "PASSED"           -> "rp-badge-passed";
            case "PASSED_WITH_BUGS" -> "rp-badge-bugs";
            case "FAILED"           -> "rp-badge-failed";
            case "CRITICAL_FAILED"  -> "rp-badge-critical";
            case "SKIPPED"          -> "rp-badge-skipped";
            case "IN_PROGRESS"      -> "rp-badge-progress";
            default                 -> "rp-badge-unknown";
        };
    }

    private static String statusToColorKey(String status) {
        return switch (status == null ? "" : status) {
            case "PASSED"           -> "passed";
            case "PASSED_WITH_BUGS" -> "bugs";
            case "FAILED"           -> "failed";
            case "CRITICAL_FAILED"  -> "critical";
            case "SKIPPED"          -> "skipped";
            case "IN_PROGRESS"      -> "progress";
            default                 -> "unknown";
        };
    }

    // ===== рекомендация =====

    private void updateReportHint(boolean blocked) {
        if (!blocked) {
            hintReport.setText(" ");
            hintReport.setOpacity(0.0);
        } else {
            hintReport.setText("Выберите решение для создания отчёта");
            hintReport.setOpacity(1.0);
        }
    }

    private String readRecommendation(ReportTarget target) {
        if (target.file() == null) return "";
        CycleDraft draft = CycleCardJsonReader.readDraft(target.file());
        if (draft == null) return "";
        return draft.recommendation != null ? draft.recommendation : "";
    }

    private Node buildRecommendationSection(String currentValue, ReportTarget target) {
        VBox box = sectionBox("Решение");

        record Chip(String value, String label, String activeClass) {}
        List<Chip> chips = List.of(
                new Chip("none",            "Без решения",       "rp-recommend-chip-none-active"),
                new Chip("recommended",     "Рекомендован",      "rp-recommend-chip-recommended"),
                new Chip("needs_work",      "Требует доработки", "rp-recommend-chip-needs-work"),
                new Chip("not_recommended", "Не рекомендован",   "rp-recommend-chip-not-recommended")
        );

        Button[] btns = new Button[chips.size()];
        for (int i = 0; i < chips.size(); i++) {
            Chip chip = chips.get(i);
            Button btn = new Button(chip.label());
            btn.getStyleClass().addAll("rp-recommend-chip",
                    chip.value().equals(currentValue) ? chip.activeClass() : "rp-recommend-chip-none");
            btn.setFocusTraversable(false);
            btns[i] = btn;
        }

        for (int i = 0; i < chips.size(); i++) {
            final int idx = i;
            final Chip chip = chips.get(i);
            btns[i].setOnAction(e -> {
                for (int j = 0; j < chips.size(); j++) {
                    btns[j].getStyleClass().removeIf(c -> c.startsWith("rp-recommend-chip-"));
                    btns[j].getStyleClass().add("rp-recommend-chip-none");
                }
                btns[idx].getStyleClass().remove("rp-recommend-chip-none");
                btns[idx].getStyleClass().add(chip.activeClass());
                saveRecommendation(target, chip.value());
                btnReport.setDisable(false);
                updateReportHint(false);
            });
        }

        FlowPane chipsPane = new FlowPane(8, 8);
        chipsPane.getChildren().addAll(btns);
        box.getChildren().add(chipsPane);
        return box;
    }

    private void saveRecommendation(ReportTarget target, String value) {
        if (target.file() == null) return;
        CycleDraft draft = CycleCardJsonReader.readDraft(target.file());
        if (draft == null) return;
        draft.recommendation = value != null ? value : "";
        try {
            Files.writeString(target.file(), CycleJson.toJson(draft), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
