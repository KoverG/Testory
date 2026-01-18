// FILE: src/main/java/app/domain/testcases/ui/StepsEditor.java
package app.domain.testcases.ui;

import app.core.I18n;
import app.domain.testcases.usecase.TestCaseDraft;
import app.ui.UiSvg;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class StepsEditor {

    public static final class StepRow {
        public final HBox root;
        public final Label indexLabel;

        public final TextArea taStep;
        public final TextArea taData;
        public final TextArea taExpected;

        StepRow(HBox root, Label indexLabel, TextArea taStep, TextArea taData, TextArea taExpected) {
            this.root = root;
            this.indexLabel = indexLabel;
            this.taStep = taStep;
            this.taData = taData;
            this.taExpected = taExpected;
        }
    }

    private final VBox stepsBox;
    private final String iconGrip;
    private final String iconClose;

    private final List<StepRow> draftSteps = new ArrayList<>();

    // ✅ NEW: change notifier (для dirty-gate в контроллере)
    private Runnable onChanged;
    private boolean suppressChanged = false;

    public void setOnChanged(Runnable r) { this.onChanged = r; }

    private void fireChanged() {
        if (suppressChanged) return;
        if (onChanged != null) onChanged.run();
    }

    public StepsEditor(VBox stepsBox, String iconGrip, String iconClose) {
        this.stepsBox = stepsBox;
        this.iconGrip = iconGrip;
        this.iconClose = iconClose;
    }

    // === snapshot draft for save ===
    public List<TestCaseDraft.StepDraft> snapshotDraft() {
        List<TestCaseDraft.StepDraft> out = new ArrayList<>();
        if (stepsBox == null) return out;

        for (Node n : stepsBox.getChildren()) {
            StepRow row = extractStepRow(n);
            if (row == null) continue;

            String step = text(row.taStep);
            String data = text(row.taData);
            String expected = text(row.taExpected);

            if (step.isBlank() && data.isBlank() && expected.isBlank()) continue;

            out.add(new TestCaseDraft.StepDraft(step, data, expected));
        }

        return out;
    }

    private String text(TextArea ta) {
        if (ta == null) return "";
        String v = ta.getText();
        return v == null ? "" : v.trim();
    }

    private StepRow extractStepRow(Node n) {
        if (n == null) return null;

        // надёжно: находим по ссылке на root, потому что ты хранишь StepRow в draftSteps
        for (StepRow r : draftSteps) {
            if (r != null && r.root == n) return r;
        }

        // если вдруг ноды меняются (обёртки) — попробуем подняться на 1 уровень
        Node p = n.getParent();
        if (p != null) {
            for (StepRow r : draftSteps) {
                if (r != null && r.root == p) return r;
            }
        }

        return null;
    }

    // === existing API (оставь твой реальный код ниже, я не переписываю логику) ===

    public void reset() {
        draftSteps.clear();
        if (stepsBox != null) stepsBox.getChildren().clear();
        fireChanged();
    }

    public void setSteps(List<TestCaseDraft.StepDraft> steps) {
        suppressChanged = true;
        try {
            reset();

            if (steps == null || steps.isEmpty()) {
                addDraftStep("");
                return;
            }

            for (TestCaseDraft.StepDraft st : steps) {
                addDraftStep("");
                StepRow last = draftSteps.isEmpty() ? null : draftSteps.get(draftSteps.size() - 1);
                if (last == null) continue;

                String step = (st == null || st.step == null) ? "" : st.step;
                String data = (st == null || st.data == null) ? "" : st.data;
                String exp  = (st == null || st.expected == null) ? "" : st.expected;

                last.taStep.setText(step);
                last.taData.setText(data);
                last.taExpected.setText(exp);
            }

            renumberDraftSteps();
        } finally {
            suppressChanged = false;
        }

        // setSteps сам по себе не должен делать dirty, поэтому fireChanged() тут НЕ вызываем
    }

    public void addDraftStep(String initialText) {
        if (stepsBox == null) return;

        HBox row = new HBox(2);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("tc-step-row");

        HBox card = new HBox(10);
        card.setAlignment(Pos.TOP_LEFT);
        card.getStyleClass().add("tc-step-card");
        HBox.setHgrow(card, Priority.ALWAYS);

        Label idx = new Label("1");
        idx.getStyleClass().add("tc-step-index");

        Button drag = new Button();
        drag.setFocusTraversable(false);
        drag.getStyleClass().addAll("icon-btn", "xs", "tc-step-drag");
        UiSvg.setButtonSvg(drag, iconGrip, 12);
        drag.setText("");

        VBox left = new VBox(6, idx, drag);
        left.getStyleClass().add("tc-step-left");
        left.setMinWidth(34);
        left.setPrefWidth(34);
        left.setMaxWidth(34);
        left.setAlignment(Pos.TOP_CENTER);
        VBox.setMargin(drag, new Insets(2, 0, 0, 0));

        Label tStep = new Label(I18n.t("tc.step.zone.step"));
        tStep.getStyleClass().add("tc-step-zone-title");

        TextArea taStep = new TextArea();
        taStep.getStyleClass().add("tc-step-area");
        taStep.setWrapText(true);
        taStep.setPromptText(I18n.t("tc.step.step.placeholder"));
        taStep.setText(initialText == null ? "" : initialText);
        taStep.setPrefRowCount(1);
        taStep.setPrefHeight(34);
        taStep.setMinHeight(Region.USE_PREF_SIZE);
        taStep.setMaxHeight(Double.MAX_VALUE);
        installAutoGrowStepTextArea(taStep, 34.0);

        VBox zoneStep = new VBox(2, tStep, taStep);
        zoneStep.getStyleClass().add("tc-step-zone");
        VBox.setVgrow(taStep, Priority.NEVER);

        Label tData = new Label(I18n.t("tc.step.zone.data"));
        tData.getStyleClass().add("tc-step-zone-title");

        TextArea taData = new TextArea();
        taData.getStyleClass().add("tc-step-area");
        taData.setWrapText(true);
        taData.setPromptText(I18n.t("tc.step.data.placeholder"));
        taData.setText("");
        taData.setPrefRowCount(1);
        taData.setPrefHeight(44);
        taData.setMinHeight(Region.USE_PREF_SIZE);
        taData.setMaxHeight(Double.MAX_VALUE);
        installAutoGrowStepTextArea(taData, 44.0);

        VBox zoneData = new VBox(2, tData, taData);
        zoneData.getStyleClass().add("tc-step-zone");

        Label tExp = new Label(I18n.t("tc.step.zone.expected"));
        tExp.getStyleClass().add("tc-step-zone-title");

        TextArea taExpected = new TextArea();
        taExpected.getStyleClass().add("tc-step-area");
        taExpected.setWrapText(true);
        taExpected.setPromptText(I18n.t("tc.step.expected.placeholder"));
        taExpected.setText("");
        taExpected.setPrefRowCount(1);
        taExpected.setPrefHeight(44);
        taExpected.setMinHeight(Region.USE_PREF_SIZE);
        taExpected.setMaxHeight(Double.MAX_VALUE);
        installAutoGrowStepTextArea(taExpected, 44.0);

        VBox zoneExpected = new VBox(2, tExp, taExpected);
        zoneExpected.getStyleClass().add("tc-step-zone");

        Region vSep1 = new Region();
        vSep1.getStyleClass().add("tc-step-zone-vsep");

        Region vSep2 = new Region();
        vSep2.getStyleClass().add("tc-step-zone-vsep");

        HBox zonesRow = new HBox();
        zonesRow.setSpacing(0);
        zonesRow.getChildren().addAll(zoneStep, vSep1, zoneData, vSep2, zoneExpected);
        zonesRow.getStyleClass().add("tc-step-zones-row");
        zonesRow.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(zonesRow, Priority.ALWAYS);

        HBox.setHgrow(zoneStep, Priority.ALWAYS);
        HBox.setHgrow(zoneData, Priority.ALWAYS);
        HBox.setHgrow(zoneExpected, Priority.ALWAYS);

        zoneStep.setMaxWidth(Double.MAX_VALUE);
        zoneData.setMaxWidth(Double.MAX_VALUE);
        zoneExpected.setMaxWidth(Double.MAX_VALUE);

        Button del = new Button();
        del.setFocusTraversable(false);
        del.getStyleClass().addAll("icon-btn", "sm", "tc-step-del");
        UiSvg.setButtonSvg(del, iconClose, 12);
        del.setText("");

        StepRow stepRow = new StepRow(row, idx, taStep, taData, taExpected);

        // ✅ NEW: любые правки в текстовых областях шагов -> changed
        taStep.textProperty().addListener((o, ov, nv) -> fireChanged());
        taData.textProperty().addListener((o, ov, nv) -> fireChanged());
        taExpected.textProperty().addListener((o, ov, nv) -> fireChanged());

        del.setOnAction(e -> {
            stepsBox.getChildren().remove(stepRow.root);
            draftSteps.remove(stepRow);
            if (draftSteps.isEmpty()) {
                addDraftStep("");
            } else {
                renumberDraftSteps();
            }
            fireChanged();
        });

        installStepDragAndDrop(stepRow, drag);

        card.getChildren().addAll(zonesRow, del);
        row.getChildren().addAll(left, card);

        stepsBox.getChildren().add(row);
        draftSteps.add(stepRow);

        renumberDraftSteps();

        fireChanged();
    }

    private void installStepDragAndDrop(StepRow stepRow, Button handle) {
        if (stepRow == null || stepRow.root == null || handle == null) return;

        handle.setOnDragDetected(ev -> {
            int from = draftSteps.indexOf(stepRow);
            if (from < 0) return;

            Dragboard db = handle.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.putString(String.valueOf(from));
            db.setContent(cc);

            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            db.setDragView(stepRow.root.snapshot(sp, null));

            ev.consume();
        });

        java.util.function.Consumer<javafx.scene.input.DragEvent> onOver = ev -> {
            if (ev.getDragboard() == null || !ev.getDragboard().hasString()) return;
            ev.acceptTransferModes(TransferMode.MOVE);
            ev.consume();
        };

        java.util.function.Consumer<javafx.scene.input.DragEvent> onDropped = ev -> {
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

            int to = draftSteps.indexOf(stepRow);
            if (from < 0 || to < 0 || from == to) {
                ev.setDropCompleted(false);
                ev.consume();
                return;
            }

            StepRow moving = draftSteps.remove(from);
            draftSteps.add(to, moving);

            if (stepsBox != null) {
                stepsBox.getChildren().clear();
                for (StepRow r : draftSteps) stepsBox.getChildren().add(r.root);
            }

            renumberDraftSteps();

            ev.setDropCompleted(true);
            ev.consume();

            fireChanged();
        };

        attachDnD(stepRow.root, onOver, onDropped);
        attachDnD(stepRow.taStep, onOver, onDropped);
        attachDnD(stepRow.taData, onOver, onDropped);
        attachDnD(stepRow.taExpected, onOver, onDropped);

        stepRow.root.setOnDragDone(ev -> ev.consume());
    }

    private void attachDnD(Node n,
                           java.util.function.Consumer<javafx.scene.input.DragEvent> onOver,
                           java.util.function.Consumer<javafx.scene.input.DragEvent> onDropped) {
        if (n == null) return;

        n.setOnDragOver(onOver::accept);
        n.setOnDragDropped(onDropped::accept);

        if (n instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                attachDnD(c, onOver, onDropped);
            }
        }
    }

    private void renumberDraftSteps() {
        for (int i = 0; i < draftSteps.size(); i++) {
            StepRow r = draftSteps.get(i);
            if (r != null && r.indexLabel != null) r.indexLabel.setText(String.valueOf(i + 1));
        }
    }

    private void installAutoGrowStepTextArea(TextArea ta, double basePrefHeight) {
        if (ta == null) return;

        ta.getProperties().put("autoGrowBasePrefHeight", basePrefHeight);

        Text measurer = new Text();
        measurer.setFont(ta.getFont());

        Runnable recalc = () -> {
            Object v = ta.getProperties().get("autoGrowBasePrefHeight");
            double basePref = (v instanceof Number) ? ((Number) v).doubleValue() : basePrefHeight;

            String txt = ta.getText();
            if (txt == null || txt.isEmpty()) txt = " ";

            measurer.setFont(ta.getFont());
            measurer.setText(txt);

            double w = ta.getWidth();
            double wrapW = Math.max(0.0, w - 18.0);
            measurer.setWrappingWidth(wrapW);

            double textH = measurer.getLayoutBounds().getHeight();
            double extra = 14.0;

            double target = Math.max(basePref, textH + extra);

            ta.setPrefHeight(target);
        };

        ta.textProperty().addListener((obs, ov, nv) -> recalc.run());
        ta.widthProperty().addListener((obs, ov, nv) -> recalc.run());
        ta.fontProperty().addListener((obs, ov, nv) -> recalc.run());

        Platform.runLater(recalc);
    }
}
