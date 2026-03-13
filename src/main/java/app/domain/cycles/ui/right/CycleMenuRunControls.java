package app.domain.cycles.ui.right;

import app.core.I18n;
import app.domain.cycles.usecase.CycleRunState;
import app.ui.UiSvg;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

public final class CycleMenuRunControls {

    private static final String SVG_NAME_KEY = "svgName";
    private static final String ICON_PAUSE = "pause-cycle.svg";
    private static final String ICON_RESUME = "resume-cycle.svg";
    private static final String ICON_RESET = "reset-cycle.svg";
    private static final double PRIMARY_WIDTH = 136.0;
    private static final double ICON_BUTTON_WIDTH = 38.0;
    private static final double ICON_SIZE = 14.0;

    private final HBox root = new HBox(4.0);
    private final Button primaryButton = new Button();
    private final Button pauseResumeButton = new Button();
    private final Button resetButton = new Button();

    private Runnable onPrimaryRequested;
    private Runnable onPauseResumeRequested;
    private Runnable onResetRequested;

    private String runState = CycleRunState.IDLE;
    private boolean available;

    public CycleMenuRunControls() {
        root.setAlignment(Pos.CENTER);
        root.setFillHeight(false);
        root.getStyleClass().add("cy-menu-run-row");

        configurePrimaryButton();
        configureIconButton(pauseResumeButton, ICON_PAUSE);
        configureIconButton(resetButton, ICON_RESET);

        pauseResumeButton.setOnAction(e -> {
            if (pauseResumeButton.isDisabled()) return;
            if (onPauseResumeRequested != null) onPauseResumeRequested.run();
        });
        resetButton.setOnAction(e -> {
            if (resetButton.isDisabled()) return;
            if (onResetRequested != null) onResetRequested.run();
        });

        root.getChildren().addAll(primaryButton, pauseResumeButton, resetButton);
        update(CycleRunState.IDLE, false);
    }

    public Node node() {
        return root;
    }

    public void setActions(Runnable onPrimaryRequested, Runnable onPauseResumeRequested, Runnable onResetRequested) {
        this.onPrimaryRequested = onPrimaryRequested;
        this.onPauseResumeRequested = onPauseResumeRequested;
        this.onResetRequested = onResetRequested;
    }

    public void update(String runState, boolean available) {
        this.runState = CycleRunState.normalize(runState);
        this.available = available;

        if (!available) {
            primaryButton.setText(textStart());
            primaryButton.setDisable(true);
            pauseResumeButton.getProperties().put(SVG_NAME_KEY, ICON_PAUSE);
            UiSvg.setButtonSvg(pauseResumeButton, ICON_PAUSE, ICON_SIZE);
            pauseResumeButton.setTooltip(new Tooltip(textPause()));
            pauseResumeButton.setDisable(true);
            resetButton.getProperties().put(SVG_NAME_KEY, ICON_RESET);
            UiSvg.setButtonSvg(resetButton, ICON_RESET, ICON_SIZE);
            resetButton.setTooltip(new Tooltip(textReset()));
            resetButton.setDisable(true);
            return;
        }

        if (CycleRunState.isIdle(this.runState)) {
            primaryButton.setText(textStart());
            primaryButton.setDisable(false);
            pauseResumeButton.getProperties().put(SVG_NAME_KEY, ICON_PAUSE);
            UiSvg.setButtonSvg(pauseResumeButton, ICON_PAUSE, ICON_SIZE);
            pauseResumeButton.setTooltip(new Tooltip(textPause()));
            pauseResumeButton.setDisable(true);
            resetButton.getProperties().put(SVG_NAME_KEY, ICON_RESET);
            UiSvg.setButtonSvg(resetButton, ICON_RESET, ICON_SIZE);
            resetButton.setTooltip(new Tooltip(textReset()));
            resetButton.setDisable(true);
            return;
        }

        primaryButton.setText(CycleRunState.isFinished(this.runState) ? textFinished() : textFinish());
        primaryButton.setDisable(CycleRunState.isFinished(this.runState));

        if (CycleRunState.isRunning(this.runState)) {
            pauseResumeButton.getProperties().put(SVG_NAME_KEY, ICON_PAUSE);
            UiSvg.setButtonSvg(pauseResumeButton, ICON_PAUSE, ICON_SIZE);
            pauseResumeButton.setTooltip(new Tooltip(textPause()));
            pauseResumeButton.setDisable(false);
            resetButton.getProperties().put(SVG_NAME_KEY, ICON_RESET);
            UiSvg.setButtonSvg(resetButton, ICON_RESET, ICON_SIZE);
            resetButton.setTooltip(new Tooltip(textReset()));
            resetButton.setDisable(false);
            return;
        }

        pauseResumeButton.getProperties().put(SVG_NAME_KEY, ICON_RESUME);
        UiSvg.setButtonSvg(pauseResumeButton, ICON_RESUME, ICON_SIZE);
        pauseResumeButton.setTooltip(new Tooltip(textResume()));
        pauseResumeButton.setDisable(CycleRunState.isFinished(this.runState));
        resetButton.getProperties().put(SVG_NAME_KEY, ICON_RESET);
        UiSvg.setButtonSvg(resetButton, ICON_RESET, ICON_SIZE);
        resetButton.setTooltip(new Tooltip(textReset()));
        resetButton.setDisable(false);
    }

    private void configurePrimaryButton() {
        primaryButton.getStyleClass().add("cy-modal-btn");
        primaryButton.setFocusTraversable(false);
        primaryButton.setPrefWidth(PRIMARY_WIDTH);
        primaryButton.setMinWidth(PRIMARY_WIDTH);
        primaryButton.setMaxWidth(PRIMARY_WIDTH);
        primaryButton.setOnAction(e -> {
            if (primaryButton.isDisabled()) return;
            if (onPrimaryRequested != null) onPrimaryRequested.run();
        });
    }

    private void configureIconButton(Button button, String svgName) {
        button.getStyleClass().addAll("cy-modal-btn", "cy-menu-run-icon");
        button.getProperties().put(SVG_NAME_KEY, svgName);
        button.setFocusTraversable(false);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setPrefWidth(ICON_BUTTON_WIDTH);
        button.setMinWidth(ICON_BUTTON_WIDTH);
        button.setMaxWidth(ICON_BUTTON_WIDTH);
        UiSvg.setButtonSvg(button, svgName, ICON_SIZE);
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

    private String localizedText(String key, String fallback) {
        String value = I18n.t(key);
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() || trimmed.equals(key) || trimmed.equals("!" + key + "!") ? fallback : value;
    }
}
