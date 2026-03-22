package app.domain.cycles.ui.left;

/**
 * Контекст экрана, на котором используется левая зона.
 * Аналог CurrentCycleContext у карточки кейса — определяет,
 * какие элементы UI доступны в зависимости от места использования.
 */
public enum LeftZoneMode {

    /** Полный функционал: toggle Cycles/Cases, кнопка удаления/добавления. */
    CYCLES,

    /** Режим экрана Отчётов: toggle и btnTrash скрыты, CASES_PICKER недоступен. */
    REPORTS
}
