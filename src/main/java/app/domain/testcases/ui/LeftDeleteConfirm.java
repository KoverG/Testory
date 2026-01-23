package app.domain.testcases.ui;

import javafx.scene.layout.StackPane;

import java.util.function.BooleanSupplier;

public final class LeftDeleteConfirm {

    private final app.ui.confirm.LeftDeleteConfirm impl;

    public LeftDeleteConfirm(StackPane leftStack, Runnable onConfirm) {
        // onConfirm здесь = onDelete в универсальном классе
        this.impl = new app.ui.confirm.LeftDeleteConfirm(leftStack, onConfirm);
    }

    public void setCanOpenSupplier(BooleanSupplier canOpenSupplier) {
        impl.setCanOpenSupplier(canOpenSupplier);
    }

    public void setTextKeys(String titleKey, String hintKey) {
        impl.setTextKeys(titleKey, hintKey);
    }

    public boolean isOpen() {
        return impl.isOpen();
    }

    public void refreshAvailability(boolean enabled) {
        impl.refreshAvailability(enabled);
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
}
