package app.domain.cycles.ui.left;

import app.domain.cycles.ui.CyclesViewRefs;
import app.domain.cycles.ui.right.RightPaneCoordinator;

public final class CyclesListActions implements LeftPaneActions {

    private final CyclesViewRefs v;
    private final RightPaneCoordinator right;
    private final Runnable switchToCasesPicker;

    public CyclesListActions(CyclesViewRefs v, RightPaneCoordinator right, Runnable switchToCasesPicker) {
        this.v = v;
        this.right = right;
        this.switchToCasesPicker = switchToCasesPicker;
    }

    @Override
    public void onCreate() {
        // ✅ сценарий: "создать цикл" => сначала выбираем кейсы
        switchToCasesPicker.run();

        // ✅ и показываем правую карточку (анимация как в testcases)
        right.openCreateCard();
    }

    @Override
    public void onDelete() {
        v.lblRightHint.setText("Delete cycle (stub)");
    }

    @Override
    public void onFilter() {
        v.lblRightHint.setText("Filter cycles (stub)");
    }

    @Override
    public void onSort() {
        v.lblRightHint.setText("Sort cycles (stub)");
    }

    @Override
    public void onSearch(String query) {
        v.lblRightHint.setText("Search: " + (query == null ? "" : query));
    }

    @Override
    public void onOpenSelected() {
        v.lblRightHint.setText("Open selected cycle (stub)");
    }
}
