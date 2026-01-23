package app.domain.cycles.ui.overlay;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.function.Consumer;

/**
 * Adapter (domain -> ui):
 * Cycles использует доменный класс, но реализация общая и живёт в app.ui.list.LeftListActionOverlay.
 *
 * Это 1-в-1 подход как в app.domain.testcases.ui.LeftDeleteConfirm:
 * домен держит тонкий wrapper, UI-реализация централизована.
 */
public final class LeftListActionOverlay {

    private final app.ui.list.LeftListActionOverlay impl;

    public LeftListActionOverlay(StackPane leftStack, Region anchor, String buttonText) {
        this.impl = new app.ui.list.LeftListActionOverlay(leftStack, anchor, buttonText);
    }

    public void init(Button btnToggle) {
        impl.init(btnToggle);
    }

    public void setOnSpacerChanged(Runnable r) {
        impl.setOnSpacerChanged(r);
    }

    public void setOnOpenChanged(Consumer<Boolean> c) {
        impl.setOnOpenChanged(c);
    }

    public boolean isOpen() {
        return impl.isOpen();
    }

    public double scrollSpacerPx() {
        return impl.scrollSpacerPx();
    }

    public Node overlayRoot() {
        return impl.overlayRoot();
    }

    public CheckBox selectAllCheckBox() {
        return impl.selectAllCheckBox();
    }

    public void setButtonText(String text) {
        impl.setButtonText(text);
    }

    public void setOnAction(Runnable r) {
        impl.setOnAction(r);
    }

    // алиасы под прежний стиль вызовов (как в универсальном классе)
    public void setOnDelete(Runnable r) { impl.setOnDelete(r); }
    public void setOnAdd(Runnable r) { impl.setOnAdd(r); }
    public void setDeleteEnabled(boolean enabled) { impl.setDeleteEnabled(enabled); }
    public void setAddEnabled(boolean enabled) { impl.setAddEnabled(enabled); }

    public void setActionEnabled(boolean enabled) {
        impl.setActionEnabled(enabled);
    }

    public void open() {
        impl.open();
    }

    public void close() {
        impl.close();
    }

    public void toggle() {
        impl.toggle();
    }

    public void setAnchor(Region newAnchor) {
        impl.setAnchor(newAnchor);
    }
}
