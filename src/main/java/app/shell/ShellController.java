package app.shell;

import app.core.AppConfig;
import app.core.Router;
import app.core.View;
import app.ui.UiSvg;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ShellController {

    private static final Duration ANIM = Duration.millis(180);

    @FXML private StackPane rootStack;

    @FXML private BorderPane appRoot;
    @FXML private VBox topBar;

    @FXML private Label title;
    @FXML private Label lblHeaderScreen;

    @FXML private Label lblVersion;
    @FXML private Label lblScreenName;

    @FXML private Button btnMenu;
    @FXML private Button btnSettings;

    @FXML private VBox drawer;            // левый
    @FXML private VBox settingsDrawer;    // правый
    @FXML private Pane drawerOverlay;     // общий overlay

    private DrawerEngine left;
    private DrawerEngine right;

    @FXML
    public void initialize() {
        title.setText(AppConfig.title());
        lblVersion.setText("v" + AppConfig.version());

        UiSvg.setButtonSvg(btnSettings, "settings.svg", 14);
        UiSvg.setButtonSvg(btnMenu, "menu.svg", 14);

        Router.init(appRoot);
        Router.get().setOnHeaderTitle(t -> lblHeaderScreen.setText(t));
        Router.get().setOnFooterTitle(t -> lblScreenName.setText(t));
        Router.get().home();

        drawer.managedProperty().bind(drawer.visibleProperty());
        settingsDrawer.managedProperty().bind(settingsDrawer.visibleProperty());
        drawerOverlay.managedProperty().bind(drawerOverlay.visibleProperty());

        drawer.setPickOnBounds(true);
        settingsDrawer.setPickOnBounds(true);

        drawer.mouseTransparentProperty().bind(drawer.visibleProperty().not());
        settingsDrawer.mouseTransparentProperty().bind(settingsDrawer.visibleProperty().not());
        drawerOverlay.mouseTransparentProperty().bind(drawerOverlay.visibleProperty().not());

        drawerOverlay.prefWidthProperty().bind(rootStack.widthProperty());

        rootStack.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            newScene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE && (left.isOpen() || right.isOpen())) {
                    closeAnyDrawer();
                    e.consume();
                }
            });
        });

        drawer.setVisible(false);
        settingsDrawer.setVisible(false);
        drawerOverlay.setVisible(false);

        left = new DrawerEngine(
                drawer,
                drawerOverlay,
                DrawerEngine.Side.LEFT,
                this::updateOverlayGeometry,
                this::anyDrawerOpen,
                ANIM
        );

        right = new DrawerEngine(
                settingsDrawer,
                drawerOverlay,
                DrawerEngine.Side.RIGHT,
                this::updateOverlayGeometry,
                this::anyDrawerOpen,
                ANIM
        );

        Platform.runLater(() -> {
            installLayoutListeners();
            updateOverlayGeometry();

            left.snapClosed();
            right.snapClosed();
        });
    }

    private boolean anyDrawerOpen() {
        return left.isOpen() || right.isOpen();
    }

    // ===== Геометрия: overlay и drawers строго под шапкой =====

    private void installLayoutListeners() {
        rootStack.widthProperty().addListener((o, a, b) -> updateOverlayGeometry());
        rootStack.heightProperty().addListener((o, a, b) -> updateOverlayGeometry());

        if (topBar != null) {
            topBar.layoutBoundsProperty().addListener((o, a, b) -> updateOverlayGeometry());
            topBar.boundsInParentProperty().addListener((o, a, b) -> updateOverlayGeometry());
        }
    }

    private double headerHeightPx() {
        Node top = (topBar != null) ? topBar : appRoot.getTop();
        if (top == null || top.getScene() == null) return 0.0;

        double sceneYBottom = top.localToScene(0, top.getLayoutBounds().getMaxY()).getY();
        double localYBottom = rootStack.sceneToLocal(0, sceneYBottom).getY();

        return Math.max(0.0, localYBottom);
    }

    private void updateOverlayGeometry() {
        double headerH = headerHeightPx();
        double contentH = Math.max(0.0, rootStack.getHeight() - headerH);

        drawerOverlay.setTranslateY(headerH);
        drawerOverlay.setPrefHeight(contentH);

        applyContentBoxGeometry(drawer, headerH, contentH);
        applyContentBoxGeometry(settingsDrawer, headerH, contentH);
    }

    private void applyContentBoxGeometry(VBox box, double headerH, double contentH) {
        box.setTranslateY(headerH);

        box.setPrefHeight(contentH);
        box.setMinHeight(contentH);
        box.setMaxHeight(contentH);
    }

    // клики внутри панели не должны закрывать меню (включая пустую область)
    @FXML
    private void consumeInsideDrawer(MouseEvent e) {
        e.consume();
    }

    // overlay click
    @FXML
    public void closeAnyDrawer() {
        left.close();
        right.close();
    }

    // ===== Toggle handlers =====

    @FXML
    public void toggleMenu() {
        if (left.isOpen()) {
            left.close();
            return;
        }
        right.closeImmediate();
        left.open();
    }

    @FXML
    public void toggleSettingsDrawer() {
        if (right.isOpen()) {
            right.close();
            return;
        }
        left.closeImmediate();
        right.open();
    }

    // ===== Навигация из левого drawer =====

    @FXML public void navHome()      { nav(View.HOME); }
    @FXML public void navTestCases() { nav(View.TEST_CASES); }
    @FXML public void navCycles()    { nav(View.CYCLES); }
    @FXML public void navHistory()   { nav(View.HISTORY); }
    @FXML public void navAnalytics() { nav(View.ANALYTICS); }
    @FXML public void navReports()   { nav(View.REPORTS); }

    private void nav(View v) {
        left.close();
        if (v == View.HOME) Router.get().home();
        else Router.get().go(v);
    }

    // ===== Actions из правого drawer =====

    @FXML
    public void openSettings() {
        right.close();
        SettingsDialog.showModal(getStageSafe());
    }

    @FXML
    public void openEditMode() {
        right.close();
        SettingsDialog.showModal(getStageSafe());
    }

    @FXML
    public void openAbout() {
        right.close();
        SettingsDialog.showModal(getStageSafe());
    }

    private Stage getStageSafe() {
        if (rootStack == null || rootStack.getScene() == null) return null;
        if (rootStack.getScene().getWindow() instanceof Stage s) return s;
        return null;
    }
}
