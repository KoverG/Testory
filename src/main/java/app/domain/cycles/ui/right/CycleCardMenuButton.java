package app.domain.cycles.ui.right;

import app.core.I18n;
import app.domain.cycles.ui.modal.RightAnchoredModal;
import app.domain.cycles.usecase.CycleRunState;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDateTime;

/**
 * Left icon button in Cycles right card header (1st row).
 * Style must match btnCloseRight (icon-btn, sm; userData=14 from FXML).
 *
 * Требование:
 * - меню-модалка открывается из v.btnMenuRight
 * - позиционирование/анимация должны быть универсальными (общий класс RightAnchoredModal)
 *
 * Здесь остаётся только контент (buildMenuModal) + wiring действий.
 */
public final class CycleCardMenuButton extends Button {

    public static final String SVG_NAME_KEY = "svgName";
    public static final String SVG_NAME = "menu.svg";

    private RightAnchoredModal host;

    private Runnable onBeforeOpen = () -> {};

    private Runnable onCopy = () -> {};
    private Runnable onRunPrimary = () -> {};
    private Runnable onRunPauseResume = () -> {};
    private Runnable onRunReset = () -> {};
    private Runnable onStats = () -> {};
    private Runnable onReport = () -> {};
    private Runnable onEdit = () -> {};
    private Runnable onSource = () -> {};
    private Runnable onDelete = () -> {};

    private Button btnCopy;
    private CycleMenuRunControls runControls;
    private Button btnStats;
    private Button btnReport;
    private Button btnEdit;
    private Button btnSource;
    private Button btnDelete;
    private Label runTimerLabel;
    private Label hintLabel;

    private boolean editEnabled = false;
    private boolean editActive = false;
    private boolean reportEnabled = false;
    private boolean sourceEnabled = false;
    private String runState = CycleRunState.IDLE;
    private boolean runAvailable = false;
    private long runElapsedSeconds = 0L;
    private String runStartedAtIso = "";
    private String defaultHintText = "";

