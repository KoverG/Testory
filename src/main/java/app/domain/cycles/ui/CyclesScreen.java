// FILE: src/main/java/app/domain/cycles/ui/CyclesScreen.java
package app.domain.cycles.ui;

import app.domain.cycles.ui.left.LeftMode;
import app.domain.cycles.ui.left.LeftPaneCoordinator;
import app.domain.cycles.ui.right.RightPaneCoordinator;

public final class CyclesScreen {

    private final CyclesViewRefs v;

    private LeftPaneCoordinator left;
    private RightPaneCoordinator right;

    public CyclesScreen(CyclesViewRefs v) {
        this.v = v;
    }

    public void init() {
        right = new RightPaneCoordinator(v);
        right.init();

        left = new LeftPaneCoordinator(v, right);
        left.init();

        // ✅ tgThemeLeft теперь отвечает за переключение списка (Cycles/Cases)
        ThemeToggleUiInstaller.install(v.tgThemeLeft, left);

        // right button "Добавить кейсы" -> ТУМБЛЕР:
        // 1-й клик: cases picker + overlay "Добавить"
        // 2-й клик: вернуть исходное состояние (cycles list)
        right.setOnAddCases(() -> left.toggleCasesPickerAddMode());

        // если пользователь закрыл правую зону — возвращаем левую зону к циклам
        right.setOnClose(() -> left.setMode(LeftMode.CYCLES_LIST));

        // после сохранения — обновить cycles list
        right.setOnSaved(() -> {
            left.setMode(LeftMode.CYCLES_LIST);
            left.refreshCyclesFromDisk();
        });

        // ✅ после удаления — обновить cycles list
        right.setOnDeleted(() -> left.refreshCyclesFromDisk());
    }
    public void openCycleFromHistory(String cycleId, String sourceCaseId) {
        if (right == null) return;
        right.openCycleFromHistory(cycleId, sourceCaseId);
    }
}