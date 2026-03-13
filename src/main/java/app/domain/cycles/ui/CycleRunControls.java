package app.domain.cycles.ui;

import app.core.I18n;
import app.domain.cycles.usecase.CycleRunState;
import app.ui.UiSvg;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.time.LocalDateTime;

public final class CycleRunControls {

    private static final String ICON_PAUSE = "pause-cycle.svg";
    private static final String ICON_RESUME = "resume-cycle.svg";
    private static final String ICON_RESET = "reset-cycle.svg";
    private static final double CAPSULE_WIDTH = 225.0;
    private static final double CAPSULE_HEIGHT = 36.0;
    private static final double CHIP_HEIGHT = 28.0;
    private static final double CHIP_WIDTH = 104.0;
    private static final double TIMER_GAP = 3.0;
    private static final double ICON_SIZE = 14.0;
    private static final double BUTTON_SIZE = 26.0;

    private final HBox root = new HBox(4.0);
    private final Button stateChip = new Button();
    private final Button pauseResumeButton = new Button();
    private final Button resetButton = new Button();
    private final Label timerLabel = new Label();
    private final Tooltip timerTooltip = new Tooltip();
    private final Region spacer = new Region();

    private String runState = CycleRunState.IDLE;
    private long elapsedSeconds = 0L;
    private String startedAtIso = "";

    private Runnable onPrimaryRequested;
    private Runnable onPauseResumeRequested;
    private Runnable onResetRequested;

    private final Timeline tick;
    private Parent themedRoot;
    private boolean chipHovered;
    private boolean chipPressed;

    private final ListChangeListener<String> themeClassListener = change -> applySurfaceStyle();
    private final ChangeListener<Parent> sceneRootListener = (obs, oldRoot, newRoot) -> {
        detachThemeRoot(oldRoot);
        attachThemeRoot(newRoot);
        applySurfaceStyle();
    };
    private final ChangeListener<Scene> sceneListener = (obs, oldScene, newScene) -> {
        if (oldScene != null) {
            oldScene.rootProperty().removeListener(sceneRootListener);
            detachThemeRoot(oldScene.getRoot());
        }
        if (newScene != null) {
            newScene.rootProperty().addListener(sceneRootListener);
            attachThemeRoot(newScene.getRoot());
        }
        applySurfaceStyle();
    };

