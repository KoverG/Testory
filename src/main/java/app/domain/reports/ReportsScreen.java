package app.domain.reports;

import app.domain.cycles.ui.ThemeToggleUiInstaller;
import app.domain.cycles.ui.left.CyclesLeftHost;
import app.domain.cycles.ui.left.CyclesLeftViewRefs;
import app.domain.cycles.ui.left.LeftPaneCoordinator;
import app.domain.cycles.ui.left.LeftZoneMode;
import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.testcases.ui.RightPaneAnimator;
import app.ui.UiSvg;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

import java.nio.file.Path;
import java.util.List;

/**
 * Координатор экрана Отчётов.
 * Реализует CyclesLeftHost в режиме REPORTS — переиспользует LeftPaneCoordinator.
 * Правая зона — подложка с кнопкой «Закрыть», анимация аналогична Cycles.
 */
public final class ReportsScreen implements CyclesLeftHost {

    private final CyclesLeftViewRefs leftRefs;
    private final StackPane rightRoot;
    private final Button btnCloseRight;

    private LeftPaneCoordinator left;
    private RightPaneAnimator anim;
    private boolean open = false;
    private String openedId = "";

    public ReportsScreen(CyclesLeftViewRefs leftRefs, StackPane rightRoot, Button btnCloseRight) {
        this.leftRefs = leftRefs;
        this.rightRoot = rightRoot;
        this.btnCloseRight = btnCloseRight;
    }

    public void init() {
        left = new LeftPaneCoordinator(leftRefs, this);
        left.init();

        ThemeToggleUiInstaller.install(leftRefs.tgThemeLeft, left);

        if (rightRoot != null) {
            anim = new RightPaneAnimator(rightRoot);
        }

        if (btnCloseRight != null) {
            UiSvg.setButtonSvg(btnCloseRight, "close.svg", 14);
            btnCloseRight.setFocusTraversable(false);
            btnCloseRight.setOnAction(e -> closeRight());
        }
    }

    // ===== CyclesLeftHost =====

    @Override
    public LeftZoneMode leftZoneMode() {
        return LeftZoneMode.REPORTS;
    }

    @Override
    public boolean isRightOpen() {
        return open;
    }

    @Override
    public String openedCycleId() {
        return openedId;
    }

    @Override
    public void openCreateCard() {
        // карточка отчёта ещё не реализована
    }

    @Override
    public void closeRight() {
        if (!open || rightRoot == null) return;
        open = false;
        openedId = "";
        anim.hide(null, () -> {
            rightRoot.setVisible(false);
            rightRoot.setManaged(false);
        });
    }

    @Override
    public void openExistingCard(Path file) {
        if (rightRoot == null) return;
        String id = file == null ? "" : file.getFileName().toString().replace(".json", "");
        openedId = id;
        showRightPane();
    }

    private void showRightPane() {
        if (!open) {
            open = true;
            anim.show(
                    () -> {
                        rightRoot.setVisible(true);
                        rightRoot.setManaged(true);
                    },
                    null
            );
        } else {
            anim.pulseReplace();
        }
    }

    @Override
    public ReadOnlyBooleanProperty rightVisibleProperty() {
        return rightRoot != null ? (ReadOnlyBooleanProperty) rightRoot.visibleProperty() : null;
    }

    // cases picker — недоступен в режиме REPORTS

    @Override
    public List<String> getAddedCaseIds() {
        return List.of();
    }

    @Override
    public void removeAddedCasesByIds(List<String> ids) {}

    @Override
    public void addAddedCases(List<CycleCaseRef> refs) {}

    @Override
    public void closePickerPreviewCaseCard() {}

    @Override
    public boolean isEditModeEnabled() {
        return false;
    }

    @Override
    public void openTestCaseCardFromList(String caseId, List<String> allIds) {
        if (rightRoot == null) return;
        // toggle-close при повторном клике по тому же кейсу
        if (open && caseId != null && caseId.equals(openedId)) {
            closeRight();
            return;
        }
        openedId = caseId != null ? caseId : "";
        showRightPane();
    }

    @Override
    public void setOnUiStateChanged(Runnable callback) {}
}
