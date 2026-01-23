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

        // ✅ если пользователь закрыл правую зону — возвращаем левую зону к циклам,
        // потому что btnFolder больше не "back", а кнопка переключения находится справа
        right.setOnClose(() -> left.setMode(LeftMode.CYCLES_LIST));

        // ❌ ничего не “показываем” справа при входе
    }
}
