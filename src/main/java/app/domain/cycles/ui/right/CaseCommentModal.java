package app.domain.cycles.ui.right;

import app.core.I18n;
import app.domain.cycles.ui.modal.RightAnchoredModal;
import app.ui.UiAutoGrowTextArea;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Comment modal for a case row (Cycles → right card → added cases list).
 *
 * Requirements:
 * - anchored to the comment button (RightAnchoredModal)
 * - content style consistent with other right-card modals
 * - input field must reuse the proven expandable TextArea behavior from Environment modal ("Builds")
 *
 * Notes:
 * - No "Enter => Save". Enter inserts new line (like Environment modal builds).
 * - Modal should be lightweight on card open (created lazily per row click).
 */
final class CaseCommentModal {

    private static final double MODAL_W = 300.0;

    /**
     * Same geometry approach as EnvironmentModal:
     * gutter = (MODAL_W - FIELD_W) / 2.
     */
    private static final double GUTTER_X = 34.0;
    private static final double FIELD_W = MODAL_W - (GUTTER_X * 2.0);

    // base height for 1 line (same as Environment builds input)
    private static final double INPUT_BASE_H = 32.0;

    private final Button anchorBtn;

    private RightAnchoredModal host;

    private TextArea ta;
    private Button btnSave;

    private VBox modalRoot;

    private Runnable beforeOpen = () -> {};
    private Supplier<String> currentValueSupplier = () -> "";
    private Consumer<String> onSaved = s -> {};

    CaseCommentModal(Button anchorBtn) {
        this.anchorBtn = anchorBtn;
    }

    void setBeforeOpen(Runnable r) {
        this.beforeOpen = (r == null) ? () -> {} : r;
    }

    void setCurrentValueSupplier(Supplier<String> s) {
        if (s != null) this.currentValueSupplier = s;
    }

    void setOnSaved(Consumer<String> c) {
        this.onSaved = (c == null) ? (v -> {}) : c;
    }

    void install(StackPane rightRoot) {
        if (rightRoot == null || anchorBtn == null) return;

        host = new RightAnchoredModal(rightRoot, anchorBtn, this::buildModal);

        // align by left edge so it feels like other right-card modals near controls
        host.setAlignByLeftEdge(true);

        // ✅ Only for this modal: if there is no space below, show ABOVE the comment button.
        host.setFlipUpWhenNoSpace(true);

        host.setOnBeforeOpen(this::onBeforeOpen);
        host.setOnAfterOpen(() -> {
            if (ta != null) ta.requestFocus();
        });

        host.install();
        host.close();
    }

    void toggle() {
        if (host != null) host.toggle();
    }

    void close() {
        if (host != null) host.close();
    }

    // ===================== OPEN / SAVE =====================

    private void onBeforeOpen() {
        try { beforeOpen.run(); } catch (Exception ignore) {}

        String initial = "";
        try { initial = safe(currentValueSupplier.get()); } catch (Exception ignore) {}

        if (ta != null) ta.setText(initial);

        // after setting text: TextArea will recalc height, then we need host relayout
        if (ta != null) {
            Platform.runLater(this::requestModalAndBackdropLayout);
        }
    }

    private void onSave() {
        String v = (ta == null) ? "" : safe(ta.getText());
        try { onSaved.accept(v); } catch (Exception ignore) {}
        if (host != null) host.close();
    }

    // ===================== UI =====================

    private VBox buildModal() {
        VBox root = new VBox();
        this.modalRoot = root;

        root.setAlignment(Pos.CENTER);

        root.getStyleClass().add("tc-right-confirm");
        root.getStyleClass().add("cy-menu-modal");

        root.setMinWidth(MODAL_W);
        root.setPrefWidth(MODAL_W);
        root.setMaxWidth(MODAL_W);

        root.setMinHeight(Region.USE_PREF_SIZE);
        root.setMaxHeight(Region.USE_PREF_SIZE);

        Label title = new Label(I18n.t("cy.case.comment.title"));
        title.setWrapText(false);
        title.setMaxWidth(Double.MAX_VALUE);
        title.getStyleClass().add("tc-delete-title");
        VBox.setMargin(title, new Insets(4, 0, 0, 0));

        Region topGap = new Region();
        topGap.setPrefHeight(20);
        topGap.setMinHeight(20);
        topGap.setMaxHeight(20);

        ta = new TextArea();
        ta.setPromptText(I18n.t("cy.case.comment.placeholder"));

        // reuse the exact style used by Environment modal "Builds"
        ta.getStyleClass().add("cy-env-value-area");

        ta.setWrapText(true);

        ta.setPrefRowCount(1);
        ta.setPrefHeight(INPUT_BASE_H);

        // follow prefHeight strictly (critical for correct growth)
        ta.setMinHeight(Region.USE_PREF_SIZE);
        ta.setMaxHeight(Region.USE_PREF_SIZE);

        ta.setMinWidth(0.0);

        disableTextAreaScrollBars(ta);

        // proven wrap-aware auto-grow (same as env modal)
        UiAutoGrowTextArea.installWrapAutoGrow(ta, INPUT_BASE_H, this::requestModalAndBackdropLayout);

        ta.setPrefWidth(FIELD_W);
        ta.setMinWidth(FIELD_W);
        ta.setMaxWidth(FIELD_W);

        VBox.setMargin(ta, new Insets(0, 0, 0, 0));

        btnSave = new Button(I18n.t("cy.case.comment.save"));
        btnSave.getStyleClass().addAll("cy-modal-btn", "cy-modal-btn-primary");
        btnSave.setPrefWidth(FIELD_W);
        btnSave.setMinWidth(FIELD_W);
        btnSave.setMaxWidth(FIELD_W);

        btnSave.setOnAction(e -> onSave());

        // ✅ ВАЖНО: никаких обработчиков Enter => save.
        // Enter в TextArea должен добавлять новую строку (как в модалке окружения).

        VBox.setMargin(btnSave, new Insets(16, 0, 6, 0));

        root.getChildren().addAll(title, topGap, ta, btnSave);

        // gutters (same as other modals with FIELD_W)
        root.setPadding(new Insets(18, GUTTER_X, 18, GUTTER_X));

        return root;
    }

    // ===================== RELAYOUT =====================

    private void requestModalAndBackdropLayout() {
        if (modalRoot == null) return;

        modalRoot.requestLayout();

        Parent p = modalRoot.getParent();
        if (p != null) {
            p.requestLayout();
            Platform.runLater(() -> {
                try {
                    p.applyCss();
                    p.layout();
                } catch (Exception ignore) {}
            });
        }

        if (host != null) {
            host.requestRelayout();
        }
    }

    // ===================== DISABLE SCROLLBARS (copied from EnvironmentModal) =====================

    private static void disableTextAreaScrollBars(TextArea ta) {
        if (ta == null) return;

        ta.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                Platform.runLater(() -> applyNoScrollPolicies(ta));
            }
        });

        Skin<?> s = ta.getSkin();
        if (s != null) {
            Platform.runLater(() -> applyNoScrollPolicies(ta));
        }
    }

    private static void applyNoScrollPolicies(TextArea ta) {
        if (ta == null) return;

        Node spNode = ta.lookup(".scroll-pane");
        if (spNode instanceof ScrollPane sp) {
            sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            sp.setFitToWidth(true);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
