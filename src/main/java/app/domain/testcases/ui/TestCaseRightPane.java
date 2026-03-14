package app.domain.testcases.ui;

import app.core.I18n;
import app.core.PrivateRootConfig;
import app.domain.testcases.repo.FileTestCaseRepository;
import app.domain.testcases.usecase.CreateTestCaseUseCase;
import app.domain.testcases.usecase.TestCaseDraft;
import app.ui.UiAutoGrowTextArea;
import app.ui.UiSaveFeedback;
import app.ui.UiSvg;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Duration;

import javafx.geometry.Pos;
import javafx.scene.text.TextAlignment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class TestCaseRightPane {
    private static final String DISABLED_BASE_CLASS = "tc-disabled-base";

    private static final String PRIV_EDIT_CLASS = "tc-priv-edit";
    private static final String EDIT_ACTIVE_CLASS = "tc-edit-active";

    // ✅ read-only, но selectable: убираем "активный" фокус-вид
    private static final String RO_SELECT_CLASS = "tc-ro-select";

    // ✅ чтобы не вешать слушатели по 10 раз (applyLockState дергается часто)
    private static final String SEL_HANDLER_INSTALLED_KEY = "tc.selClearOnBlurInstalled";

    // ✅ Save inset: берем как ЧИСЛО из spacer (а не из overlay)
    private static final double SAVE_OVERLAY_MARGIN_BOTTOM = 18.0;  // соответствует FXML Insets bottom="18"
    private static final double SAVE_OVERLAY_EXTRA_SCROLL  = 10.0;  // небольшой запас
    private double saveInsetPx = 0.0;

    // ✅ NEW: smooth scroll animation for add-step auto scroll
    private Timeline addStepScrollAnim;

    // ✅ NEW: keep focused field visible on auto-grow (steps only)
    private static final String KEEP_FOCUS_SCROLL_INSTALLED_KEY = "tc.keepFocusScrollInstalled";
    private static final String KEEP_FOCUS_SCROLL_FIELD_KEY = "tc.keepFocusScrollInstalled.field";

    private Timeline keepFocusScrollAnim;
    private boolean keepFocusScrollGuard = false;
    private long keepFocusScrollReqId = 0;

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

    // ✅ NEW: scroll context for "keep Add Step button in place"
    private final ScrollPane spRight;
    private final Region rightScrollBottomSpacer;

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

    private boolean privEditable = false;

    private boolean existingCard = false;
    private boolean editEnabled = true;

    private Path openedFile = null;

    // stable meta for current card session
    private String draftId = "";
    private String draftCreatedAt = "";

    private Runnable onSaved;

    // guard from controller (pair uniqueness etc.)
    private BooleanSupplier canSaveSupplier;
    private String lastSaveBlockMessage = "";

    // ✅ NEW: подсказка под кнопкой Save (вместо tooltip)
    private Label saveDisabledHintLabel;
    private static final String SAVE_HINT_INSTALLED_KEY = "tc.saveHintInstalled";

    // ✅ Анимируем rightPane (там “подложка”), чтобы фон и контент ехали вместе
    private final RightPaneAnimator animator;

    // ✅ NEW: universal save feedback for the Save button (spinner -> check)
    private UiSaveFeedback.Handle saveFx;

    // ===================== DIRTY TRACKING =====================
    // baseline: состояние карточки при openExisting / после успешного update-save
    private TestCaseDraft baselineDraft = null;

    // чтобы контроллер мог дергать updateSaveGateUi на любые изменения в форме
    private Runnable onUserChanged;
    private boolean suppressUserChanged = false;

    public void setOnUserChanged(Runnable r) {
        this.onUserChanged = r;
    }

    private void fireUserChanged() {
        if (suppressUserChanged) return;
        if (onUserChanged != null) onUserChanged.run();
    }

    // важно: для create-flow dirty не ограничиваем
    public boolean isDirty() {
        if (!existingCard) return true;
        if (baselineDraft == null) return false;

        TestCaseDraft cur = buildDraftFromUi();
        return !sameDraftNormalized(baselineDraft, cur);
    }

    private void captureBaselineFromUi() {
        baselineDraft = buildDraftFromUi();
    }

    private static boolean sameDraftNormalized(TestCaseDraft a, TestCaseDraft b) {
        if (a == null || b == null) return a == b;

        if (!n(a.code).equals(n(b.code))) return false;
        if (!n(a.number).equals(n(b.number))) return false;
        if (!n(a.title).equals(n(b.title))) return false;
        if (!n(a.description).equals(n(b.description))) return false;

        if (!listN(a.labels).equals(listN(b.labels))) return false;
        if (!listN(a.tags).equals(listN(b.tags))) return false;

        var sa = (a.steps == null) ? List.<TestCaseDraft.StepDraft>of() : a.steps;
        var sb = (b.steps == null) ? List.<TestCaseDraft.StepDraft>of() : b.steps;
        if (sa.size() != sb.size()) return false;

        for (int i = 0; i < sa.size(); i++) {
            var xa = sa.get(i);
            var xb = sb.get(i);

            String aStep = (xa == null) ? "" : xa.step;
            String aData = (xa == null) ? "" : xa.data;
            String aExp  = (xa == null) ? "" : xa.expected;

            String bStep = (xb == null) ? "" : xb.step;
            String bData = (xb == null) ? "" : xb.data;
            String bExp  = (xb == null) ? "" : xb.expected;

            if (!n(aStep).equals(n(bStep))) return false;
            if (!n(aData).equals(n(bData))) return false;
            if (!n(aExp).equals(n(bExp))) return false;
        }

        return true;
    }

    private static String n(String s) {
        return s == null ? "" : s.trim();
    }

    private static List<String> listN(List<String> xs) {
        if (xs == null || xs.isEmpty()) return List.of();
        ArrayList<String> out = new ArrayList<>();
        for (String x : xs) {
            String t = n(x);
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
    // ===================== END DIRTY TRACKING =====================

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void setCanSaveSupplier(BooleanSupplier supplier) {
        this.canSaveSupplier = supplier;
    }

    public void setLastSaveBlockMessage(String msg) {
        this.lastSaveBlockMessage = msg == null ? "" : msg.trim();
        updateSaveDisabledHint();
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

            // ✅ NEW
            ScrollPane spRight,
            Region rightScrollBottomSpacer,

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

        this.spRight = spRight;
        this.rightScrollBottomSpacer = rightScrollBottomSpacer;

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

        this.animator = new RightPaneAnimator(rightPane);
        this.animator.setDx(RightPaneAnimator.DEFAULT_DX);
        this.animator.setMs(RightPaneAnimator.DEFAULT_MS);
    }

    public void init() {
        forceCloseInstant();

        applyI18nStaticTexts();
        initTopRow();

        // ✅ эти поля в read-only режиме НЕ должны ловить клики/фокус вообще (копирование не требуется)
        installNoFocusWhenLocked(tfPrivTop);
        installNoFocusWhenLocked(tfTop2);
        installNoFocusWhenLocked(tfRightLabel);
        installNoFocusWhenLocked(tfRightTag);

        // ✅ поля, где selection/copy разрешён: выделение должно очищаться при потере фокуса
        installClearSelectionOnFocusLost(tfTitle);
        installClearSelectionOnFocusLost(taRightDescription);

        initLabelsUi();
        initTagsUi();
        initDescriptionUi();
        initStepsUi();
        initActions();

        // ✅ NEW: считаем нижний inset под Save через spacer и держим его актуальным
        installSaveBottomSpacer();

        // ===================== DIRTY LISTENERS =====================
        // текстовые поля
        if (tfPrivTop != null) tfPrivTop.textProperty().addListener((o, ov, nv) -> fireUserChanged());
        if (tfTop2 != null) tfTop2.textProperty().addListener((o, ov, nv) -> fireUserChanged());
        if (tfTitle != null) tfTitle.textProperty().addListener((o, ov, nv) -> fireUserChanged());
        if (taRightDescription != null) taRightDescription.textProperty().addListener((o, ov, nv) -> fireUserChanged());

        // chips add/remove
        if (fpRightLabels != null) {
            fpRightLabels.getChildren().addListener((ListChangeListener<Node>) c -> fireUserChanged());
        }
        if (fpRightTags != null) {
            fpRightTags.getChildren().addListener((ListChangeListener<Node>) c -> fireUserChanged());
        }

        // steps changed
        stepsEditor.setOnChanged(this::fireUserChanged);
        // ===================== END DIRTY LISTENERS =====================

        applyEditModeUi();
        applyLockState();
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isEditEnabled() {
        return !existingCard || editEnabled;
    }

    public void openNew() {
        boolean wasOpen = open;

        existingCard = false;
        editEnabled = true;

        openedFile = null;

        TestCaseDraft d = TestCaseDraft.newWithId();
        draftId = s(d.id);
        draftCreatedAt = s(d.createdAt);

        baselineDraft = null; // ✅ create-flow не требует dirty

        fillUiFromDraft(d);

        privEditable = false;
        normalizePrivFieldUi();

        applyEditModeUi();
        applyLockState();

        if (!wasOpen) showAnimated();
        else pulseReplace();
    }

    public void openExisting(Path file) {
        boolean wasOpen = open;

        existingCard = true;
        editEnabled = false;

        openedFile = file;

        TestCaseDraft d;
        try {
            d = repo.readDraft(file);
        } catch (Exception e) {
            d = new TestCaseDraft();
        }

        draftId = s(d.id);
        draftCreatedAt = s(d.createdAt);

        fillUiFromDraft(d);

        // ✅ baseline = состояние карточки при открытии
        captureBaselineFromUi();

        privEditable = false;
        normalizePrivFieldUi();

        applyEditModeUi();
        applyLockState();

        if (!wasOpen) showAnimated();
        else pulseReplace();
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

        // ✅ включили edit, но dirty ещё может быть false -> контроллер пересчитает save-gate
        fireUserChanged();
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

            tfPrivTop.setFocusTraversable(true);

            tfPrivTop.requestFocus();
            tfPrivTop.selectAll();
        } else {
            tfPrivTop.setEditable(false);
            tfPrivTop.getStyleClass().remove(PRIV_EDIT_CLASS);

            PrivateRootConfig.setRightField1(tfPrivTop.getText());
        }

        applyLockState();

        // ✅ переключение edit-режима приватного поля не должно само по себе включать Save,
        // но пусть контроллер перегейтит (dirty может быть false)
        fireUserChanged();
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

        fireUserChanged();
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

        fireUserChanged();
    }

    /**
     * ✅ UX (stable + smooth):
     * - если новый шаг помещается до границы Save (учитывая bottom spacer) — ничего не скроллим
     * - если не помещается — плавно скроллим вверх ровно на overflow, чтобы кнопка "Добавить шаг" не съезжала под Save
     */
    public void onAddStep() {
        if (existingCard && !editEnabled) return;

        // fallback: без scroll context работаем как раньше
        if (spRight == null || btnAddStep == null) {
            stepsEditor.addDraftStep("");
            fireUserChanged();
            return;
        }

        stepsEditor.addDraftStep("");

        // ✅ дождаться CSS+layout (часто нужен 2й тик)
        Platform.runLater(() -> Platform.runLater(() -> {
            if (spRight.getScene() == null) return;

            // стабилизируем размеры/позиции после добавления
            forceLayout(spRight);

            double inset = resolveSaveInsetPx();
            Bounds vb = spRight.getViewportBounds();
            if (vb == null) return;

            double viewportH = vb.getHeight();
            if (viewportH <= 1.0) return;

            double btnBottomInViewport = nodeBottomInViewport(btnAddStep);

            // граница "не заходить под Save"
            double saveBoundaryY = viewportH - inset;

            // overflow вниз (на сколько кнопка залезла под Save)
            double overflowPx = btnBottomInViewport - saveBoundaryY;

            if (overflowPx <= 0.5) return;

            // плавно уезжаем вверх (чуть с запасом)
            animateScrollByPixels(overflowPx + 2.0);
        }));

        fireUserChanged();
    }

    public void onSaveRight() {
        if (existingCard && !editEnabled) return;

        if (canSaveSupplier != null) {
            boolean ok;
            try {
                ok = canSaveSupplier.getAsBoolean();
            } catch (Exception e) {
                ok = false;
            }
            if (!ok) return;
        }

        // ✅ NEW: show spinner immediately on click
        if (saveFx != null) saveFx.start();

        TestCaseDraft draft = buildDraftFromUi();

        Path saved;
        if (existingCard && openedFile != null) {
            saved = createUseCase.update(openedFile, draft);
            openedFile = saved;

            draftId = s(draft.id);
            if (draftCreatedAt.isBlank()) draftCreatedAt = s(draft.createdAt);

            // ✅ после успешного update: baseline обновился -> dirty=false
            captureBaselineFromUi();

            // ✅ пересчитать save-gate
            fireUserChanged();

        } else {
            saved = createUseCase.create(draft);

            String curNum = s(tfTop2 == null ? "" : tfTop2.getText());
            String nextNum = incNumberPreserveWidth(curNum);

            existingCard = false;
            editEnabled = true;
            openedFile = null;

            TestCaseDraft next = TestCaseDraft.newWithId();
            draftId = s(next.id);
            draftCreatedAt = s(next.createdAt);

            if (tfTop2 != null) tfTop2.setText(nextNum);

            privEditable = false;
            normalizePrivFieldUi();

            baselineDraft = null; // create-flow
        }

        System.out.println("[TestCase] saved: " + saved.toAbsolutePath());

        // ✅ NEW: after save completed successfully -> show check (with min spinner time)
        if (saveFx != null) saveFx.success();

        if (onSaved != null) onSaved.run();
    }

    private String incNumberPreserveWidth(String cur) {
        String v = s(cur);
        if (v.isBlank()) return v;

        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c < '0' || c > '9') return v;
        }

        try {
            int width = v.length();
            long num = Long.parseLong(v);
            long next = num + 1;

            String raw = Long.toString(next);
            if (raw.length() < width) {
                return "0".repeat(width - raw.length()) + raw;
            }
            return raw;
        } catch (Exception ignore) {
            return v;
        }
    }

    // ===================== INTERNAL =====================

    private void normalizePrivFieldUi() {
        if (tfPrivTop == null) return;
        tfPrivTop.setEditable(false);
        tfPrivTop.getStyleClass().remove(PRIV_EDIT_CLASS);
    }

    private void fillUiFromDraft(TestCaseDraft d) {
        if (d == null) d = new TestCaseDraft();

        // ✅ важно: программная заливка UI не должна триггерить dirty-ивенты
        suppressUserChanged = true;
        try {
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

            // ✅ после перерисовки/реинициализации шагов — убедимся, что авто-скролл на auto-grow подключён
            // (installKeepFocused... сам защищён от повторной установки)
            installKeepFocusedStepFieldInView();

        } finally {
            suppressUserChanged = false;
        }
    }

    private void clearChipsKeepInput(FlowPane pane) {
        if (pane == null) return;
        while (pane.getChildren().size() > 1) pane.getChildren().remove(1);
    }

    private void showAnimated() {
        if (rightPane == null) return;

        open = true;

        animator.show(
                this::forceShowContainersNow,
                () -> {
                    rightPane.setOpacity(1.0);
                    rightPane.setTranslateX(0.0);
                }
        );
    }

    private void pulseReplace() {
        if (rightPane == null) return;

        if (!open) {
            showAnimated();
            return;
        }

        forceShowContainersNow();
        animator.pulseReplace();
    }

    private void forceShowContainersNow() {
        if (rightPane == null) return;

        animator.stop();

        open = true;

        rightPane.setVisible(true);
        rightPane.setManaged(true);
        rightPane.setOpacity(1.0);
        rightPane.setTranslateX(0.0);

        if (rightCard != null) {
            rightCard.setVisible(true);
            rightCard.setManaged(true);
            rightCard.setOpacity(1.0);
            rightCard.setTranslateX(0.0);
        }

        // ✅ NEW: при открытии панели актуализируем подсказку под Save
        Platform.runLater(this::updateSaveDisabledHint);
    }

    public void forceCloseInstant() {
        open = false;

        animator.stop();

        // ✅ FIX: мягко убираем фокус с закрываемой панели, чтобы не было "залипа" событий/кликов
        try {
            if (rightStack != null) rightStack.requestFocus();
        } catch (Exception ignore) {}

        if (rightPane != null) {
            rightPane.setVisible(false);
            rightPane.setManaged(false);
            rightPane.setOpacity(0.0);
            rightPane.setTranslateX(0.0);
        }

        if (rightCard != null) {
            rightCard.setVisible(false);
            rightCard.setManaged(false);
            rightCard.setOpacity(0.0);
            rightCard.setTranslateX(0.0);
        }
    }

    private TestCaseDraft buildDraftFromUi() {
        TestCaseDraft d = new TestCaseDraft();

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

    private void initDescriptionUi() {
        if (taRightDescription == null) return;

        // берем базу из FXML (prefHeight)
        double base = taRightDescription.getPrefHeight();
        if (base <= 0) base = 94; // на всякий случай

        taRightDescription.setMinHeight(Region.USE_PREF_SIZE);
        taRightDescription.setMaxHeight(Double.MAX_VALUE);

        UiAutoGrowTextArea.installWrapAutoGrow(taRightDescription, base, null);
    }
    /**
     * Считает количество "визуальных строк" с учетом wrap по ширине.
     */
    private void initStepsUi() {
        if (stepsBox == null) return;
        if (stepsBox.getChildren().isEmpty()) {
            stepsEditor.addDraftStep("");
        }

        // ✅ NEW: keep focused step field visible when it auto-grows
        installKeepFocusedStepFieldInView();
    }

    private void initActions() {
        if (btnEditPriv != null) btnEditPriv.setOnAction(e -> onEditPriv());

        if (btnCloseRight != null) {
            UiSvg.setButtonSvg(btnCloseRight, iconClose, 14);
            btnCloseRight.setFocusTraversable(false);
            btnCloseRight.setOnAction(e -> close());
        }

        if (btnSaveRight != null) {
            // ✅ NEW: вместо вечной SVG-галочки ставим универсальный feedback (spinner -> check)
            saveFx = UiSaveFeedback.install(btnSaveRight, Duration.millis(1000), Duration.millis(900));
            saveFx.reset(); // старт: ничего не показываем

            btnSaveRight.setFocusTraversable(false);
            btnSaveRight.setOnAction(e -> onSaveRight());

            // ✅ NEW: подсказка под кнопкой Save (вместо tooltip)
            installSaveDisabledHintUnderButton();
            btnSaveRight.disableProperty().addListener((obs, ov, nv) -> updateSaveDisabledHint());
            updateSaveDisabledHint();
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

        if (btnSaveRight != null) {
            var classes = btnSaveRight.getStyleClass();

            if (!allow) {
                btnSaveRight.setDisable(true);
                if (!classes.contains(DISABLED_BASE_CLASS)) classes.add(DISABLED_BASE_CLASS);

                // ✅ NEW: причина дизейбла (lock)
                setLastSaveBlockMessage("locked");

                // если залочили — и фидбек тоже сбросим, чтобы не висела галочка/спиннер
                if (saveFx != null) saveFx.reset();
            } else {
                // не трогаем disable (его может контролировать gate/canSave),
                // но убираем модификатор, если он был добавлен lock-логикой
                classes.remove(DISABLED_BASE_CLASS);
            }
        }

        if (fpRightLabels != null) fpRightLabels.setDisable(!allow);
        if (fpRightTags != null) fpRightTags.setDisable(!allow);

        if (stepsBox != null) {
            stepsBox.setDisable(false);
            applyStepsInteractivity(stepsBox, allow);
        }

        if (btnCloseRight != null) btnCloseRight.setDisable(false);
        if (btnEdit != null) btnEdit.setDisable(false);

        syncLockFocusPolicy(tfPrivTop);
        syncLockFocusPolicy(tfTop2);
        syncLockFocusPolicy(tfRightLabel);
        syncLockFocusPolicy(tfRightTag);

        // ✅ визуально гасим "active focus" у selectable read-only полей
        syncReadOnlySelectableLook(tfTitle, allow);
        syncReadOnlySelectableLook(taRightDescription, allow);

        // ✅ ключевое: курсор I-beam на hover для title (и вообще для ro-select)
        syncReadOnlySelectableCursor(tfTitle, allow);
        syncReadOnlySelectableCursor(taRightDescription, allow);

        // ✅ NEW: синхронизируем подсказку под Save на случай, если disable поменяли здесь
        updateSaveDisabledHint();
    }

    // ===================== SAVE DISABLED HINT (UNDER BUTTON) =====================

    private void installSaveDisabledHintUnderButton() {
        if (btnSaveRight == null) return;

        Object already = btnSaveRight.getProperties().get(SAVE_HINT_INSTALLED_KEY);
        if (already instanceof Boolean b && b) return;
        btnSaveRight.getProperties().put(SAVE_HINT_INSTALLED_KEY, Boolean.TRUE);

        saveDisabledHintLabel = new Label();
        saveDisabledHintLabel.getStyleClass().add("tc-save-hint");
        saveDisabledHintLabel.setWrapText(true);
        saveDisabledHintLabel.setMouseTransparent(true);

        // ✅ место под подсказку резервируем всегда (чтобы кнопка не "гуляла")
        saveDisabledHintLabel.setText(" ");        // пробел, чтобы высота строки считалась
        saveDisabledHintLabel.setVisible(true);
        saveDisabledHintLabel.setManaged(true);
        saveDisabledHintLabel.setOpacity(0.0);    // невидимо, но место есть

        // ✅ фиксируем высоту одной строки (чтобы не прыгало от wrap/пустоты)
        double h = saveDisabledHintLabel.prefHeight(-1);
        if (h <= 0) h = 14; // fallback
        saveDisabledHintLabel.setMinHeight(h);
        saveDisabledHintLabel.setPrefHeight(h);
        saveDisabledHintLabel.setMaxHeight(h);

        // ✅ подсказка должна быть по центру
        saveDisabledHintLabel.setAlignment(Pos.CENTER);
        saveDisabledHintLabel.setTextAlignment(TextAlignment.CENTER);
        // даём лейблу ширину контейнера, чтобы центрирование было визуально по центру
        saveDisabledHintLabel.setMaxWidth(Double.MAX_VALUE);

        // если кнопка уже внутри VBox (например, повторная инициализация) — просто добавим label

        Parent p0 = btnSaveRight.getParent();
        if (p0 instanceof VBox vb) {
            vb.setAlignment(Pos.CENTER);
            vb.setFillWidth(true);

            // ✅ FIX: VBox не должен перехватывать клики по "пустой" области вокруг кнопки
            vb.setPickOnBounds(false);

            if (!vb.getChildren().contains(saveDisabledHintLabel)) {
                vb.getChildren().add(saveDisabledHintLabel);
            }
            return;
        }


        Parent parent = btnSaveRight.getParent();
        if (!(parent instanceof Pane pane)) return;

        int idx = pane.getChildren().indexOf(btnSaveRight);
        if (idx < 0) return;

        // ✅ важно: сначала вынимаем кнопку, потом вставляем wrap
        pane.getChildren().remove(btnSaveRight);

        VBox wrap = new VBox(4.0);
        wrap.setAlignment(Pos.CENTER);   // ✅ кнопка остаётся по центру как раньше
        wrap.setFillWidth(true);         // ✅ ширину отдаём детям, но кнопку НЕ растягиваем (мы её maxWidth не меняем)

// ✅ FIX 1: контейнер не должен ловить клики "по воздуху"
        wrap.setPickOnBounds(false);

// ✅ FIX 2: не даём wrap растягиваться на всю ширину, чтобы он не перекрывал соседние кнопки
        wrap.setMinWidth(Region.USE_PREF_SIZE);
        wrap.setPrefWidth(Region.USE_COMPUTED_SIZE);
        wrap.setMaxWidth(Region.USE_PREF_SIZE);

// (опционально, но полезно): запретим wrap подхватывать мышь где не надо — клики пойдут в children
        wrap.setMouseTransparent(false);

        wrap.getChildren().addAll(btnSaveRight, saveDisabledHintLabel);

        if (idx > pane.getChildren().size()) idx = pane.getChildren().size();
        pane.getChildren().add(idx, wrap);

    }

    private void updateSaveDisabledHint() {
        if (btnSaveRight == null) return;
        if (saveDisabledHintLabel == null) return;

        // показываем подсказку только когда Save disabled и есть причина
        if (!btnSaveRight.isDisabled()) {
            saveDisabledHintLabel.setText(" ");
            saveDisabledHintLabel.setOpacity(0.0);
            return;
        }

        String key = s(lastSaveBlockMessage);
        if (key.isEmpty()) {
            saveDisabledHintLabel.setText(" ");
            saveDisabledHintLabel.setOpacity(0.0);
            return;
        }

        String text;
        switch (key) {
            case "fill.code" -> text = I18n.t("tc.save.block.fill.code");
            case "fill.num"  -> text = I18n.t("tc.save.block.fill.num");
            case "fill.both" -> text = I18n.t("tc.save.block.fill.both");
            case "fill"      -> text = I18n.t("tc.save.block.fill.both");

            case "dupOther", "duplicate" -> text = I18n.t("tc.save.block.duplicate");

            case "dirty" -> text = I18n.t("tc.save.block.dirty"); // ✅ NEW

            case "locked" -> text = I18n.t("tc.save.block.locked");
            case "mode"   -> text = I18n.t("tc.save.block.mode");
            case "closed" -> text = I18n.t("tc.save.block.closed");

            default -> text = I18n.t("tc.save.block.unknown");
        }

        text = s(text);

        if (text.isEmpty()) {
            saveDisabledHintLabel.setText(" ");
            saveDisabledHintLabel.setOpacity(0.0);
            return;
        }

        saveDisabledHintLabel.setText(text);
        saveDisabledHintLabel.setOpacity(1.0);
    }

    private void hideAnimated() {
        if (rightPane == null) return;

        open = false;

        // ✅ FIX: мягко убираем фокус в момент закрытия, чтобы не оставался "залипший" фокус/события
        try {
            if (rightStack != null) rightStack.requestFocus();
        } catch (Exception ignore) {}

        animator.hide(
                null,
                () -> {
                    rightPane.setVisible(false);
                    rightPane.setManaged(false);
                    rightPane.setOpacity(0.0);
                    rightPane.setTranslateX(0.0);

                    if (rightCard != null) {
                        rightCard.setVisible(false);
                        rightCard.setManaged(false);
                        rightCard.setOpacity(0.0);
                        rightCard.setTranslateX(0.0);
                    }
                }
        );
    }

    // ===================== LOCK: NO CLICK / NO FOCUS =====================

    private void installNoFocusWhenLocked(TextField tf) {
        if (tf == null) return;

        tf.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (isLocked(tf)) {
                e.consume();
                focusFallback();
            }
        });

        tf.focusedProperty().addListener((obs, ov, nv) -> {
            if (nv && isLocked(tf)) {
                Platform.runLater(this::focusFallback);
            }
        });
    }

    private void syncLockFocusPolicy(TextField tf) {
        if (tf == null) return;

        boolean locked = isLocked(tf);

        tf.setFocusTraversable(!locked);

        if (locked && tf.isFocused()) {
            Platform.runLater(this::focusFallback);
        }
    }

    private boolean isLocked(TextField tf) {
        if (tf == null) return true;
        return tf.isDisabled() || !tf.isEditable();
    }

    private void focusFallback() {
        if (rightPane != null) {
            rightPane.requestFocus();
            return;
        }
        if (rightStack != null) {
            rightStack.requestFocus();
        }
    }
    // ===================== READ-ONLY SELECTION UX =====================

    private void installClearSelectionOnFocusLost(TextInputControl c) {
        if (c == null) return;
        if (isSelectionHandlerInstalled(c)) return;
        markSelectionHandlerInstalled(c);

        c.focusedProperty().addListener((obs, ov, nv) -> {
            if (!nv) {
                Platform.runLater(() -> {
                    try {
                        c.deselect();
                    } catch (Exception ignore) {
                    }
                });
            }
        });
    }

    private boolean isSelectionHandlerInstalled(TextInputControl c) {
        if (c == null) return false;
        return c.getProperties().containsKey(SEL_HANDLER_INSTALLED_KEY);
    }

    private void markSelectionHandlerInstalled(TextInputControl c) {
        if (c == null) return;
        c.getProperties().put(SEL_HANDLER_INSTALLED_KEY, Boolean.TRUE);
    }

    private void syncReadOnlySelectableLook(TextInputControl c, boolean allowEdit) {
        if (c == null) return;

        boolean roSelectable = !allowEdit;

        if (roSelectable) {
            if (!c.getStyleClass().contains(RO_SELECT_CLASS)) c.getStyleClass().add(RO_SELECT_CLASS);
        } else {
            c.getStyleClass().remove(RO_SELECT_CLASS);
        }
    }

    // ✅ фикс: курсор "TEXT" должен появляться на hover даже без клика (как у описания)
    private void syncReadOnlySelectableCursor(TextInputControl c, boolean allowEdit) {
        if (c == null) return;

        boolean roSelectable = !allowEdit;

        if (roSelectable) {
            c.setCursor(Cursor.TEXT);
        } else {
            c.setCursor(Cursor.DEFAULT);
        }
    }

    // ===================== STEPS: READ-ONLY BUT SELECTABLE =====================

    private void applyStepsInteractivity(Node node, boolean allowEdit) {
        if (node == null) return;

        if (node instanceof Button b) {
            b.setDisable(!allowEdit);
            b.setFocusTraversable(allowEdit);
        }

        if (node instanceof TextInputControl tic) {
            tic.setEditable(allowEdit);
            if (tic.isDisabled()) tic.setDisable(false);

            installClearSelectionOnFocusLost(tic);

            // ✅ тот же стиль для read-only selectable шагов
            syncReadOnlySelectableLook(tic, allowEdit);
            syncReadOnlySelectableCursor(tic, allowEdit);
        }

        if (node instanceof Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                applyStepsInteractivity(c, allowEdit);
            }
        }
    }

    // ===================== STEPS: KEEP FOCUSED FIELD IN VIEW (AUTO-GROW) =====================

    private void installKeepFocusedStepFieldInView() {
        if (spRight == null || stepsBox == null) return;

        Object already = stepsBox.getProperties().get(KEEP_FOCUS_SCROLL_INSTALLED_KEY);
        if (already instanceof Boolean b && b) {
            // всё уже повешено, но всё равно пробежимся по дереву (на случай reset/replace узлов)
            attachKeepFocusedListenerRecursive(stepsBox);
            return;
        }

        stepsBox.getProperties().put(KEEP_FOCUS_SCROLL_INSTALLED_KEY, Boolean.TRUE);

        // 1) поля, которые уже есть
        attachKeepFocusedListenerRecursive(stepsBox);

        // 2) поля вмв новых шагах (которые добавятся в stepsBox)
        stepsBox.getChildren().addListener((ListChangeListener<Node>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (Node n : c.getAddedSubList()) {
                        attachKeepFocusedListenerRecursive(n);
                    }
                }
            }
        });
    }

    private void attachKeepFocusedListenerRecursive(Node node) {
        if (node == null) return;

        if (node instanceof TextInputControl tic) {
            attachKeepFocusedListener(tic);
            return; // TextInputControl детей не обходим
        }

        if (node instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) {
                attachKeepFocusedListenerRecursive(ch);
            }
        }
    }

    private void attachKeepFocusedListener(TextInputControl tic) {
        if (tic == null) return;

        if (tic.getProperties().containsKey(KEEP_FOCUS_SCROLL_FIELD_KEY)) return;
        tic.getProperties().put(KEEP_FOCUS_SCROLL_FIELD_KEY, Boolean.TRUE);

        // реагируем на ввод текста — именно он триггерит auto-grow у TextArea в шагах
        tic.textProperty().addListener((obs, ov, nv) -> {
            // только если это активное поле — иначе при fill/инициализации будет “самоскролл”
            if (!tic.isFocused()) return;

            // guard от циклов/дребезга
            if (keepFocusScrollGuard) return;

            scheduleEnsureFocusedInView(tic);
        });
    }

    private void scheduleEnsureFocusedInView(TextInputControl focusedField) {
        if (focusedField == null) return;
        if (spRight == null) return;

        final long reqId = ++keepFocusScrollReqId;

        // дождаться layout после изменения высоты (обычно нужно 2 тика)
        Platform.runLater(() -> Platform.runLater(() -> {
            if (reqId != keepFocusScrollReqId) return; // берём только последнюю правку
            ensureFocusedInViewNow(focusedField);
        }));
    }

    private void ensureFocusedInViewNow(TextInputControl focusedField) {
        if (focusedField == null || spRight == null) return;
        if (spRight.getScene() == null) return;

        // фокус мог уйти между тиками
        if (!focusedField.isFocused()) return;

        if (keepFocusScrollGuard) return;
        keepFocusScrollGuard = true;
        try {
            // стабилизируем размеры/позиции
            forceLayout(spRight);

            Bounds vb = spRight.getViewportBounds();
            if (vb == null) return;

            double viewportH = vb.getHeight();
            if (viewportH <= 1.0) return;

            double inset = resolveSaveInsetPx();

            // граница "не заходить под Save"
            double saveBoundaryY = viewportH - inset;

            double fieldBottomInViewport = nodeBottomInViewport(focusedField);

            // если поле целиком помещается над Save — не трогаем
            double overflowPx = fieldBottomInViewport - saveBoundaryY;
            if (overflowPx <= 0.5) return;

            // скроллим ровно на overflow (чуть с запасом)
            animateKeepFocusScrollByPixels(overflowPx + 2.0);

        } finally {
            keepFocusScrollGuard = false;
        }
    }

    private void animateKeepFocusScrollByPixels(double deltaPx) {
        if (spRight == null || deltaPx <= 0.5) return;
        if (spRight.getContent() == null) return;

        double contentH = spRight.getContent().getBoundsInLocal().getHeight();
        Bounds vb = spRight.getViewportBounds();
        double viewportH = (vb == null) ? 0.0 : vb.getHeight();

        double max = contentH - viewportH;
        if (max <= 1.0) return;

        double dv = deltaPx / max;

        double from = spRight.getVvalue();
        double to = clamp01(from + dv);

        if (Math.abs(to - from) <= 0.0005) return;

        // чтобы анимации не “боролись”
        if (addStepScrollAnim != null) addStepScrollAnim.stop();
        if (keepFocusScrollAnim != null) keepFocusScrollAnim.stop();

        keepFocusScrollAnim = new Timeline(
                new KeyFrame(Duration.millis(120),
                        new KeyValue(spRight.vvalueProperty(), to, Interpolator.EASE_BOTH)
                )
        );
        keepFocusScrollAnim.play();
    }

    // ===================== SAVE INSET VIA SPACER =====================

    private void installSaveBottomSpacer() {
        if (rightScrollBottomSpacer == null) return;

        if (btnSaveRight != null) {
            btnSaveRight.heightProperty().addListener((obs, ov, nv) -> updateSaveBottomSpacerNow());
            btnSaveRight.visibleProperty().addListener((obs, ov, nv) -> updateSaveBottomSpacerNow());
            btnSaveRight.managedProperty().addListener((obs, ov, nv) -> updateSaveBottomSpacerNow());
        }

        Platform.runLater(this::updateSaveBottomSpacerNow);
    }

    private void updateSaveBottomSpacerNow() {
        if (rightScrollBottomSpacer == null) return;

        boolean saveVisible = btnSaveRight != null && btnSaveRight.isVisible() && btnSaveRight.isManaged();

        if (!saveVisible) {
            saveInsetPx = 0.0;
            rightScrollBottomSpacer.setMinHeight(0);
            rightScrollBottomSpacer.setPrefHeight(0);
            rightScrollBottomSpacer.setMaxHeight(0);
            return;
        }

        double btnH = btnSaveRight.getHeight();
        if (btnH <= 0) btnH = btnSaveRight.prefHeight(-1);
        if (btnH <= 0) btnH = 44;

        double spacerH = btnH + SAVE_OVERLAY_MARGIN_BOTTOM + SAVE_OVERLAY_EXTRA_SCROLL;

        saveInsetPx = spacerH;

        rightScrollBottomSpacer.setMinHeight(spacerH);
        rightScrollBottomSpacer.setPrefHeight(spacerH);
        rightScrollBottomSpacer.setMaxHeight(spacerH);
    }

    private double resolveSaveInsetPx() {
        double inset = saveInsetPx;

        if (inset <= 0.5 && rightScrollBottomSpacer != null) {
            double h = rightScrollBottomSpacer.getHeight();
            if (h <= 0) h = rightScrollBottomSpacer.prefHeight(-1);
            inset = Math.max(0.0, h);
        }

        return inset;
    }

    // ===================== SCROLL HELPERS =====================

    private double nodeBottomInViewport(Node n) {
        if (n == null || spRight == null || spRight.getScene() == null) return 0.0;

        Bounds nb = n.localToScene(n.getBoundsInLocal());
        double nodeBottomSceneY = nb.getMaxY();

        double spSceneY = spRight.localToScene(0, 0).getY();
        return nodeBottomSceneY - spSceneY;
    }

    private void scrollByPixels(double deltaPx) {
        if (spRight == null || deltaPx <= 0.0) return;
        if (spRight.getContent() == null) return;

        double contentH = spRight.getContent().getBoundsInLocal().getHeight();
        double viewportH = spRight.getViewportBounds() == null ? 0.0 : spRight.getViewportBounds().getHeight();
        double max = contentH - viewportH;

        if (max <= 1.0) return;

        double dv = deltaPx / max;

        double next = spRight.getVvalue() + dv;
        if (next < 0.0) next = 0.0;
        if (next > 1.0) next = 1.0;

        spRight.setVvalue(next);
    }

    // ===================== STABLE + SMOOTH SCROLL (ADD STEP) =====================

    private void forceLayout(ScrollPane sp) {
        try {
            sp.applyCss();

            Node content = sp.getContent();
            if (content != null) content.applyCss();

            if (content instanceof Parent p) {
                p.layout();
            }

            sp.layout();
        } catch (Exception ignore) {}
    }

    private void animateScrollByPixels(double deltaPx) {
        if (spRight == null || deltaPx <= 0.5) return;
        if (spRight.getContent() == null) return;

        double contentH = spRight.getContent().getBoundsInLocal().getHeight();
        Bounds vb = spRight.getViewportBounds();
        double viewportH = (vb == null) ? 0.0 : vb.getHeight();

        double max = contentH - viewportH;
        if (max <= 1.0) return;

        double dv = deltaPx / max;

        double from = spRight.getVvalue();
        double to = clamp01(from + dv);

        if (Math.abs(to - from) <= 0.0005) return;

        if (addStepScrollAnim != null) addStepScrollAnim.stop();

        addStepScrollAnim = new Timeline(
                new KeyFrame(Duration.millis(150),
                        new KeyValue(spRight.vvalueProperty(), to, Interpolator.EASE_BOTH)
                )
        );
        addStepScrollAnim.play();
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}


