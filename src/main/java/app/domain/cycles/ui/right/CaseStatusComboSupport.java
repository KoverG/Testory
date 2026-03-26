package app.domain.cycles.ui.right;

import app.core.I18n;
import app.domain.cycles.CaseStatusRegistry;
import app.ui.UiComboBox;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class CaseStatusComboSupport {

    public static final double COMBO_WIDTH_PX = 204.0;

    private static final double HINT_OPACITY = 0.62;
    private static final String CLEAR_SENTINEL = "__@CLEAR_SELECTION@__";
    private static final String CHANGE_GUARD_KEY = "cy.case.status.change.guard";
    private static final String ACTIVE_TEXT_COLOR_KEY = "cy.case.status.active.text.color";
    private static final String THEME_SYNC_INSTALLED_KEY = "cy.case.status.theme.sync.installed";

    private static final String DARK_POPUP_TEXT = "#FFFFFF";
    private static final String LIGHT_POPUP_TEXT = "#161616";
    private static final String DARK_ACTIVE_COMBO_TEXT = "#161616";
    private static final String LIGHT_ACTIVE_COMBO_TEXT = "#161616";

    private CaseStatusComboSupport() {
    }

    public static ComboBox<String> createCombo(String... extraStyleClasses) {
        ComboBox<String> cb = new ComboBox<>();
        cb.getStyleClass().add("app-combo");
        if (extraStyleClasses != null) {
            for (String styleClass : extraStyleClasses) {
                if (styleClass == null || styleClass.isBlank()) {
                    continue;
                }
                cb.getStyleClass().add(styleClass);
            }
        }

        UiComboBox.install(cb, 0.92);
        cb.setFocusTraversable(false);
        cb.setPrefWidth(COMBO_WIDTH_PX);
        cb.setMaxWidth(COMBO_WIDTH_PX);
        installThemeSync(cb);
        return cb;
    }

    public static void install(ComboBox<String> cb, Consumer<String> onStatusChanged) {
        if (cb == null) {
            return;
        }

        final Map<String, String> colorsByText = statusEntries();
        final List<String> baseItems = new ArrayList<>(colorsByText.keySet());
        final String hintStatus = safe(I18n.t("cy.case.status.placeholder"));
        final String clearLabel = safe(I18n.t("cy.combo.clearSelection"));

        cb.setPromptText(hintStatus);

        cb.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setOpacity(1.0);
                    setStyle("");
                    return;
                }

                setText(CLEAR_SENTINEL.equals(item) ? clearLabel : CaseStatusRegistry.displayLabel(item));
                setOpacity(1.0);
                setStyle("-fx-text-fill: " + popupTextColor(cb) + ";");
            }
        });

        cb.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                boolean noValue = empty || item == null || item.isBlank() || CLEAR_SENTINEL.equals(item);
                if (noValue) {
                    setText(hintStatus);
                    setOpacity(HINT_OPACITY);
                    setStyle("");
                    return;
                }

                setText(CaseStatusRegistry.displayLabel(item));
                setOpacity(1.0);
                setStyle("-fx-text-fill: " + activeComboTextColor(cb) + ";");
            }
        });

        final Runnable syncItemsWithSelection = () -> {
            String value = cb.getValue();
            boolean hasSelection = value != null && !value.isBlank() && !CLEAR_SENTINEL.equals(value);

            if (hasSelection) {
                List<String> withClear = new ArrayList<>(baseItems.size() + 1);
                withClear.add(CLEAR_SENTINEL);
                withClear.addAll(baseItems);
                cb.getItems().setAll(withClear);
            } else {
                cb.getItems().setAll(baseItems);
            }

            cb.setVisibleRowCount(Math.max(1, cb.getItems().size()));
        };

        syncItemsWithSelection.run();
        cb.setOnShowing(e -> syncItemsWithSelection.run());

        cb.valueProperty().addListener((obs, oldV, newV) -> {
            if (Boolean.TRUE.equals(cb.getProperties().get(CHANGE_GUARD_KEY))) {
                return;
            }

            if (CLEAR_SENTINEL.equals(newV)) {
                cb.hide();
                Platform.runLater(() -> {
                    if (onStatusChanged != null) {
                        onStatusChanged.accept("");
                    }
                    setStatus(cb, "");
                });
                return;
            }

            applyComboColor(cb, newV, colorsByText.get(newV));
            syncItemsWithSelection.run();

            if (onStatusChanged != null) {
                onStatusChanged.accept(safe(newV));
            }
        });
    }

    public static void setStatus(ComboBox<String> cb, String status) {
        if (cb == null) {
            return;
        }

        String normalized = safe(status);
        cb.getProperties().put(CHANGE_GUARD_KEY, Boolean.TRUE);
        try {
            if (normalized.isBlank()) {
                cb.setValue(null);
                cb.setStyle("");
            } else {
                if (!cb.getItems().contains(normalized)) {
                    cb.getItems().add(normalized);
                }
                cb.setValue(normalized);
            }
        } finally {
            cb.getProperties().put(CHANGE_GUARD_KEY, Boolean.FALSE);
        }

        Map<String, String> colorsByText = statusEntries();
        applyComboColor(cb, normalized, colorsByText.get(normalized));

        if (normalized.isBlank()) {
            cb.getItems().setAll(colorsByText.keySet());
        } else {
            List<String> withClear = new ArrayList<>(colorsByText.size() + 1);
            withClear.add(CLEAR_SENTINEL);
            withClear.addAll(colorsByText.keySet());
            if (!withClear.contains(normalized)) {
                withClear.add(normalized);
            }
            cb.getItems().setAll(withClear);
            cb.setValue(normalized);
        }
    }

    public static Map<String, String> statusEntries() {
        Map<String, String> fromCfg = CaseStatusRegistry.comboColors();
        if (fromCfg != null && !fromCfg.isEmpty()) {
            return fromCfg;
        }

        return Map.of();
    }

    public static String resolveBackgroundWeb(Node node, String status) {
        String key = safe(status);
        String color = safe(statusEntries().get(key));
        boolean lightTheme = isLightTheme(node);
        return toWeb(resolveStatusColor(key, color, lightTheme));
    }

    public static String resolveBorderWeb(Node node, String status) {
        String key = safe(status);
        String color = safe(statusEntries().get(key));
        boolean lightTheme = isLightTheme(node);
        return toWeb(adjustBorder(resolveStatusColor(key, color, lightTheme), lightTheme));
    }

    public static String resolvePopupTextWeb(Node node) {
        return isLightTheme(node) ? LIGHT_POPUP_TEXT : DARK_POPUP_TEXT;
    }

    public static String resolveActiveTextWeb(Node node) {
        return isLightTheme(node) ? LIGHT_ACTIVE_COMBO_TEXT : DARK_ACTIVE_COMBO_TEXT;
    }

    private static void applyComboColor(ComboBox<?> cb, String status, String cssColor) {
        if (cb == null) {
            return;
        }

        String color = safe(cssColor);
        if (color.isBlank()) {
            cb.setStyle("");
            cb.getProperties().put(ACTIVE_TEXT_COLOR_KEY, "");
            if (cb.getButtonCell() != null) {
                cb.getButtonCell().setStyle("");
            }
            return;
        }

        boolean lightTheme = isLightTheme(cb);
        Color background = resolveStatusColor(status, color, lightTheme);
        String textColor = activeComboTextColor(cb);
        String borderColor = toWeb(adjustBorder(background, lightTheme));

        cb.getProperties().put(ACTIVE_TEXT_COLOR_KEY, textColor);
        if (cb.getButtonCell() != null) {
            cb.getButtonCell().setStyle("-fx-text-fill: " + textColor + ";");
        }

        cb.setStyle(
                "-fx-background-color: " + toWeb(background) + ";" +
                        "-fx-background-insets: 1;" +
                        "-fx-border-color: " + borderColor + ";" +
                        "-fx-border-insets: 0;" +
                        "-fx-border-width: 1;" +
                        "-fx-text-color: " + textColor + ";"
        );
    }

    private static void installThemeSync(ComboBox<String> cb) {
        if (cb == null || Boolean.TRUE.equals(cb.getProperties().get(THEME_SYNC_INSTALLED_KEY))) {
            return;
        }
        cb.getProperties().put(THEME_SYNC_INSTALLED_KEY, Boolean.TRUE);

        cb.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null || newScene.getRoot() == null) {
                return;
            }

            newScene.getRoot().getStyleClass().addListener((ListChangeListener<String>) change ->
                    Platform.runLater(() -> applyComboColor(cb, safe(cb.getValue()), statusEntries().get(safe(cb.getValue())))));
            cb.showingProperty().addListener((o, oldShowing, newShowing) ->
                    Platform.runLater(() -> applyComboColor(cb, safe(cb.getValue()), statusEntries().get(safe(cb.getValue())))));
            Platform.runLater(() -> applyComboColor(cb, safe(cb.getValue()), statusEntries().get(safe(cb.getValue()))));
        });
    }

    private static boolean isLightTheme(Node node) {
        if (node == null || node.getScene() == null || node.getScene().getRoot() == null) {
            return false;
        }
        return node.getScene().getRoot().getStyleClass().contains("theme-light");
    }

    private static boolean isLightTheme(ComboBox<?> cb) {
        return isLightTheme((Node) cb);
    }

    private static String popupTextColor(ComboBox<?> cb) {
        return isLightTheme(cb) ? LIGHT_POPUP_TEXT : DARK_POPUP_TEXT;
    }

    private static String activeComboTextColor(ComboBox<?> cb) {
        return isLightTheme(cb) ? LIGHT_ACTIVE_COMBO_TEXT : DARK_ACTIVE_COMBO_TEXT;
    }

    private static Color resolveStatusColor(String statusRaw, String fallbackCssColor, boolean lightTheme) {
        return parseColor(fallbackCssColor);
    }

    private static Color parseColor(String cssColor) {
        try {
            return Color.web(cssColor);
        } catch (Exception ignore) {
            return Color.web("#808080");
        }
    }

    private static Color adjustBorder(Color base, boolean lightTheme) {
        return lightTheme ? mix(base, Color.BLACK, 0.20) : mix(base, Color.BLACK, 0.32);
    }

    private static Color mix(Color from, Color to, double ratio) {
        double t = Math.max(0.0, Math.min(1.0, ratio));
        double inv = 1.0 - t;
        return new Color(
                from.getRed() * inv + to.getRed() * t,
                from.getGreen() * inv + to.getGreen() * t,
                from.getBlue() * inv + to.getBlue() * t,
                1.0
        );
    }

    private static String toWeb(Color color) {
        int r = (int) Math.round(color.getRed() * 255.0);
        int g = (int) Math.round(color.getGreen() * 255.0);
        int b = (int) Math.round(color.getBlue() * 255.0);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

