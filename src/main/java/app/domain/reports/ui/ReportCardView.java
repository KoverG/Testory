package app.domain.reports.ui;

import app.core.CardNavigationBridge;
import app.core.Router;
import app.domain.reports.ReportsScreen;
import app.domain.reports.export.HtmlReportExporter;
import app.domain.reports.model.HistorySection;
import app.domain.reports.model.ReportData;
import app.domain.reports.model.ReportMetaSummary;
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
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ReportCardView {

    private static final List<String> STATUS_ORDER = List.of(
            "PASSED", "PASSED_WITH_BUGS", "FAILED", "CRITICAL_FAILED", "IN_PROGRESS", "SKIPPED"
    );
    private static final Map<String, String> STATUS_LABELS = Map.of(
            "PASSED", "Passed",
            "PASSED_WITH_BUGS", "Passed with bugs",
            "FAILED", "Failed",
            "CRITICAL_FAILED", "Critical failed",
            "IN_PROGRESS", "In progress",
            "SKIPPED", "Skipped"
    );

    private final ReportDataService dataService = new ReportDataService();
    private final HtmlReportExporter exporter = new HtmlReportExporter();
    private final Runnable onClose;

    private final StackPane root;
    private final Label lblTypeBadge = new Label();
    private final Label lblTitle = new Label();
    private final Button btnNavigate = new Button();
    private final Button btnReport;
    private final Label hintReport = new Label(" ");
    private final VBox stickyMetaBox = new VBox(8);
    private final VBox stickyRecommendationBox = new VBox(8);
    private final Region stickyMetaScrollbarGap = new Region();
    private final Region stickyRecommendationScrollbarGap = new Region();
    private final VBox scrollContent = new VBox(12);
    private final ScrollPane scroll;
    private final Region bottomSpacer;

    private ReportData currentData;
    private String currentRecommendation = "";

    public ReportCardView(Runnable onClose) {
        this.onClose = onClose;

        Button btnClose = new Button();
        btnClose.getStyleClass().addAll("icon-btn", "sm");
        btnClose.setFocusTraversable(false);
        UiSvg.setButtonSvg(btnClose, "close.svg", 14);
        btnClose.setOnAction(e -> {
            if (onClose != null) onClose.run();
        });

        lblTypeBadge.getStyleClass().add("rp-type-badge");
        lblTitle.getStyleClass().add("rp-title");
        lblTitle.setWrapText(true);

        btnNavigate.getStyleClass().addAll("icon-btn", "sm", "rp-navigate-btn");
        btnNavigate.setFocusTraversable(false);
        UiSvg.setButtonSvg(btnNavigate, "navigate.svg", 14);

        Region badgeSpacer = new Region();
        HBox.setHgrow(badgeSpacer, Priority.ALWAYS);
        HBox badgeRow = new HBox(8, lblTypeBadge, badgeSpacer, btnClose);
        badgeRow.setAlignment(Pos.CENTER_LEFT);

        HBox.setHgrow(lblTitle, Priority.ALWAYS);
        HBox titleRow = new HBox(8, btnNavigate, lblTitle);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(4, badgeRow, titleRow);
        header.getStyleClass().add("rp-header");
        header.setPadding(new Insets(0, 0, 4, 0));

        stickyMetaBox.getStyleClass().add("rp-sticky-block");
        stickyRecommendationBox.getStyleClass().add("rp-sticky-block");
        stickyMetaScrollbarGap.getStyleClass().add("rp-sticky-scroll-gap");
        stickyRecommendationScrollbarGap.getStyleClass().add("rp-sticky-scroll-gap");

        HBox stickyMetaRow = new HBox(stickyMetaBox, stickyMetaScrollbarGap);
        stickyMetaRow.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(stickyMetaBox, Priority.ALWAYS);

        HBox stickyRecommendationRow = new HBox(stickyRecommendationBox, stickyRecommendationScrollbarGap);
        stickyRecommendationRow.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(stickyRecommendationBox, Priority.ALWAYS);

        VBox stickyTopGroup = new VBox(6, header, stickyMetaRow);
        VBox stickyHeader = new VBox(12, stickyTopGroup, stickyRecommendationRow);
        stickyHeader.getStyleClass().add("rp-sticky-header");
        stickyHeader.setFillWidth(true);

        scrollContent.setPadding(new Insets(12, 0, 8, 0));

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

        VBox contentBox = new VBox(0, stickyHeader, scroll);
        contentBox.setMaxWidth(Double.MAX_VALUE);
        contentBox.setMaxHeight(Double.MAX_VALUE);

        btnReport = new Button("Отчёт");
        btnReport.getStyleClass().addAll("tc-filter-apply", "tc-save-btn", "tc-disabled-base");
        btnReport.setPrefWidth(250);
        btnReport.setFocusTraversable(false);

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

        StackPane innerOverlay = new StackPane(btnHintBox);
        innerOverlay.setPickOnBounds(false);
        innerOverlay.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(btnHintBox, Pos.BOTTOM_CENTER);
        StackPane.setMargin(btnHintBox, new Insets(0, 0, 12, 0));

        VBox overlay = new VBox(innerOverlay);
        overlay.setAlignment(Pos.BOTTOM_CENTER);
        overlay.setPickOnBounds(false);

        root = new StackPane(contentBox, overlay);
        root.setMaxWidth(Double.MAX_VALUE);
        root.setMaxHeight(Double.MAX_VALUE);

        String cssUrl = getClass().getResource("/ui/report-card.css").toExternalForm();
        root.getStylesheets().add(cssUrl);
        installStickyScrollbarGapBinding();
    }

    public StackPane view() {
        return root;
    }

    public void load(ReportTarget target) {
        currentRecommendation = "";
        scrollContent.getChildren().clear();
        stickyMetaBox.getChildren().clear();
        stickyRecommendationBox.getChildren().clear();
        lblTitle.setText("...");
        lblTypeBadge.setText("");

        currentData = dataService.build(target);
        applyHeader(currentData);
        buildScrollContent(currentData);
    }

    private void installStickyScrollbarGapBinding() {
        scroll.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(this::updateStickyScrollbarGap);
            }
        });
        scroll.skinProperty().addListener((obs, oldSkin, newSkin) -> Platform.runLater(this::updateStickyScrollbarGap));
    }

    private void updateStickyScrollbarGap() {
        double gap = 0;
        for (Node node : scroll.lookupAll(".scroll-bar:vertical")) {
            if (node instanceof ScrollBar bar) {
                gap = Math.max(gap, bar.prefWidth(-1));
            }
        }
        stickyMetaScrollbarGap.setPrefWidth(gap);
        stickyMetaScrollbarGap.setMinWidth(gap);
        stickyMetaScrollbarGap.setMaxWidth(gap);
        stickyRecommendationScrollbarGap.setPrefWidth(gap);
        stickyRecommendationScrollbarGap.setMinWidth(gap);
        stickyRecommendationScrollbarGap.setMaxWidth(gap);
    }

    private void applyHeader(ReportData data) {
        String typeText = data.target().type() == ReportTargetType.TEST_CASE ? "КЕЙС" : "ЦИКЛ";
        lblTypeBadge.setText(typeText);
        lblTitle.setText(data.title().isBlank() ? data.target().id() : data.title());
        buildStickySections(data);
        btnReport.setOnAction(e -> exporter.export(
                data,
                normalizeRecommendationForExport(currentRecommendation),
                msg -> Platform.runLater(() -> btnReport.setText("!"))
        ));

        if (data.target().type() == ReportTargetType.CYCLE) {
            btnNavigate.setOnAction(e -> {
                ReportsScreen.setPendingRestore(data.target());
                CardNavigationBridge.requestCycleHistoryNavigation(data.target().id(), "");
                Router.get().cycles();
            });
            boolean blocked = currentRecommendation.isBlank();
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

    private void buildStickySections(ReportData data) {
        stickyMetaBox.getChildren().clear();
        stickyRecommendationBox.getChildren().clear();

        Node meta = buildStickyMetaBlock(data);
        if (meta != null) {
            stickyMetaBox.getChildren().add(meta);
        }

        if (data.target().type() == ReportTargetType.CYCLE) {
            stickyRecommendationBox.getChildren().add(buildRecommendationSection());
        }
    }

    private void buildScrollContent(ReportData data) {
        data.<StatusSummarySection>section(StatusSummarySection.TYPE)
                .ifPresent(s -> scrollContent.getChildren().add(buildSummarySection(s)));

        data.<HistorySection>section(HistorySection.TYPE)
                .ifPresent(s -> scrollContent.getChildren().add(buildHistorySection(s)));

        scrollContent.getChildren().add(bottomSpacer);
    }

    private Node buildStickyMetaBlock(ReportData data) {
        if (data.target().type() == ReportTargetType.CYCLE && data.metaSummary() != null) {
            TrendSection trend = data.<TrendSection>section(TrendSection.TYPE).orElse(null);
            return buildCycleMetaBlock(data.metaSummary(), trend);
        }
        return buildCaseMetaBlock(data);
    }

    private Node buildCycleMetaBlock(ReportMetaSummary meta, TrendSection trend) {
        VBox box = new VBox(10);
        box.getStyleClass().add("rp-meta-surface");

        if (meta.hasContext()) {
            HBox chipsRow = new HBox(8);
            chipsRow.setAlignment(Pos.CENTER_LEFT);
            chipsRow.getStyleClass().add("rp-meta-chip-row");
            addChipIfPresent(chipsRow, meta.category(), null);
            addChipIfPresent(chipsRow, meta.environment(), null);
            addTaskChipIfPresent(chipsRow, "Задача: " + meta.taskLabel(), meta.taskUrl());
            if (!chipsRow.getChildren().isEmpty()) {
                box.getChildren().add(chipsRow);
            }
        }

        HBox timingRow = buildMetricRow(
                new MetaMetric("Начат", meta.startedAt(), false, ""),
                new MetaMetric(meta.lifecycleLabel(), meta.lifecycleValue(), false, ""),
                new MetaMetric("Длительность", meta.duration(), true, meta.durationFull())
        );
        if (!timingRow.getChildren().isEmpty()) {
            box.getChildren().add(timingRow);
        }

        HBox progressRow = buildMetricRow(
                new MetaMetric("Всего кейсов", String.valueOf(meta.totalCases()), true, ""),
                new MetaMetric("Пройдено кейсов", String.valueOf(meta.completedCases()), true, ""),
                new MetaMetric("Прогресс", meta.completionPercent() + "%", true, "")
        );
        if (trend != null && !trend.capsules().isEmpty()) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox capsules = new HBox(8);
            capsules.setAlignment(Pos.CENTER_RIGHT);
            capsules.getStyleClass().add("rp-meta-capsules");
            for (TrendCapsule cap : trend.capsules()) {
                capsules.getChildren().add(buildCapsule(cap));
            }
            progressRow.getChildren().addAll(spacer, capsules);
        }
        if (!progressRow.getChildren().isEmpty()) {
            box.getChildren().add(progressRow);
        }

        return box.getChildren().isEmpty() ? null : box;
    }

    private Node buildCaseMetaBlock(ReportData data) {
        VBox box = new VBox(8);
        box.getStyleClass().add("rp-meta-block");

        if (!data.subtitle().isBlank()) {
            Label sub = new Label(data.subtitle());
            sub.getStyleClass().addAll("rp-subtitle", "rp-meta-subtitle");
            sub.setWrapText(true);
            box.getChildren().add(sub);
        }

        if (!data.lastRunDate().isBlank()) {
            HBox lastRunRow = buildMetricRow(new MetaMetric("Последнее прохождение", data.lastRunDate(), false, ""));
            box.getChildren().add(lastRunRow);
        }

        return box.getChildren().isEmpty() ? null : box;
    }

    private static HBox buildMetricRow(MetaMetric... metrics) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("rp-meta-inline-row");

        boolean first = true;
        for (MetaMetric metric : metrics) {
            if (metric == null || metric.isEmpty()) continue;
            if (!first) {
                row.getChildren().add(buildMetaSeparator());
            }
            row.getChildren().add(buildMetaMetric(metric.label(), metric.value(), metric.accentValue(), metric.tooltipText()));
            first = false;
        }
        return row;
    }

    private static Label buildMetaSeparator() {
        Label sep = new Label("|");
        sep.getStyleClass().add("rp-meta-separator");
        return sep;
    }

    private static void addChipIfPresent(HBox row, String text, String tooltipText) {
        if (text == null || text.isBlank()) return;
        Label chip = new Label(text);
        chip.getStyleClass().add("rp-meta-chip");
        if (tooltipText != null && !tooltipText.isBlank()) {
            Tooltip.install(chip, new Tooltip(tooltipText));
        }
        row.getChildren().add(chip);
    }

    private void addTaskChipIfPresent(HBox row, String text, String url) {
        if (text == null || text.isBlank()) return;
        Button chip = new Button(text);
        chip.setFocusTraversable(false);
        chip.getStyleClass().addAll("rp-meta-chip", "rp-meta-chip-link");
        if (url != null && !url.isBlank()) {
            chip.setOnAction(e -> openExternalUrl(url));
            chip.setTooltip(new Tooltip(url));
        } else {
            chip.setMouseTransparent(true);
        }
        row.getChildren().add(chip);
    }

    private static Node buildMetaMetric(String label, String value, boolean accentValue, String tooltipText) {
        HBox item = new HBox(6);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("rp-meta-item");

        if (label != null && !label.isBlank()) {
            Label labelNode = new Label(label);
            labelNode.getStyleClass().add("rp-meta-label");
            item.getChildren().add(labelNode);
        }

        if (value != null && !value.isBlank()) {
            Label valueNode = new Label(value);
            valueNode.getStyleClass().addAll("rp-meta-value", accentValue ? "rp-meta-value-strong" : "rp-meta-value-soft");
            if (tooltipText != null && !tooltipText.isBlank()) {
                Tooltip.install(valueNode, new Tooltip(tooltipText));
            }
            item.getChildren().add(valueNode);
        }

        return item;
    }

    private Node buildCapsule(TrendCapsule cap) {
        Label lbl = new Label(String.valueOf(cap.count()));
        lbl.setAlignment(Pos.CENTER);
        lbl.setMouseTransparent(true);

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

    private Node buildSummarySection(StatusSummarySection section) {
        VBox box = sectionBox("Сводка");

        DonutChart donut = new DonutChart();
        List<DonutChart.Slice> slices = new ArrayList<>();
        for (String status : STATUS_ORDER) {
            int count = section.countsByStatus().getOrDefault(status, 0);
            if (count > 0) {
                slices.add(new DonutChart.Slice(statusToColorKey(status), count));
            }
        }
        donut.draw(slices, section.total());

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

            HBox metrics = new HBox(8);
            metrics.getStyleClass().add("rp-legend-metrics");
            metrics.setAlignment(Pos.CENTER_RIGHT);
            HBox.setHgrow(metrics, Priority.ALWAYS);

            Region metricsSpacer = new Region();
            HBox.setHgrow(metricsSpacer, Priority.ALWAYS);

            Label countLbl = new Label(String.valueOf(count));
            countLbl.getStyleClass().add("rp-stat-val");
            countLbl.setMinWidth(24);
            countLbl.setAlignment(Pos.CENTER_RIGHT);

            Label pctLbl = new Label(String.format("%.0f%%", pct));
            pctLbl.getStyleClass().addAll("rp-stat-val", "rp-stat-pct");
            pctLbl.setMinWidth(36);
            pctLbl.setAlignment(Pos.CENTER_RIGHT);

            metrics.getChildren().addAll(metricsSpacer, countLbl, pctLbl);
            row.getChildren().addAll(badge, metrics);
            legend.getChildren().add(row);
        }

        HBox inner = new HBox(16, donut, legend);
        inner.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(legend, Priority.ALWAYS);

        box.getChildren().add(inner);
        return box;
    }

    private Node buildHistorySection(HistorySection section) {
        if (section.rows().isEmpty()) return new Region();

        VBox box = sectionBox("История");
        for (HistorySection.HistoryRow row : section.rows()) {
            box.getChildren().add(buildHistoryRow(row));
        }
        return box;
    }

    private Node buildHistoryRow(HistorySection.HistoryRow row) {
        VBox box = new VBox(3);
        box.getStyleClass().add("rp-history-row");

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
            case "PASSED" -> "rp-badge-passed";
            case "PASSED_WITH_BUGS" -> "rp-badge-bugs";
            case "FAILED" -> "rp-badge-failed";
            case "CRITICAL_FAILED" -> "rp-badge-critical";
            case "SKIPPED" -> "rp-badge-skipped";
            case "IN_PROGRESS" -> "rp-badge-progress";
            default -> "rp-badge-unknown";
        };
    }

    private static String statusToColorKey(String status) {
        return switch (status == null ? "" : status) {
            case "PASSED" -> "passed";
            case "PASSED_WITH_BUGS" -> "bugs";
            case "FAILED", "CRITICAL_FAILED" -> "failed";
            default -> "unknown";
        };
    }

    private static String capsuleColorClass(String colorKey) {
        return switch (colorKey == null ? "" : colorKey) {
            case "passed" -> "rp-cap-passed";
            case "bugs" -> "rp-cap-bugs";
            case "failed" -> "rp-cap-failed";
            default -> "rp-cap-unknown";
        };
    }

    private static String capsuleLabel(String colorKey) {
        return switch (colorKey == null ? "" : colorKey) {
            case "passed" -> "Passed";
            case "bugs" -> "With bugs";
            case "failed" -> "Failed";
            default -> colorKey;
        };
    }

    private void updateReportHint(boolean blocked) {
        if (!blocked) {
            hintReport.setText(" ");
            hintReport.setOpacity(0.0);
        } else {
            hintReport.setText("Выберите решение для создания отчёта");
            hintReport.setOpacity(1.0);
        }
    }

    private Node buildRecommendationSection() {
        VBox box = sectionBox("Решение");

        List<RecommendationChip> chips = List.of(
                new RecommendationChip("none", "Без решения", "rp-recommend-chip-none-active"),
                new RecommendationChip("recommended", "Рекомендован", "rp-recommend-chip-recommended"),
                new RecommendationChip("needs_work", "Требует доработки", "rp-recommend-chip-needs-work"),
                new RecommendationChip("not_recommended", "Не рекомендован", "rp-recommend-chip-not-recommended")
        );

        FlowPane chipsPane = new FlowPane(8, 8);
        for (RecommendationChip chip : chips) {
            Button btn = new Button(chip.label());
            btn.setFocusTraversable(false);
            applyRecommendationChipState(btn, chip, chip.value().equals(currentRecommendation));
            btn.setOnAction(e -> {
                currentRecommendation = chip.value();
                btnReport.setDisable(false);
                updateReportHint(false);
                buildStickySections(currentData);
            });
            chipsPane.getChildren().add(btn);
        }

        box.getChildren().add(chipsPane);
        return box;
    }

    private static void applyRecommendationChipState(Button btn, RecommendationChip chip, boolean active) {
        btn.getStyleClass().removeIf(c -> c.startsWith("rp-recommend-chip-"));
        btn.getStyleClass().add("rp-recommend-chip");
        if (active) {
            btn.getStyleClass().add(chip.activeClass());
        } else {
            btn.getStyleClass().add("rp-recommend-chip-none");
        }
    }

    private static String normalizeRecommendationForExport(String recommendation) {
        if (recommendation == null || recommendation.isBlank() || "none".equals(recommendation)) {
            return "";
        }
        return recommendation;
    }

    private static void openExternalUrl(String url) {
        if (url == null || url.isBlank() || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignore) {
        }
    }

    private record RecommendationChip(String value, String label, String activeClass) {}
    private record MetaMetric(String label, String value, boolean accentValue, String tooltipText) {
        boolean isEmpty() {
            return (label == null || label.isBlank()) && (value == null || value.isBlank());
        }
    }
}
