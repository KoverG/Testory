package app.domain.testcases.ui;

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

import java.util.function.Supplier;

public final class TestCaseCardMenuButton extends Button {

    public static final String SVG_NAME_KEY = "svgName";
    public static final String SVG_NAME = "menu.svg";

    private RightAnchoredModal host;

    private Runnable onBeforeOpen = () -> {};
    private Runnable onEdit = () -> {};
    private Runnable onCopy = () -> {};

    public TestCaseCardMenuButton() {
        setUserData(14);
        getProperties().put(SVG_NAME_KEY, SVG_NAME);
        setText("");
        setFocusTraversable(false);
        getStyleClass().addAll("icon-btn", "sm");
    }

    public void install(StackPane rightRoot, Runnable onBeforeOpen) {
        if (rightRoot == null) return;

        this.onBeforeOpen = nz(onBeforeOpen);

        host = new RightAnchoredModal(rightRoot, this, this::buildMenuModal);
        host.setOnBeforeOpen(this.onBeforeOpen);
        host.install();
        host.close();

        setOnAction(e -> host.toggle());
    }

    public void setOnEditAction(Runnable r) {
        this.onEdit = nz(r);
    }

    public void setOnCopyAction(Runnable r) {
        this.onCopy = nz(r);
    }

    public void closeMenu() {
        if (host != null) host.close();
    }

    private VBox buildMenuModal() {
        VBox modal = new VBox();
        modal.setAlignment(Pos.CENTER);
        modal.getStyleClass().addAll("tc-right-confirm", "cy-menu-modal");

        modal.setMinWidth(300);
        modal.setPrefWidth(300);
        modal.setMaxWidth(300);
        modal.setMinHeight(Region.USE_PREF_SIZE);
        modal.setMaxHeight(Region.USE_PREF_SIZE);

        modal.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getTarget() == modal) e.consume();
        });

        Label title = new Label(I18n.t("cy.menu.title"));
        title.setWrapText(false);
        title.setMaxWidth(Double.MAX_VALUE);
        title.getStyleClass().add("tc-delete-title");
        VBox.setMargin(title, new Insets(4, 0, 0, 0));

        VBox buttonsBox = new VBox(10);
        buttonsBox.setAlignment(Pos.CENTER);
        buttonsBox.getChildren().addAll(
                buildActionButton("cy.menu.edit", true, () -> onEdit),
                buildActionButton("cy.menu.copy", true, () -> onCopy)
        );

        Region topGap = fixedSpacer(20);
        Region bottomGap = fixedSpacer(16);

        Label hint = new Label(I18n.t("cy.menu.hint"));
        hint.setWrapText(true);
        hint.setMaxWidth(Double.MAX_VALUE);
        hint.getStyleClass().add("tc-delete-hint");

        VBox footerBox = new VBox(6, hint);
        footerBox.setAlignment(Pos.CENTER);
        footerBox.setMinHeight(24);
        footerBox.setPrefHeight(24);
        footerBox.setMaxHeight(24);

        modal.getChildren().addAll(title, topGap, buttonsBox, bottomGap, footerBox);
        return modal;
    }

    private Button buildActionButton(String key, boolean closeAfterClick, Supplier<Runnable> actionSupplier) {
        Button button = new Button(I18n.t(key));
        button.setFocusTraversable(false);
        button.setPrefWidth(220);
        button.setMinWidth(220);
        button.setMaxWidth(220);
        button.getStyleClass().add("cy-modal-btn");
        button.setOnAction(e -> {
            Runnable action = actionSupplier == null ? null : actionSupplier.get();
            if (action != null) action.run();
            if (closeAfterClick) closeMenu();
        });
        return button;
    }

    private static Region fixedSpacer(double h) {
        Region r = new Region();
        r.setPrefHeight(h);
        r.setMinHeight(h);
        r.setMaxHeight(h);
        return r;
    }

    private static Runnable nz(Runnable r) {
        return r == null ? () -> {} : r;
    }
}
