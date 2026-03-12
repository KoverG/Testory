package app.domain.testcases.ui;

import app.core.I18n;
import app.domain.cycles.repo.CycleCardJsonReader;
import app.domain.cycles.repo.FileCycleRepository;
import app.domain.cycles.ui.right.CaseCommentModal;
import app.domain.cycles.ui.right.CaseStatusComboSupport;
import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.cycles.usecase.CycleDraft;
import app.ui.UiSvg;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class TestCaseCyclesAccessory {

    public record CurrentCycleContext(
            String cycleId,
            String cycleTitle,
            String cycleCreatedAt,
            String caseStatus,
            String caseComment,
            int caseNumber,
            int caseTotal,
            Consumer<String> onStatusChanged,
            Consumer<String> onCommentChanged,
            Runnable onSaveRequested
    ) {
    }

    private static final double SAVE_ICON_SIZE = 12.0;
    private static final double SAVE_INDICATOR_SIZE = 16.0;
    private static final double SAVE_MIN_SPINNER_MS = 700.0;
    private static final double SAVE_CHECK_HOLD_MS = 900.0;
    private static final double COMMENT_PREVIEW_WIDTH = 180.0;
    private static final double CURRENT_SIDE_WIDTH = 220.0;
    private static final double HISTORY_STATUS_WIDTH = 150.0;
    private static final String DISABLED_BASE_CLASS = "tc-disabled-base";

    private final FileCycleRepository cycleRepo = new FileCycleRepository();
    private final StackPane modalHost;

    private final VBox root = new VBox(10.0);
    private final VBox currentBlock = new VBox(8.0);
    private final Label currentTitle = new Label(I18n.t("cy.case.status.footer"));
    private final HBox currentRow = new HBox(10.0);
    private final StackPane currentCycleMeta = new StackPane();
    private final VBox currentCycleMetaMain = new VBox(4.0);
    private final Label currentCycleName = new Label();
    private final Label currentCycleCopyHint = new Label(I18n.t("cy.case.current.copy.hint"));
    private final Region currentCycleUnderline = new Region();
    private final ComboBox<String> currentStatusCombo = CaseStatusComboSupport.createCombo("cy-added-case-combo");
    private final HBox currentRightBox = new HBox(8.0);
    private final VBox currentCommentMeta = new VBox(4.0);
    private final Button currentCommentPreview = new Button();
    private final Region currentCommentUnderline = new Region();
    private final Button currentSaveButton = new Button();
    private final StackPane currentSaveGraphic = new StackPane();
    private final ProgressIndicator currentSaveSpinner = new ProgressIndicator();
    private final SVGPath currentSaveAnimatedCheck = new SVGPath();
    private Node currentSaveIdleIcon;

    private final VBox cyclesBlock = new VBox(8.0);
    private final Label cyclesTitle = new Label(I18n.t("cy.case.cycles.footer"));
    private final VBox cyclesRows = new VBox(8.0);

    private String currentCaseId = "";
    private CurrentCycleContext currentContext;
    private String currentStatus = "";
    private String currentComment = "";
    private String savedStatus = "";
    private String savedComment = "";
    private boolean syncingCombo = false;
    private long currentSaveStartedAt = 0L;
    private Animation currentSaveAnimation;
    private ChangeListener<Paint> currentSavePaintListener;
    private Animation currentCycleCopyHintAnimation;

    public TestCaseCyclesAccessory(StackPane modalHost) {
        this.modalHost = modalHost;

        root.getStyleClass().add("cy-testcase-cycles-box");

        currentTitle.getStyleClass().add("tc-btnlike-title");
        cyclesTitle.getStyleClass().add("tc-btnlike-title");

        currentRow.getStyleClass().add("cy-testcase-current-row");
        currentRow.setAlignment(Pos.CENTER);
        currentRow.setFillHeight(true);

        currentCycleMeta.setMinWidth(CURRENT_SIDE_WIDTH);
        currentCycleMeta.setPrefWidth(CURRENT_SIDE_WIDTH);
        currentCycleMeta.setMaxWidth(CURRENT_SIDE_WIDTH);
        currentCycleMeta.setAlignment(Pos.TOP_CENTER);

        currentCycleMetaMain.getStyleClass().add("cy-testcase-current-meta");
        currentCycleMetaMain.setAlignment(Pos.CENTER);
        currentCycleMetaMain.setFillWidth(true);
        currentCycleMetaMain.setMaxWidth(Double.MAX_VALUE);

        currentCycleName.getStyleClass().addAll("cy-testcase-cycle-name", "cy-testcase-cycle-name-clickable");
        currentCycleName.setMinWidth(0.0);
        currentCycleName.setMaxWidth(Double.MAX_VALUE);
        currentCycleName.setAlignment(Pos.CENTER);
        currentCycleName.setTextOverrun(OverrunStyle.ELLIPSIS);
        currentCycleName.setTooltip(new Tooltip());
        currentCycleName.setOnMouseClicked(e -> copyCurrentCycleName());

        currentCycleCopyHint.getStyleClass().add("cy-testcase-copy-hint");
        currentCycleCopyHint.setVisible(false);
        currentCycleCopyHint.setMouseTransparent(true);
        currentCycleCopyHint.setOpacity(0.0);
        currentCycleCopyHint.setTranslateY(28.0);
        StackPane.setAlignment(currentCycleCopyHint, Pos.TOP_CENTER);

        currentCycleUnderline.getStyleClass().add("cy-testcase-current-underline");
        currentCycleUnderline.setMaxWidth(Double.MAX_VALUE);

        currentStatusCombo.setMaxWidth(Region.USE_PREF_SIZE);
        currentStatusCombo.setPrefWidth(CaseStatusComboSupport.COMBO_WIDTH_PX);
        CaseStatusComboSupport.install(currentStatusCombo, this::handleCurrentStatusChanged);

        currentRightBox.setAlignment(Pos.CENTER_LEFT);
        currentRightBox.getStyleClass().add("cy-testcase-current-actions");
        currentRightBox.setMinWidth(CURRENT_SIDE_WIDTH);
        currentRightBox.setPrefWidth(CURRENT_SIDE_WIDTH);
        currentRightBox.setMaxWidth(CURRENT_SIDE_WIDTH);

        currentCommentMeta.getStyleClass().add("cy-testcase-current-meta");
        currentCommentMeta.setMinWidth(COMMENT_PREVIEW_WIDTH);
        currentCommentMeta.setPrefWidth(COMMENT_PREVIEW_WIDTH);
        currentCommentMeta.setMaxWidth(COMMENT_PREVIEW_WIDTH);

        currentCommentPreview.getStyleClass().add("cy-testcase-comment-preview");
        currentCommentPreview.setFocusTraversable(false);
        currentCommentPreview.setContentDisplay(ContentDisplay.TEXT_ONLY);
        currentCommentPreview.setTextOverrun(OverrunStyle.ELLIPSIS);
        currentCommentPreview.setMinWidth(COMMENT_PREVIEW_WIDTH);
        currentCommentPreview.setPrefWidth(COMMENT_PREVIEW_WIDTH);
        currentCommentPreview.setMaxWidth(COMMENT_PREVIEW_WIDTH);
        currentCommentPreview.setAlignment(Pos.CENTER);
        currentCommentPreview.setOnAction(e -> openCommentModal(currentCommentPreview, true, currentComment, this::handleCurrentCommentChanged));

        currentCommentUnderline.getStyleClass().add("cy-testcase-current-underline");
        currentCommentUnderline.setMaxWidth(Double.MAX_VALUE);

        configureCurrentSaveButton();

        currentCycleMetaMain.getChildren().addAll(currentCycleName, currentCycleUnderline);
        currentCycleMeta.getChildren().addAll(currentCycleMetaMain, currentCycleCopyHint);
        currentCommentMeta.getChildren().addAll(currentCommentPreview, currentCommentUnderline);
        currentRightBox.getChildren().addAll(currentCommentMeta, currentSaveButton);
        currentRow.getChildren().addAll(currentCycleMeta, currentStatusCombo, currentRightBox);
        currentBlock.getChildren().addAll(currentTitle, currentRow);

        cyclesBlock.getChildren().addAll(cyclesTitle, cyclesRows);
        root.getChildren().addAll(currentBlock, cyclesBlock);

        clear();
    }

    public Node node() {
        return root;
    }

    public void showForCase(String caseId, CurrentCycleContext context) {
        currentCaseId = safe(caseId);
        currentContext = context;
        currentStatus = context == null ? "" : safe(context.caseStatus());
        currentComment = context == null ? "" : safe(context.caseComment());
        savedStatus = currentStatus;
        savedComment = currentComment;
        refresh();
    }

    public void clear() {
        currentCaseId = "";
        currentContext = null;
        currentStatus = "";
        currentComment = "";
        savedStatus = "";
        savedComment = "";
        currentBlock.setVisible(false);
        currentBlock.setManaged(false);
        cyclesRows.getChildren().clear();
        root.setVisible(false);
        root.setManaged(false);
        currentTitle.setText(I18n.t("cy.case.status.footer"));
        syncCurrentCombo("");
        currentCommentPreview.setText(formatCommentPreview(""));
        currentCommentPreview.setTooltip(null);
        hideCurrentCycleCopyHint();
        resetCurrentSaveGraphic();
        setCurrentSaveEnabled(false);
    }

    public void refresh() {
        if (currentCaseId.isBlank()) {
            clear();
            return;
        }

        List<CycleEntry> entries = loadCycleEntries(currentCaseId);
        entries = mergeCurrentContext(entries);

        renderCurrentBlock();
        renderCyclesBlock(entries);

        root.setVisible(true);
        root.setManaged(true);
    }

    private void configureCurrentSaveButton() {
        currentSaveButton.getStyleClass().addAll("icon-btn", "xs", "cy-testcase-save-btn", "tc-save-btn");
        currentSaveButton.setFocusTraversable(false);
        currentSaveButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        Tooltip.install(currentSaveButton, new Tooltip(I18n.t("cy.case.current.save.tooltip")));
        currentSaveButton.setOnAction(e -> saveCurrentChanges());

        currentSaveIdleIcon = UiSvg.createSvg("check.svg", SAVE_ICON_SIZE);

        currentSaveSpinner.setPrefSize(SAVE_INDICATOR_SIZE, SAVE_INDICATOR_SIZE);
        currentSaveSpinner.setMinSize(SAVE_INDICATOR_SIZE, SAVE_INDICATOR_SIZE);
        currentSaveSpinner.setMaxSize(SAVE_INDICATOR_SIZE, SAVE_INDICATOR_SIZE);
        currentSaveSpinner.getStyleClass().add("ui-savefx-spinner");

        currentSaveAnimatedCheck.setContent("M2 10 L8 16 L18 4");
        currentSaveAnimatedCheck.setFill(null);
        currentSaveAnimatedCheck.setStrokeWidth(2.2);
        currentSaveAnimatedCheck.setStrokeLineCap(StrokeLineCap.ROUND);
        currentSaveAnimatedCheck.setStrokeLineJoin(StrokeLineJoin.ROUND);
        currentSaveAnimatedCheck.getStrokeDashArray().setAll(24.0);
        currentSaveAnimatedCheck.setStrokeDashOffset(24.0);

        currentSavePaintListener = (obs, oldPaint, newPaint) -> applyCurrentSavePaint(newPaint);
        currentSaveButton.textFillProperty().addListener(currentSavePaintListener);
        applyCurrentSavePaint(currentSaveButton.getTextFill());

        currentSaveGraphic.setMinSize(SAVE_INDICATOR_SIZE, SAVE_INDICATOR_SIZE);
        currentSaveGraphic.setPrefSize(SAVE_INDICATOR_SIZE, SAVE_INDICATOR_SIZE);
        currentSaveGraphic.setMaxSize(SAVE_INDICATOR_SIZE, SAVE_INDICATOR_SIZE);
        currentSaveButton.setGraphic(currentSaveGraphic);
        resetCurrentSaveGraphic();
    }

    private void applyCurrentSavePaint(Paint paint) {
        if (paint == null) return;
        currentSaveAnimatedCheck.setStroke(paint);
        currentSaveSpinner.setStyle("-fx-progress-color: " + paint.toString().replace("0x", "#") + ";");
    }

    private void handleCurrentStatusChanged(String status) {
        if (syncingCombo) return;

        currentStatus = safe(status);
        if (currentContext != null && currentContext.onStatusChanged() != null) {
            currentContext.onStatusChanged().accept(currentStatus);
        }
        refresh();
    }

    private void handleCurrentCommentChanged(String comment) {
        currentComment = safe(comment);
        if (currentContext != null && currentContext.onCommentChanged() != null) {
            currentContext.onCommentChanged().accept(currentComment);
        }
        refresh();
    }

    private void saveCurrentChanges() {
        if (currentContext == null || !hasPendingCurrentChanges()) return;

        String pendingStatus = safe(currentStatus);
        String pendingComment = safe(currentComment);
        Runnable saveAction = currentContext.onSaveRequested();

        setCurrentSaveEnabled(false);
        startCurrentSaveFeedback();

        PauseTransition delay = new PauseTransition(Duration.millis(90));
        delay.setOnFinished(e -> {
            if (saveAction != null) {
                saveAction.run();
            }

            savedStatus = pendingStatus;
            savedComment = pendingComment;

            successCurrentSaveFeedback();
            refresh();
        });
        delay.playFromStart();
    }

    private boolean hasPendingCurrentChanges() {
        return !savedStatus.equals(safe(currentStatus)) || !savedComment.equals(safe(currentComment));
    }

    private void setCurrentSaveEnabled(boolean enabled) {
        currentSaveButton.setDisable(!enabled);
        var classes = currentSaveButton.getStyleClass();
        if (enabled) {
            classes.remove(DISABLED_BASE_CLASS);
        } else if (!classes.contains(DISABLED_BASE_CLASS)) {
            classes.add(DISABLED_BASE_CLASS);
        }
    }

    private void startCurrentSaveFeedback() {
        stopCurrentSaveAnimation();
        currentSaveStartedAt = System.currentTimeMillis();
        currentSaveGraphic.getChildren().setAll(currentSaveSpinner);
    }

    private void successCurrentSaveFeedback() {
        long elapsed = System.currentTimeMillis() - currentSaveStartedAt;
        long waitMs = Math.max(0L, (long) SAVE_MIN_SPINNER_MS - elapsed);

        PauseTransition delay = new PauseTransition(Duration.millis(waitMs));
        delay.setOnFinished(e -> showAnimatedCurrentSaveCheck());
        currentSaveAnimation = delay;
        delay.playFromStart();
    }

    private void showAnimatedCurrentSaveCheck() {
        currentSaveAnimatedCheck.setStrokeDashOffset(24.0);
        currentSaveAnimatedCheck.setOpacity(1.0);
        currentSaveAnimatedCheck.setScaleX(1.0);
        currentSaveAnimatedCheck.setScaleY(1.0);
        currentSaveGraphic.getChildren().setAll(currentSaveAnimatedCheck);

        javafx.animation.Timeline draw = new javafx.animation.Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(currentSaveAnimatedCheck.strokeDashOffsetProperty(), 24.0, Interpolator.LINEAR),
                        new KeyValue(currentSaveAnimatedCheck.opacityProperty(), 1.0, Interpolator.LINEAR)
                ),
                new KeyFrame(Duration.millis(240),
                        new KeyValue(currentSaveAnimatedCheck.strokeDashOffsetProperty(), 0.0, Interpolator.EASE_OUT)
                )
        );

        ScaleTransition pop = new ScaleTransition(Duration.millis(220), currentSaveAnimatedCheck);
        pop.setFromX(0.85);
        pop.setFromY(0.85);
        pop.setToX(1.0);
        pop.setToY(1.0);
        pop.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition appear = new ParallelTransition(draw, pop);
        PauseTransition hold = new PauseTransition(Duration.millis(SAVE_CHECK_HOLD_MS));
        hold.setOnFinished(e -> resetCurrentSaveGraphic());

        currentSaveAnimation = new SequentialTransition(appear, hold);
        currentSaveAnimation.playFromStart();
    }

    private void resetCurrentSaveGraphic() {
        stopCurrentSaveAnimation();
        if (currentSaveIdleIcon != null) {
            currentSaveGraphic.getChildren().setAll(currentSaveIdleIcon);
        } else {
            currentSaveGraphic.getChildren().clear();
        }
        currentSaveAnimatedCheck.setStrokeDashOffset(24.0);
    }

    private void stopCurrentSaveAnimation() {
        if (currentSaveAnimation != null) {
            currentSaveAnimation.stop();
            currentSaveAnimation = null;
        }
    }

    private void renderCurrentBlock() {
        boolean visible = currentContext != null;
        currentBlock.setVisible(visible);
        currentBlock.setManaged(visible);
        if (!visible) {
            currentTitle.setText(I18n.t("cy.case.status.footer"));
            syncCurrentCombo("");
            currentCommentPreview.setText(formatCommentPreview(""));
            currentCommentPreview.setTooltip(null);
            setCurrentSaveEnabled(false);
            return;
        }

        currentTitle.setText(formatCurrentTitle(currentContext.caseNumber(), currentContext.caseTotal()));
        String currentCycleCaption = formatCycleCaption(
                currentContext.cycleTitle(),
                currentContext.cycleCreatedAt(),
                currentContext.cycleId()
        );
        currentCycleName.setText(currentCycleCaption);
        Tooltip currentCycleTooltip = currentCycleName.getTooltip();
        if (currentCycleTooltip != null) {
            currentCycleTooltip.setText(currentCycleCaption);
        }
        currentCycleCopyHint.setText(I18n.t("cy.case.current.copy.hint"));
        syncCurrentCombo(currentStatus);
        currentCommentPreview.setText(formatCommentPreview(currentComment));
        currentCommentPreview.setTooltip(buildCommentTooltip(currentComment));
        setCurrentSaveEnabled(hasPendingCurrentChanges());
    }

    private void renderCyclesBlock(List<CycleEntry> entries) {
        cyclesRows.getChildren().clear();

        if (entries.isEmpty()) {
            Label empty = new Label(I18n.t("cy.case.cycles.empty"));
            empty.getStyleClass().add("cy-testcase-cycle-status");
            empty.setWrapText(true);
            cyclesRows.getChildren().add(empty);
            return;
        }

        for (CycleEntry entry : entries) {
            HBox row = new HBox(0.0);
            row.getStyleClass().add("cy-testcase-cycle-row");
            row.setAlignment(Pos.CENTER_LEFT);

            Label name = new Label(formatCycleCaption(entry.title(), entry.createdAtUi(), entry.id()));
            name.getStyleClass().add("cy-testcase-cycle-name");
            name.setMinWidth(0.0);
            name.prefWidthProperty().bind(row.widthProperty().subtract(24.0).divide(2.0));
            name.maxWidthProperty().bind(row.widthProperty().subtract(24.0).divide(2.0));
            name.setTextOverrun(OverrunStyle.ELLIPSIS);
            name.setAlignment(Pos.CENTER_LEFT);

            Label status = new Label(formatStatusText(entry.status()));
            status.getStyleClass().add("cy-testcase-cycle-status");
            status.setMinWidth(HISTORY_STATUS_WIDTH);
            status.setPrefWidth(HISTORY_STATUS_WIDTH);
            status.setMaxWidth(HISTORY_STATUS_WIDTH);
            status.setAlignment(Pos.CENTER_LEFT);

            Button commentPreview = new Button(formatCommentPreview(entry.comment()));
            commentPreview.getStyleClass().addAll("cy-testcase-comment-preview", "cy-testcase-cycle-comment-preview");
            commentPreview.setFocusTraversable(false);
            commentPreview.setContentDisplay(ContentDisplay.TEXT_ONLY);
            commentPreview.setTextOverrun(OverrunStyle.ELLIPSIS);
            commentPreview.setMinWidth(COMMENT_PREVIEW_WIDTH);
            commentPreview.setPrefWidth(COMMENT_PREVIEW_WIDTH);
            commentPreview.setMaxWidth(COMMENT_PREVIEW_WIDTH);
            commentPreview.setTooltip(buildCommentTooltip(entry.comment()));
            commentPreview.setOnAction(e -> openCommentModal(commentPreview, false, entry.comment(), null));

            row.getChildren().addAll(name, status, commentPreview);
            cyclesRows.getChildren().add(row);
        }
    }

    private void syncCurrentCombo(String status) {
        String normalized = safe(status);
        syncingCombo = true;
        try {
            CaseStatusComboSupport.setStatus(currentStatusCombo, normalized);
        } finally {
            syncingCombo = false;
        }

        Platform.runLater(() -> {
            syncingCombo = true;
            try {
                CaseStatusComboSupport.setStatus(currentStatusCombo, normalized);
            } finally {
                syncingCombo = false;
            }
        });
    }

    private List<CycleEntry> mergeCurrentContext(List<CycleEntry> entries) {
        if (currentContext == null) {
            return entries;
        }

        List<CycleEntry> merged = new ArrayList<>(entries);
        CycleEntry currentEntry = new CycleEntry(
                safe(currentContext.cycleId()),
                safe(currentContext.cycleTitle()),
                safe(currentContext.cycleCreatedAt()),
                currentStatus,
                currentComment
        );

        boolean matched = false;
        for (int i = 0; i < merged.size(); i++) {
            CycleEntry entry = merged.get(i);
            if (sameCycle(entry, currentEntry)) {
                merged.set(i, currentEntry);
                matched = true;
                break;
            }
        }

        if (!matched && (!currentEntry.title().isBlank() || !currentEntry.createdAtUi().isBlank() || !currentEntry.id().isBlank())) {
            merged.add(0, currentEntry);
        }

        return merged;
    }

    private List<CycleEntry> loadCycleEntries(String caseId) {
        Path rootDir = cycleRepo.rootDir();
        if (rootDir == null || !Files.isDirectory(rootDir)) {
            return List.of();
        }

        List<CycleEntry> entries = new ArrayList<>();

        try (Stream<Path> files = Files.list(rootDir)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName() != null && path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .forEach(path -> addCycleEntry(entries, path, caseId));
        } catch (IOException ignore) {
            return List.of();
        }

        return entries;
    }

    private void addCycleEntry(List<CycleEntry> entries, Path path, String caseId) {
        CycleDraft draft = CycleCardJsonReader.readDraft(path);
        if (draft == null || draft.cases == null || draft.cases.isEmpty()) {
            return;
        }

        for (CycleCaseRef ref : draft.cases) {
            if (ref == null) continue;
            if (!safe(caseId).equals(ref.safeId())) continue;

            entries.add(new CycleEntry(
                    safe(draft.id),
                    safe(draft.title),
                    safe(draft.createdAtUi),
                    ref.safeStatus(),
                    ref.safeComment()
            ));
            return;
        }
    }

    private void openCommentModal(Button anchor, boolean editable, String comment, Consumer<String> onSaved) {
        if (anchor == null || modalHost == null) return;

        CaseCommentModal modal = (CaseCommentModal) anchor.getProperties().get("cy.case.comment.modal");
        if (modal == null) {
            modal = new CaseCommentModal(anchor);
            modal.install(modalHost);
            anchor.getProperties().put("cy.case.comment.modal", modal);
        }

        String value = safe(comment);
        modal.setCurrentValueSupplier(() -> value);
        modal.setEditableSupplier(() -> editable);
        modal.setOnSaved(onSaved == null ? s -> {} : onSaved);
        modal.toggle();
    }

    private static boolean sameCycle(CycleEntry left, CycleEntry right) {
        if (left == null || right == null) return false;

        String leftId = safe(left.id());
        String rightId = safe(right.id());
        if (!leftId.isBlank() && !rightId.isBlank()) {
            return leftId.equals(rightId);
        }

        return safe(left.title()).equals(safe(right.title()))
                && safe(left.createdAtUi()).equals(safe(right.createdAtUi()));
    }

    private void copyCurrentCycleName() {
        String value = safe(currentCycleName.getText());
        if (value.isBlank()) return;

        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
        showCurrentCycleCopyHint();
    }

    private void showCurrentCycleCopyHint() {
        currentCycleCopyHint.setText(I18n.t("cy.case.current.copy.hint"));
        currentCycleCopyHint.setVisible(true);
        currentCycleCopyHint.setOpacity(0.0);

        if (currentCycleCopyHintAnimation != null) {
            currentCycleCopyHintAnimation.stop();
        }

        FadeTransition fadeIn = new FadeTransition(Duration.millis(160), currentCycleCopyHint);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        PauseTransition hold = new PauseTransition(Duration.millis(1600));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(240), currentCycleCopyHint);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> hideCurrentCycleCopyHint());

        currentCycleCopyHintAnimation = new SequentialTransition(fadeIn, hold, fadeOut);
        currentCycleCopyHintAnimation.playFromStart();
    }

    private void hideCurrentCycleCopyHint() {
        if (currentCycleCopyHintAnimation != null) {
            currentCycleCopyHintAnimation.stop();
            currentCycleCopyHintAnimation = null;
        }
        currentCycleCopyHint.setOpacity(0.0);
        currentCycleCopyHint.setVisible(false);
    }

    private static String formatCurrentTitle(int caseNumber, int caseTotal) {
        String base = I18n.t("cy.case.status.footer");
        if (caseNumber <= 0 || caseTotal <= 0) return base;
        return base + " " + caseNumber + "/" + caseTotal;
    }

    private static String formatCycleCaption(String title, String createdAtUi, String fallbackId) {
        String safeTitle = safe(title);
        String safeCreatedAt = safe(createdAtUi);
        String safeId = safe(fallbackId);

        String head = safeTitle.isBlank() ? safeId : safeTitle;
        if (head.isBlank()) head = "-";
        if (safeCreatedAt.isBlank()) return head;
        return head + " " + safeCreatedAt;
    }

    private static String formatStatusText(String status) {
        String safeStatus = safe(status);
        if (!safeStatus.isBlank()) return safeStatus;

        String placeholder = I18n.t("cy.case.status.placeholder");
        return placeholder == null ? "" : placeholder;
    }

    private static String formatCommentPreview(String comment) {
        String safeComment = safe(comment);
        if (safeComment.isBlank()) {
            String empty = I18n.t("cy.case.comment.empty");
            return empty == null || empty.isBlank() ? I18n.t("cy.case.comment.btn") : empty;
        }
        return safeComment;
    }

    private static Tooltip buildCommentTooltip(String comment) {
        String safeComment = safe(comment);
        if (safeComment.isBlank()) return null;
        return new Tooltip(safeComment);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record CycleEntry(String id, String title, String createdAtUi, String status, String comment) {
    }
}
