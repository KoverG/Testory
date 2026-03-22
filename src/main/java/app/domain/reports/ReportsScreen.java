package app.domain.reports;

import app.domain.cycles.ui.ThemeToggleUiInstaller;
import app.domain.cycles.ui.left.CyclesLeftHost;
import app.domain.cycles.ui.left.CyclesLeftViewRefs;
import app.domain.cycles.ui.left.LeftPaneCoordinator;
import app.domain.cycles.ui.left.LeftZoneMode;
import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.reports.model.ReportTarget;
import app.domain.reports.ui.ReportCardView;
import app.domain.testcases.ui.RightPaneAnimator;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.List;

/**
 * Координатор экрана Отчётов.
 */
public final class ReportsScreen implements CyclesLeftHost {

    private final CyclesLeftViewRefs leftRefs;
    private final StackPane rightRoot;
    private final VBox rightPlaceholder;

    private LeftPaneCoordinator left;
    private RightPaneAnimator anim;
    private ReportCardView cardView;
    private boolean open = false;
    private String openedId = "";

    public ReportsScreen(CyclesLeftViewRefs leftRefs,
                         StackPane rightRoot,
                         VBox rightPlaceholder) {
        this.leftRefs         = leftRefs;
        this.rightRoot        = rightRoot;
        this.rightPlaceholder = rightPlaceholder;
    }

    public void init() {
        left = new LeftPaneCoordinator(leftRefs, this);
        left.init();

        ThemeToggleUiInstaller.install(leftRefs.tgThemeLeft, left);

        if (rightRoot != null) {
            anim = new RightPaneAnimator(rightRoot);
        }

        if (rightPlaceholder != null) {
            rightPlaceholder.setSpacing(0);
            cardView = new ReportCardView(this::closeRight);
            VBox.setVgrow(cardView.view(), Priority.ALWAYS);
            rightPlaceholder.getChildren().add(cardView.view());
        }
    }

    // ===== CyclesLeftHost =====

    @Override
    public LeftZoneMode leftZoneMode() {
        return LeftZoneMode.REPORTS;
    }

    @Override
    public boolean isRightOpen() { return open; }

    @Override
    public String openedCycleId() { return openedId; }

    @Override
    public void openCreateCard() {}

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
        if (open && id.equals(openedId)) {
            closeRight();
            return;
        }
        openedId = id;
        if (cardView != null && file != null) {
            cardView.load(ReportTarget.forCycle(id, file));
        }
        showRightPane();
    }

    @Override
    public void openTestCaseCardFromList(String caseId, List<String> allIds) {
        if (rightRoot == null) return;
        if (open && caseId != null && caseId.equals(openedId)) {
            closeRight();
            return;
        }
        openedId = caseId != null ? caseId : "";
        if (cardView != null && !openedId.isEmpty()) {
            cardView.load(ReportTarget.forCase(openedId));
        }
        showRightPane();
    }

    private void showRightPane() {
        if (!open) {
            open = true;
            anim.show(() -> {
                rightRoot.setVisible(true);
                rightRoot.setManaged(true);
            }, null);
        } else {
            anim.pulseReplace();
        }
    }

    @Override
    public ReadOnlyBooleanProperty rightVisibleProperty() {
        return rightRoot != null ? (ReadOnlyBooleanProperty) rightRoot.visibleProperty() : null;
    }

    @Override public List<String> getAddedCaseIds()                      { return List.of(); }
    @Override public void removeAddedCasesByIds(List<String> ids)        {}
    @Override public void addAddedCases(List<CycleCaseRef> refs)         {}
    @Override public void closePickerPreviewCaseCard()                   {}
    @Override public boolean isEditModeEnabled()                         { return false; }
    @Override public void setOnUiStateChanged(Runnable callback)         {}
}
