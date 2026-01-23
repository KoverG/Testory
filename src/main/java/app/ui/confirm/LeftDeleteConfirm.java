package app.ui.confirm;

import app.core.I18n;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.util.function.BooleanSupplier;

public final class LeftDeleteConfirm {

    private static final Duration ANIM_IN  = Duration.millis(160);
    private static final Duration ANIM_OUT = Duration.millis(140);

    // как у правой модалки (RightDeleteConfirm / testcases.fxml)
    private static final double MODAL_W     = 320.0;
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

    private Node focusOwnerBeforeOpen;

    // configurable i18n keys (defaults)
    private String titleKey = "tc.trash.delete.title";
    private String hintKey  = "tc.trash.delete.hint";

    // button texts are shared across screens
    private static final String OK_KEY     = "tc.delete.confirm";
    private static final String CANCEL_KEY = "tc.delete.cancel";

    public LeftDeleteConfirm(StackPane leftStack, Runnable onConfirm) {
        this.leftStack = leftStack;
        this.onConfirm = (onConfirm == null) ? () -> {} : onConfirm;
    }

    public void setCanOpenSupplier(BooleanSupplier s) {
        this.canOpenSupplier = (s == null) ? () -> true : s;
    }

    public void setTextKeys(String titleKey, String hintKey) {
        if (titleKey != null && !titleKey.isBlank()) this.titleKey = titleKey.trim();
        if (hintKey != null && !hintKey.isBlank()) this.hintKey = hintKey.trim();

        if (open && lbTitle != null && lbHint != null) {
            lbTitle.setText(I18n.t(this.titleKey));
            lbHint.setText(I18n.t(this.hintKey));
        }
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

        Scene sc = leftStack.getScene();
        focusOwnerBeforeOpen = (sc != null) ? sc.getFocusOwner() : null;

        lbTitle.setText(I18n.t(titleKey));
        lbHint.setText(I18n.t(hintKey));

        open = true;

        deleteLayer.setVisible(true);
        deleteLayer.setManaged(true);

        if (animOut != null) animOut.stop();

        deleteLayer.setOpacity(0.0);
        deleteModal.setTranslateY(6.0);

        FadeTransition fade = new FadeTransition(ANIM_IN, deleteLayer);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition tr = new TranslateTransition(ANIM_IN, deleteModal);
        tr.setFromY(6.0);
        tr.setToY(0.0);
        tr.setInterpolator(Interpolator.EASE_OUT);

        animIn = new ParallelTransition(fade, tr);
        animIn.playFromStart();

        if (btnCancel != null) btnCancel.requestFocus();
        else deleteLayer.requestFocus();
    }

    public void close() {
        if (!open) return;
        open = false;

        if (animIn != null) animIn.stop();

        FadeTransition fade = new FadeTransition(ANIM_OUT, deleteLayer);
        fade.setFromValue(deleteLayer.getOpacity());
        fade.setToValue(0.0);
        fade.setInterpolator(Interpolator.EASE_IN);

        TranslateTransition tr = new TranslateTransition(ANIM_OUT, deleteModal);
        tr.setFromY(deleteModal.getTranslateY());
        tr.setToY(6.0);
        tr.setInterpolator(Interpolator.EASE_IN);

        animOut = new ParallelTransition(fade, tr);
        animOut.setOnFinished(ev -> {
            deleteLayer.setVisible(false);
            deleteLayer.setManaged(false);
            deleteLayer.setOpacity(0.0);
            deleteModal.setTranslateY(0.0);

            if (focusOwnerBeforeOpen != null) {
                Platform.runLater(() -> {
                    try { focusOwnerBeforeOpen.requestFocus(); } catch (Exception ignored) {}
                });
            }
        });

        animOut.playFromStart();
    }

    /** ✅ Добавлено для совместимости с доменным адаптером */
    public void toggle() {
        if (open) close();
        else open();
    }

    // ===================== INTERNAL =====================

