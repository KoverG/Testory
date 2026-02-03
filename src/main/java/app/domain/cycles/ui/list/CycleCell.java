package app.domain.cycles.ui.list;

import javafx.scene.control.ListCell;

public class CycleCell extends ListCell<Object> {

    private static final String UNTITLED = "(без названия)";

    @Override
    protected void updateItem(Object item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            return;
        }

        if (!(item instanceof CycleListItem it)) {
            setText(null);
            return;
        }

        // Вариант 2: формируем UI строку в UI слое, не показываем id
        String t = it.safeTitle();
        String d = it.safeCreatedAtUi();

        if (t.isBlank()) t = UNTITLED;     // если хочешь i18n — это можно позже заменить на I18n.t(...)
        if (d.isBlank()) setText(t);
        else setText(t + " " + d);
    }
}
