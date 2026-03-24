package app.domain.cycles.ui.right;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CycleCategoryAutocomplete {

    private static final double POPUP_OFFSET_Y = 0.0;
    private static final int MAX_VISIBLE_ITEMS = 8;
    private static final String UNFOCUSED_CLASS = "cy-category-field-unfocused";

    private final TextField field;
    private final Label displayLabel;
    private final Label ghostLabel;
    private final FlowPane suggestionsPane;
    private final Popup popup = new Popup();
    private final StackPane popupRoot = new StackPane();
    private final VBox popupPanel = new VBox(4.0);
    private final EventHandler<MouseEvent> sceneMousePressedFilter = this::onSceneMousePressed;

    private final List<String> availableValues = new ArrayList<>();

    private Runnable onValueChanged = () -> {};
    private boolean editable = true;
    private boolean applyingCompletion = false;
    private boolean suppressPopupToggle = false;
    private String bestSuggestion = "";
    private Scene attachedScene;

    public CycleCategoryAutocomplete(TextField field, Label displayLabel, Label ghostLabel, FlowPane suggestionsPane) {
        this.field = field;
        this.displayLabel = displayLabel;
        this.ghostLabel = ghostLabel;
        this.suggestionsPane = suggestionsPane;
    }

    public void init() {
        popup.setAutoHide(false);
        popup.setHideOnEscape(true);
        popup.setAutoFix(true);

        popupRoot.getStyleClass().add("root");
        popupRoot.setPickOnBounds(false);
        popupRoot.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        popupPanel.getStyleClass().add("cy-category-popup-root");
        popupPanel.setFillWidth(true);
        popupRoot.getChildren().setAll(popupPanel);
        popup.getContent().setAll(popupRoot);

        if (suggestionsPane != null) {
            suggestionsPane.setManaged(false);
            suggestionsPane.setVisible(false);
        }

        if (displayLabel != null) {
            displayLabel.setManaged(true);
            displayLabel.setVisible(false);
            displayLabel.setMouseTransparent(true);
            displayLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            displayLabel.setEllipsisString("...");
        }

        if (field != null) {
            field.textProperty().addListener((obs, oldV, newV) -> {
                if (!applyingCompletion) hidePopup();
                refreshUi();
                if (!applyingCompletion) onValueChanged.run();
            });
            field.widthProperty().addListener((obs, oldV, newV) -> {
                refreshGhostPosition();
                refreshDisplayPosition();
                refreshPopupGeometry();
            });
            field.heightProperty().addListener((obs, oldV, newV) -> refreshPopupGeometry());
            field.sceneProperty().addListener((obs, oldV, newV) -> {
                detachSceneFilter(oldV);
                attachSceneFilter(newV);
                refreshUi();
            });
            field.layoutBoundsProperty().addListener((obs, oldV, newV) -> refreshPopupGeometry());
            field.localToSceneTransformProperty().addListener((obs, oldV, newV) -> refreshPopupGeometry());
            field.fontProperty().addListener((obs, oldV, newV) -> {
                refreshGhostPosition();
                refreshDisplayPosition();
            });
            field.focusedProperty().addListener((obs, oldV, focused) -> {
                if (!focused) hidePopup();
                refreshUi();
            });
            field.setOnMouseClicked(e -> {
                if (!editable) return;
                if (suppressPopupToggle) {
                    suppressPopupToggle = false;
                    return;
                }
                if (popup.isShowing()) hidePopup();
                else showPopupIfNeeded();
                refreshUi();
            });
            field.setOnKeyPressed(e -> {
                if (!editable) return;
                if ((e.getCode() == KeyCode.TAB || e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.ENTER) && canApplySuggestion()) {
                    suppressPopupToggle = true;
                    applyBestSuggestion();
                    e.consume();
                    return;
                }
                if (e.getCode() == KeyCode.DOWN) {
                    showPopupIfNeeded();
                    e.consume();
                    return;
                }
                if (e.getCode() == KeyCode.ESCAPE) {
                    hidePopup();
                }
            });
        }

        if (ghostLabel != null) {
            ghostLabel.setManaged(true);
            ghostLabel.setMaxWidth(Double.MAX_VALUE);
            ghostLabel.setVisible(false);
            ghostLabel.setMouseTransparent(false);
            ghostLabel.setViewOrder(-1.0);
            ghostLabel.setOnMouseClicked(e -> {
                if (!editable) return;
                suppressPopupToggle = true;
                applyBestSuggestion();
                e.consume();
            });
        }

        refreshUi();
    }

    public void setOnValueChanged(Runnable onValueChanged) {
        this.onValueChanged = onValueChanged == null ? () -> {} : onValueChanged;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
        if (!editable) hidePopup();
        refreshUi();
    }

    public void setAvailableValues(List<String> values) {
        availableValues.clear();
        if (values != null) {
            for (String value : values) {
                String normalized = normalize(value);
                if (normalized.isEmpty()) continue;
                if (containsIgnoreCase(availableValues, normalized)) continue;
                availableValues.add(normalized);
            }
        }
        sortAvailableValues();
        refreshUi();
    }

    public boolean containsExactValue(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) return false;
        return containsIgnoreCase(availableValues, normalized);
    }
    public void rememberValue(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) return;
        if (!containsIgnoreCase(availableValues, normalized)) availableValues.add(normalized);
        sortAvailableValues();
        refreshUi();
    }

    public void syncFromValue(String value) {
        if (field == null) return;
        applyingCompletion = true;
        try {
            field.setText(normalize(value));
            field.positionCaret(field.getText().length());
        } finally {
            applyingCompletion = false;
        }
        refreshUi();
    }

    public String commitCurrentValue() {
        String value = currentValue();
        if (value.isEmpty()) return "";
        rememberValue(value);
        if (field != null) {
            Platform.runLater(() -> {
                field.requestFocus();
                field.end();
            });
        }
        return value;
    }

    private void refreshUi() {
        updateBestSuggestion();
        updateGhost();
        updateDisplayValue();
        rebuildPopupItems();
        if (field == null || !field.isFocused() || popupPanel.getChildren().isEmpty()) hidePopup();
    }

    private void updateBestSuggestion() {
        String value = currentValue();
        bestSuggestion = "";
        if (value.isEmpty()) return;

        String lowerValue = value.toLowerCase(Locale.ROOT);
        for (String candidate : availableValues) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lowerValue)) {
                bestSuggestion = candidate;
                return;
            }
        }
    }

    private void updateGhost() {
        if (ghostLabel == null) return;

        String value = currentValue();
        if (!editable || value.isEmpty() || bestSuggestion.isEmpty()) {
            ghostLabel.setText("");
            ghostLabel.setVisible(false);
            return;
        }

        if (!field.isFocused()) {
            ghostLabel.setText("");
            ghostLabel.setVisible(false);
            return;
        }

        if (!bestSuggestion.toLowerCase(Locale.ROOT).startsWith(value.toLowerCase(Locale.ROOT))) {
            ghostLabel.setText("");
            ghostLabel.setVisible(false);
            return;
        }

        if (bestSuggestion.equalsIgnoreCase(value)) {
            ghostLabel.setText("");
            ghostLabel.setVisible(false);
            return;
        }

        ghostLabel.setText(bestSuggestion.substring(value.length()));
        ghostLabel.setVisible(true);
        ghostLabel.toFront();
        refreshGhostPosition();
    }

    private void updateDisplayValue() {
        if (displayLabel == null || field == null) return;

        String value = currentValue();
        boolean showDisplay = !field.isFocused() && !value.isEmpty();
        displayLabel.setText(showDisplay ? value : "");
        displayLabel.setVisible(showDisplay);
        refreshDisplayPosition();

        if (showDisplay) {
            if (!field.getStyleClass().contains(UNFOCUSED_CLASS)) field.getStyleClass().add(UNFOCUSED_CLASS);
        } else {
            field.getStyleClass().remove(UNFOCUSED_CLASS);
        }
    }

    private void refreshGhostPosition() {
        if (ghostLabel == null || field == null || !ghostLabel.isVisible()) return;

        Text text = new Text(currentValue());
        text.setFont(field.getFont());
        double prefixWidth = Math.ceil(text.getLayoutBounds().getWidth());

        ghostLabel.setTranslateX(0.0);
        ghostLabel.setMaxWidth(Math.max(0.0, field.getWidth() - 16.0));
        ghostLabel.setPadding(new Insets(0, 0, 0, 12.0 + prefixWidth + 1.0));
    }

    private void refreshDisplayPosition() {
        if (displayLabel == null || field == null) return;
        double width = Math.max(0.0, field.getWidth());
        displayLabel.setMinWidth(0.0);
        displayLabel.setPrefWidth(width);
        displayLabel.setMaxWidth(width);
        displayLabel.setPadding(new Insets(0, 28, 0, 12));
    }

    private void rebuildPopupItems() {
        popupPanel.getChildren().clear();
        if (!editable || field == null) return;

        List<String> matches = findMatches();
        if (matches.isEmpty()) return;

        syncPopupTheme();
        applyPopupPanelStyle();

        for (String match : matches) {
            Label option = new Label(match);
            option.getStyleClass().add("cy-category-popup-item");
            option.setMinWidth(Region.USE_PREF_SIZE);
            option.setMaxWidth(Double.MAX_VALUE);
            option.setPrefWidth(Region.USE_COMPUTED_SIZE);
            applyPopupItemBaseStyle(option);
            option.hoverProperty().addListener((obs, oldV, hovered) -> applyPopupItemStyle(option, hovered, option.isPressed()));
            option.pressedProperty().addListener((obs, oldV, pressed) -> applyPopupItemStyle(option, option.isHover(), pressed));
            option.setOnMousePressed(e -> {
                suppressPopupToggle = true;
                applySuggestion(match);
                e.consume();
            });
            popupPanel.getChildren().add(option);
        }
    }

    private void syncPopupTheme() {
        popupPanel.getStyleClass().remove("theme-light");
        popupPanel.getStyleClass().remove("theme-dark");
        if (field == null || field.getScene() == null || field.getScene().getRoot() == null) return;
        List<String> rootClasses = field.getScene().getRoot().getStyleClass();
        if (rootClasses.contains("theme-light")) popupPanel.getStyleClass().add("theme-light");
        else popupPanel.getStyleClass().add("theme-dark");
    }

    private void applyPopupPanelStyle() {
        popupRoot.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        popupPanel.setStyle(isLightTheme()
                ? "-fx-background-color: #dfe1e3; -fx-border-color: #b5bac0; -fx-border-width: 1; -fx-background-radius: 0 0 12 12; -fx-border-radius: 0 0 12 12; -fx-padding: 4 6 6 6; -fx-opacity: 1;"
                : "-fx-background-color: #252524; -fx-border-color: #575754; -fx-border-width: 1; -fx-background-radius: 0 0 12 12; -fx-border-radius: 0 0 12 12; -fx-padding: 4 6 6 6; -fx-opacity: 1;");
    }

    private void applyPopupItemBaseStyle(Label option) {
        if (option == null) return;
        option.setStyle(buildPopupItemStyle(false, false));
    }

    private void applyPopupItemStyle(Label option, boolean hovered, boolean pressed) {
        if (option == null) return;
        option.setStyle(buildPopupItemStyle(hovered, pressed));
    }

    private String buildPopupItemStyle(boolean hovered, boolean pressed) {
        String bg = "transparent";
        if (pressed) bg = "-fx-surface-2";
        else if (hovered) bg = "-fx-surface";

        return "-fx-alignment: CENTER_LEFT; " +
                "-fx-background-color: " + bg + "; " +
                "-fx-background-insets: 0; " +
                "-fx-background-radius: 8; " +
                "-fx-padding: 7 10 7 10; " +
                "-fx-text-fill: -fx-text-color; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: 700; " +
                "-fx-cursor: hand;";
    }

    private boolean isLightTheme() {
        if (field == null || field.getScene() == null || field.getScene().getRoot() == null) return false;
        return field.getScene().getRoot().getStyleClass().contains("theme-light");
    }

    private List<String> findMatches() {
        List<String> out = new ArrayList<>();
        String value = currentValue();
        String normalized = value.toLowerCase(Locale.ROOT);

        if (normalized.isEmpty()) {
            for (String candidate : availableValues) {
                out.add(candidate);
                if (out.size() >= MAX_VISIBLE_ITEMS) break;
            }
            return out;
        }

        for (String candidate : availableValues) {
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (!lower.startsWith(normalized)) continue;
            if (candidate.equalsIgnoreCase(value)) continue;
            out.add(candidate);
            if (out.size() >= MAX_VISIBLE_ITEMS) return out;
        }

        return out;
    }

    private void showPopupIfNeeded() {
        if (field == null || !editable || popupPanel.getChildren().isEmpty()) {
            hidePopup();
            return;
        }
        if (!field.isFocused()) {
            field.requestFocus();
        }

        Bounds bounds = field.localToScreen(field.getBoundsInLocal());
        if (bounds == null) return;

        refreshPopupGeometry(bounds);
        if (!popup.isShowing()) {
            popup.show(field, bounds.getMinX(), bounds.getMaxY() + POPUP_OFFSET_Y);
            syncPopupSceneStyles();
            popupRoot.applyCss();
            popupRoot.layout();
            refreshPopupGeometry(bounds);
        }
    }

    private void syncPopupSceneStyles() {
        if (field == null || field.getScene() == null || popup.getScene() == null) return;
        List<String> popupSheets = new ArrayList<>();
        popupSheets.addAll(field.getScene().getStylesheets());
        if (field.getScene().getRoot() != null) {
            popupSheets.addAll(field.getScene().getRoot().getStylesheets());
        }
        popup.getScene().getStylesheets().setAll(popupSheets);
        popup.getScene().setFill(Color.TRANSPARENT);
        if (popup.getScene().getRoot() != null) {
            popup.getScene().getRoot().setStyle("-fx-background-color: transparent;");
        }
    }

    private void refreshPopupGeometry() {
        if (field == null) return;
        Bounds bounds = field.localToScreen(field.getBoundsInLocal());
        refreshPopupGeometry(bounds);
    }

    private void refreshPopupGeometry(Bounds bounds) {
        if (field == null || bounds == null) return;

        double width = Math.max(0.0, Math.ceil(bounds.getWidth()));
        popupPanel.setMinWidth(width);
        popupPanel.setPrefWidth(width);
        popupPanel.setMaxWidth(width);

        if (!popup.isShowing()) return;
        popup.setX(Math.round(bounds.getMinX()));
        popup.setY(Math.round(bounds.getMaxY() + POPUP_OFFSET_Y));
    }

    private void hidePopup() {
        if (popup.isShowing()) popup.hide();
    }

    private void attachSceneFilter(Scene scene) {
        if (scene == null) return;
        attachedScene = scene;
        attachedScene.addEventFilter(MouseEvent.MOUSE_PRESSED, sceneMousePressedFilter);
    }

    private void detachSceneFilter(Scene scene) {
        if (scene == null) return;
        scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, sceneMousePressedFilter);
        if (attachedScene == scene) attachedScene = null;
    }

    private void onSceneMousePressed(MouseEvent event) {
        if (!popup.isShowing() || field == null) return;
        double screenX = event.getScreenX();
        double screenY = event.getScreenY();
        if (isInsideScreenBounds(field.localToScreen(field.getBoundsInLocal()), screenX, screenY)) return;
        if (isInsideScreenBounds(popupPanel.localToScreen(popupPanel.getBoundsInLocal()), screenX, screenY)) return;
        hidePopup();
    }

    private boolean canApplySuggestion() {
        String value = currentValue();
        if (value.isEmpty() || bestSuggestion.isEmpty()) return false;
        if (bestSuggestion.equalsIgnoreCase(value)) return false;
        return bestSuggestion.toLowerCase(Locale.ROOT).startsWith(value.toLowerCase(Locale.ROOT));
    }

    private void applyBestSuggestion() {
        if (!canApplySuggestion()) return;
        applySuggestion(bestSuggestion);
    }

    private void applySuggestion(String suggestion) {
        if (field == null) return;
        String value = normalize(suggestion);
        if (value.isEmpty()) return;

        hidePopup();
        applyingCompletion = true;
        try {
            field.setText(value);
            field.positionCaret(value.length());
        } finally {
            applyingCompletion = false;
        }

        refreshUi();
        onValueChanged.run();
        Platform.runLater(() -> {
            field.requestFocus();
            field.end();
        });
    }

    private String currentValue() {
        return field == null ? "" : normalize(field.getText());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        if (values == null || candidate == null) return false;
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    private static boolean isInsideScreenBounds(Bounds bounds, double screenX, double screenY) {
        return bounds != null
                && screenX >= bounds.getMinX()
                && screenX <= bounds.getMaxX()
                && screenY >= bounds.getMinY()
                && screenY <= bounds.getMaxY();
    }

    private void sortAvailableValues() {
        availableValues.sort(String.CASE_INSENSITIVE_ORDER);
    }
}