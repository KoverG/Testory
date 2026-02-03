package app.domain.cycles.ui.right;

import app.core.I18n;
import app.domain.cycles.CyclePrivateConfig;
import app.domain.cycles.ui.CyclesViewRefs;
import app.domain.cycles.usecase.CycleCaseRef;
import app.ui.UiComboBox;
import app.ui.UiSvg;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;

/**
 * UI-only блок: список кейсов, добавленных в цикл.
 *
 * Важно: без логики домена. Отображает только реально добавленные кейсы.
 * Никаких preview/шаблонов при пустом списке.
 */
public final class AddedCasesListUi {

    // ✅ больше отступ справа, чтобы элементы не прилипали к скроллбару
    private static final double RIGHT_SCROLL_RESERVE_PX = 24.0;

    private static final String ICON_CASE_ACTION = "kebab.svg";
    private static final String ICON_CASE_DELETE = "trash.svg";

    // ✅ comment icon (must exist in the same icons folder as others)
    private static final String ICON_COMMENT = "comment.svg";

    // 160 * 1.5 = 240
    private static final double COMBO_WIDTH_PX = 240.0;

    // визуальная “приглушённость” плейсхолдера (hint)
    private static final double HINT_OPACITY = 0.62;

    /**
     * Sentinel для пункта "Очистить выбор".
     * Важно: должен быть уникальным и не пересекаться с реальными значениями из конфига.
     */
    private static final String CLEAR_SENTINEL = "__@CLEAR_SELECTION@__";

    // key for fast toggle delete buttons inside rows
    private static final String ROW_DELETE_BTN_KEY = "cy.row.delete.btn";

    private final CyclesViewRefs v;

    // UI-only: комментарии по строке (index + title), т.к. в текущем UI нет caseId
    private final Map<String, String> commentsByRowKey = new LinkedHashMap<>();

    private boolean deleteMode = false;

    private Consumer<CycleCaseRef> onDeleteCase;

    public AddedCasesListUi(CyclesViewRefs v) {
        this.v = v;
    }

    public void init() {
        if (v.vbAddedCases == null) return;

        // резерв справа, чтобы контент не конфликтовал со скроллбаром ScrollPane
        v.vbAddedCases.setPadding(new Insets(0, RIGHT_SCROLL_RESERVE_PX, 0, 0));
    }

    public void setOnDeleteCase(Consumer<CycleCaseRef> onDeleteCase) {
        this.onDeleteCase = onDeleteCase;
    }

    public void setDeleteMode(boolean enabled) {
        this.deleteMode = enabled;

        if (v.vbAddedCases == null) return;

        for (var n : v.vbAddedCases.getChildren()) {
            if (n == null) continue;
            Object o = n.getProperties().get(ROW_DELETE_BTN_KEY);
            if (o instanceof Button b) {
                b.setVisible(enabled);
                b.setManaged(enabled);
            }
        }
    }

    /**
     * UI-only: показать выбранные кейсы.
     * Если список пустой — просто очищаем контейнер и выходим (без заглушек).
     */
    public void showCases(List<CycleCaseRef> cases) {
        if (v.vbAddedCases == null) return;

        v.vbAddedCases.getChildren().clear();

        if (cases == null || cases.isEmpty()) {
            return;
        }

        int i = 1;
        for (CycleCaseRef ref : cases) {
            if (ref == null) continue;
            addRow(i++, ref);
        }
    }

    // ===================== INTERNAL =====================

