package app.domain.cycles.ui.list;

import app.core.I18n;
import javafx.scene.control.ListCell;

public class CaseCell extends ListCell<Object> {

    @Override
    protected void updateItem(Object item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            return;
        }

        if (!(item instanceof CaseListItem it)) {
            setText(null);
            return;
        }

        // 1-в-1 логика отображения имени как на экране Testcases:
        // - без "•"
        // - если заголовок пустой -> tc.default.title
        String title = it.title() == null ? "" : it.title().trim();
        if (title.isBlank()) title = I18n.t("tc.default.title");

        setText(title);
    }
}
