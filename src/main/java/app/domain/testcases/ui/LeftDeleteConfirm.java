// FILE: src/main/java/app/domain/testcases/ui/LeftDeleteConfirm.java
package app.domain.testcases.ui;

import app.core.I18n;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.BooleanSupplier;

public final class LeftDeleteConfirm {

    private static final Duration ANIM_IN = Duration.millis(140);
    private static final Duration ANIM_OUT = Duration.millis(120);

    private static final double MODAL_W = 320.0;
    private static final double MODAL_MIN_H = 212.0;

    private final StackPane leftStack;
    private final Runnable onConfirm;

    private BooleanSupplier canOpenSupplier = () -> true;

    private boolean open = false;

    private StackPane deleteLayer;
    private VBox deleteModal;

    private Label lbTitle;
    private Label lbHint;

    private Button btnCancel;
    private Button btnConfirm;

    private ParallelTransition animIn;
    private ParallelTransition animOut;

    private Scene attachedScene;

    // ✅ фикс: не давать фокусу возвращаться в инпут после закрытия
    private Node focusOwnerBeforeOpen;

    // ✅ на всякий случай: если close по клику снаружи — гасим следующий release
    private boolean swallowNextMouseRelease = false;

    private final javafx.beans.value.ChangeListener<Scene> sceneListener = (obs, oldSc, newSc) -> {
        detachSceneFilters();
        attachSceneFilters(newSc);
    };