    private void addRow(int index, CycleCaseRef ref) {
        if (v.vbAddedCases == null) return;

        // spacing=0, расстояния между "колонками" задаём margin'ами (равные)
        HBox row = new HBox(0);
        row.getStyleClass().add("cy-added-case-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setMinWidth(0.0);

        Label lbIndex = new Label(String.valueOf(index));
        lbIndex.getStyleClass().add("cy-added-case-index");

        // ✅ авто-ширина под 1/2/3+ цифр: колонка расширяется по содержимому
        lbIndex.setMinWidth(Region.USE_PREF_SIZE);
        lbIndex.setPrefWidth(Region.USE_COMPUTED_SIZE);
        lbIndex.setMaxWidth(Region.USE_PREF_SIZE);

        // ✅ визуально: сдвиг вправо и центровка по высоте строки
        lbIndex.setAlignment(Pos.CENTER_RIGHT);
        lbIndex.setPadding(new Insets(0, 0, 0, 6));

        // title box (title + icons INSIDE)
        HBox titleBox = new HBox(8);
        titleBox.getStyleClass().add("cy-added-case-titlebox");
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.setMaxWidth(Double.MAX_VALUE);
        titleBox.setMinWidth(0.0);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        // ✅ одинаковый отступ между колонками: "номер -> titleBox" задаёт row spacing=0,
        //    а "titleBox -> combo" делаем margin'ом
        HBox.setMargin(titleBox, new Insets(0, 10, 0, 8));

        String title = (ref == null) ? "" : safe(ref.safeTitleSnapshot());

        Label lbTitle = new Label(title);
        lbTitle.getStyleClass().add("cy-added-case-title");

        // ✅ фикс бага: длинное название обрезаем троеточием и не вылезаем за экран
        lbTitle.setMinWidth(0.0);
        lbTitle.setPrefWidth(0.0);
        lbTitle.setMaxWidth(Double.MAX_VALUE);
        lbTitle.setWrapText(false);
        lbTitle.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox.setHgrow(lbTitle, Priority.ALWAYS);

        Button btnKebab = new Button();
        btnKebab.getStyleClass().addAll("icon-btn", "xs", "cy-added-case-action");
        btnKebab.setFocusTraversable(false);
        UiSvg.setButtonSvg(btnKebab, ICON_CASE_ACTION, 12);
        // логики пока нет — кнопка просто UI

        // ✅ NEW: row trash button (hidden until deleteMode)
        Button btnTrash = new Button();
        btnTrash.getStyleClass().addAll("icon-btn", "xs", "cy-added-case-delete");
        btnTrash.setFocusTraversable(false);
        btnTrash.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        UiSvg.setButtonSvg(btnTrash, ICON_CASE_DELETE, 12);
        Tooltip.install(btnTrash, new Tooltip(I18n.t("tc.trash.delete")));

        btnTrash.setVisible(deleteMode);
        btnTrash.setManaged(deleteMode);

        // store for fast toggling
        row.getProperties().put(ROW_DELETE_BTN_KEY, btnTrash);

        // action: delete this case row
        final CycleCaseRef safeRef = new CycleCaseRef(ref.safeId(), ref.safeTitleSnapshot());
        btnTrash.setOnAction(e -> {
            if (onDeleteCase == null) return;
            onDeleteCase.accept(safeRef);
        });

        // ✅ order matters: kebab first, trash appears to the right in same cell
        titleBox.getChildren().addAll(lbTitle, btnKebab, btnTrash);

        ComboBox<String> cb = new ComboBox<>();
        cb.getStyleClass().addAll("cy-added-case-combo", "app-combo");
        UiComboBox.install(cb, 0.92);

        cb.setFocusTraversable(false);

        // ✅ 1) ширина x1.5
        cb.setPrefWidth(COMBO_WIDTH_PX);
        cb.setMaxWidth(COMBO_WIDTH_PX);

        // ✅ одинаковый отступ между колонками: "combo -> comment btn"
        HBox.setMargin(cb, new Insets(0, 10, 0, 0));

        // ✅ 2-3) список + цвета из private-config.json
        Map<String, String> colorsByText = loadCaseComboColors();

        // i18n texts
        final String hintStatus = safe(I18n.t("cy.case.status.placeholder"));
        final String clearLabel = safe(I18n.t("cy.combo.clearSelection"));

        // базовые пункты (только статусы из конфига)
        final List<String> baseItems = new ArrayList<>(colorsByText.keySet());

        // promptText оставим, но реальный плейсхолдер рисуем через buttonCell
        cb.setPromptText(hintStatus);

        // dropdown cells: "Очистить выбор" отображаем по sentinel
        cb.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setOpacity(1.0);
                    return;
                }
                if (CLEAR_SENTINEL.equals(item)) {
                    setText(clearLabel);
                    setOpacity(1.0);
                    return;
                }
                setText(item);
                setOpacity(1.0);
            }
        });

        // button cell: плейсхолдер когда value == null
        cb.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                boolean noValue = empty || item == null || item.isBlank();
                if (noValue) {
                    setText(hintStatus);
                    setOpacity(HINT_OPACITY);
                    return;
                }

                // если вдруг попали на sentinel (теоретически не должно оставаться как value)
                if (CLEAR_SENTINEL.equals(item)) {
                    setText(hintStatus);
                    setOpacity(HINT_OPACITY);
                    return;
                }

                setText(item);
                setOpacity(1.0);
            }
        });

        // обновление items без дублей:
        // - если есть выбранный статус -> sentinel первым (всегда виден, без скролла)
        // - если выбора нет -> только статусы
        final Runnable syncItemsWithSelection = () -> {
            String v = cb.getValue();
            boolean hasSelection = v != null && !v.isBlank() && !CLEAR_SENTINEL.equals(v);

            if (hasSelection) {
                List<String> withClear = new ArrayList<>(baseItems.size() + 1);
                withClear.add(CLEAR_SENTINEL);
                withClear.addAll(baseItems);
                cb.getItems().setAll(withClear);
            } else {
                cb.getItems().setAll(baseItems);
            }
        };

        // начальное состояние: пусто -> без sentinel
        syncItemsWithSelection.run();

        // перед открытием popup гарантируем актуальный список
        cb.setOnShowing(e -> syncItemsWithSelection.run());

        // ✅ 4) при выборе — красим комбобокс + поддержка "Очистить выбор"
        cb.valueProperty().addListener((obs, oldV, newV) -> {
            // "Очистить выбор" -> сбрасываем в null
            if (CLEAR_SENTINEL.equals(newV)) {
                javafx.application.Platform.runLater(() -> {
                    cb.getSelectionModel().clearSelection();
                    cb.setValue(null);
                    cb.setStyle("");
                    syncItemsWithSelection.run();
                });
                return;
            }

            // пусто/сброс — убираем стиль
            if (newV == null || newV.isBlank()) {
                cb.setStyle("");
                syncItemsWithSelection.run();
                return;
            }

            // обычный выбранный статус
            String cssColor = colorsByText.get(newV);
            applyComboColor(cb, cssColor);

            // после выбора добавим sentinel первым элементом
            syncItemsWithSelection.run();
        });

        // ===================== COMMENT BUTTON + MODAL (LAZY) =====================

        final String rowKey = buildRowKey(index, title);

        // ✅ icon-only button (centered), tooltip text
        Button btnComment = new Button();
        btnComment.getStyleClass().addAll("icon-btn", "xs", "cy-added-case-comment");
        btnComment.setFocusTraversable(false);
        btnComment.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        btnComment.setAlignment(Pos.CENTER);

        UiSvg.setButtonSvg(btnComment, ICON_COMMENT, 12);

        Tooltip.install(btnComment, new Tooltip(I18n.t("cy.case.comment.btn")));

        // ✅ lazy modal creation to avoid lag on card open
        btnComment.setOnAction(e -> {
            if (v.rightRoot == null) return;

            CaseCommentModal modal = (CaseCommentModal) btnComment.getProperties().get("cy.case.comment.modal");
            if (modal == null) {
                modal = new CaseCommentModal(btnComment);

                modal.setCurrentValueSupplier(() -> commentsByRowKey.getOrDefault(rowKey, ""));
                modal.setOnSaved(val -> {
                    String s = safe(val);
                    if (s.isEmpty()) commentsByRowKey.remove(rowKey);
                    else commentsByRowKey.put(rowKey, s);
                });

                modal.install(v.rightRoot);

                btnComment.getProperties().put("cy.case.comment.modal", modal);
            }

            modal.toggle();
        });

        row.getChildren().addAll(lbIndex, titleBox, cb, btnComment);

        v.vbAddedCases.getChildren().add(row);
    }

    private static Map<String, String> loadCaseComboColors() {
        Map<String, String> fromCfg = CyclePrivateConfig.caseComboColors();
        if (fromCfg != null && !fromCfg.isEmpty()) return fromCfg;

        // fallback: сохраняем прежнее поведение (чтобы не "сломать функционал")
        Map<String, String> def = new LinkedHashMap<>();
        def.put("Option A", "");
        def.put("Option B", "");
        def.put("Option C", "");
        return def;
    }

    private static void applyComboColor(ComboBox<?> cb, String cssColor) {
        if (cb == null) return;

        String c = (cssColor == null) ? "" : cssColor.trim();
        if (c.isEmpty()) {
            cb.setStyle("");
            return;
        }

        // Фон задаём inline, чтобы отработало поверх существующих стилей.
        // Бордер/остальные стили остаются из CSS классов.
        cb.setStyle(
                "-fx-background-color: " + c + ";" +
                        "-fx-background-insets: 1;" +
                        "-fx-background-radius: 12;"
        );
    }

    private static String buildRowKey(int index, String title) {
        return index + "|" + safe(title);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
