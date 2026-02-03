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
        // ✅ Требование: при нажатии "Создать" create-card справа должен открываться
        // независимо от положения tgThemeLeft.
        // Если мы в режиме кейсов — сначала возвращаемся к списку циклов (это же переключит toggle),
        // затем открываем create-card справа.
        if (switchToCyclesList != null) {
            switchToCyclesList.run();
        }

        if (right != null) {
            right.openCreateCard();
        }

        // как в CyclesListActions: снимаем выделение слева (на всякий случай)
        try {
            if (v != null && v.lvLeft != null) v.lvLeft.getSelectionModel().clearSelection();
        } catch (Exception ignored) {}
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