    private final Timeline timerTick = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshRunTimer()));
    private final PauseTransition hintResetFx = new PauseTransition(Duration.seconds(2.4));

    public CycleCardMenuButton() {
        setUserData(14);
        getProperties().put(SVG_NAME_KEY, SVG_NAME);
        setText("");
        setFocusTraversable(false);
        timerTick.setCycleCount(Animation.INDEFINITE);
        hintResetFx.setOnFinished(e -> resetHintText());
    }

    public void install(StackPane rightRoot, Runnable onBeforeOpen) {
        if (rightRoot == null) return;

        this.onBeforeOpen = nz(onBeforeOpen);

        host = new RightAnchoredModal(rightRoot, this, this::buildMenuModal);
        host.setOnBeforeOpen(() -> this.onBeforeOpen.run());
        host.install();
        host.close();

        setOnAction(e -> host.toggle());
    }

    public boolean isMenuOpen() {
        return host != null && host.isOpen();
    }

    public void openMenu() {
        if (host != null) host.open();
    }

    public void closeMenu() {
        if (host != null) host.close();
    }

    public void setOnCopyAction(Runnable r) { this.onCopy = nz(r); }
    public void setRunActions(Runnable onPrimary, Runnable onPauseResume, Runnable onReset) {
        this.onRunPrimary = nz(onPrimary);
        this.onRunPauseResume = nz(onPauseResume);
        this.onRunReset = nz(onReset);
        syncRunControls();
    }
    public void setOnStats(Runnable r) { this.onStats = nz(r); }
    public void setOnReport(Runnable r) { this.onReport = nz(r); }
    public void setOnEdit(Runnable r) { this.onEdit = nz(r); }
    public void setOnSource(Runnable r) { this.onSource = nz(r); }
    public void setOnDelete(Runnable r) { this.onDelete = nz(r); }

    public void setRunContext(String runState, boolean available, long elapsedSeconds, String startedAtIso) {
        this.runState = CycleRunState.normalize(runState);
        this.runAvailable = available;
        this.runElapsedSeconds = Math.max(0L, elapsedSeconds);
        this.runStartedAtIso = startedAtIso == null ? "" : startedAtIso.trim();
        syncRunControls();
        syncRunTimer();
    }

    public void setEditEnabled(boolean enabled) {
        this.editEnabled = enabled;
        syncEditButton();
    }

    public void setEditActive(boolean active) {
        this.editActive = active;
        syncEditButton();
    }

    public void setReportEnabled(boolean enabled) {
        this.reportEnabled = enabled;
        if (btnReport != null) btnReport.setDisable(!enabled);
    }

    public void setSourceEnabled(boolean enabled) {
        this.sourceEnabled = enabled;
        if (btnSource != null) btnSource.setDisable(!enabled);
    }

    public void showEditApplyHint() {
        if (hintLabel == null) return;
        hintResetFx.stop();
        if (!hintLabel.getStyleClass().contains("cy-menu-hint-warning")) {
            hintLabel.getStyleClass().add("cy-menu-hint-warning");
        }
        hintLabel.setText(editApplyHintText());
        hintResetFx.playFromStart();
    }

    private VBox buildMenuModal() {
        VBox modal = new VBox();
        modal.setAlignment(Pos.CENTER);
        modal.getStyleClass().add("tc-right-confirm");
        modal.getStyleClass().add("cy-menu-modal");

        modal.setMinWidth(300);
        modal.setPrefWidth(300);
        modal.setMaxWidth(300);
        modal.setMinHeight(Region.USE_PREF_SIZE);
        modal.setMaxHeight(Region.USE_PREF_SIZE);

        modal.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getTarget() == modal) e.consume();
        });
        Label title = new Label(I18n.t("cy.menu.title"));
        title.setWrapText(false);
        title.setMaxWidth(Double.MAX_VALUE);
        title.getStyleClass().add("tc-delete-title");
        VBox.setMargin(title, new Insets(4, 0, 0, 0));

        Region topGap = fixedSpacer(20);

        VBox buttonsBox = new VBox(10);
        buttonsBox.setAlignment(Pos.CENTER);

        runControls = new CycleMenuRunControls();
        btnCopy = buildMenuBtn("cy.menu.copy", false, false);
        btnStats = buildMenuBtn("cy.menu.stats", false, false);
        btnReport = buildMenuBtn("cy.menu.report", false, false);
        btnEdit = buildMenuBtn("cy.menu.edit", true, false);
        btnSource = buildMenuBtn("cy.menu.source", true, false);
        btnDelete = buildMenuBtn("cy.menu.delete", false, true);
        runTimerLabel = new Label();
        runTimerLabel.getStyleClass().add("cy-menu-run-timer");
        runTimerLabel.setMinWidth(220);
        runTimerLabel.setPrefWidth(220);
        runTimerLabel.setMaxWidth(220);
        runTimerLabel.setAlignment(Pos.CENTER);
        Tooltip timerTooltip = new Tooltip(textTimerTooltip());
        timerTooltip.setShowDelay(Duration.millis(120));
        runTimerLabel.setTooltip(timerTooltip);

        syncEditButton();
        btnReport.setDisable(!reportEnabled);
        btnSource.setDisable(!sourceEnabled);
        syncRunControls();
        syncRunTimer();

        buttonsBox.getChildren().addAll(
                runControls.node(),
                btnCopy,
                btnStats,
                btnReport,
                btnEdit,
                btnSource,
                btnDelete
        );

        Region bottomGap = fixedSpacer(16);

        VBox footerBox = new VBox(6);
        footerBox.setAlignment(Pos.CENTER);
        footerBox.setMinHeight(50);
        footerBox.setPrefHeight(50);
        footerBox.setMaxHeight(50);

        hintLabel = new Label(I18n.t("cy.menu.hint"));
        defaultHintText = hintLabel.getText();
        hintLabel.setWrapText(true);
        hintLabel.setMaxWidth(Double.MAX_VALUE);
        hintLabel.getStyleClass().add("tc-delete-hint");
        VBox.setMargin(hintLabel, new Insets(0, 0, 2, 0));

        footerBox.getChildren().addAll(runTimerLabel, hintLabel);

        modal.getChildren().addAll(title, topGap, buttonsBox, bottomGap, footerBox);

        btnCopy.setOnAction(e -> onCopy.run());
        btnStats.setOnAction(e -> onStats.run());
        btnReport.setOnAction(e -> onReport.run());
        btnEdit.setOnAction(e -> onEdit.run());
        btnSource.setOnAction(e -> onSource.run());
        btnDelete.setOnAction(e -> onDelete.run());

        return modal;
    }

    private void syncRunControls() {
        if (runControls == null) return;
        runControls.setActions(onRunPrimary, onRunPauseResume, onRunReset);
        runControls.update(runState, runAvailable);
    }

    private void syncRunTimer() {
        if (runTimerLabel == null) return;

        boolean visible = runAvailable && !CycleRunState.isIdle(runState);
        runTimerLabel.setManaged(true);
        runTimerLabel.setVisible(visible);
        if (!visible) {
            runTimerLabel.setText("");
            timerTick.stop();
            return;
        }

        refreshRunTimer();
        if (CycleRunState.isRunning(runState)) {
            timerTick.playFromStart();
        } else {
            timerTick.stop();
        }
    }

    private void refreshRunTimer() {
        if (runTimerLabel == null) return;
        long totalSeconds = resolveDisplayedElapsedSeconds();
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        String text = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        runTimerLabel.setText(text);
    }

    private long resolveDisplayedElapsedSeconds() {
        if (!CycleRunState.isRunning(runState)) {
            return Math.max(0L, runElapsedSeconds);
        }

        LocalDateTime started = parseIso(runStartedAtIso);
        if (started == null) return Math.max(0L, runElapsedSeconds);

        long delta = Math.max(0L, java.time.Duration.between(started, LocalDateTime.now()).getSeconds());
        return Math.max(0L, runElapsedSeconds + delta);
    }

    private static LocalDateTime parseIso(String iso) {
        try {
            if (iso == null || iso.isBlank()) return null;
            return LocalDateTime.parse(iso.trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Region fixedSpacer(double h) {
        Region r = new Region();
        r.setPrefHeight(h);
        r.setMinHeight(h);
        r.setMaxHeight(h);
        return r;
    }

    private Button buildMenuBtn(String i18nKey, boolean disabled, boolean danger) {
        Button b = new Button(I18n.t(i18nKey));
        b.setFocusTraversable(false);
        b.setPrefWidth(220);
        b.setMinWidth(220);
        b.setMaxWidth(220);
        b.getStyleClass().add("cy-modal-btn");
        if (danger) b.getStyleClass().add("cy-modal-btn-danger");
        b.setDisable(disabled);
        return b;
    }

    private void resetHintText() {
        if (hintLabel == null) return;
        hintLabel.setText(defaultHintText);
        hintLabel.getStyleClass().remove("cy-menu-hint-warning");
    }

    private String editApplyHintText() {
        String lang = I18n.lang();
        boolean ru = lang == null || lang.isBlank() || lang.toLowerCase().startsWith("ru");
        return ru ? "Сначала примените изменения" : "Apply changes first";
    }

    private void syncEditButton() {
        if (btnEdit == null) return;
        btnEdit.setDisable(!editEnabled);
        btnEdit.setText(editButtonText());
    }

    private String editButtonText() {
        String lang = I18n.lang();
        boolean ru = lang == null || lang.isBlank() || lang.toLowerCase().startsWith("ru");
        if (ru) {
            return editActive ? "Изменить: Вкл." : "Изменить: Выкл.";
        }
        return editActive ? "Edit: On" : "Edit: Off";
    }

    private String textTimerTooltip() {
        String value = I18n.t("cy.run.tooltip.full");
        if (value == null) return "Cycle time";
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.equals("cy.run.tooltip.full") || trimmed.equals("!cy.run.tooltip.full!")) {
            return "Cycle time";
        }
        int placeholder = trimmed.indexOf("%s");
        String label = placeholder >= 0 ? trimmed.substring(0, placeholder).trim() : trimmed;
        while (label.endsWith(":")) {
            label = label.substring(0, label.length() - 1).trim();
        }
        return label.isEmpty() ? "Cycle time" : label;
    }

    private static Runnable nz(Runnable r) { return (r == null) ? () -> {} : r; }
}