    private void ensureUi() {
        if (leftStack == null) return;
        if (deleteLayer != null) return;

        // dim layer
        Region dim = new Region();
        dim.getStyleClass().add("tc-left-dim");
        dim.setMaxWidth(Double.MAX_VALUE);
        dim.setMaxHeight(Double.MAX_VALUE);

        // modal root (как в testcases.fxml)
        deleteModal = new VBox();
        deleteModal.setAlignment(Pos.CENTER);
        deleteModal.getStyleClass().add("tc-left-confirm");

        lbTitle = new Label();
        lbTitle.getStyleClass().add("tc-delete-title");
        lbTitle.setWrapText(false);
        lbTitle.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(lbTitle, new Insets(4, 0, 0, 0));

        Region sp1 = fixedSpacer(20);

        VBox buttons = new VBox(10);
        buttons.setAlignment(Pos.CENTER);

        btnCancel = new Button(I18n.t(CANCEL_KEY));
        btnCancel.getStyleClass().add("tc-btn");
        btnCancel.setFocusTraversable(false);
        btnCancel.setPrefWidth(220);
        btnCancel.setMinWidth(220);
        btnCancel.setMaxWidth(220);

        btnConfirm = new Button(I18n.t(OK_KEY));
        btnConfirm.getStyleClass().addAll("tc-btn", "tc-danger-btn");
        btnConfirm.setFocusTraversable(false);
        btnConfirm.setPrefWidth(220);
        btnConfirm.setMinWidth(220);
        btnConfirm.setMaxWidth(220);

        buttons.getChildren().addAll(btnCancel, btnConfirm);

        Region sp2 = fixedSpacer(20);

        lbHint = new Label();
        lbHint.getStyleClass().add("tc-delete-hint");
        lbHint.setWrapText(false);
        lbHint.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(lbHint, new Insets(0, 0, 2, 0));

        deleteModal.getChildren().addAll(lbTitle, sp1, buttons, sp2, lbHint);

        applyConfirmModalUi(deleteModal, MODAL_W, MODAL_MIN_H);

        deleteLayer = new StackPane(dim, deleteModal);
        deleteLayer.setPickOnBounds(true);
        deleteLayer.setVisible(false);
        deleteLayer.setManaged(false);
        deleteLayer.setMaxWidth(Double.MAX_VALUE);
        deleteLayer.setMaxHeight(Double.MAX_VALUE);

        StackPane.setAlignment(deleteModal, Pos.CENTER);

        leftStack.getChildren().add(deleteLayer);

        installHandlers(dim);
    }

    private Region fixedSpacer(double h) {
        Region r = new Region();
        r.setPrefHeight(h);
        r.setMinHeight(h);
        r.setMaxHeight(h);
        return r;
    }

    private void installHandlers(Region dim) {
        if (dim != null) {
            dim.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> close());
        }

        if (deleteModal != null) {
            deleteModal.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> e.consume());
        }

        if (btnCancel != null) {
            btnCancel.setOnAction(e -> close());
        }

        if (btnConfirm != null) {
            btnConfirm.setOnAction(e -> {
                try {
                    close();
                    onConfirm.run();
                } catch (Exception ex) {
                    System.err.println("[LeftDeleteConfirm] confirm failed: " + ex);
                    close();
                }
            });
        }

        if (leftStack != null) {
            leftStack.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (!open) return;
                if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    e.consume();
                    close();
                }
            });
        }
    }

    private static void applyConfirmModalUi(VBox modal, double modalW, double minH) {
        if (modal == null) return;

        modal.setAlignment(Pos.CENTER);

        modal.setMinWidth(modalW);
        modal.setPrefWidth(modalW);
        modal.setMaxWidth(modalW);

        modal.setMinHeight(minH);
        modal.setPrefHeight(Region.USE_COMPUTED_SIZE);
        modal.setMaxHeight(Region.USE_PREF_SIZE);

        applyToLabels(modal, modalW);
    }

    private static void applyToLabels(Node root, double modalW) {
        if (root == null) return;

        if (root instanceof Label lb) {
            tuneLabel(lb, modalW);
            return;
        }

        if (root instanceof Region r) {
            for (Node ch : r.getChildrenUnmodifiable()) {
                applyToLabels(ch, modalW);
            }
        }
    }

    private static void tuneLabel(Label lb, double modalW) {
        lb.setMaxWidth(Math.max(1.0, modalW - 28.0));
        lb.setWrapText(true);

        lb.setAlignment(Pos.CENTER);
        lb.setTextAlignment(TextAlignment.CENTER);

        lb.setTextOverrun(OverrunStyle.CLIP);
    }
}
