// FILE: src/main/java/app/domain/cycles/ui/left/CyclesListActions.java
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
        // "Создать":
        // - если правая зона закрыта -> открываем create-card
        // - если правая зона открыта:
        //     - если открыт existing-cycle -> переключаемся на create-card (с replace-анимацией)
        //     - если уже открыт create-card -> закрываем (сохраняем прежний toggle-UX)
        if (right == null) return;

        if (right.isOpen()) {
            String openedId = safeTrim(right.openedCycleId());
            if (!openedId.isEmpty()) {
                // был открыт existing -> открыть create с той же анимацией replace
                right.openCreateCard();
                try {
                    if (v.lvLeft != null) v.lvLeft.getSelectionModel().clearSelection();
                } catch (Exception ignored) {}
            } else {
                // уже открыт create -> toggle-close как раньше
                right.close();
            }
        } else {
            right.openCreateCard();
            try {
                if (v.lvLeft != null) v.lvLeft.getSelectionModel().clearSelection();
            } catch (Exception ignored) {}
        }
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

    private static String safeTrim(String v) {
        return v == null ? "" : v.trim();
    }
}
