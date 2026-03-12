package app.domain.cycles.ui.right;

import app.core.I18n;
import app.domain.cycles.ui.modal.RightAnchoredModal;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

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

    // hook: RightPaneCoordinator может попросить закрыть delete-confirm перед показом меню
    private Runnable onBeforeOpen = () -> {};

    // handlers
    private Runnable onCopy = () -> {};
    private Runnable onPause = () -> {};
    private Runnable onStats = () -> {};
    private Runnable onReport = () -> {};
    private Runnable onEdit = () -> {};
    private Runnable onSource = () -> {};
    private Runnable onDelete = () -> {};

    private Button btnCopy;
    private Button btnPause;
    private Button btnStats;
    private Button btnReport;
    private Button btnEdit;
    private Button btnSource;
    private Button btnDelete;

    private boolean editEnabled = false;
    private boolean sourceEnabled = false;

    public CycleCardMenuButton() {
        // marker for ThemeToggleUiInstaller / icon setup
        setUserData(14);
        getProperties().put(SVG_NAME_KEY, SVG_NAME);
        setText("");
        setFocusTraversable(false);
    }

    /**
     * Must be called by RightPaneCoordinator after FXML is loaded.
     *
     * @param rightRoot overlay host (StackPane from cycles.fxml right zone)
     * @param onBeforeOpen optional hook (e.g. close other modals)
     */
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

    // ===================== PUBLIC API (actions) =====================

    public void setOnCopyAction(Runnable r) { this.onCopy = nz(r); }
    public void setOnPause(Runnable r) { this.onPause = nz(r); }
    public void setOnStats(Runnable r) { this.onStats = nz(r); }
    public void setOnReport(Runnable r) { this.onReport = nz(r); }
    public void setOnEdit(Runnable r) { this.onEdit = nz(r); }
    public void setOnSource(Runnable r) { this.onSource = nz(r); }
    public void setOnDelete(Runnable r) { this.onDelete = nz(r); }

    public void setEditEnabled(boolean enabled) {
        this.editEnabled = enabled;
        if (btnEdit != null) btnEdit.setDisable(!enabled);
    }

    public void setSourceEnabled(boolean enabled) {
        this.sourceEnabled = enabled;
        if (btnSource != null) btnSource.setDisable(!enabled);
    }

    // ===================== UI =====================
    private VBox buildMenuModal() {
        VBox modal = new VBox();
        modal.setAlignment(Pos.CENTER);
        modal.getStyleClass().add("tc-right-confirm");
        modal.getStyleClass().add("cy-menu-modal");

        // ✅ unify modal width with others
        modal.setMinWidth(300);
        modal.setPrefWidth(300);
        modal.setMaxWidth(300);

        // critical: do not stretch to full height of StackPane
        modal.setMinHeight(Region.USE_PREF_SIZE);
        modal.setMaxHeight(Region.USE_PREF_SIZE);

        // clicks inside modal must not close it, but inner buttons must still receive action events
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

        btnCopy = buildMenuBtn("cy.menu.copy", false, false);
        btnPause = buildMenuBtn("cy.menu.pause", false, false);
        btnStats = buildMenuBtn("cy.menu.stats", false, false);
        btnReport = buildMenuBtn("cy.menu.report", false, false);
        btnEdit = buildMenuBtn("cy.menu.edit", true, false);
        btnSource = buildMenuBtn("cy.menu.source", true, false);
        btnDelete = buildMenuBtn("cy.menu.delete", false, true);

        // apply current availability flags
        btnEdit.setDisable(!editEnabled);
        btnSource.setDisable(!sourceEnabled);

        buttonsBox.getChildren().addAll(
                btnCopy,
                btnPause,
                btnStats,
                btnReport,
                btnEdit,
                btnSource,
                btnDelete
        );

        Region bottomGap = fixedSpacer(20);

        Label hint = new Label(I18n.t("cy.menu.hint"));
        hint.setWrapText(true);
        hint.setMaxWidth(Double.MAX_VALUE);
        hint.getStyleClass().add("tc-delete-hint");
        VBox.setMargin(hint, new Insets(0, 0, 2, 0));

        modal.getChildren().addAll(title, topGap, buttonsBox, bottomGap, hint);

        btnCopy.setOnAction(e -> onCopy.run());
        btnPause.setOnAction(e -> onPause.run());
        btnStats.setOnAction(e -> onStats.run());
        btnReport.setOnAction(e -> onReport.run());
        btnEdit.setOnAction(e -> onEdit.run());
        btnSource.setOnAction(e -> onSource.run());
        btnDelete.setOnAction(e -> onDelete.run());

        return modal;
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

    private static Runnable nz(Runnable r) { return (r == null) ? () -> {} : r; }
}
