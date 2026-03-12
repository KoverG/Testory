package app.domain.cycles.ui.right;

import app.core.I18n;
import app.domain.cycles.ui.CyclesViewRefs;
import app.domain.cycles.usecase.CycleCaseRef;
import app.ui.UiSvg;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class AddedCasesListUi {

    private static final String ICON_KEBAB = "kebab.svg";
    private static final String ICON_COMMENT = "comment.svg";
    private static final String ICON_CASE_DELETE = "trash.svg";
    private static final String ROW_DELETE_BTN_KEY = "cy.added.case.delete.btn";

    private final CyclesViewRefs v;

    private boolean deleteMode = false;
    private Consumer<CycleCaseRef> onDeleteCase;
    private Consumer<CycleCaseRef> onOpenCase;
    private BiConsumer<CycleCaseRef, String> onStatusChanged;
    private BiConsumer<CycleCaseRef, String> onCommentChanged;

    public AddedCasesListUi(CyclesViewRefs v) {
        this.v = v;
    }

    public void init() {
        if (v.vbAddedCases != null) {
            v.vbAddedCases.setSpacing(8.0);
        }
    }

    public void setOnDeleteCase(Consumer<CycleCaseRef> onDeleteCase) {
        this.onDeleteCase = onDeleteCase;
    }

    public void setOnOpenCase(Consumer<CycleCaseRef> onOpenCase) {
        this.onOpenCase = onOpenCase;
    }

    public void setOnStatusChanged(BiConsumer<CycleCaseRef, String> onStatusChanged) {
        this.onStatusChanged = onStatusChanged;
    }

    public void setOnCommentChanged(BiConsumer<CycleCaseRef, String> onCommentChanged) {
        this.onCommentChanged = onCommentChanged;
    }

    public void setDeleteMode(boolean deleteMode) {
        this.deleteMode = deleteMode;
        if (v.vbAddedCases == null) return;

        for (javafx.scene.Node node : v.vbAddedCases.getChildren()) {
            if (!(node instanceof HBox row)) continue;
            Object btn = row.getProperties().get(ROW_DELETE_BTN_KEY);
            if (btn instanceof Button b) {
                b.setVisible(deleteMode);
                b.setManaged(deleteMode);
            }
        }
    }

    public void showCases(List<CycleCaseRef> cases) {
        if (v.vbAddedCases == null) return;

        v.vbAddedCases.getChildren().clear();
        if (cases == null || cases.isEmpty()) return;

        int i = 1;
        for (CycleCaseRef ref : cases) {
            if (ref == null) continue;
            addRow(i++, ref);
        }
    }

    private void addRow(int index, CycleCaseRef ref) {
        if (v.vbAddedCases == null) return;

        HBox row = new HBox(10.0);
        row.getStyleClass().add("cy-added-case-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label lbIndex = new Label(String.valueOf(index));
        lbIndex.getStyleClass().add("cy-added-case-index");
        lbIndex.setMinWidth(26.0);
        lbIndex.setPrefWidth(26.0);
        lbIndex.setMaxWidth(26.0);

        HBox titleBox = new HBox(8.0);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        titleBox.getStyleClass().add("cy-added-case-titlebox");

        String title = ref.safeTitleSnapshot();
        Label lbTitle = new Label(title);
        lbTitle.getStyleClass().add("cy-added-case-title");
        lbTitle.setMaxWidth(Double.MAX_VALUE);
        lbTitle.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox.setHgrow(lbTitle, Priority.ALWAYS);

        Button btnKebab = new Button();
        btnKebab.getStyleClass().addAll("icon-btn", "xs", "cy-added-case-kebab");
        btnKebab.setFocusTraversable(false);
        btnKebab.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        UiSvg.setButtonSvg(btnKebab, ICON_KEBAB, 12);
        Tooltip.install(btnKebab, new Tooltip(title));


        Button btnTrash = new Button();
        btnTrash.getStyleClass().addAll("icon-btn", "xs", "cy-added-case-delete");
        btnTrash.setFocusTraversable(false);
        btnTrash.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        UiSvg.setButtonSvg(btnTrash, ICON_CASE_DELETE, 12);
        Tooltip.install(btnTrash, new Tooltip(I18n.t("tc.trash.delete")));
        btnTrash.setVisible(deleteMode);
        btnTrash.setManaged(deleteMode);
        row.getProperties().put(ROW_DELETE_BTN_KEY, btnTrash);

        final CycleCaseRef safeRef = new CycleCaseRef(
                ref.safeId(),
                ref.safeTitleSnapshot(),
                ref.safeStatus(),
                ref.safeComment()
        );

        btnTrash.setOnAction(e -> {
            if (onDeleteCase != null) onDeleteCase.accept(safeRef);
        });

        titleBox.getStyleClass().add("cy-added-case-titlebox-clickable");
        titleBox.setOnMouseClicked(e -> {
            if (deleteMode) return;
            if (onOpenCase != null) onOpenCase.accept(safeRef);
        });

        titleBox.getChildren().addAll(lbTitle, btnKebab, btnTrash);

        ComboBox<String> cb = CaseStatusComboSupport.createCombo("cy-added-case-combo");
        HBox.setMargin(cb, new Insets(0, 10, 0, 0));
        CaseStatusComboSupport.install(cb, status -> {
            if (onStatusChanged != null) onStatusChanged.accept(safeRef, status);
        });
        CaseStatusComboSupport.setStatus(cb, safeRef.safeStatus());

        Button btnComment = new Button();
        btnComment.getStyleClass().addAll("icon-btn", "xs", "cy-added-case-comment");
        btnComment.setFocusTraversable(false);
        btnComment.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        btnComment.setAlignment(Pos.CENTER);
        UiSvg.setButtonSvg(btnComment, ICON_COMMENT, 12);
        Tooltip.install(btnComment, new Tooltip(I18n.t("cy.case.comment.btn")));

        btnComment.setOnAction(e -> {
            if (v.rightRoot == null) return;

            CaseCommentModal modal = (CaseCommentModal) btnComment.getProperties().get("cy.case.comment.modal");
            if (modal == null) {
                modal = new CaseCommentModal(btnComment);
                modal.install(v.rightRoot);
                btnComment.getProperties().put("cy.case.comment.modal", modal);
            }

            modal.setCurrentValueSupplier(safeRef::safeComment);
            modal.setEditableSupplier(() -> true);
            modal.setOnSaved(val -> {
                if (onCommentChanged != null) onCommentChanged.accept(safeRef, safe(val));
            });
            modal.toggle();
        });

        row.getChildren().addAll(lbIndex, titleBox, cb, btnComment);
        v.vbAddedCases.getChildren().add(row);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}

