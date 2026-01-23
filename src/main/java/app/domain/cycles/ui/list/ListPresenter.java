package app.domain.cycles.ui.list;

import javafx.collections.ObservableList;
import javafx.scene.control.ListView;

@SuppressWarnings("unchecked")
public class ListPresenter {

    private final ListView<Object> list;

    public ListPresenter(ListView<Object> list) {
        this.list = list;
    }

    public void showCycles(ObservableList<? extends Object> items) {
        list.setCellFactory(lv -> new CycleCell());
        list.setItems((ObservableList<Object>) items);
        list.getSelectionModel().clearSelection();
    }

    public void showCases(ObservableList<? extends Object> items) {
        list.setCellFactory(lv -> new CaseCell());
        list.setItems((ObservableList<Object>) items);
        list.getSelectionModel().clearSelection();
    }
}