    public CycleRunControls() {
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPickOnBounds(false);
        root.setFillHeight(false);
        root.setMinWidth(CAPSULE_WIDTH);
        root.setPrefWidth(CAPSULE_WIDTH);
        root.setMaxWidth(CAPSULE_WIDTH);
        root.setMinHeight(CAPSULE_HEIGHT);
        root.setPrefHeight(CAPSULE_HEIGHT);
        root.setMaxHeight(CAPSULE_HEIGHT);
        root.sceneProperty().addListener(sceneListener);

        stateChip.setFocusTraversable(false);
        stateChip.setContentDisplay(ContentDisplay.TEXT_ONLY);
        stateChip.setMinHeight(CHIP_HEIGHT);
        stateChip.setPrefHeight(CHIP_HEIGHT);
        stateChip.setMaxHeight(CHIP_HEIGHT);
        stateChip.setMinWidth(CHIP_WIDTH);
        stateChip.setPrefWidth(CHIP_WIDTH);
        stateChip.setMaxWidth(CHIP_WIDTH);
        stateChip.setOnAction(e -> {
            if (stateChip.isDisabled()) return;
            if (onPrimaryRequested != null) onPrimaryRequested.run();
        });
        stateChip.setOnMouseEntered(e -> {
            chipHovered = true;
            applyChipStyle();
        });
        stateChip.setOnMouseExited(e -> {
            chipHovered = false;
            chipPressed = false;
            stateChip.setTranslateY(0);
            applyChipStyle();
        });
        stateChip.setOnMousePressed(e -> {
            chipPressed = true;
            stateChip.setTranslateY(1.2);
            applyChipStyle();
        });
        stateChip.setOnMouseReleased(e -> {
            chipPressed = false;
            stateChip.setTranslateY(0);
            applyChipStyle();
        });

        configureIconButton(pauseResumeButton);
        configureIconButton(resetButton);
        pauseResumeButton.setTooltip(new Tooltip(textPause()));
        resetButton.setTooltip(new Tooltip(textReset()));

        pauseResumeButton.setOnAction(e -> {
            if (pauseResumeButton.isDisabled()) return;
            if (onPauseResumeRequested != null) onPauseResumeRequested.run();
        });
        resetButton.setOnAction(e -> {
            if (resetButton.isDisabled()) return;
            if (onResetRequested != null) onResetRequested.run();
        });

        UiSvg.setButtonSvg(pauseResumeButton, ICON_PAUSE, ICON_SIZE);
        UiSvg.setButtonSvg(resetButton, ICON_RESET, ICON_SIZE);

        timerLabel.setMinWidth(Region.USE_PREF_SIZE);
        timerLabel.setStyle("-fx-text-fill: -fx-text-color; -fx-font-size: 13px; -fx-font-weight: 800;");
        timerTooltip.setShowDelay(Duration.millis(120));
        timerLabel.setTooltip(timerTooltip);

        spacer.setMinWidth(TIMER_GAP);
        spacer.setPrefWidth(TIMER_GAP);
        spacer.setMaxWidth(TIMER_GAP);
        root.getChildren().addAll(stateChip, pauseResumeButton, resetButton, spacer, timerLabel);

        tick = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshTimer()));
        tick.setCycleCount(Animation.INDEFINITE);

        if (root.getScene() != null) {
            attachThemeRoot(root.getScene().getRoot());
        }
        applySurfaceStyle();
        setVisible(false);
    }

    public Node node() {
        return root;
    }

    public void setVisible(boolean visible) {
        root.setVisible(visible);
        root.setManaged(visible);
        if (!visible) {
            tick.stop();
        } else if (CycleRunState.isRunning(runState)) {
            tick.playFromStart();
        }
    }

    public void setActions(Runnable onPrimaryRequested, Runnable onPauseResumeRequested, Runnable onResetRequested) {
        this.onPrimaryRequested = onPrimaryRequested;
        this.onPauseResumeRequested = onPauseResumeRequested;
        this.onResetRequested = onResetRequested;
    }

    public void update(String runState, long elapsedSeconds, String startedAtIso, boolean available) {
        this.runState = CycleRunState.normalize(runState);
        this.elapsedSeconds = Math.max(0L, elapsedSeconds);
        this.startedAtIso = startedAtIso == null ? "" : startedAtIso.trim();

        setVisible(available);
        if (!available) return;

        applySurfaceStyle();

        if (CycleRunState.isIdle(this.runState)) {
            stateChip.setText(textStart());
            applyChipStyle();
            stateChip.setDisable(false);
            UiSvg.setButtonSvg(pauseResumeButton, ICON_PAUSE, ICON_SIZE);
            pauseResumeButton.setTooltip(new Tooltip(textPause()));
            pauseResumeButton.setDisable(true);
            resetButton.setDisable(true);
            timerLabel.setVisible(false);
            timerLabel.setManaged(false);
            timerLabel.setText("");
            timerTooltip.setText("");
            tick.stop();
            return;
        }

        stateChip.setText(CycleRunState.isFinished(this.runState) ? textFinished() : textFinish());
        applyChipStyle();
        stateChip.setDisable(CycleRunState.isFinished(this.runState));

        if (CycleRunState.isRunning(this.runState)) {
            UiSvg.setButtonSvg(pauseResumeButton, ICON_PAUSE, ICON_SIZE);
            pauseResumeButton.setTooltip(new Tooltip(textPause()));
            pauseResumeButton.setDisable(false);
            resetButton.setDisable(false);
            timerLabel.setVisible(true);
            timerLabel.setManaged(true);
            refreshTimer();
            tick.playFromStart();
            return;
        }

        UiSvg.setButtonSvg(pauseResumeButton, ICON_RESUME, ICON_SIZE);
        pauseResumeButton.setTooltip(new Tooltip(textResume()));
        pauseResumeButton.setDisable(CycleRunState.isFinished(this.runState));
        resetButton.setDisable(false);
        timerLabel.setVisible(true);
        timerLabel.setManaged(true);
        refreshTimer();
        tick.stop();
    }

    private void configureIconButton(Button button) {
        button.getStyleClass().addAll("icon-btn", "xs");
        button.setFocusTraversable(false);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setMinWidth(BUTTON_SIZE);
        button.setPrefWidth(BUTTON_SIZE);
        button.setMaxWidth(BUTTON_SIZE);
        button.setMinHeight(BUTTON_SIZE);
        button.setPrefHeight(BUTTON_SIZE);
        button.setMaxHeight(BUTTON_SIZE);
    }

    private void attachThemeRoot(Parent rootNode) {
        if (rootNode == null || rootNode == themedRoot) return;
        detachThemeRoot(themedRoot);
        themedRoot = rootNode;
        themedRoot.getStyleClass().addListener(themeClassListener);
    }

    private void detachThemeRoot(Parent rootNode) {
        if (rootNode == null) return;
        rootNode.getStyleClass().removeListener(themeClassListener);
        if (rootNode == themedRoot) {
            themedRoot = null;
        }
    }

    private void applySurfaceStyle() {
        root.setStyle(capsuleStyle());
        applyChipStyle();
    }

    private void refreshTimer() {
        long totalSeconds = resolveDisplayedElapsedSeconds();
        long totalMinutes = totalSeconds / 60L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        long seconds = totalSeconds % 60L;
        String shortTime = String.format("%02d:%02d", hours, minutes);
        String fullTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        timerLabel.setText(shortTime);
        timerTooltip.setText(String.format(textTimerTooltip(), fullTime));
    }

    private long resolveDisplayedElapsedSeconds() {
        if (!CycleRunState.isRunning(runState)) {
            return Math.max(0L, elapsedSeconds);
        }

        LocalDateTime started = parseIso(startedAtIso);
        if (started == null) return Math.max(0L, elapsedSeconds);

        long delta = Math.max(0L, java.time.Duration.between(started, LocalDateTime.now()).getSeconds());
        return Math.max(0L, elapsedSeconds + delta);
    }

    private static LocalDateTime parseIso(String iso) {
        try {
            if (iso == null || iso.isBlank()) return null;
            return LocalDateTime.parse(iso.trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private String capsuleStyle() {
        if (isLightTheme()) {
            return "-fx-padding: 0 8 0 8;"
                    + " -fx-spacing: 4;"
                    + " -fx-background-color: #f1f1f1;"
                    + " -fx-background-radius: 14;"
                    + " -fx-border-radius: 14;"
                    + " -fx-border-width: 1;"
                    + " -fx-border-color: #bfbab9;";
        }
        return "-fx-padding: 0 8 0 8;"
                + " -fx-spacing: 4;"
                + " -fx-background-color: #393736;"
                + " -fx-background-radius: 14;"
                + " -fx-border-radius: 14;"
                + " -fx-border-width: 1;"
                + " -fx-border-color: #676361;";
    }

    private void applyChipStyle() {
        stateChip.setStyle(chipStyle(!CycleRunState.isIdle(runState), chipHovered, chipPressed));
        if (!chipPressed) {
            stateChip.setTranslateY(0);
        }
    }

    private String chipStyle(boolean active, boolean hovered, boolean pressed) {
        String base = "-fx-background-radius: 999; -fx-border-radius: 999; -fx-padding: 0 14 0 14; -fx-font-size: 13px; -fx-font-weight: 800; -fx-opacity: 1;";
        if (CycleRunState.isFinished(runState)) {
            if (isLightTheme()) {
                return base + " -fx-background-color: rgb(214, 230, 220); -fx-border-color: rgba(67, 117, 92, 0.36); -fx-border-width: 1; -fx-text-fill: rgb(31, 62, 47);";
            }
            return base + " -fx-background-color: rgb(58, 95, 78); -fx-border-color: rgba(148, 199, 171, 0.26); -fx-border-width: 1; -fx-text-fill: white;";
        }
        if (active) {
            if (pressed) {
                return base + " -fx-background-color: rgb(122, 12, 36); -fx-border-color: rgba(255,255,255,0.28); -fx-border-width: 1; -fx-text-fill: white; -fx-effect: innershadow(gaussian, rgba(0,0,0,0.42), 6, 0.2, 0, 1);";
            }
            if (hovered) {
                return base + " -fx-background-color: rgb(175, 30, 61); -fx-border-color: rgba(255,255,255,0.18); -fx-border-width: 1; -fx-text-fill: white;";
            }
            return base + " -fx-background-color: rgb(163, 25, 55); -fx-border-color: rgba(255,255,255,0.12); -fx-border-width: 1; -fx-text-fill: white;";
        }
        if (isLightTheme()) {
            if (pressed) {
                return base + " -fx-background-color: rgb(208, 203, 201); -fx-border-color: rgba(245, 0, 73, 0.92); -fx-border-width: 1; -fx-text-fill: #161616; -fx-effect: innershadow(gaussian, rgba(0,0,0,0.16), 6, 0.2, 0, 1);";
            }
            if (hovered) {
                return base + " -fx-background-color: rgb(232, 229, 228); -fx-border-color: rgba(245, 0, 73, 0.72); -fx-border-width: 1; -fx-text-fill: #161616;";
            }
            return base + " -fx-background-color: rgb(226, 223, 221); -fx-border-color: rgba(245, 0, 73, 0.58); -fx-border-width: 1; -fx-text-fill: #161616;";
        }
        if (pressed) {
            return base + " -fx-background-color: rgb(44, 40, 39); -fx-border-color: rgba(245, 0, 73, 1.0); -fx-border-width: 1; -fx-text-fill: white; -fx-effect: innershadow(gaussian, rgba(0,0,0,0.45), 6, 0.2, 0, 1);";
        }
        if (hovered) {
            return base + " -fx-background-color: rgb(72, 68, 67); -fx-border-color: rgba(245, 0, 73, 0.88); -fx-border-width: 1; -fx-text-fill: white;";
        }
        return base + " -fx-background-color: rgb(64, 60, 59); -fx-border-color: rgba(245, 0, 73, 0.70); -fx-border-width: 1; -fx-text-fill: white;";
    }

    private boolean isLightTheme() {
        Parent currentRoot = themedRoot;
        if (currentRoot == null && root.getScene() != null) {
            currentRoot = root.getScene().getRoot();
        }
        return currentRoot != null && currentRoot.getStyleClass().contains("theme-light");
    }

    private String textStart() {
        return localizedText("cy.run.start", "Start");
    }

    private String textFinish() {
        return localizedText("cy.run.finish", "Finish");
    }

    private String textFinished() {
        return localizedText("cy.run.finished", "Finished");
    }

    private String textPause() {
        return localizedText("cy.menu.pause", "Pause");
    }

    private String textResume() {
        return localizedText("cy.run.resume", "Resume");
    }

    private String textReset() {
        return localizedText("cy.run.reset", "Reset");
    }

    private String textTimerTooltip() {
        return localizedText("cy.run.tooltip.full", "Cycle time: %s");
    }

    private String localizedText(String key, String fallback) {
        String value = I18n.t(key);
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() || trimmed.equals(key) || trimmed.equals("!" + key + "!") ? fallback : value;
    }
}
