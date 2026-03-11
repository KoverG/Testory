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
 * UI-only Р±Р»РѕРє: СЃРїРёСЃРѕРє РєРµР№СЃРѕРІ, РґРѕР±Р°РІР»РµРЅРЅС‹С… РІ С†РёРєР».
 *
 * Р’Р°Р¶РЅРѕ: Р±РµР· Р»РѕРіРёРєРё РґРѕРјРµРЅР°. РћС‚РѕР±СЂР°Р¶Р°РµС‚ С‚РѕР»СЊРєРѕ СЂРµР°Р»СЊРЅРѕ РґРѕР±Р°РІР»РµРЅРЅС‹Рµ РєРµР№СЃС‹.
 * РќРёРєР°РєРёС… preview/С€Р°Р±Р»РѕРЅРѕРІ РїСЂРё РїСѓСЃС‚РѕРј СЃРїРёСЃРєРµ.
 */
public final class AddedCasesListUi {

    // вњ… Р±РѕР»СЊС€Рµ РѕС‚СЃС‚СѓРї СЃРїСЂР°РІР°, С‡С‚РѕР±С‹ СЌР»РµРјРµРЅС‚С‹ РЅРµ РїСЂРёР»РёРїР°Р»Рё Рє СЃРєСЂРѕР»Р»Р±Р°СЂСѓ
    private static final double RIGHT_SCROLL_RESERVE_PX = 24.0;

    private static final String ICON_CASE_ACTION = "kebab.svg";
    private static final String ICON_CASE_DELETE = "trash.svg";

    // вњ… comment icon (must exist in the same icons folder as others)
    private static final String ICON_COMMENT = "comment.svg";

    // 160 * 1.5 = 240
    private static final double COMBO_WIDTH_PX = 240.0;

    // РІРёР·СѓР°Р»СЊРЅР°СЏ вЂњРїСЂРёРіР»СѓС€С‘РЅРЅРѕСЃС‚СЊвЂќ РїР»РµР№СЃС…РѕР»РґРµСЂР° (hint)
    private static final double HINT_OPACITY = 0.62;

    /**
     * Sentinel РґР»СЏ РїСѓРЅРєС‚Р° "РћС‡РёСЃС‚РёС‚СЊ РІС‹Р±РѕСЂ".
     * Р’Р°Р¶РЅРѕ: РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ СѓРЅРёРєР°Р»СЊРЅС‹Рј Рё РЅРµ РїРµСЂРµСЃРµРєР°С‚СЊСЃСЏ СЃ СЂРµР°Р»СЊРЅС‹РјРё Р·РЅР°С‡РµРЅРёСЏРјРё РёР· РєРѕРЅС„РёРіР°.
     */
    private static final String CLEAR_SENTINEL = "__@CLEAR_SELECTION@__";

    // key for fast toggle delete buttons inside rows
    private static final String ROW_DELETE_BTN_KEY = "cy.row.delete.btn";

    private final CyclesViewRefs v;

    // UI-only: РєРѕРјРјРµРЅС‚Р°СЂРёРё РїРѕ СЃС‚СЂРѕРєРµ (index + title), С‚.Рє. РІ С‚РµРєСѓС‰РµРј UI РЅРµС‚ caseId
    private final Map<String, String> commentsByRowKey = new LinkedHashMap<>();

    private boolean deleteMode = false;

    private Consumer<CycleCaseRef> onDeleteCase;
    private Consumer<CycleCaseRef> onOpenCase;

    public AddedCasesListUi(CyclesViewRefs v) {
        this.v = v;
    }

    public void init() {
        if (v.vbAddedCases == null) return;

        // СЂРµР·РµСЂРІ СЃРїСЂР°РІР°, С‡С‚РѕР±С‹ РєРѕРЅС‚РµРЅС‚ РЅРµ РєРѕРЅС„Р»РёРєС‚РѕРІР°Р» СЃРѕ СЃРєСЂРѕР»Р»Р±Р°СЂРѕРј ScrollPane
        v.vbAddedCases.setPadding(new Insets(0, RIGHT_SCROLL_RESERVE_PX, 0, 0));
    }

