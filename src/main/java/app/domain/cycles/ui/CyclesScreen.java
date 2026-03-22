// FILE: src/main/java/app/domain/cycles/ui/CyclesScreen.java
package app.domain.cycles.ui;

import app.domain.cycles.ui.left.CyclesLeftHost;
import app.domain.cycles.ui.left.CyclesLeftViewRefs;
import app.domain.cycles.ui.left.LeftMode;
import app.domain.cycles.ui.left.LeftPaneCoordinator;
import app.domain.cycles.ui.left.LeftZoneMode;
import app.domain.cycles.ui.right.RightPaneCoordinator;
import app.domain.cycles.usecase.CycleCaseRef;
import javafx.beans.property.ReadOnlyBooleanProperty;

import java.nio.file.Path;
import java.util.List;

public final class CyclesScreen implements CyclesLeftHost {

    private final CyclesViewRefs v;

    private LeftPaneCoordinator left;
    private RightPaneCoordinator right;

    public CyclesScreen(CyclesViewRefs v) {
        this.v = v;
    }

    public void init() {
        right = new RightPaneCoordinator(v);
        right.init();

        CyclesLeftViewRefs leftRefs = new CyclesLeftViewRefs(
                v.leftStack,
                v.tfSearch,
                v.btnSearch,
                v.btnFolder,
                v.btnCreate,
                v.tgThemeLeft,
                v.btnTrash,
                v.btnFilter,
                v.btnSort,
                v.lblSortSummary,
                v.casesSheet,
                v.lvLeft,
                v.filterSheet,
                v.sortSheet
        );

        left = new LeftPaneCoordinator(leftRefs, this);
        left.init();

        // tgThemeLeft отвечает за переключение списка (Cycles/Cases)
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

        // после удаления — обновить cycles list
        right.setOnDeleted(() -> left.refreshCyclesFromDisk());
    }

    public void openCycleFromHistory(String cycleId, String sourceCaseId) {
        if (right == null) return;
        right.openCycleFromHistory(cycleId, sourceCaseId);
    }

    // ===== CyclesLeftHost =====

    @Override
    public boolean isRightOpen() {
        return right != null && right.isOpen();
    }

    @Override
    public String openedCycleId() {
        return right != null ? right.openedCycleId() : "";
    }

    @Override
    public void openCreateCard() {
        if (right != null) right.openCreateCard();
    }

    @Override
    public void closeRight() {
        if (right != null) right.close();
    }

    @Override
    public void openExistingCard(Path file) {
        if (right != null) right.openExistingCard(file);
    }

    @Override
    public List<String> getAddedCaseIds() {
        return right != null ? right.getAddedCaseIds() : List.of();
    }

    @Override
    public void removeAddedCasesByIds(List<String> ids) {
        if (right != null) right.removeAddedCasesByIds(ids);
    }

    @Override
    public void addAddedCases(List<CycleCaseRef> refs) {
        if (right != null) right.addAddedCases(refs);
    }

    @Override
    public void closePickerPreviewCaseCard() {
        if (right != null) right.closePickerPreviewCaseCard();
    }

    @Override
    public boolean isEditModeEnabled() {
        return right != null && right.isEditModeEnabled();
    }

    @Override
    public void openTestCaseCardFromList(String caseId, List<String> allIds) {
        if (right != null) right.openTestCaseCardFromList(caseId, allIds);
    }

    @Override
    public void setOnUiStateChanged(Runnable callback) {
        if (right != null) right.setOnUiStateChanged(callback);
    }

    @Override
    public ReadOnlyBooleanProperty rightVisibleProperty() {
        return v.rightRoot != null ? (ReadOnlyBooleanProperty) v.rightRoot.visibleProperty() : null;
    }

    @Override
    public LeftZoneMode leftZoneMode() {
        return LeftZoneMode.CYCLES;
    }
}