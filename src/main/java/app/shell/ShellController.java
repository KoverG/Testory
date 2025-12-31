package app.shell;

import app.core.AppConfig;
import app.core.AppSettings;
import app.core.I18n;
import app.core.Router;
import app.core.View;
import app.ui.ToggleSwitch;
import app.ui.UiBlur;
import app.ui.UiSvg;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ShellController {

    private static final Duration ANIM = Duration.millis(180);

    @FXML private StackPane rootStack;
    @FXML private StackPane contentStack;

    @FXML private BorderPane appRoot;
    @FXML private VBox topBar;

    @FXML private Label lblHeaderScreen;
    @FXML private Label lblVersion;

    @FXML private Button btnMenu;
    @FXML private Button btnBack;
    @FXML private Button btnSettings;

    @FXML private VBox drawer;            // левый
    @FXML private VBox settingsDrawer;    // правый
    @FXML private Pane drawerOverlay;     // общий overlay

    // Language UI (в правом drawer)
    @FXML private Button btnLanguage;
    @FXML private VBox languageRow;
    @FXML private ComboBox<String> cbLanguage;

    // Theme UI (в правом drawer)
    @FXML private Button btnTheme;
    @FXML private HBox themeRow;
    @FXML private ToggleSwitch tgTheme;
    @FXML private Label lblTheme;

    private DrawerEngine left;
    private DrawerEngine right;

    // ===================== BLUR (через UiBlur) =====================
    private final UiBlur drawerBlur = new UiBlur(ANIM, 10.0);

    private void initBlurTargets() {
        if (appRoot != null) drawerBlur.setTargets(appRoot);
    }

    private void updateBlurForDrawers() {
        drawerBlur.setActive(anyDrawerOpen());
    }
    // ===============================================================

    // ====== Сохранение состояния правого drawer на время перезагрузки shell ======
    private static final class RestoreState {
        final boolean settingsDrawerOpen;
        final boolean languageRowOpen;
        final boolean themeRowOpen;

        RestoreState(boolean settingsDrawerOpen, boolean languageRowOpen, boolean themeRowOpen) {
            this.settingsDrawerOpen = settingsDrawerOpen;
            this.languageRowOpen = languageRowOpen;
            this.themeRowOpen = themeRowOpen;
        }
    }

    private static RestoreState PENDING_RESTORE;

    private static void setPendingRestore(RestoreState rs) {
        PENDING_RESTORE = rs;
    }

    private static RestoreState consumePendingRestore() {
        RestoreState rs = PENDING_RESTORE;
        PENDING_RESTORE = null;
        return rs;
    }
    // ============================================================================

    @FXML
    public void initialize() {
        if (lblVersion != null) lblVersion.setText("v" + AppConfig.version());

        UiSvg.setButtonSvg(btnSettings, "settings.svg", 14);
        UiSvg.setButtonSvg(btnMenu, "menu.svg", 14);
        UiSvg.setButtonSvg(btnBack, "back.svg", 14);

        Router.init(appRoot);

        // ✅ восстановить back-stack (если это reload после смены языка)
        Router.NavState ns = Router.consumePendingNavState();
        if (ns != null) {
            Router.get().restoreSnapshot(ns);
        }

        Router.get().setOnHeaderTitle(t -> {
            if (lblHeaderScreen != null) lblHeaderScreen.setText(t);
            updateBackButton();
        });

        View init = Router.consumeInitialView();
        if (init != null) Router.get().open(init);
        else Router.get().home();

        updateBackButton();

        if (topBar != null) topBar.setPickOnBounds(false);

        drawer.managedProperty().bind(drawer.visibleProperty());
        settingsDrawer.managedProperty().bind(settingsDrawer.visibleProperty());
        drawerOverlay.managedProperty().bind(drawerOverlay.visibleProperty());

        drawer.setPickOnBounds(true);
        settingsDrawer.setPickOnBounds(true);

        drawer.mouseTransparentProperty().bind(drawer.visibleProperty().not());
        settingsDrawer.mouseTransparentProperty().bind(settingsDrawer.visibleProperty().not());
        drawerOverlay.mouseTransparentProperty().bind(drawerOverlay.visibleProperty().not());

        drawerOverlay.prefWidthProperty().bind(contentStack.widthProperty());
        drawerOverlay.prefHeightProperty().bind(contentStack.heightProperty());

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(contentStack.widthProperty());
        clip.heightProperty().bind(contentStack.heightProperty());
        contentStack.setClip(clip);

        rootStack.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            newScene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE && anyDrawerOpen()) {
                    closeAnyDrawer();
                    e.consume();
                }
            });
        });

        drawer.setVisible(false);
        settingsDrawer.setVisible(false);
        drawerOverlay.setVisible(false);

        if (themeRow != null) {
            themeRow.managedProperty().bind(themeRow.visibleProperty());
            themeRow.setVisible(false);
        }

        if (languageRow != null) {
            languageRow.managedProperty().bind(languageRow.visibleProperty());
            languageRow.setVisible(false);
        }

        if (cbLanguage != null) {
            cbLanguage.getItems().setAll("Русский", "English");

            String cur = I18n.lang();
            if (cur != null && cur.toLowerCase().startsWith("en")) cbLanguage.getSelectionModel().select("English");
            else cbLanguage.getSelectionModel().select("Русский");

            cbLanguage.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
                if (b == null) return;
                String code = b.equals("English") ? "en" : "ru";

                boolean settingsOpen = (right != null && right.isOpen());
                boolean langOpen = (languageRow != null && languageRow.isVisible());
                boolean themeOpen = (themeRow != null && themeRow.isVisible());
                setPendingRestore(new RestoreState(settingsOpen, langOpen, themeOpen));

                // ✅ сохраняем back-stack ДО перезагрузки
                Router.setPendingNavState(Router.get().snapshot());

                I18n.setLang(code);
                AppSettings.setLang(code);

                reloadShellPreserveView();
            });
        }

        initBlurTargets();

        left = new DrawerEngine(
                drawer,
                drawerOverlay,
                DrawerEngine.Side.LEFT,
                this::updateOverlayGeometry,
                this::anyDrawerOpen,
                ANIM,
                this::updateBlurForDrawers,
                this::updateBlurForDrawers,
                this::updateBlurForDrawers
        );

        right = new DrawerEngine(
                settingsDrawer,
                drawerOverlay,
                DrawerEngine.Side.RIGHT,
                this::updateOverlayGeometry,
                this::anyDrawerOpen,
                ANIM,
                this::updateBlurForDrawers,
                this::updateBlurForDrawers,
                this::updateBlurForDrawers
        );

        if (tgTheme != null) {
            boolean light = AppSettings.themeLight();

            tgTheme.setAnimated(false);
            tgTheme.setSelected(light);
            tgTheme.setAnimated(true);

            applyTheme(light);

            tgTheme.selectedProperty().addListener((oo, oldV, newV) -> {
                applyTheme(newV);
                AppSettings.setThemeLight(newV);
            });

            Platform.runLater(() -> {
                installThemeIcons();
                updateThemeTexts();
            });
        } else {
            applyTheme(AppSettings.themeLight());
        }

        Platform.runLater(() -> {
            installLayoutListeners();
            updateOverlayGeometry();

            left.snapClosed();
            right.snapClosed();

            updateBlurForDrawers();
            updateBackButton();

            RestoreState rs = consumePendingRestore();
            if (rs != null) {
                if (languageRow != null) {
                    languageRow.setVisible(rs.languageRowOpen);
                    languageRow.setOpacity(1.0);
                    languageRow.setTranslateY(0.0);
                }
                if (themeRow != null) {
                    themeRow.setVisible(rs.themeRowOpen);
                    themeRow.setOpacity(1.0);
                    themeRow.setTranslateY(0.0);
                }

                if (rs.settingsDrawerOpen && right != null) {
                    right.openImmediate();
                }
            }
        });
    }

    // ===================== BACK BUTTON =====================

    private void updateBackButton() {
        if (btnBack == null) return;

        Router r = Router.get();
        View v = r.currentView();

        boolean show = (v != null && v != View.HOME && r.canGoBack());

        btnBack.setVisible(show);
        btnBack.setManaged(show);
        btnBack.setDisable(!show);
    }

    @FXML
    public void navBack() {
        if (anyDrawerOpen()) closeAnyDrawer();

        Router r = Router.get();
        if (!r.canGoBack()) {
            updateBackButton();
            return;
        }

        r.back();
        updateBackButton();
    }

    // ===================== i18n: reload shell =====================

    private void reloadShellPreserveView() {
        Stage stage = getStageSafe();
        if (stage == null) return;

        View cur = Router.get().currentView();
        if (cur != null && cur != View.HOME) Router.setInitialView(cur);
        else Router.setInitialView(null);

        Parent root = app.core.Fxml.load("/ui/shell.fxml");

        Scene scene = stage.getScene();
        if (scene != null) {
            scene.setRoot(root);
        } else {
            Scene s = new Scene(root, 1200, 800);
            s.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
            stage.setScene(s);
        }
    }

    // ===================== reset UI state of settings drawer =====================

    private void collapseThemeChooserImmediate() {
        if (themeRow == null) return;
        themeRow.setVisible(false);
        themeRow.setOpacity(1.0);
        themeRow.setTranslateY(0.0);
    }

    private void collapseLanguageChooserImmediate() {
        if (languageRow == null) return;
        languageRow.setVisible(false);
        languageRow.setOpacity(1.0);
        languageRow.setTranslateY(0.0);
    }

    private void resetSettingsDrawerUiState() {
        collapseThemeChooserImmediate();
        collapseLanguageChooserImmediate();
    }

    // =============================================================================

    private void installThemeIcons() {
        if (tgTheme == null) return;

        Node sun = createThemeIcon("sun.svg", 12);
        Node moon = createThemeIcon("moon.svg", 12);

        if (sun != null)  tgTheme.setGraphicOn(sun);
        if (moon != null) tgTheme.setGraphicOff(moon);
    }

    private Node createThemeIcon(String iconFileName, double sizePx) {
        Node n = UiSvg.createSvg(iconFileName, sizePx);
        if (n == null) return null;

        if (n instanceof SVGPath p) {
            p.getStyleClass().add("toggle-icon");
        }
        return n;
    }

    private boolean anyDrawerOpen() {
        if (left == null || right == null) return false;
        return left.isOpen() || right.isOpen();
    }

    private void installLayoutListeners() {
        contentStack.widthProperty().addListener((o, a, b) -> updateOverlayGeometry());
        contentStack.heightProperty().addListener((o, a, b) -> updateOverlayGeometry());
    }

    private void updateOverlayGeometry() {
        drawerOverlay.setTranslateY(0);

        applyFullHeight(drawer);
        applyFullHeight(settingsDrawer);
    }

    private void applyFullHeight(VBox box) {
        if (box == null) return;

        box.setTranslateY(0);

        double h = contentStack.getHeight();

        box.setPrefHeight(h);
        box.setMinHeight(h);
        box.setMaxHeight(h);
    }

    @FXML
    private void consumeInsideDrawer(MouseEvent e) {
        e.consume();
    }

    @FXML
    public void closeAnyDrawer() {
        if (left != null) left.close();

        if (right != null) {
            resetSettingsDrawerUiState();
            right.close();
        }

        updateBlurForDrawers();
    }

    @FXML
    public void toggleMenu() {
        if (left == null || right == null) return;

        if (left.isOpen()) {
            left.close();
            return;
        }

        if (right.isOpen()) {
            resetSettingsDrawerUiState();
        }
        right.closeImmediate();

        left.open();
    }

    @FXML
    public void toggleSettingsDrawer() {
        if (left == null || right == null) return;

        if (right.isOpen()) {
            resetSettingsDrawerUiState();
            right.close();
            return;
        }

        left.closeImmediate();
        right.open();
    }

    @FXML public void navHome()      { nav(View.HOME); }
    @FXML public void navTestCases() { nav(View.TEST_CASES); }
    @FXML public void navCycles()    { nav(View.CYCLES); }
    @FXML public void navHistory()   { nav(View.HISTORY); }
    @FXML public void navAnalytics() { nav(View.ANALYTICS); }
    @FXML public void navReports()   { nav(View.REPORTS); }

    private void nav(View v) {
        if (left != null) left.close();

        if (v == View.HOME) { Router.get().home(); return; }

        switch (v) {
            case TEST_CASES -> Router.get().testCases();
            case CYCLES -> Router.get().cycles();
            case HISTORY -> Router.get().history();
            case ANALYTICS -> Router.get().analytics();
            case REPORTS -> Router.get().reports();
            default -> Router.get().open(v);
        }

        updateBackButton();
    }

    @FXML
    public void openSettings() {
        if (right != null) {
            resetSettingsDrawerUiState();
            right.close();
        }
        SettingsDialog.showModal(getStageSafe());
    }

    @FXML
    public void openEditMode() {
        if (right != null) {
            resetSettingsDrawerUiState();
            right.close();
        }
        SettingsDialog.showModal(getStageSafe());
    }

    @FXML
    public void openAbout() {
        if (right != null) {
            resetSettingsDrawerUiState();
            right.close();
        }
        SettingsDialog.showModal(getStageSafe());
    }

    @FXML
    public void toggleLanguageChooser() {
        if (languageRow == null) return;

        boolean show = !languageRow.isVisible();
        animateReveal(languageRow, show);
    }

    @FXML
    public void toggleThemeChooser() {
        if (themeRow == null) return;

        boolean show = !themeRow.isVisible();
        animateReveal(themeRow, show);
    }

    private void applyTheme(boolean light) {
        if (rootStack == null) return;

        if (light) {
            if (!rootStack.getStyleClass().contains("theme-light")) {
                rootStack.getStyleClass().add("theme-light");
            }
        } else {
            rootStack.getStyleClass().remove("theme-light");
        }

        updateThemeTexts();
    }

    private void updateThemeTexts() {
        if (tgTheme == null) return;

        String t = tgTheme.isSelected()
                ? I18n.t("theme.light")
                : I18n.t("theme.dark");

        if (lblTheme != null) lblTheme.setText(t);
    }

    private void animateReveal(Node node, boolean show) {
        if (node == null) return;

        if (show) {
            node.setOpacity(0.0);
            node.setTranslateY(-6);
            node.setVisible(true);

            FadeTransition ft = new FadeTransition(ANIM, node);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);

            TranslateTransition tt = new TranslateTransition(ANIM, node);
            tt.setFromY(-6);
            tt.setToY(0);

            new ParallelTransition(ft, tt).play();
        } else {
            FadeTransition ft = new FadeTransition(ANIM, node);
            ft.setFromValue(node.getOpacity());
            ft.setToValue(0.0);

            TranslateTransition tt = new TranslateTransition(ANIM, node);
            tt.setFromY(node.getTranslateY());
            tt.setToY(-6);

            ParallelTransition pt = new ParallelTransition(ft, tt);
            pt.setOnFinished(e -> {
                node.setVisible(false);
                node.setOpacity(1.0);
                node.setTranslateY(0);
            });
            pt.play();
        }
    }

    private Stage getStageSafe() {
        if (rootStack == null || rootStack.getScene() == null) return null;
        if (rootStack.getScene().getWindow() instanceof Stage s) return s;
        return null;
    }
}
