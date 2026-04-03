package app.domain.cycles.ui.right;

import app.core.I18n;
import app.domain.cycles.ui.CyclesViewRefs;
import app.domain.cycles.usecase.CycleCaseRef;
import app.ui.UiSvg;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.input.ClipboardContent;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class AddedCasesListUi {

    private static final String ICON_KEBAB = "kebab.svg";
    private static final String ICON_COMMENT = "comment.svg";
    private static final String ICON_CASE_DELETE = "trash.svg";
    private static final String ROW_DELETE_BTN_KEY = "cy.added.case.delete.btn";
    private static final String ROW_STATUS_COMBO_KEY = "cy.added.case.status.combo";
    private static final String ROW_COMMENT_BTN_KEY = "cy.added.case.comment.btn";
    private static final String DROP_BEFORE_CLASS = "cy-added-case-drop-before";
    private static final String DROP_AFTER_CLASS = "cy-added-case-drop-after";
    private static final double INDEX_COLUMN_WIDTH = 36.0;
    private static final double INDEX_TO_TITLE_GAP = 12.0;
    private static final double TITLE_TO_STATUS_GAP = 12.0;

    private final CyclesViewRefs v;

    private boolean deleteMode = false;
    private boolean caseEditAllowed = false;
    private boolean reorderEnabled = false;
    private Consumer<CycleCaseRef> onDeleteCase;
    private Consumer<CycleCaseRef> onOpenCase;
    private BiConsumer<CycleCaseRef, String> onStatusChanged;
    private BiConsumer<CycleCaseRef, String> onCommentChanged;
    private BiConsumer<Integer, Integer> onReorderCase;

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

    public void setOnReorderCase(BiConsumer<Integer, Integer> onReorderCase) {
        this.onReorderCase = onReorderCase;
    }

    public void setReorderEnabled(boolean reorderEnabled) {
        this.reorderEnabled = reorderEnabled;
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

    public void setCaseEditAllowed(boolean caseEditAllowed) {
        this.caseEditAllowed = caseEditAllowed;
        if (v.vbAddedCases == null) return;

        for (javafx.scene.Node node : v.vbAddedCases.getChildren()) {
            if (!(node instanceof HBox row)) continue;

            Object combo = row.getProperties().get(ROW_STATUS_COMBO_KEY);
            if (combo instanceof ComboBox<?> cb) {
                cb.setDisable(!caseEditAllowed);
            }

            Object commentButton = row.getProperties().get(ROW_COMMENT_BTN_KEY);
            if (commentButton instanceof Button b) {
                b.setDisable(false);
            }
        }
    }

    public void showCases(List<CycleCaseRef> cases) {
        if (v.vbAddedCases == null) return;
        clearDropPreview();

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

        HBox row = new HBox(0.0);
        row.getStyleClass().add("cy-added-case-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        Label lbIndex = new Label(String.valueOf(index));
        lbIndex.getStyleClass().add("cy-added-case-index");
        if (index >= 1000) {
            lbIndex.getStyleClass().add("cy-added-case-index-compact");
        }
        lbIndex.setMinWidth(INDEX_COLUMN_WIDTH);
        lbIndex.setPrefWidth(INDEX_COLUMN_WIDTH);
        lbIndex.setMaxWidth(INDEX_COLUMN_WIDTH);

        Region indexGap = new Region();
        indexGap.setMinWidth(INDEX_TO_TITLE_GAP);
        indexGap.setPrefWidth(INDEX_TO_TITLE_GAP);
        indexGap.setMaxWidth(INDEX_TO_TITLE_GAP);

        HBox titleBox = new HBox(8.0);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.setMinWidth(0.0);
        titleBox.setPrefWidth(0.0);
        titleBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        titleBox.getStyleClass().add("cy-added-case-titlebox");

        String title = ref.safeTitleSnapshot();
        Label lbTitle = new Label(title);
        lbTitle.getStyleClass().add("cy-added-case-title");
        lbTitle.setMinWidth(0.0);
        lbTitle.setPrefWidth(0.0);
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
        HBox.setMargin(cb, new Insets(0, 10, 0, TITLE_TO_STATUS_GAP));
        CaseStatusComboSupport.install(cb, status -> {
            if (onStatusChanged != null) onStatusChanged.accept(safeRef, status);
        });
        CaseStatusComboSupport.setStatus(cb, safeRef.safeStatus());
        cb.setDisable(!caseEditAllowed);
        row.getProperties().put(ROW_STATUS_COMBO_KEY, cb);

        Button btnComment = new Button();
        btnComment.getStyleClass().addAll("icon-btn", "xs", "cy-added-case-comment");
        btnComment.setFocusTraversable(false);
        btnComment.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        btnComment.setAlignment(Pos.CENTER);
        UiSvg.setButtonSvg(btnComment, ICON_COMMENT, 12);
        Tooltip.install(btnComment, new Tooltip(I18n.t("cy.case.comment.btn")));
        btnComment.setDisable(false);
        row.getProperties().put(ROW_COMMENT_BTN_KEY, btnComment);

        btnComment.setOnAction(e -> {
            if (v.rightRoot == null) return;

            CaseCommentModal modal = (CaseCommentModal) btnComment.getProperties().get("cy.case.comment.modal");
            if (modal == null) {
                modal = new CaseCommentModal(btnComment);
                modal.install(v.rightRoot);
                btnComment.getProperties().put("cy.case.comment.modal", modal);
            }

            modal.setCurrentValueSupplier(safeRef::safeComment);
            modal.setEditableSupplier(() -> caseEditAllowed);
            modal.setOnSaved(val -> {
                if (onCommentChanged != null) onCommentChanged.accept(safeRef, safe(val));
            });
            modal.toggle();
        });

        installRowDragAndDrop(row, titleBox, lbTitle, cb, btnComment, btnTrash, btnKebab);

        row.getChildren().addAll(lbIndex, indexGap, titleBox, cb, btnComment);
        v.vbAddedCases.getChildren().add(row);
    }


    private void installRowDragAndDrop(
            HBox row,
            HBox titleBox,
            Label titleLabel,
            ComboBox<String> statusCombo,
            Button commentButton,
            Button deleteButton,
            Button handle
    ) {
        if (row == null || handle == null) return;

        handle.setOnDragDetected(ev -> {
            if (!canReorderRows()) return;

            int from = rowIndex(row);
            if (from < 0) return;

            Dragboard db = handle.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.putString(String.valueOf(from));
            db.setContent(cc);

            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            Point2D dragPoint = row.sceneToLocal(ev.getSceneX(), ev.getSceneY());
            db.setDragView(row.snapshot(sp, null), dragPoint.getX(), dragPoint.getY());

            ev.consume();
        });

        java.util.function.Consumer<javafx.scene.input.DragEvent> onOver = ev -> {
            if (!canReorderRows()) return;
            if (ev.getDragboard() == null || !ev.getDragboard().hasString()) return;
            int from = dragIndex(ev.getDragboard());
            int to = rowIndex(row);
            applyDropPreview(from, to);
            ev.acceptTransferModes(TransferMode.MOVE);
            ev.consume();
        };

        java.util.function.Consumer<javafx.scene.input.DragEvent> onDropped = ev -> {
            if (!canReorderRows()) {
                ev.setDropCompleted(false);
                ev.consume();
                return;
            }
            if (ev.getDragboard() == null || !ev.getDragboard().hasString()) {
                ev.setDropCompleted(false);
                ev.consume();
                return;
            }

            int from;
            try {
                from = Integer.parseInt(ev.getDragboard().getString());
            } catch (Exception ex) {
                ev.setDropCompleted(false);
                ev.consume();
                return;
            }

            int to = rowIndex(row);
            if (from < 0 || to < 0 || from == to) {
                clearDropPreview();
                ev.setDropCompleted(false);
                ev.consume();
                return;
            }

            if (onReorderCase != null) onReorderCase.accept(from, to);
            clearDropPreview();
            ev.setDropCompleted(true);
            ev.consume();
        };

        attachDnD(row, onOver, onDropped);
        attachDnD(titleBox, onOver, onDropped);
        attachDnD(titleLabel, onOver, onDropped);
        attachDnD(statusCombo, onOver, onDropped);
        attachDnD(commentButton, onOver, onDropped);
        attachDnD(deleteButton, onOver, onDropped);

        row.setOnDragDone(ev -> { clearDropPreview(); ev.consume(); });
    }

    private boolean canReorderRows() {
        return reorderEnabled && !deleteMode && v.vbAddedCases != null && v.vbAddedCases.getChildren().size() > 1;
    }

    private int rowIndex(HBox row) {
        return v.vbAddedCases == null ? -1 : v.vbAddedCases.getChildren().indexOf(row);
    }

    private int dragIndex(Dragboard dragboard) {
        if (dragboard == null || !dragboard.hasString()) return -1;
        try {
            return Integer.parseInt(dragboard.getString());
        } catch (Exception ex) {
            return -1;
        }
    }

    private void applyDropPreview(int from, int to) {
        clearDropPreview();
        if (v.vbAddedCases == null) return;
        if (from < 0 || to < 0 || from == to) return;

        int previewIndex = to;
        boolean before = to < from;
        setRowPreview(previewIndex, before);
    }

    private void clearDropPreview() {
        if (v.vbAddedCases == null) return;
        for (javafx.scene.Node node : v.vbAddedCases.getChildren()) {
            if (!(node instanceof HBox row)) continue;
            row.getStyleClass().remove(DROP_BEFORE_CLASS);
            row.getStyleClass().remove(DROP_AFTER_CLASS);
        }
    }

    private void setRowPreview(int index, boolean before) {
        if (v.vbAddedCases == null) return;
        if (index < 0 || index >= v.vbAddedCases.getChildren().size()) return;

        javafx.scene.Node node = v.vbAddedCases.getChildren().get(index);
        if (!(node instanceof HBox row)) return;

        row.getStyleClass().remove(DROP_BEFORE_CLASS);
        row.getStyleClass().remove(DROP_AFTER_CLASS);
        row.getStyleClass().add(before ? DROP_BEFORE_CLASS : DROP_AFTER_CLASS);
    }

    private static void attachDnD(javafx.scene.Node node, java.util.function.Consumer<javafx.scene.input.DragEvent> onOver, java.util.function.Consumer<javafx.scene.input.DragEvent> onDropped) {
        if (node == null) return;
        node.setOnDragOver(onOver::accept);
        node.setOnDragDropped(onDropped::accept);
    }
    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
