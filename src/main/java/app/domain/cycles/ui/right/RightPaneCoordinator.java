package app.domain.cycles.ui.right;

import app.domain.cycles.ui.CyclesViewRefs;
import app.domain.testcases.ui.RightPaneAnimator;
import app.ui.UiSvg;

public final class RightPaneCoordinator {

    private final CyclesViewRefs v;
    private final RightPaneAnimator anim;

    private boolean open = false;

    // ✅ чтобы при закрытии правой зоны можно было гарантированно вернуться к циклам
    private Runnable onClose;

    public RightPaneCoordinator(CyclesViewRefs v) {
        this.v = v;
        this.anim = new RightPaneAnimator(v.rightRoot); // анимируем весь rightRoot
        this.anim.setDx(RightPaneAnimator.DEFAULT_DX);
        this.anim.setMs(RightPaneAnimator.DEFAULT_MS);
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void init() {
        snapClosed();

        // кнопка закрытия, как в testcases (иконка close.svg)
        if (v.btnCloseRight != null) {
            UiSvg.setButtonSvg(v.btnCloseRight, "close.svg", 14);
            v.btnCloseRight.setFocusTraversable(false);
            v.btnCloseRight.setOnAction(e -> close());
        }
    }

    public boolean isOpen() {
        return open;
    }

    /**
     * Каноничный метод: его вызывает CyclesListActions.onCreate()
     */
    public void openCreateCard() {
        if (open) return;
        open = true;

        // Пока заглушка контента — но карточка должна появиться
        if (v.lblRightTitle != null) v.lblRightTitle.setText("Cycle");
        if (v.lblRightHint != null) v.lblRightHint.setText("Create cycle (stub)");

        anim.show(
                () -> {
                    v.rightRoot.setVisible(true);
                    v.rightRoot.setManaged(true);
                },
                null
        );
    }

    /**
     * Алиас (если где-то в будущем будет вызываться open()).
     * Но основной метод — openCreateCard().
     */
    public void open() {
        openCreateCard();
    }

    public void close() {
        if (!open) return;
        open = false;

        anim.hide(
                null,
                () -> {
                    v.rightRoot.setVisible(false);
                    v.rightRoot.setManaged(false);
                    if (onClose != null) onClose.run();
                }
        );
    }

    public void snapClosed() {
        open = false;

        if (v.rightRoot != null) {
            v.rightRoot.setVisible(false);
            v.rightRoot.setManaged(false);
            v.rightRoot.setOpacity(0.0);
            v.rightRoot.setTranslateX(0.0);
        }
    }
}
