package app.domain.reports.ui;

import app.core.CardNavigationBridge;
import app.core.I18n;
import app.core.Router;
import app.domain.cycles.CaseStatusDefinition;
import app.domain.cycles.CaseStatusRegistry;
import app.domain.reports.ReportsScreen;
import app.domain.reports.export.HtmlReportExporter;
import app.domain.cycles.ui.right.CaseCommentModal;
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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ReportCardView {

    private static final String HISTORY_FILTER_ALL = "__ALL__";
    private static final String HISTORY_FILTER_NOT_STARTED = "__NOT_STARTED__";
    private static final int COMMENT_PREVIEW_LIMIT = 30;
    private final ReportDataService dataService = new ReportDataService();
    private final HtmlReportExporter exporter = new HtmlReportExporter();
    private final Runnable onClose;

    private final StackPane root;
    private final Label lblTypeBadge = new Label();
    private final Label lblTitle = new Label();
    private final Button btnTaskLink = new Button();
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
    private String currentHistoryFilter = HISTORY_FILTER_ALL;

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

        btnTaskLink.getStyleClass().addAll("icon-btn", "sm", "rp-task-link-btn", "rp-task-link-btn-lg");
        btnTaskLink.setFocusTraversable(false);
        btnTaskLink.setVisible(false);
        btnTaskLink.setManaged(false);
        UiSvg.setButtonSvg(btnTaskLink, "link-open.svg", 14);

        btnNavigate.getStyleClass().addAll("icon-btn", "sm", "rp-navigate-btn");
        btnNavigate.setFocusTraversable(false);
        UiSvg.setButtonSvg(btnNavigate, "navigate.svg", 14);

        Region badgeSpacer = new Region();
        HBox.setHgrow(badgeSpacer, Priority.ALWAYS);
        HBox badgeRow = new HBox(8, lblTypeBadge, badgeSpacer, btnClose);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        badgeRow.setMinWidth(0);

        HBox.setHgrow(lblTitle, Priority.ALWAYS);
        HBox titleRow = new HBox(8, btnTaskLink, btnNavigate, lblTitle);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setMinWidth(0);

        VBox header = new VBox(4, badgeRow, titleRow);
        header.getStyleClass().add("rp-header");
        header.setPadding(new Insets(0, 0, 4, 0));
        header.setMinWidth(0);

        stickyMetaBox.getStyleClass().add("rp-sticky-block");
        stickyRecommendationBox.getStyleClass().add("rp-sticky-block");
        stickyMetaBox.setMinWidth(0);
        stickyRecommendationBox.setMinWidth(0);
        stickyMetaScrollbarGap.getStyleClass().add("rp-sticky-scroll-gap");
        stickyRecommendationScrollbarGap.getStyleClass().add("rp-sticky-scroll-gap");

        HBox stickyMetaRow = new HBox(stickyMetaBox, stickyMetaScrollbarGap);
        stickyMetaRow.setAlignment(Pos.TOP_LEFT);
        stickyMetaRow.setMinWidth(0);
        HBox.setHgrow(stickyMetaBox, Priority.ALWAYS);

        HBox stickyRecommendationRow = new HBox(stickyRecommendationBox, stickyRecommendationScrollbarGap);
        stickyRecommendationRow.setAlignment(Pos.TOP_LEFT);
        stickyRecommendationRow.setMinWidth(0);
        HBox.setHgrow(stickyRecommendationBox, Priority.ALWAYS);

        VBox stickyTopGroup = new VBox(6, header, stickyMetaRow);
        stickyTopGroup.setMinWidth(0);
        VBox stickyHeader = new VBox(12, stickyTopGroup, stickyRecommendationRow);
        stickyHeader.getStyleClass().add("rp-sticky-header");
        stickyHeader.setFillWidth(true);
        stickyHeader.setMinWidth(0);

        scrollContent.setPadding(new Insets(12, 0, 8, 0));
        scrollContent.setMinWidth(0);

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
        contentBox.setMinWidth(0);
        contentBox.setMaxWidth(Double.MAX_VALUE);
        contentBox.setMaxHeight(Double.MAX_VALUE);

        btnReport = new Button(tr("cy.menu.report", "Отчёт"));
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
        root.setMinWidth(0);
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
        currentHistoryFilter = HISTORY_FILTER_ALL;
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
        String typeText = data.target().type() == ReportTargetType.TEST_CASE
                ? tr("rp.type.case", "Кейс").toUpperCase()
                : tr("rp.type.cycle", "Цикл").toUpperCase();
        lblTypeBadge.setText(typeText);
        lblTitle.setText(data.title().isBlank() ? data.target().id() : data.title());
        applyTaskLinkHeaderButton(data);
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

    private void rebuildScrollContent() {
        scrollContent.getChildren().clear();
        if (currentData != null) {
            buildScrollContent(currentData);
        }
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
            if (meta.qaResponsible() != null && !meta.qaResponsible().isBlank()) {
                addChipIfPresent(chipsRow, "QA: " + meta.qaResponsible(), null);
            }
            if (meta.taskLabel() != null && !meta.taskLabel().isBlank()) {
                addTaskChipIfPresent(chipsRow, tr("rp.meta.task", "Задача") + ": " + meta.taskLabel(), meta.taskUrl());
            }
            if (!chipsRow.getChildren().isEmpty()) {
                box.getChildren().add(chipsRow);
            }
        }

        HBox timingRow = buildMetricRow(
                new MetaMetric(tr("rp.meta.started", "Начат"), meta.startedAt(), false, ""),
                new MetaMetric(meta.lifecycleLabel(), meta.lifecycleValue(), false, ""),
                new MetaMetric(tr("rp.meta.duration", "Длительность"), meta.duration(), true, meta.durationFull())
        );
        if (!timingRow.getChildren().isEmpty()) {
            box.getChildren().add(timingRow);
        }

        HBox progressRow = buildMetricRow(
                new MetaMetric(tr("rp.meta.totalCases", "Всего кейсов"), String.valueOf(meta.totalCases()), true, ""),
                new MetaMetric(tr("rp.meta.completedCases", "Пройдено кейсов"), String.valueOf(meta.completedCases()), true, ""),
                new MetaMetric(tr("rp.meta.progress", "Прогресс"), meta.completionPercent() + "%", true, "")
        );
        progressRow.setMinHeight(24);
        progressRow.setPrefHeight(24);
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

        box.getChildren().add(buildCaseMetaLine(tr("rp.case.labels", "Метки") + ":", data.caseLabelsText(), tr("rp.case.empty", "Нет данных")));
        box.getChildren().add(buildCaseMetaLine(tr("rp.case.tags", "Теги") + ":", data.caseTagsText(), tr("rp.case.empty", "Нет данных")));

        if (!data.lastRunDate().isBlank()) {
            HBox lastRunRow = buildMetricRow(new MetaMetric(tr("rp.case.lastRun", "Последнее прохождение"), data.lastRunDate(), false, ""));
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

    private Node buildCaseMetaLine(String headerText, String valueText, String emptyText) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("rp-case-meta-line");
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);

        Label header = new Label(headerText);
        header.getStyleClass().add("rp-case-meta-label");
        header.setMinWidth(Region.USE_PREF_SIZE);

        boolean empty = valueText == null || valueText.isBlank();
        Label value = new Label(empty ? emptyText : valueText);
        value.setWrapText(false);
        value.setMinWidth(0);
        value.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(value, Priority.ALWAYS);
        value.getStyleClass().add(empty ? "rp-case-meta-empty" : "rp-case-meta-value");

        if (!empty) {
            Tooltip.install(value, new Tooltip(valueText));
            installFittedCaseMetaText(value, valueText);
        }

        row.getChildren().addAll(header, value);
        return row;
    }

    private void installFittedCaseMetaText(Label label, String fullText) {
        Runnable apply = () -> {
            double width = label.getWidth();
            if (width <= 0) {
                width = label.prefWidth(-1);
            }
            if (width <= 0) {
                return;
            }
            label.setText(fitMetaText(fullText, width, label));
        };

        label.widthProperty().addListener((obs, oldV, newV) -> apply.run());
        label.fontProperty().addListener((obs, oldV, newV) -> apply.run());
        label.sceneProperty().addListener((obs, oldV, newV) -> Platform.runLater(apply));
        Platform.runLater(apply);
    }

    private static String fitMetaText(String fullText, double maxWidth, Label label) {
        if (fullText == null || fullText.isBlank()) {
            return "";
        }
        if (textWidth(fullText, label) <= maxWidth) {
            return fullText;
        }

        List<String> parts = splitMetaValues(fullText);
        if (parts.isEmpty()) {
            return fitSingleToken(fullText, maxWidth, label);
        }

        String ellipsis = "...";
        StringBuilder shown = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String candidate = shown.isEmpty() ? parts.get(i) : shown + ", " + parts.get(i);
            String candidateWithEllipsis = i < parts.size() - 1 ? candidate + ellipsis : candidate;
            if (textWidth(candidateWithEllipsis, label) > maxWidth) {
                if (shown.isEmpty()) {
                    return fitSingleToken(parts.get(i) + ellipsis, maxWidth, label);
                }
                return shown + ellipsis;
            }
            shown.setLength(0);
            shown.append(candidate);
        }
        return shown.toString();
    }

    private static List<String> splitMetaValues(String fullText) {
        List<String> result = new ArrayList<>();
        for (String part : fullText.split(",")) {
            String trimmed = part == null ? "" : part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static String fitSingleToken(String text, double maxWidth, Label label) {
        if (textWidth(text, label) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < clean.length(); i++) {
            String candidate = out.toString() + clean.charAt(i) + ellipsis;
            if (textWidth(candidate, label) > maxWidth) {
                return out.isEmpty() ? ellipsis : out + ellipsis;
            }
            out.append(clean.charAt(i));
        }
        return out.toString();
    }

    private static double textWidth(String text, Label label) {
        Text helper = new Text(text == null ? "" : text);
        helper.setFont(label.getFont());
        return helper.getLayoutBounds().getWidth();
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

    private void applyTaskLinkHeaderButton(ReportData data) {
        String url = data == null ? "" : data.caseTaskUrl();
        boolean visible = url != null && !url.isBlank() && data != null && data.target().type() == ReportTargetType.TEST_CASE;
        btnTaskLink.setVisible(visible);
        btnTaskLink.setManaged(visible);
        if (!visible) {
            btnTaskLink.setOnAction(null);
            btnTaskLink.setTooltip(null);
            return;
        }
        btnTaskLink.setTooltip(new Tooltip(url));
        btnTaskLink.setOnAction(e -> openExternalUrl(url));
    }

    private Node buildTaskLinkButton(String url, double iconSize, String... styleClasses) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Button button = new Button();
        button.setFocusTraversable(false);
        button.getStyleClass().addAll(styleClasses);
        UiSvg.setButtonSvg(button, "link-open.svg", (int) Math.round(iconSize));
        button.setTooltip(new Tooltip(url));
        button.setOnAction(e -> openExternalUrl(url));
        return button;
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
        VBox box = sectionBox(tr("rp.section.summary", "Сводка"));

        DonutChart donut = new DonutChart();
        List<DonutChart.Slice> slices = new ArrayList<>();
        for (String status : orderedStatuses(section.countsByStatus())) {
            int count = section.countsByStatus().getOrDefault(status, 0);
            if (count > 0) {
                slices.add(new DonutChart.Slice(statusToColorKey(status), count));
            }
        }
        donut.draw(slices, section.total());

        VBox legend = new VBox(0);
        for (String status : orderedStatuses(section.countsByStatus())) {
            int count = section.countsByStatus().getOrDefault(status, 0);
            if (count == 0) continue;

            double pct = section.total() > 0 ? (count * 100.0 / section.total()) : 0;

            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(3, 0, 3, 0));

            Label badge = statusBadge(status);
            badge.setMinWidth(96);

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

        HBox inner = new HBox(12, donut, legend);
        inner.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(legend, Priority.ALWAYS);

        box.getChildren().add(inner);
        return box;
    }

    private Node buildHistorySection(HistorySection section) {
        if (section.rows().isEmpty()) return new Region();

        VBox box = new VBox(8);
        box.getStyleClass().add("rp-section");

        Label lbl = new Label(tr("rp.section.history", "История").toUpperCase());
        lbl.getStyleClass().add("rp-section-title");
        box.getChildren().add(lbl);

        FlowPane filters = new FlowPane(8, 8);
        filters.getStyleClass().add("rp-history-filters");
        filters.getChildren().add(buildHistoryFilterChip(tr("rp.history.filter.all", "Все"), HISTORY_FILTER_ALL));
        for (String status : orderedStatuses(historyStatusCounts(section))) {
            if (historyHasStatus(section, status)) {
                filters.getChildren().add(buildHistoryFilterChip(statusLabel(status), status));
            }
        }
        if (historyHasNotStarted(section)) {
            filters.getChildren().add(buildHistoryFilterChip(tr("rp.status.notStarted", "Не начат"), HISTORY_FILTER_NOT_STARTED));
        }
        box.getChildren().add(filters);

        List<HistorySection.HistoryRow> filteredRows = section.rows().stream()
                .filter(this::historyFilterMatches)
                .toList();

        if (filteredRows.isEmpty()) {
            Label empty = new Label(tr("rp.history.emptyFiltered", "Нет кейсов по выбранному статусу"));
            empty.getStyleClass().add("rp-history-empty");
            box.getChildren().add(empty);
            return box;
        }

        for (HistorySection.HistoryRow row : filteredRows) {
            box.getChildren().add(buildHistoryRow(row));
        }
        return box;
    }

    private Node buildHistoryRow(HistorySection.HistoryRow row) {
        VBox box = new VBox(3);
        box.getStyleClass().add("rp-history-row");

        Label ordinal = null;
        if (row.ordinal() > 0) {
            ordinal = new Label(String.valueOf(row.ordinal()));
            ordinal.getStyleClass().add("rp-history-ordinal");
            if (row.ordinal() >= 1000) {
                ordinal.getStyleClass().add("rp-history-ordinal-compact");
            }
        }

        Label badge = statusBadge(row.status());
        badge.setMinWidth(Region.USE_PREF_SIZE);
        badge.setPrefWidth(Region.USE_COMPUTED_SIZE);
        badge.setMaxWidth(Region.USE_PREF_SIZE);

        Node taskLinkButton = buildTaskLinkButton(row.taskUrl(), 10, "rp-task-link-btn", "rp-task-link-btn-sm");
        Node title = buildHistoryTitleNode(row);
        HBox.setHgrow(title, Priority.ALWAYS);

        Node commentNode = buildHistoryCommentNode(row);

        Label date = new Label(row.dateLabel());
        date.getStyleClass().add("rp-history-context");
        date.getStyleClass().add("rp-history-date");
        date.setMinWidth(Region.USE_PREF_SIZE);
        date.setAlignment(Pos.CENTER_RIGHT);

        HBox top = new HBox(4);
        top.setAlignment(Pos.CENTER_LEFT);
        if (ordinal != null) {
            top.getChildren().add(ordinal);
        }
        top.getChildren().add(badge);
        if (taskLinkButton != null) {
            top.getChildren().add(taskLinkButton);
        }
        top.getChildren().add(title);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        top.getChildren().add(spacer);
        HBox trailingBox = new HBox(8);
        trailingBox.setAlignment(Pos.CENTER_RIGHT);
        trailingBox.getStyleClass().add("rp-history-trailing");
        trailingBox.getChildren().add(commentNode);
        trailingBox.getChildren().add(date);
        top.getChildren().add(trailingBox);

        box.getChildren().add(top);

        String context = row.title().isBlank() ? "" : row.contextLabel();
        if (!context.isBlank()) {
            Label ctx = new Label(context);
            ctx.getStyleClass().add("rp-history-context");
            box.getChildren().add(ctx);
        }

        return box;
    }

    private Button buildHistoryFilterChip(String label, String filterValue) {
        Button btn = new Button(label);
        btn.setFocusTraversable(false);
        btn.getStyleClass().add("rp-history-filter-chip");
        if (filterValue.equals(currentHistoryFilter)) {
            btn.getStyleClass().add("rp-history-filter-chip-active");
        }
        btn.setOnAction(e -> {
            currentHistoryFilter = filterValue;
            rebuildScrollContent();
        });
        return btn;
    }

    private boolean historyFilterMatches(HistorySection.HistoryRow row) {
        if (HISTORY_FILTER_ALL.equals(currentHistoryFilter)) {
            return true;
        }
        if (HISTORY_FILTER_NOT_STARTED.equals(currentHistoryFilter)) {
            return row.status() == null || row.status().isBlank();
        }
        return currentHistoryFilter.equals(row.status());
    }

    private static boolean historyHasStatus(HistorySection section, String status) {
        return section.rows().stream().anyMatch(row -> status.equals(row.status()));
    }

    private static boolean historyHasNotStarted(HistorySection section) {
        return section.rows().stream().anyMatch(row -> row.status() == null || row.status().isBlank());
    }

    private Node buildHistoryCommentNode(HistorySection.HistoryRow row) {
        if (row.comment().isBlank()) {
            Region empty = new Region();
            empty.getStyleClass().add("rp-history-comment-slot");
            return empty;
        }

        Button comment = new Button(previewComment(row.comment()));
        comment.setFocusTraversable(false);
        comment.getStyleClass().addAll("rp-history-comment-btn", "rp-history-comment-slot");
        comment.setTooltip(new Tooltip(row.comment()));
        comment.setOnAction(e -> {
            CaseCommentModal modal = (CaseCommentModal) comment.getProperties().get("rp.history.comment.modal");
            if (modal == null) {
                modal = new CaseCommentModal(comment);
                modal.install(root);
                comment.getProperties().put("rp.history.comment.modal", modal);
            }

            modal.setCurrentValueSupplier(row::comment);
            modal.setEditableSupplier(() -> false);
            modal.setOnSaved(v -> {});
            modal.toggle();
        });
        return comment;
    }

    private static String previewComment(String comment) {
        if (comment == null) {
            return "";
        }
        String trimmed = comment.trim();
        if (trimmed.length() <= COMMENT_PREVIEW_LIMIT) {
            return trimmed;
        }
        return trimmed.substring(0, COMMENT_PREVIEW_LIMIT) + "...";
    }

    private Node buildHistoryTitleNode(HistorySection.HistoryRow row) {
        String titleText = row.title().isBlank() ? row.contextLabel() : row.title();
        if (currentData != null
                && currentData.target().type() == ReportTargetType.TEST_CASE
                && row.title().isBlank()
                && row.entityId() != null
                && !row.entityId().isBlank()) {
            Button button = new Button(titleText);
            button.setFocusTraversable(false);
            button.getStyleClass().addAll("rp-history-title", "rp-history-link");
            button.setTextOverrun(OverrunStyle.ELLIPSIS);
            button.setMinWidth(0);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(e -> {
                ReportsScreen.setPendingRestore(currentData.target());
                CardNavigationBridge.requestCycleHistoryNavigation(row.entityId(), currentData.target().id());
                Router.get().cycles();
            });
            return button;
        }

        Label title = new Label(titleText);
        title.getStyleClass().add("rp-history-title");
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        return title;
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
        String label = blank ? tr("rp.status.notStarted", "Не начат") : statusLabel(status);
        Label badge = new Label(label);
        badge.getStyleClass().add("rp-badge");
        if (blank) {
            badge.getStyleClass().add("rp-badge-none");
        } else {
            badge.setStyle(CaseStatusRegistry.reportBadgeStyle(status));
        }
        return badge;
    }

    private static List<String> orderedStatuses(Map<String, Integer> countsByStatus) {
        List<String> ordered = new ArrayList<>();
        for (CaseStatusDefinition definition : CaseStatusRegistry.orderedWith(countsByStatus)) {
            ordered.add(definition.code());
        }
        if (countsByStatus != null && countsByStatus.containsKey("") && !ordered.contains("")) {
            ordered.add("");
        }
        return ordered;
    }

    private static Map<String, Integer> historyStatusCounts(HistorySection section) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        if (section == null) {
            return counts;
        }
        for (HistorySection.HistoryRow row : section.rows()) {
            String status = row.status();
            if (status == null || status.isBlank()) {
                continue;
            }
            counts.merge(status.trim().toUpperCase(), 1, Integer::sum);
        }
        return counts;
    }


    private static String statusToColorKey(String status) {
        if (status == null || status.isBlank()) {
            return "unknown";
        }
        return CaseStatusRegistry.trendColorKey(status);
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
            case "passed" -> CaseStatusRegistry.displayLabel(CaseStatusRegistry.PASSED);
            case "bugs" -> CaseStatusRegistry.displayLabel(CaseStatusRegistry.PASSED_WITH_BUGS);
            case "failed" -> CaseStatusRegistry.displayLabel(CaseStatusRegistry.FAILED);
            default -> colorKey;
        };
    }

    private void updateReportHint(boolean blocked) {
        if (!blocked) {
            hintReport.setText(" ");
            hintReport.setOpacity(0.0);
        } else {
            hintReport.setText(tr("rp.report.hint.selectRecommendation", "Выберите решение для создания отчёта"));
            hintReport.setOpacity(1.0);
        }
    }

    private Node buildRecommendationSection() {
        VBox box = new VBox(8);
        box.getStyleClass().add("rp-section");

        Label lbl = new Label(tr("rp.section.recommendation", "Решение").toUpperCase());
        lbl.getStyleClass().add("rp-section-title");

        List<RecommendationChip> chips = List.of(
                new RecommendationChip("none", tr("rp.recommend.none", "Без решения"), "rp-recommend-chip-none-active"),
                new RecommendationChip("recommended", tr("rp.recommend.recommended", "Рекомендован"), "rp-recommend-chip-recommended"),
                new RecommendationChip("needs_work", tr("rp.recommend.needsWork", "Требует доработки"), "rp-recommend-chip-needs-work"),
                new RecommendationChip("not_recommended", tr("rp.recommend.notRecommended", "Не рекомендован"), "rp-recommend-chip-not-recommended")
        );

        FlowPane chipsPane = new FlowPane(8, 8);
        chipsPane.getStyleClass().add("rp-recommend-row");
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

        HBox headerRow = new HBox(12, lbl, chipsPane);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(chipsPane, Priority.ALWAYS);

        box.getChildren().add(headerRow);
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

    private static String statusLabel(String status) {
        if (status == null || status.isBlank()) {
            return tr("rp.status.notStarted", "Не начат");
        }
        return CaseStatusRegistry.displayLabel(status);
    }

    private static String tr(String key, String fallback) {
        String value = I18n.t(key);
        if (value == null || value.isBlank() || value.equals("!" + key + "!")) {
            return fallback;
        }
        return value;
    }

    private record RecommendationChip(String value, String label, String activeClass) {}
    private record MetaMetric(String label, String value, boolean accentValue, String tooltipText) {
        boolean isEmpty() {
            return (label == null || label.isBlank()) && (value == null || value.isBlank());
        }
    }
}
