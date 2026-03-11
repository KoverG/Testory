package app.domain.testcases.ui;

import app.core.I18n;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.function.Consumer;

/**
 * Adapter (domain -> ui):
 * TestCasesController исторически создаёт overlay как new TestCasesTrashOverlay(leftStack)
 * и потом отдельно вызывает setAnchor(casesSheet).
 *
 * Поэтому держим совместимый конструктор (StackPane),
 * а anchor задаём через setAnchor().
 *
 * Реальная реализация — app.ui.list.LeftListActionOverlay.
 */
public final class TestCasesTrashOverlay {

    private static final String I18N_DELETE_BTN = "tc.trash.delete";

    private final StackPane leftStack;

    private Region anchor; // может быть задан позже

    private app.ui.list.LeftListActionOverlay impl;

    public TestCasesTrashOverlay(StackPane leftStack) {
        this.leftStack = leftStack;
        ensureImpl();
    }

    /** Доп. конструктор (не обязателен для контроллера), но иногда удобен. */
    public TestCasesTrashOverlay(StackPane leftStack, Region anchor) {
        this.leftStack = leftStack;
        this.anchor = anchor;
        ensureImpl();
    }

    public void setAnchor(Region newAnchor) {
        this.anchor = newAnchor;
        ensureImpl();
        impl.setAnchor(newAnchor);
    }

    public void init(Button btnTrash) {
        ensureImpl();
        impl.init(btnTrash);
    }

    public void setOnSpacerChanged(Runnable r) {
        ensureImpl();
        impl.setOnSpacerChanged(r);
    }

    public void setOnOpenChanged(Consumer<Boolean> c) {
        ensureImpl();
        impl.setOnOpenChanged(c);
    }

    public boolean isOpen() {
        ensureImpl();
        return impl.isOpen();
    }

    public double scrollSpacerPx() {
        ensureImpl();
        return impl.scrollSpacerPx();
    }

    public Node overlayRoot() {
        ensureImpl();
        return impl.overlayRoot();
    }

    public CheckBox selectAllCheckBox() {
        ensureImpl();
        return impl.selectAllCheckBox();
    }

    public void setOnDelete(Runnable r) {
        ensureImpl();
        impl.setOnDelete(r);
    }

    public void setDeleteEnabled(boolean enabled) {
        ensureImpl();
        impl.setDeleteEnabled(enabled);
    }

    public void open() {
        ensureImpl();
        impl.open();
    }

    public void close() {
        ensureImpl();
        impl.close();
    }

    public void toggle() {
        ensureImpl();
        impl.toggle();
    }

    public void refreshI18n() {
        ensureImpl();
        impl.setButtonText(I18n.t(I18N_DELETE_BTN));
    }

    // ===================== INTERNAL =====================

    private void ensureImpl() {
        if (impl != null) return;

        // создаём универсальный overlay с текущим anchor (может быть null — это ок)
        impl = new app.ui.list.LeftListActionOverlay(leftStack, anchor, I18n.t(I18N_DELETE_BTN));
    }
}