    private final javafx.event.EventHandler<KeyEvent> onSceneKey = e -> {
        if (!open) return;
        if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
            close();
            e.consume();
        }
    };

    private final javafx.event.EventHandler<MouseEvent> onSceneMousePressed = e -> {
        if (!open) return;

        Node t = (e.getTarget() instanceof Node n) ? n : null;
        if (t == null) {
            // закрываем и гасим последующий release
            swallowNextMouseRelease = true;
            close();
            e.consume();
            return;
        }

        // клик внутри модалки — не закрываем (и фокус держим на кнопке, чтобы не было caret в инпутах)
        if (isDescendantOf(t, deleteModal)) {
            if (btnCancel != null) btnCancel.requestFocus();
            else deleteLayer.requestFocus();
            return;
        }

        // клик где угодно вне модалки — закрываем и гасим событие
        swallowNextMouseRelease = true;
        close();
        e.consume();
    };

    private final javafx.event.EventHandler<MouseEvent> onSceneMouseReleased = e -> {
        if (!swallowNextMouseRelease) return;
        swallowNextMouseRelease = false;
        e.consume();
    };

    public LeftDeleteConfirm(StackPane leftStack, Runnable onConfirm) {
        this.leftStack = leftStack;
        this.onConfirm = (onConfirm == null) ? () -> {} : onConfirm;
    }

    public void setCanOpenSupplier(BooleanSupplier s) {
        this.canOpenSupplier = (s == null) ? () -> true : s;
    }

    public boolean isOpen() {
        return open;
    }

    public void refreshAvailability(boolean enabled) {
        if (!enabled) close();
    }

    public void open() {
        if (open) return;
        if (!canOpenSupplier.getAsBoolean()) return;

        ensureUi();

        // ✅ запоминаем, кто был в фокусе ДО модалки (обычно это инпут)
        Scene sc = leftStack.getScene();
        focusOwnerBeforeOpen = (sc != null) ? sc.getFocusOwner() : null;

        lbTitle.setText(I18n.t("tc.trash.delete.title"));
        lbHint.setText(I18n.t("tc.trash.delete.hint"));

        open = true;

        deleteLayer.setVisible(true);
        deleteLayer.setManaged(true);
        deleteLayer.setOpacity(0.0);

        // фокус на Cancel — предсказуемо и убирает caret из инпутов пока модалка открыта
        if (btnCancel != null) btnCancel.requestFocus();
        else deleteLayer.requestFocus();

        if (animOut != null) animOut.stop();
        if (animIn != null) {
            animIn.stop();
            animIn.playFromStart();
        }
    }

    public void close() {
        if (!open) return;
        open = false;

        if (animIn != null) animIn.stop();
        if (animOut != null) {
            animOut.stop();
            animOut.playFromStart();
        } else {
            deleteLayer.setVisible(false);
            deleteLayer.setManaged(false);
            deleteLayer.setOpacity(0.0);
        }

        // ✅ КЛЮЧЕВОЕ: после скрытия overlay JavaFX может вернуть фокус в старый инпут.
        // Мы принудительно уводим фокус на leftStack (нейтральная зона).
        Platform.runLater(() -> {
            if (leftStack != null) {
                leftStack.requestFocus();
            }
        });
    }

    public void toggle() {
        if (open) close();
        else open();
    }

    // ===================== UI =====================

    private void ensureUi() {
        if (deleteLayer != null) return;
        if (leftStack == null) return;

        // ✅ делаем контейнер фокусируемым (иначе requestFocus может быть проигнорирован)
        leftStack.setFocusTraversable(true);

        deleteLayer = new StackPane();
        deleteLayer.setVisible(false);
        deleteLayer.setManaged(false);
        deleteLayer.setPickOnBounds(true);
        deleteLayer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        deleteLayer.setFocusTraversable(true);

        Region dim = new Region();
        dim.getStyleClass().add("tc-left-dim");
        dim.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        dim.setFocusTraversable(true);

        deleteModal = new VBox();
        deleteModal.setAlignment(Pos.CENTER);
        deleteModal.getStyleClass().add("tc-left-confirm");
        deleteModal.setFocusTraversable(true);

        ConfirmModalUi.apply(deleteModal, MODAL_W, MODAL_MIN_H);

        lbTitle = new Label();
        lbTitle.getStyleClass().add("tc-delete-title");
        VBox.setMargin(lbTitle, new Insets(6, 10, 0, 10));

        Region sp1 = new Region();
        sp1.setPrefHeight(16);

        VBox buttons = new VBox(10);
        buttons.setAlignment(Pos.CENTER);

        btnCancel = new Button(I18n.t("tc.delete.cancel"));
        btnCancel.getStyleClass().add("tc-btn");
        btnCancel.setPrefWidth(220);
        btnCancel.setMinWidth(220);
        btnCancel.setMaxWidth(220);

        btnConfirm = new Button(I18n.t("tc.delete.confirm"));
        btnConfirm.getStyleClass().addAll("tc-btn", "tc-danger-btn");
        btnConfirm.setPrefWidth(220);
        btnConfirm.setMinWidth(220);
        btnConfirm.setMaxWidth(220);

        buttons.getChildren().addAll(btnCancel, btnConfirm);

        Region sp2 = new Region();
        sp2.setPrefHeight(14);

        lbHint = new Label();
        lbHint.getStyleClass().add("tc-delete-hint");
        VBox.setMargin(lbHint, new Insets(0, 10, 10, 10));

        ConfirmModalUi.applyToLabels(lbTitle, MODAL_W);
        ConfirmModalUi.applyToLabels(lbHint, MODAL_W);

        deleteModal.getChildren().addAll(lbTitle, sp1, buttons, sp2, lbHint);

        StackPane.setAlignment(deleteModal, Pos.CENTER);

        deleteLayer.getChildren().addAll(dim, deleteModal);
        leftStack.getChildren().add(deleteLayer);

        installHandlers();
        installSceneBinding();
    }

    private void installHandlers() {
        if (btnCancel != null) {
            btnCancel.setOnAction(e -> close());
        }

        if (btnConfirm != null) {
            btnConfirm.setOnAction(e -> {
                close();
                onConfirm.run();
            });
        }

        FadeTransition fadeIn = new FadeTransition(ANIM_IN, deleteLayer);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        FadeTransition fadeOut = new FadeTransition(ANIM_OUT, deleteLayer);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(ev -> {
            deleteLayer.setVisible(false);
            deleteLayer.setManaged(false);
            deleteLayer.setOpacity(0.0);
        });

        animIn = new ParallelTransition(fadeIn);
        animOut = new ParallelTransition(fadeOut);
    }

    private void installSceneBinding() {
        leftStack.sceneProperty().addListener(sceneListener);
        attachSceneFilters(leftStack.getScene());
    }

    private void attachSceneFilters(Scene sc) {
        if (sc == null) return;
        attachedScene = sc;

        sc.addEventFilter(KeyEvent.KEY_PRESSED, onSceneKey);
        sc.addEventFilter(MouseEvent.MOUSE_PRESSED, onSceneMousePressed);
        sc.addEventFilter(MouseEvent.MOUSE_RELEASED, onSceneMouseReleased);
    }

    private void detachSceneFilters() {
        if (attachedScene == null) return;

        try {
            attachedScene.removeEventFilter(KeyEvent.KEY_PRESSED, onSceneKey);
            attachedScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, onSceneMousePressed);
            attachedScene.removeEventFilter(MouseEvent.MOUSE_RELEASED, onSceneMouseReleased);
        } catch (Exception ignored) {
        } finally {
            attachedScene = null;
        }
    }

    private static boolean isDescendantOf(Node node, Node root) {
        if (node == null || root == null) return false;
        Node cur = node;
        while (cur != null) {
            if (cur == root) return true;
            cur = cur.getParent();
        }
        return false;
    }
}
