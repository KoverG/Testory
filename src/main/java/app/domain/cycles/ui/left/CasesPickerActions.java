package app.domain.cycles.ui.left;

public final class CasesPickerActions implements LeftPaneActions {

    private final CyclesLeftViewRefs v;
    private final CyclesLeftHost host;
    private final Runnable switchToCyclesList;

    public CasesPickerActions(CyclesLeftViewRefs v, CyclesLeftHost host, Runnable switchToCyclesList) {
        this.v = v;
        this.host = host;
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

        host.openCreateCard();

        // как в CyclesListActions: снимаем выделение слева (на всякий случай)
        try {
            if (v != null && v.lvLeft != null) v.lvLeft.getSelectionModel().clearSelection();
        } catch (Exception ignored) {}
    }

    @Override
    public void onDelete() {
        // удаление кейсов запрещено — кнопка должна быть disabled в coordinator
    }

    @Override
    public void onFilter() {}

    @Override
    public void onSort() {}

    @Override
    public void onSearch(String query) {}

    @Override
    public void onOpenSelected() {}
}
