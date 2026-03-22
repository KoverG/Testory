package app.domain.cycles.ui.left;

import app.domain.cycles.usecase.CycleCaseRef;
import javafx.beans.property.ReadOnlyBooleanProperty;

import java.nio.file.Path;
import java.util.List;

/**
 * Абстракция правой зоны (и экрана в целом), которая нужна LeftPaneCoordinator.
 * Реализуется CyclesScreen — позволяет переиспользовать LeftPaneCoordinator
 * на любом экране, у которого есть такая же левая зона (например, экран Отчётов).
 */
public interface CyclesLeftHost {

    // ===== состояние правой зоны =====

    boolean isRightOpen();

    /** ID открытого цикла, или "" если открыта create-карточка. */
    String openedCycleId();

    void openCreateCard();

    void closeRight();

    void openExistingCard(Path file);

    // ===== cases picker (специфично для Cycles) =====

    List<String> getAddedCaseIds();

    void removeAddedCasesByIds(List<String> ids);

    void addAddedCases(List<CycleCaseRef> refs);

    void closePickerPreviewCaseCard();

    boolean isEditModeEnabled();

    void openTestCaseCardFromList(String caseId, List<String> allIds);

    // ===== события =====

    /**
     * Устанавливает колбэк, который вызывается при изменении UI-состояния правой зоны.
     * Координатор вызывает его из init(), поэтому он должен быть установлен до init().
     */
    void setOnUiStateChanged(Runnable callback);

    /**
     * Свойство видимости правой зоны — используется для обновления btnTrash.
     * Может вернуть null, если правая зона отсутствует (например, экран Отчётов).
     */
    ReadOnlyBooleanProperty rightVisibleProperty();
}
