package app.domain.cycles.ui.right;

import app.ui.UiSvg;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Environment chip for Cycles right card meta row.
 *
 * Требования:
 * - 2 иконки: desktop.svg / mobile.svg
 * - разделитель "/" между ними
 * - центрирование контента
 * - иконки и "/" должны корректно поддерживать светлую/тёмную тему
 * - иконки без hover/pressed (иконки не Button)
 * - чип имеет hover + эффект клика (pressed) как у остальных чипов
 *
 * + Modal (RightAnchoredModal) for environment selection
 */
public final class EnvironmentChip extends HBox {

    private static final String ICON_DESKTOP = "desktop.svg";
    private static final String ICON_MOBILE  = "mobile.svg";

    private static final double ICON_SIZE_PX = 12.0;
    private static final double SEP_MARGIN_PX = 6.0;

    // ✅ кастомный pressed-state для HBox (CSS: :down)
    private static final PseudoClass PC_DOWN = PseudoClass.getPseudoClass("down");

    private final EnvironmentModal modal = new EnvironmentModal(this);

    private Runnable beforeOpen = () -> {};

    private Supplier<Boolean> currentMobileSupplier = () -> false;
    private Supplier<String> currentValueSupplier = () -> "";
    private Supplier<List<String>> currentLinksSupplier = List::of;
    private Supplier<Boolean> currentTypeSetSupplier = () -> false;

    private Consumer<EnvironmentModal.EnvState> onSaved = s -> {};

    public EnvironmentChip() {
        getStyleClass().add("cy-environment-chip");

        setAlignment(Pos.CENTER);
        setCursor(Cursor.HAND);

        // padding тут не критичен: его всё равно переопределит CSS класса cy-task-link-chip
        setPadding(new Insets(2, 8, 2, 8));
        setFillHeight(true);

        // ✅ включаем "эффект клика" (pressed) для HBox через pseudo-class
        installPressedPseudoClass();

        Label themeColorSource = new Label();
        themeColorSource.getStyleClass().add("cy-meta-value");
        themeColorSource.setVisible(false);
        themeColorSource.setManaged(false);

        Node desktop = UiSvg.createSvg(ICON_DESKTOP, ICON_SIZE_PX);
        Node mobile  = UiSvg.createSvg(ICON_MOBILE, ICON_SIZE_PX);

        Label sep = new Label("/");
        sep.setMouseTransparent(true);
        sep.setOpacity(0.75);
        sep.setFont(Font.font(
                sep.getFont().getFamily(),
                FontWeight.BOLD,
                sep.getFont().getSize()
        ));

        HBox.setMargin(sep, new Insets(0, SEP_MARGIN_PX, 0, SEP_MARGIN_PX));

        bindToTheme(desktop, themeColorSource);
        bindToTheme(mobile, themeColorSource);
        sep.textFillProperty().bind(themeColorSource.textFillProperty());

        if (desktop != null) getChildren().add(desktop);
        getChildren().add(sep);
        if (mobile != null) getChildren().add(mobile);

        getChildren().add(themeColorSource);

        installOpenHandlers();
    }

    public void install(StackPane rightRoot, Runnable beforeOpen) {
        if (beforeOpen != null) this.beforeOpen = beforeOpen;

        modal.setBeforeOpen(this.beforeOpen);
        modal.setCurrentSuppliers(currentMobileSupplier, currentValueSupplier, currentLinksSupplier, currentTypeSetSupplier);
        modal.setOnSaved(onSaved);

        modal.install(rightRoot);
    }

    public void closeModalIfOpen() {
        modal.close();
    }

    public void setCurrentSuppliers(Supplier<Boolean> mobileSupplier, Supplier<String> valueSupplier) {
        setCurrentSuppliers(mobileSupplier, valueSupplier, null, null);
    }

    public void setCurrentSuppliers(
            Supplier<Boolean> mobileSupplier,
            Supplier<String> valueSupplier,
            Supplier<List<String>> linksSupplier,
            Supplier<Boolean> typeSetSupplier
    ) {
        if (mobileSupplier != null) this.currentMobileSupplier = mobileSupplier;
        if (valueSupplier != null) this.currentValueSupplier = valueSupplier;
        if (linksSupplier != null) this.currentLinksSupplier = linksSupplier;
        if (typeSetSupplier != null) this.currentTypeSetSupplier = typeSetSupplier;

        modal.setCurrentSuppliers(this.currentMobileSupplier, this.currentValueSupplier, this.currentLinksSupplier, this.currentTypeSetSupplier);
    }

    public void setOnSaved(Consumer<EnvironmentModal.EnvState> onSaved) {
        if (onSaved != null) this.onSaved = onSaved;
        modal.setOnSaved(this.onSaved);
    }

    private void installOpenHandlers() {
        addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;

            // prevent immediate reopen when modal closes by outside click
            if (modal.consumeSuppressNextOpenClick()) return;

            try { beforeOpen.run(); } catch (Exception ignore) {}
            modal.toggle();
        });
    }

    private void installPressedPseudoClass() {
        // чтобы нажатие работало даже если кликаешь по детям (иконки mouseTransparent=true — ок)
        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> pseudoClassStateChanged(PC_DOWN, true));
        addEventFilter(MouseEvent.MOUSE_RELEASED, e -> pseudoClassStateChanged(PC_DOWN, false));
        addEventFilter(MouseEvent.MOUSE_EXITED, e -> pseudoClassStateChanged(PC_DOWN, false));
    }

    private static void bindToTheme(Node n, Label themeColorSource) {
        if (n == null || themeColorSource == null) return;

        if (n instanceof SVGPath p) {
            ChangeListener<Paint> apply = (obs, oldV, newV) -> {};
            themeColorSource.textFillProperty().addListener(apply);

            p.fillProperty().bind(themeColorSource.textFillProperty());
            p.setMouseTransparent(true);
        } else {
            n.setMouseTransparent(true);
        }
    }
}
