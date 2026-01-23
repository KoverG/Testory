package app.domain.cycles.ui.list;

import javafx.scene.control.ListCell;

public class CycleCell extends ListCell<Object> {

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

        String title = it.title() == null ? "" : it.title().trim();
        setText(title);
    }
}
