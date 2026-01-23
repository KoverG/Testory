// FILE: src/main/java/app/domain/cycles/ui/left/CasesPickerActions.java
package app.domain.cycles.ui.left;

import app.domain.cycles.ui.CyclesViewRefs;
import app.domain.cycles.ui.right.RightPaneCoordinator;

public final class CasesPickerActions implements LeftPaneActions {

    private final CyclesViewRefs v;
    private final RightPaneCoordinator right;
    private final Runnable switchToCyclesList;

    public CasesPickerActions(CyclesViewRefs v, RightPaneCoordinator right, Runnable switchToCyclesList) {
        this.v = v;
        this.right = right;
        this.switchToCyclesList = switchToCyclesList;
    }

    @Override
    public void onCreate() {
        // save cycle (stub)
        right.close();
        switchToCyclesList.run();
    }

    @Override
    public void onDelete() {
        // удаление кейсов запрещено — кнопка должна быть disabled в coordinator,
        // но на всякий случай игнорируем
    }

    @Override
    public void onFilter() {
        v.lblRightHint.setText("Filter cases (stub)");
    }

    @Override
    public void onSort() {
        v.lblRightHint.setText("Sort cases (stub)");
    }

    @Override
    public void onSearch(String query) {
        v.lblRightHint.setText("Search cases: " + (query == null ? "" : query));
    }

    @Override
    public void onOpenSelected() {
        v.lblRightHint.setText("Open selected case (stub)");
    }
}
