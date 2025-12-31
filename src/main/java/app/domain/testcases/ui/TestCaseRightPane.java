package app.domain.testcases.ui;

import app.core.I18n;
import app.core.PrivateRootConfig;
import app.domain.testcases.repo.FileTestCaseRepository;
import app.domain.testcases.usecase.CreateTestCaseUseCase;
import app.domain.testcases.usecase.TestCaseDraft;
import app.ui.UiSvg;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TestCaseRightPane {

    private static final double RIGHT_ANIM_MS = 170.0;

    private static final String PRIV_EDIT_CLASS = "tc-priv-edit";
    private static final String EDIT_ACTIVE_CLASS = "tc-edit-active";

    private final VBox rightPane;
    private final VBox rightCard;
    private final StackPane rightStack;

    private final HBox rightTopRow;

    private final Button btnEdit;
    private final Button btnEditPriv;

    private final TextField tfPrivTop;
    private final TextField tfTop2;

    private final TextField tfTitle;

    private final FlowPane fpRightLabels;
    private final TextField tfRightLabel;
    private final Button btnAddRightLabel;

    private final FlowPane fpRightTags;
    private final TextField tfRightTag;
    private final Button btnAddRightTag;

    private final TextArea taRightDescription;

    private final Button btnAddStep;
    private final VBox stepsBox;

    private final Button btnCloseRight;
    private final Button btnSaveRight;

    private final SmoothScrollSupport smoothScroll;
    private final RightChipFactory chipFactory;

    private final String iconPencil;
    private final String iconClose;
    private final String iconCheck;
    private final String iconPlus;
    private final String iconGrip;

    private final FileTestCaseRepository repo = new FileTestCaseRepository();
    private final CreateTestCaseUseCase createUseCase = new CreateTestCaseUseCase(repo);

    private final StepsEditor stepsEditor;

    private boolean open = false;
    private ParallelTransition anim;

    private boolean privEditable = false;

    private boolean existingCard = false;
    private boolean editEnabled = true;

    private Path openedFile = null;

    // stable meta for current card session
    private String draftId = "";
    private String draftCreatedAt = "";

    private Runnable onSaved;

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public TestCaseRightPane(
            VBox rightPane,
            VBox rightCard,
            StackPane rightStack,

            HBox rightTopRow,
            Button btnEdit,
            Button btnEditPriv,
            TextField tfPrivTop,
            TextField tfTop2,

            TextField tfTitle,

            FlowPane fpRightLabels,
            TextField tfRightLabel,
            Button btnAddRightLabel,

            FlowPane fpRightTags,
            TextField tfRightTag,
            Button btnAddRightTag,

            TextArea taRightDescription,

            Button btnAddStep,
            VBox stepsBox,

            Button btnCloseRight,
            Button btnSaveRight,

            SmoothScrollSupport smoothScroll,
            RightChipFactory chipFactory,

            String iconPencil,
            String iconClose,
            String iconCheck,
            String iconPlus,
            String iconGrip
    ) {
        this.rightPane = rightPane;
        this.rightCard = rightCard;
        this.rightStack = rightStack;

        this.rightTopRow = rightTopRow;

        this.btnEdit = btnEdit;
        this.btnEditPriv = btnEditPriv;

        this.tfPrivTop = tfPrivTop;
        this.tfTop2 = tfTop2;

        this.tfTitle = tfTitle;

        this.fpRightLabels = fpRightLabels;
        this.tfRightLabel = tfRightLabel;
        this.btnAddRightLabel = btnAddRightLabel;

        this.fpRightTags = fpRightTags;
        this.tfRightTag = tfRightTag;
        this.btnAddRightTag = btnAddRightTag;

        this.taRightDescription = taRightDescription;

        this.btnAddStep = btnAddStep;
        this.stepsBox = stepsBox;

        this.btnCloseRight = btnCloseRight;
        this.btnSaveRight = btnSaveRight;

        this.smoothScroll = smoothScroll;
        this.chipFactory = chipFactory;

        this.iconPencil = iconPencil;
        this.iconClose = iconClose;
        this.iconCheck = iconCheck;
        this.iconPlus = iconPlus;
        this.iconGrip = iconGrip;

        this.stepsEditor = new StepsEditor(stepsBox, iconGrip, iconClose);
    }

    public void init() {
        forceCloseInstant();

        applyI18nStaticTexts();
        initTopRow();
        initLabelsUi();
        initTagsUi();
        initDescriptionUi();
        initStepsUi();
        initActions();

        applyEditModeUi();
        applyLockState();
    }

    public boolean isOpen() {
        return open;
    }

    public void openNew() {
        existingCard = false;
        editEnabled = true;

        openedFile = null;

        TestCaseDraft d = TestCaseDraft.newWithId();
        draftId = s(d.id);
        draftCreatedAt = s(d.createdAt);

        fillUiFromDraft(d);

        privEditable = false;
        normalizePrivFieldUi();

        applyEditModeUi();
        applyLockState();
        forceShowNow();
    }

    public void openExisting(Path file) {
        existingCard = true;
        editEnabled = false;

        openedFile = file;

        TestCaseDraft d;
        try {
            d = repo.readDraft(file);
        } catch (Exception e) {
            d = new TestCaseDraft();
        }

        // legacy fallback: if old file has no meta.id -> keep empty here;
        // repository will generate and migrate on first save.
        draftId = s(d.id);
        draftCreatedAt = s(d.createdAt);

        fillUiFromDraft(d);

        privEditable = false;
        normalizePrivFieldUi();

        applyEditModeUi();
        applyLockState();
        forceShowNow();
    }

    public void close() {
        if (!open) return;
        hideAnimated();
    }

    public void onToggleEdit() {
        if (!existingCard) return;

        editEnabled = !editEnabled;

        if (!editEnabled) {
            privEditable = false;
            if (tfPrivTop != null) {
                tfPrivTop.setEditable(false);
                tfPrivTop.getStyleClass().remove(PRIV_EDIT_CLASS);
                PrivateRootConfig.setRightField1(tfPrivTop.getText());
            }
        } else {
            privEditable = false;
            normalizePrivFieldUi();
        }

        applyEditModeUi();
        applyLockState();
    }

    public void onEditPriv() {
        if (tfPrivTop == null) return;

        if (existingCard && !editEnabled) return;

        boolean enable = !privEditable;
        privEditable = enable;

        if (enable) {
            tfPrivTop.setEditable(true);

            if (!tfPrivTop.getStyleClass().contains(PRIV_EDIT_CLASS)) {
                tfPrivTop.getStyleClass().add(PRIV_EDIT_CLASS);
            }

            tfPrivTop.requestFocus();
            tfPrivTop.selectAll();
        } else {
            tfPrivTop.setEditable(false);
            tfPrivTop.getStyleClass().remove(PRIV_EDIT_CLASS);

            PrivateRootConfig.setRightField1(tfPrivTop.getText());
        }

        applyLockState();
    }

    public void onAddRightLabel() {
        if (existingCard && !editEnabled) return;

        if (fpRightLabels == null || tfRightLabel == null || chipFactory == null) return;

        String raw = tfRightLabel.getText();
        String v = (raw == null) ? "" : raw.trim();
        if (v.isEmpty()) return;

        HBox chip = chipFactory.createChipEllipsis(fpRightLabels, v);
        fpRightLabels.getChildren().add(chip);

        tfRightLabel.clear();
        updateAddLabelBtnVisibility();
        tfRightLabel.requestFocus();
    }

    public void onAddRightTag() {
        if (existingCard && !editEnabled) return;

        if (fpRightTags == null || tfRightTag == null || chipFactory == null) return;

        String raw = tfRightTag.getText();
        String v = (raw == null) ? "" : raw.trim();
        if (v.isEmpty()) return;

        HBox chip = chipFactory.createChipEllipsis(fpRightTags, v);
        fpRightTags.getChildren().add(chip);

        tfRightTag.clear();
        updateAddTagBtnVisibility();
        tfRightTag.requestFocus();
    }

    public void onAddStep() {
        if (existingCard && !editEnabled) return;
        stepsEditor.addDraftStep("");
    }

    public void onSaveRight() {
        if (existingCard && !editEnabled) return;

        TestCaseDraft draft = buildDraftFromUi();

        Path saved;
        if (existingCard && openedFile != null) {
            saved = createUseCase.update(openedFile, draft);
        } else {
            saved = createUseCase.create(draft);

            // after first save new card becomes existing in this session
            existingCard = true;
            editEnabled = true; // keep editing enabled for just created card
            openedFile = saved;

            // draftId must match actual saved file meta.id
            draftId = s(draft.id);
            if (draftCreatedAt.isBlank()) draftCreatedAt = s(draft.createdAt);
        }

        System.out.println("[TestCase] saved: " + saved.toAbsolutePath());

        // if repository migrated file (legacy -> id.json), update openedFile
        openedFile = saved;

        if (onSaved != null) onSaved.run();
    }

    // ===================== INTERNAL =====================

    private void normalizePrivFieldUi() {
        if (tfPrivTop == null) return;
        tfPrivTop.setEditable(false);
        tfPrivTop.getStyleClass().remove(PRIV_EDIT_CLASS);
    }

    private void fillUiFromDraft(TestCaseDraft d) {
        if (d == null) d = new TestCaseDraft();

        if (tfPrivTop != null) {
            tfPrivTop.setEditable(false);
            tfPrivTop.getStyleClass().remove(PRIV_EDIT_CLASS);
            privEditable = false;

            String code = s(d.code);
            if (code.isEmpty()) code = s(PrivateRootConfig.rightField1());
            tfPrivTop.setText(code);
        }

        if (tfTop2 != null) tfTop2.setText(s(d.number));
        if (tfTitle != null) tfTitle.setText(s(d.title));
        if (taRightDescription != null) taRightDescription.setText(s(d.description));

        clearChipsKeepInput(fpRightLabels);
        if (fpRightLabels != null && chipFactory != null && d.labels != null) {
            for (String v : d.labels) {
                String t = s(v);
                if (t.isEmpty()) continue;
                fpRightLabels.getChildren().add(chipFactory.createChipEllipsis(fpRightLabels, t));
            }
        }

        clearChipsKeepInput(fpRightTags);
        if (fpRightTags != null && chipFactory != null && d.tags != null) {
            for (String v : d.tags) {
                String t = s(v);
                if (t.isEmpty()) continue;
                fpRightTags.getChildren().add(chipFactory.createChipEllipsis(fpRightTags, t));
            }
        }

        if (tfRightLabel != null) tfRightLabel.clear();
        if (tfRightTag != null) tfRightTag.clear();
        updateAddLabelBtnVisibility();
        updateAddTagBtnVisibility();

        stepsEditor.reset();
        if (d.steps != null && !d.steps.isEmpty()) {
            stepsEditor.setSteps(d.steps);
        } else {
            stepsEditor.addDraftStep("");
        }
    }

    private void clearChipsKeepInput(FlowPane pane) {
        if (pane == null) return;
        while (pane.getChildren().size() > 1) pane.getChildren().remove(1);
    }

    private void forceShowNow() {
        if (rightPane == null || rightCard == null) return;

        if (anim != null) {
            anim.stop();
            anim = null;
        }

        open = true;

        rightPane.setVisible(true);
        rightPane.setManaged(true);
        rightPane.setOpacity(1.0);

        rightCard.setVisible(true);
        rightCard.setManaged(true);
        rightCard.setOpacity(1.0);
        rightCard.setTranslateY(0.0);
    }

    public void forceCloseInstant() {
        open = false;

        if (anim != null) {
            anim.stop();
            anim = null;
        }

        if (rightPane != null) {
            rightPane.setVisible(false);
            rightPane.setManaged(false);
            rightPane.setOpacity(0.0);
        }

        if (rightCard != null) {
            rightCard.setVisible(false);
            rightCard.setManaged(false);
            rightCard.setOpacity(0.0);
        }
    }

    private TestCaseDraft buildDraftFromUi() {
        TestCaseDraft d = new TestCaseDraft();

        // preserve stable meta
        d.id = s(draftId);
        d.createdAt = s(draftCreatedAt);

        d.code = s(tfPrivTop != null ? tfPrivTop.getText() : "");
        d.number = s(tfTop2 != null ? tfTop2.getText() : "");
        d.title = s(tfTitle != null ? tfTitle.getText() : "");
        d.description = s(taRightDescription != null ? taRightDescription.getText() : "");

        d.labels = collectChips(fpRightLabels);
        d.tags = collectChips(fpRightTags);

        d.steps = stepsEditor.snapshotDraft();
        return d;
    }

    private List<String> collectChips(FlowPane pane) {
        List<String> out = new ArrayList<>();
        if (pane == null) return out;

        for (Node n : pane.getChildren()) {
            String v = findChipText(n);
            if (!v.isBlank()) out.add(v);
        }
        return out;
    }

    private String findChipText(Node node) {
        if (node == null) return "";

        if (node instanceof javafx.scene.control.Labeled l) {
            String t = s(l.getText()).trim();
            if (!t.isBlank()) return t;
        }

        if (node instanceof Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                String v = findChipText(c);
                if (!v.isBlank()) return v;
            }
        }

        return "";
    }

    private String s(String v) {
        if (v == null) return "";
        return v.trim();
    }

    private void applyI18nStaticTexts() {
        if (tfPrivTop != null) tfPrivTop.setPromptText(I18n.t("tc.right.private.placeholder"));
        if (tfTop2 != null) tfTop2.setPromptText(I18n.t("tc.right.public.placeholder"));

        if (tfTitle != null) tfTitle.setPromptText(I18n.t("tc.right.title.placeholder"));

        if (tfRightLabel != null) tfRightLabel.setPromptText(I18n.t("tc.right.label.placeholder"));
        if (tfRightTag != null) tfRightTag.setPromptText(I18n.t("tc.right.tag.placeholder"));

        if (taRightDescription != null) taRightDescription.setPromptText(I18n.t("tc.right.desc.placeholder"));
    }

    private void initTopRow() {
        if (btnEditPriv != null) {
            UiSvg.setButtonSvg(btnEditPriv, iconPencil, 14);
            btnEditPriv.setFocusTraversable(false);
        }

        if (tfPrivTop != null) {
            tfPrivTop.setEditable(false);
            tfPrivTop.getStyleClass().remove(PRIV_EDIT_CLASS);
            tfPrivTop.setText(s(PrivateRootConfig.rightField1()));
        }

        if (tfTop2 != null) tfTop2.setText("");
    }

    private void initLabelsUi() {
        if (tfRightLabel != null) {
            tfRightLabel.setOnMousePressed(e -> tfRightLabel.requestFocus());
            tfRightLabel.textProperty().addListener((obs, ov, nv) -> updateAddLabelBtnVisibility());
            tfRightLabel.setOnAction(e -> onAddRightLabel());
            tfRightLabel.focusedProperty().addListener((obs, ov, nv) -> updateAddLabelBtnVisibility());
        }

        if (btnAddRightLabel != null) {
            UiSvg.setButtonSvg(btnAddRightLabel, iconPlus, 14);
            btnAddRightLabel.setFocusTraversable(false);
        }

        updateAddLabelBtnVisibility();
    }

    private void initTagsUi() {
        if (tfRightTag != null) {
            tfRightTag.setOnMousePressed(e -> tfRightTag.requestFocus());
            tfRightTag.textProperty().addListener((obs, ov, nv) -> updateAddTagBtnVisibility());
            tfRightTag.setOnAction(e -> onAddRightTag());
            tfRightTag.focusedProperty().addListener((obs, ov, nv) -> updateAddTagBtnVisibility());
        }

        if (btnAddRightTag != null) {
            UiSvg.setButtonSvg(btnAddRightTag, iconPlus, 14);
            btnAddRightTag.setFocusTraversable(false);
        }

        updateAddTagBtnVisibility();
    }

    private void initDescriptionUi() {}

    private void initStepsUi() {
        if (stepsBox == null) return;
        if (stepsBox.getChildren().isEmpty()) {
            stepsEditor.addDraftStep("");
        }
    }

    private void initActions() {
        if (btnEditPriv != null) btnEditPriv.setOnAction(e -> onEditPriv());

        if (btnCloseRight != null) {
            UiSvg.setButtonSvg(btnCloseRight, iconClose, 14);
            btnCloseRight.setFocusTraversable(false);
            btnCloseRight.setOnAction(e -> close());
        }

        if (btnSaveRight != null) {
            UiSvg.setButtonSvg(btnSaveRight, iconCheck, 14);
            btnSaveRight.setFocusTraversable(false);
            btnSaveRight.setOnAction(e -> onSaveRight());
        }

        if (btnAddStep != null) {
            UiSvg.setButtonSvg(btnAddStep, iconPlus, 14);
            btnAddStep.setFocusTraversable(false);
            btnAddStep.setOnAction(e -> onAddStep());
        }

        if (btnAddRightLabel != null) btnAddRightLabel.setOnAction(e -> onAddRightLabel());
        if (btnAddRightTag != null) btnAddRightTag.setOnAction(e -> onAddRightTag());
    }

    private void updateAddLabelBtnVisibility() {
        if (btnAddRightLabel == null || tfRightLabel == null) return;
        String v = s(tfRightLabel.getText());
        boolean show = !v.isEmpty();
        btnAddRightLabel.setVisible(show);
        btnAddRightLabel.setManaged(show);
        btnAddRightLabel.setDisable(!show || (existingCard && !editEnabled));
    }

    private void updateAddTagBtnVisibility() {
        if (btnAddRightTag == null || tfRightTag == null) return;
        String v = s(tfRightTag.getText());
        boolean show = !v.isEmpty();
        btnAddRightTag.setVisible(show);
        btnAddRightTag.setManaged(show);
        btnAddRightTag.setDisable(!show || (existingCard && !editEnabled));
    }

    private void applyEditModeUi() {
        if (btnEdit != null) {
            boolean show = existingCard;
            btnEdit.setVisible(show);
            btnEdit.setManaged(show);

            if (show && editEnabled) {
                if (!btnEdit.getStyleClass().contains(EDIT_ACTIVE_CLASS)) {
                    btnEdit.getStyleClass().add(EDIT_ACTIVE_CLASS);
                }
            } else {
                btnEdit.getStyleClass().remove(EDIT_ACTIVE_CLASS);
            }
        }
    }

    private void applyLockState() {
        boolean allow = !existingCard || editEnabled;

        if (tfPrivTop != null) tfPrivTop.setEditable(allow && privEditable);

        if (tfTop2 != null) tfTop2.setEditable(allow);
        if (tfTitle != null) tfTitle.setEditable(allow);
        if (taRightDescription != null) taRightDescription.setEditable(allow);

        if (tfRightLabel != null) tfRightLabel.setEditable(allow);
        if (tfRightTag != null) tfRightTag.setEditable(allow);

        if (btnEditPriv != null) btnEditPriv.setDisable(!allow);

        if (btnAddRightLabel != null) btnAddRightLabel.setDisable(!allow || s(tfRightLabel == null ? "" : tfRightLabel.getText()).isEmpty());
        if (btnAddRightTag != null) btnAddRightTag.setDisable(!allow || s(tfRightTag == null ? "" : tfRightTag.getText()).isEmpty());

        if (btnAddStep != null) btnAddStep.setDisable(!allow);
        if (btnSaveRight != null) btnSaveRight.setDisable(!allow);

        if (fpRightLabels != null) fpRightLabels.setDisable(!allow);
        if (fpRightTags != null) fpRightTags.setDisable(!allow);
        if (stepsBox != null) stepsBox.setDisable(!allow);

        if (btnCloseRight != null) btnCloseRight.setDisable(false);
        if (btnEdit != null) btnEdit.setDisable(false);
    }

    private void hideAnimated() {
        if (rightPane == null || rightCard == null) return;

        open = false;

        FadeTransition fade = new FadeTransition(Duration.millis(RIGHT_ANIM_MS), rightCard);
        fade.setFromValue(rightCard.getOpacity());
        fade.setToValue(0.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        anim = new ParallelTransition(fade);
        anim.setOnFinished(e -> {
            rightPane.setVisible(false);
            rightPane.setManaged(false);

            rightCard.setVisible(false);
            rightCard.setManaged(false);
            rightCard.setOpacity(0.0);
        });

        anim.play();
    }
}