    public void setOnDeleteCase(Consumer<CycleCaseRef> onDeleteCase) {
        this.onDeleteCase = onDeleteCase;
    }

    public void setOnOpenCase(Consumer<CycleCaseRef> onOpenCase) {
        this.onOpenCase = onOpenCase;
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
     * UI-only: РїРѕРєР°Р·Р°С‚СЊ РІС‹Р±СЂР°РЅРЅС‹Рµ РєРµР№СЃС‹.
     * Р•СЃР»Рё СЃРїРёСЃРѕРє РїСѓСЃС‚РѕР№ вЂ” РїСЂРѕСЃС‚Рѕ РѕС‡РёС‰Р°РµРј РєРѕРЅС‚РµР№РЅРµСЂ Рё РІС‹С…РѕРґРёРј (Р±РµР· Р·Р°РіР»СѓС€РµРє).
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

        // spacing=0, СЂР°СЃСЃС‚РѕСЏРЅРёСЏ РјРµР¶РґСѓ "РєРѕР»РѕРЅРєР°РјРё" Р·Р°РґР°С‘Рј margin'Р°РјРё (СЂР°РІРЅС‹Рµ)
        HBox row = new HBox(0);
        row.getStyleClass().add("cy-added-case-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setMinWidth(0.0);

        Label lbIndex = new Label(String.valueOf(index));
        lbIndex.getStyleClass().add("cy-added-case-index");

        // вњ… Р°РІС‚Рѕ-С€РёСЂРёРЅР° РїРѕРґ 1/2/3+ С†РёС„СЂ: РєРѕР»РѕРЅРєР° СЂР°СЃС€РёСЂСЏРµС‚СЃСЏ РїРѕ СЃРѕРґРµСЂР¶РёРјРѕРјСѓ
        lbIndex.setMinWidth(Region.USE_PREF_SIZE);
        lbIndex.setPrefWidth(Region.USE_COMPUTED_SIZE);
        lbIndex.setMaxWidth(Region.USE_PREF_SIZE);

        // вњ… РІРёР·СѓР°Р»СЊРЅРѕ: СЃРґРІРёРі РІРїСЂР°РІРѕ Рё С†РµРЅС‚СЂРѕРІРєР° РїРѕ РІС‹СЃРѕС‚Рµ СЃС‚СЂРѕРєРё
        lbIndex.setAlignment(Pos.CENTER_RIGHT);
        lbIndex.setPadding(new Insets(0, 0, 0, 6));

        // title box (title + icons INSIDE)
        HBox titleBox = new HBox(8);
        titleBox.getStyleClass().add("cy-added-case-titlebox");
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.setMaxWidth(Double.MAX_VALUE);
        titleBox.setMinWidth(0.0);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        // вњ… РѕРґРёРЅР°РєРѕРІС‹Р№ РѕС‚СЃС‚СѓРї РјРµР¶РґСѓ РєРѕР»РѕРЅРєР°РјРё: "РЅРѕРјРµСЂ -> titleBox" Р·Р°РґР°С‘С‚ row spacing=0,
        //    Р° "titleBox -> combo" РґРµР»Р°РµРј margin'РѕРј
        HBox.setMargin(titleBox, new Insets(0, 10, 0, 8));

        String title = (ref == null) ? "" : safe(ref.safeTitleSnapshot());

        Label lbTitle = new Label(title);
        lbTitle.getStyleClass().add("cy-added-case-title");

        // вњ… С„РёРєСЃ Р±Р°РіР°: РґР»РёРЅРЅРѕРµ РЅР°Р·РІР°РЅРёРµ РѕР±СЂРµР·Р°РµРј С‚СЂРѕРµС‚РѕС‡РёРµРј Рё РЅРµ РІС‹Р»РµР·Р°РµРј Р·Р° СЌРєСЂР°РЅ
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
        // Р»РѕРіРёРєРё РїРѕРєР° РЅРµС‚ вЂ” РєРЅРѕРїРєР° РїСЂРѕСЃС‚Рѕ UI

        // вњ… NEW: row trash button (hidden until deleteMode)
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

        row.setOnMouseClicked(e -> {
            if (deleteMode) return;
            if (onOpenCase == null) return;
            onOpenCase.accept(safeRef);
        });

        // вњ… order matters: kebab first, trash appears to the right in same cell
        titleBox.getChildren().addAll(lbTitle, btnKebab, btnTrash);

        ComboBox<String> cb = new ComboBox<>();
        cb.getStyleClass().addAll("cy-added-case-combo", "app-combo");
        UiComboBox.install(cb, 0.92);

        cb.setFocusTraversable(false);

        // вњ… 1) С€РёСЂРёРЅР° x1.5
        cb.setPrefWidth(COMBO_WIDTH_PX);
        cb.setMaxWidth(COMBO_WIDTH_PX);

        // вњ… РѕРґРёРЅР°РєРѕРІС‹Р№ РѕС‚СЃС‚СѓРї РјРµР¶РґСѓ РєРѕР»РѕРЅРєР°РјРё: "combo -> comment btn"
        HBox.setMargin(cb, new Insets(0, 10, 0, 0));

        // вњ… 2-3) СЃРїРёСЃРѕРє + С†РІРµС‚Р° РёР· private-config.json
        Map<String, String> colorsByText = loadCaseComboColors();

        // i18n texts
        final String hintStatus = safe(I18n.t("cy.case.status.placeholder"));
        final String clearLabel = safe(I18n.t("cy.combo.clearSelection"));

        // Р±Р°Р·РѕРІС‹Рµ РїСѓРЅРєС‚С‹ (С‚РѕР»СЊРєРѕ СЃС‚Р°С‚СѓСЃС‹ РёР· РєРѕРЅС„РёРіР°)
        final List<String> baseItems = new ArrayList<>(colorsByText.keySet());

        // promptText РѕСЃС‚Р°РІРёРј, РЅРѕ СЂРµР°Р»СЊРЅС‹Р№ РїР»РµР№СЃС…РѕР»РґРµСЂ СЂРёСЃСѓРµРј С‡РµСЂРµР· buttonCell
        cb.setPromptText(hintStatus);

        // dropdown cells: "РћС‡РёСЃС‚РёС‚СЊ РІС‹Р±РѕСЂ" РѕС‚РѕР±СЂР°Р¶Р°РµРј РїРѕ sentinel
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

        // button cell: РїР»РµР№СЃС…РѕР»РґРµСЂ РєРѕРіРґР° value == null
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

                // РµСЃР»Рё РІРґСЂСѓРі РїРѕРїР°Р»Рё РЅР° sentinel (С‚РµРѕСЂРµС‚РёС‡РµСЃРєРё РЅРµ РґРѕР»Р¶РЅРѕ РѕСЃС‚Р°РІР°С‚СЊСЃСЏ РєР°Рє value)
                if (CLEAR_SENTINEL.equals(item)) {
                    setText(hintStatus);
                    setOpacity(HINT_OPACITY);
                    return;
                }

                setText(item);
                setOpacity(1.0);
            }
        });

        // РѕР±РЅРѕРІР»РµРЅРёРµ items Р±РµР· РґСѓР±Р»РµР№:
        // - РµСЃР»Рё РµСЃС‚СЊ РІС‹Р±СЂР°РЅРЅС‹Р№ СЃС‚Р°С‚СѓСЃ -> sentinel РїРµСЂРІС‹Рј (РІСЃРµРіРґР° РІРёРґРµРЅ, Р±РµР· СЃРєСЂРѕР»Р»Р°)
        // - РµСЃР»Рё РІС‹Р±РѕСЂР° РЅРµС‚ -> С‚РѕР»СЊРєРѕ СЃС‚Р°С‚СѓСЃС‹
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

        // РЅР°С‡Р°Р»СЊРЅРѕРµ СЃРѕСЃС‚РѕСЏРЅРёРµ: РїСѓСЃС‚Рѕ -> Р±РµР· sentinel
        syncItemsWithSelection.run();

        // РїРµСЂРµРґ РѕС‚РєСЂС‹С‚РёРµРј popup РіР°СЂР°РЅС‚РёСЂСѓРµРј Р°РєС‚СѓР°Р»СЊРЅС‹Р№ СЃРїРёСЃРѕРє
        cb.setOnShowing(e -> syncItemsWithSelection.run());

        // вњ… 4) РїСЂРё РІС‹Р±РѕСЂРµ вЂ” РєСЂР°СЃРёРј РєРѕРјР±РѕР±РѕРєСЃ + РїРѕРґРґРµСЂР¶РєР° "РћС‡РёСЃС‚РёС‚СЊ РІС‹Р±РѕСЂ"
        cb.valueProperty().addListener((obs, oldV, newV) -> {
            // "РћС‡РёСЃС‚РёС‚СЊ РІС‹Р±РѕСЂ" -> СЃР±СЂР°СЃС‹РІР°РµРј РІ null
            if (CLEAR_SENTINEL.equals(newV)) {
                javafx.application.Platform.runLater(() -> {
                    cb.getSelectionModel().clearSelection();
                    cb.setValue(null);
                    cb.setStyle("");
                    syncItemsWithSelection.run();
                });
                return;
            }

            // РїСѓСЃС‚Рѕ/СЃР±СЂРѕСЃ вЂ” СѓР±РёСЂР°РµРј СЃС‚РёР»СЊ
            if (newV == null || newV.isBlank()) {
                cb.setStyle("");
                syncItemsWithSelection.run();
                return;
            }

            // РѕР±С‹С‡РЅС‹Р№ РІС‹Р±СЂР°РЅРЅС‹Р№ СЃС‚Р°С‚СѓСЃ
            String cssColor = colorsByText.get(newV);
            applyComboColor(cb, cssColor);

            // РїРѕСЃР»Рµ РІС‹Р±РѕСЂР° РґРѕР±Р°РІРёРј sentinel РїРµСЂРІС‹Рј СЌР»РµРјРµРЅС‚РѕРј
            syncItemsWithSelection.run();
        });

        // ===================== COMMENT BUTTON + MODAL (LAZY) =====================

        final String rowKey = buildRowKey(index, title);

        // вњ… icon-only button (centered), tooltip text
        Button btnComment = new Button();
        btnComment.getStyleClass().addAll("icon-btn", "xs", "cy-added-case-comment");
        btnComment.setFocusTraversable(false);
        btnComment.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        btnComment.setAlignment(Pos.CENTER);

        UiSvg.setButtonSvg(btnComment, ICON_COMMENT, 12);

        Tooltip.install(btnComment, new Tooltip(I18n.t("cy.case.comment.btn")));

        // вњ… lazy modal creation to avoid lag on card open
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

        // fallback: СЃРѕС…СЂР°РЅСЏРµРј РїСЂРµР¶РЅРµРµ РїРѕРІРµРґРµРЅРёРµ (С‡С‚РѕР±С‹ РЅРµ "СЃР»РѕРјР°С‚СЊ С„СѓРЅРєС†РёРѕРЅР°Р»")
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

        // Р¤РѕРЅ Р·Р°РґР°С‘Рј inline, С‡С‚РѕР±С‹ РѕС‚СЂР°Р±РѕС‚Р°Р»Рѕ РїРѕРІРµСЂС… СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёС… СЃС‚РёР»РµР№.
        // Р‘РѕСЂРґРµСЂ/РѕСЃС‚Р°Р»СЊРЅС‹Рµ СЃС‚РёР»Рё РѕСЃС‚Р°СЋС‚СЃСЏ РёР· CSS РєР»Р°СЃСЃРѕРІ.
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





