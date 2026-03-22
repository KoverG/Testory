// FILE: src/main/java/app/domain/cycles/ui/left/CyclesListActions.java
package app.domain.cycles.ui.left;

public final class CyclesListActions implements LeftPaneActions {

    private final CyclesLeftViewRefs v;
    private final CyclesLeftHost host;
    private final Runnable switchToCasesPicker;

    public CyclesListActions(CyclesLeftViewRefs v, CyclesLeftHost host, Runnable switchToCasesPicker) {
        this.v = v;
        this.host = host;
        this.switchToCasesPicker = switchToCasesPicker;
    }

    @Override
    public void onCreate() {
        // "Создать":
        // - если правая зона закрыта -> открываем create-card
        // - если правая зона открыта:
        //     - если открыт existing-cycle -> переключаемся на create-card (с replace-анимацией)
        //     - если уже открыт create-card -> закрываем (сохраняем прежний toggle-UX)
        if (host.isRightOpen()) {
            String openedId = safeTrim(host.openedCycleId());
            if (!openedId.isEmpty()) {
                // был открыт existing -> открыть create с той же анимацией replace
                host.openCreateCard();
                try {
                    if (v.lvLeft != null) v.lvLeft.getSelectionModel().clearSelection();
                } catch (Exception ignored) {}
            } else {
                // уже открыт create -> toggle-close как раньше
                host.closeRight();
            }
        } else {
            host.openCreateCard();
            try {
                if (v.lvLeft != null) v.lvLeft.getSelectionModel().clearSelection();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDelete() {}

    @Override
    public void onFilter() {}

    @Override
    public void onSort() {}

    @Override
    public void onSearch(String query) {}

    @Override
    public void onOpenSelected() {}

    private static String safeTrim(String v) {
        return v == null ? "" : v.trim();
    }
}
