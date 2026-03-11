package app.domain.testcases.ui;

import app.core.I18n;
import app.domain.testcases.TestCase;
import app.domain.testcases.repo.TestCaseCardStore;
import app.ui.UiSvg;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.DoubleBinding;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class TestCaseOverlayHost {

    private static final String ICON_PENCIL = "pencil.svg";
    private static final String ICON_CLOSE = "close.svg";
    private static final String ICON_CHECK = "check.svg";
    private static final String ICON_PLUS = "plus.svg";
    private static final String ICON_GRIP = "grip.svg";
    private static final String ICON_TRASH = "trash.svg";
    private static final double RIGHT_SCROLL_RESERVE_PX = 10.0;
    private static final String DISABLED_BASE_CLASS = "tc-disabled-base";
    private static final String INVALID_GLOW_CLASS = "tc-invalid-glow";
    private static final double CASES_SHEET_RADIUS = 18.0;

    private final VBox hostRoot;
    private final StackPane overlayWrap;
    private final Region overlayBackdrop;
    private final VBox rightPane;
    private final StackPane rightRootStack;
    private final StackPane rightContentWrap;
    private final StackPane deleteLayer;
    private final VBox deleteModal;
    private final Button btnDeleteConfirm;
    private final Button btnDeleteCancel;

    private final TestCaseCardController testcaseCardController;

    private final VBox rightCard;
    private final HBox rightTopRow;
    private final TextField tfPrivTop;
    private final Button btnEditPriv;
    private final TextField tfTop2;
    private final VBox rightInlineStripBox;
    private final Label lbRightInlineTitle;
    private final Button btnEdit;
    private final Button btnCloseRight;
    private final StackPane titleWrap;
    private final TextField tfTitle;
    private final Label lbTitleDisplay;
    private final StackPane rightStack;
    private final ScrollPane spRight;
    private final VBox rightScrollRoot;
    private final FlowPane fpRightLabels;
    private final TextField tfRightLabel;
    private final Button btnAddRightLabel;
    private final FlowPane fpRightTags;
    private final TextField tfRightTag;
    private final Button btnAddRightTag;
    private final TextArea taRightDescription;
    private final VBox stepsBox;
    private final Button btnAddStep;
    private final Button btnDeleteRight;
    private final Button btnSaveRight;
    private final Region rightScrollBottomSpacer;

    private final SmoothScrollSupport smoothScroll = new SmoothScrollSupport();
    private final RightChipFactory chipFactory;
    private final TestCaseRightPane rightPaneCtl;
    private final RightDeleteConfirm deleteConfirm;
    private final PauseTransition closeTransition = new PauseTransition(Duration.millis(RightPaneAnimator.DEFAULT_MS));

    private final List<TestCase> all = new ArrayList<>();

    private Consumer<String> onDeleted;
    private Runnable onSaved;
    private Runnable onVisibilityChanged;

    private boolean open = false;
    private boolean saveGateValidationArmed = false;
    private String openedCaseId;
    private String tfTitlePrompt = "";

    public TestCaseOverlayHost(StackPane parent) {
        this.hostRoot = buildHostRoot();
        this.overlayBackdrop = buildOverlayBackdrop();
        this.overlayWrap = buildOverlayWrap();
        this.rightPane = (VBox) hostRoot.lookup("#rightPane");
        this.rightRootStack = (StackPane) hostRoot.lookup("#rightRootStack");
        this.rightContentWrap = (StackPane) hostRoot.lookup("#rightContentWrap");
        this.deleteLayer = (StackPane) hostRoot.lookup("#deleteLayer");
        this.deleteModal = (VBox) hostRoot.lookup("#deleteModal");
        this.btnDeleteConfirm = (Button) hostRoot.lookup("#btnDeleteConfirm");
        this.btnDeleteCancel = (Button) hostRoot.lookup("#btnDeleteCancel");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/fragments/testcase_card.fxml"));
            loader.setResources(I18n.bundle());
            Parent fragment = loader.load();
            this.testcaseCardController = loader.getController();
            rightContentWrap.getChildren().add(fragment);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load testcase card fragment", e);
        }

        this.rightCard = testcaseCardController.rightCard();
        this.rightTopRow = testcaseCardController.rightTopRow();
        this.tfPrivTop = testcaseCardController.tfPrivTop();
        this.btnEditPriv = testcaseCardController.btnEditPriv();
        this.tfTop2 = testcaseCardController.tfTop2();
        this.rightInlineStripBox = testcaseCardController.rightInlineStripBox();
        this.lbRightInlineTitle = testcaseCardController.lbRightInlineTitle();
        this.btnEdit = testcaseCardController.btnEdit();
        this.btnCloseRight = testcaseCardController.btnCloseRight();
        this.titleWrap = testcaseCardController.titleWrap();
        this.tfTitle = testcaseCardController.tfTitle();
        this.lbTitleDisplay = testcaseCardController.lbTitleDisplay();
        this.rightStack = testcaseCardController.rightStack();
        this.spRight = testcaseCardController.spRight();
        this.rightScrollRoot = testcaseCardController.rightScrollRoot();
        this.fpRightLabels = testcaseCardController.fpRightLabels();
        this.tfRightLabel = testcaseCardController.tfRightLabel();
        this.btnAddRightLabel = testcaseCardController.btnAddRightLabel();
        this.fpRightTags = testcaseCardController.fpRightTags();
        this.tfRightTag = testcaseCardController.tfRightTag();
        this.btnAddRightTag = testcaseCardController.btnAddRightTag();
        this.taRightDescription = testcaseCardController.taRightDescription();
        this.stepsBox = testcaseCardController.stepsBox();
        this.btnAddStep = testcaseCardController.btnAddStep();
        this.btnDeleteRight = testcaseCardController.btnDeleteRight();
        this.btnSaveRight = testcaseCardController.btnSaveRight();
        this.rightScrollBottomSpacer = testcaseCardController.rightScrollBottomSpacer();

        testcaseCardController.setOnEditPriv(this::onEditPriv);
        testcaseCardController.setOnEdit(this::onEdit);
        testcaseCardController.setOnCloseRight(this::onCloseRight);
        testcaseCardController.setOnAddRightLabel(this::onAddRightLabel);
        testcaseCardController.setOnAddRightTag(this::onAddRightTag);
        testcaseCardController.setOnAddStep(this::onAddStep);

        chipFactory = new RightChipFactory(spRight, ICON_CLOSE);
        rightPaneCtl = new TestCaseRightPane(
                rightPane,
                rightCard,
                rightStack,
                rightTopRow,
                btnEdit,
                btnEditPriv,
                tfPrivTop,
                tfTop2,
                tfTitle,
                fpRightLabels,
                tfRightLabel,
                btnAddRightLabel,
                fpRightTags,
                tfRightTag,
                btnAddRightTag,
                taRightDescription,
                btnAddStep,
                stepsBox,
                spRight,
                rightScrollBottomSpacer,
                btnCloseRight,
                btnSaveRight,
                smoothScroll,
                chipFactory,
                ICON_PENCIL,
                ICON_CLOSE,
                ICON_CHECK,
                ICON_PLUS,
                ICON_GRIP
        );
        rightPaneCtl.init();
        rightPaneCtl.setOnUserChanged(() -> Platform.runLater(this::updateSaveGateUi));
        rightPaneCtl.setOnSaved(this::handleSaved);

        if (btnCloseRight != null) {
            btnCloseRight.setOnAction(this::onCloseRight);
        }

        deleteConfirm = new RightDeleteConfirm(
                hostRoot,
                rightRootStack,
                deleteLayer,
                deleteModal,
                btnDeleteRight,
                btnDeleteCancel,
                btnDeleteConfirm
        );
        deleteConfirm.setCanOpenSupplier(() -> rightPaneCtl.isOpen() && openedCaseId != null);
        deleteConfirm.setCurrentFileSupplier(() -> openedCaseId == null ? null : TestCaseCardStore.fileOf(openedCaseId));
        deleteConfirm.setAfterDeleted(this::handleDeleted);

        closeTransition.setOnFinished(e -> {
            if (open) return;
            overlayWrap.setVisible(false);
            overlayWrap.setManaged(false);
            overlayBackdrop.setVisible(false);
            overlayBackdrop.setManaged(false);
            refreshDeleteAvailability();
            hostRoot.setVisible(false);
            hostRoot.setManaged(false);
            hostRoot.setMouseTransparent(true);
            notifyVisibilityChanged();
        });

        if (btnDeleteRight != null) {
            UiSvg.setButtonSvg(btnDeleteRight, ICON_TRASH, getIconSizeFromUserData(btnDeleteRight, 10));
        }

        installDigitsOnly(tfTop2);
        installRightScrollWidthBinding();

        installRightStackClip();
        installRightInlineTitle();
        installTitleEllipsisField();

        if (tfPrivTop != null) tfPrivTop.textProperty().addListener((o, a, b) -> updateSaveGateUi());
        if (tfTop2 != null) tfTop2.textProperty().addListener((o, a, b) -> updateSaveGateUi());
        installSaveGateArmOnUserInput(tfPrivTop);
        installSaveGateArmOnUserInput(tfTop2);

        parent.getChildren().add(overlayWrap);
        StackPane.setAlignment(overlayWrap, javafx.geometry.Pos.TOP_RIGHT);
    }

    public void setOnDeleted(Consumer<String> onDeleted) {
        this.onDeleted = onDeleted;
    }

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void setOnVisibilityChanged(Runnable onVisibilityChanged) {
        this.onVisibilityChanged = onVisibilityChanged;
    }

    public void bindToWidth(DoubleBinding widthBinding) {
        if (widthBinding == null) return;
        overlayBackdrop.minWidthProperty().bind(widthBinding);
        overlayBackdrop.prefWidthProperty().bind(widthBinding);
        overlayBackdrop.maxWidthProperty().bind(widthBinding);
        hostRoot.minWidthProperty().bind(widthBinding);
        hostRoot.prefWidthProperty().bind(widthBinding);
        hostRoot.maxWidthProperty().bind(widthBinding);
    }

    public boolean isOpen() {
        return open;
    }

    public String openedCaseId() {
        return openedCaseId == null ? "" : openedCaseId;
    }

    public void openExisting(String caseId) {
        String id = safeTrim(caseId);
        if (id.isBlank()) return;

        Path file = TestCaseCardStore.fileOf(id);
        if (!Files.exists(file)) return;

        closeTransition.stop();
        reloadAllCases();
        openedCaseId = id;
        saveGateValidationArmed = false;
        overlayWrap.setVisible(true);
        overlayWrap.setManaged(true);
        hostRoot.setVisible(true);
        overlayBackdrop.setVisible(true);
        overlayBackdrop.setManaged(true);
        hostRoot.setManaged(true);
        hostRoot.setMouseTransparent(false);
        open = true;
        rightPaneCtl.openExisting(file);
        updateSaveGateUi();
        refreshDeleteAvailability();
        notifyVisibilityChanged();
    }

    public void close() {
        if (!open) return;

        open = false;
        overlayBackdrop.setVisible(false);
        overlayBackdrop.setManaged(false);
        openedCaseId = null;
        saveGateValidationArmed = false;
        rightPaneCtl.close();
        closeTransition.stop();
        closeTransition.playFromStart();
    }

    private void handleSaved() {
        reloadAllCases();
        updateSaveGateUi();
        refreshDeleteAvailability();
        if (onSaved != null) onSaved.run();
    }

    private void handleDeleted() {
        String deletedId = openedCaseId;
        close();
        overlayBackdrop.setVisible(false);
        overlayBackdrop.setManaged(false);
        hostRoot.setVisible(false);
        hostRoot.setManaged(false);
        if (onDeleted != null && deletedId != null) onDeleted.accept(deletedId);
    }

    private void refreshDeleteAvailability() {
        boolean enable = rightPaneCtl.isOpen() && openedCaseId != null;
        deleteConfirm.refreshAvailability(enable);
        if (btnDeleteRight == null) return;
        btnDeleteRight.setVisible(enable);
        btnDeleteRight.setManaged(enable);
    }

    private void notifyVisibilityChanged() {
        if (onVisibilityChanged != null) onVisibilityChanged.run();
    }

    private void reloadAllCases() {
        all.clear();
        all.addAll(TestCaseCardStore.loadAll());
    }

    private VBox buildHostRoot() {
        VBox pane = new VBox();
        pane.setId("rightPane");
        pane.setSpacing(12);
        pane.setVisible(false);
        pane.setManaged(false);
        pane.setMouseTransparent(true);
        pane.setPickOnBounds(false);
        pane.getStyleClass().add("tc-right");

        StackPane rootStack = new StackPane();
        rootStack.setId("rightRootStack");
        VBox.setVgrow(rootStack, Priority.ALWAYS);
        rootStack.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        StackPane contentWrap = new StackPane();
        contentWrap.setId("rightContentWrap");
        contentWrap.setPadding(new Insets(14, 14, 14, 14));
        contentWrap.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        rootStack.getChildren().add(contentWrap);

        StackPane overlay = new StackPane();
        overlay.setId("deleteLayer");
        overlay.setVisible(false);
        overlay.setManaged(false);
        overlay.setPickOnBounds(true);
        overlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Region dim = new Region();
        dim.getStyleClass().add("tc-right-dim");
        dim.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        VBox modal = new VBox();
        modal.setId("deleteModal");
        modal.setAlignment(javafx.geometry.Pos.CENTER);
        modal.getStyleClass().add("tc-right-confirm");
        modal.setPrefWidth(300);
        modal.setMaxWidth(300);
        modal.setPrefHeight(200);
        modal.setMaxHeight(210);

        Label title = new Label(I18n.t("tc.delete.title"));
        title.getStyleClass().add("tc-delete-title");
        title.setMaxWidth(Double.MAX_VALUE);

        Region topGap = spacer(20);
        Region bottomGap = spacer(20);

        VBox buttons = new VBox(10);
        buttons.setAlignment(javafx.geometry.Pos.CENTER);

        Button cancel = new Button(I18n.t("tc.delete.cancel"));
        cancel.setId("btnDeleteCancel");
        cancel.setFocusTraversable(false);
        cancel.setPrefWidth(220);
        cancel.getStyleClass().add("tc-btn");

        Button confirm = new Button(I18n.t("tc.delete.confirm"));
        confirm.setId("btnDeleteConfirm");
        confirm.setFocusTraversable(false);
        confirm.setPrefWidth(220);
        confirm.getStyleClass().addAll("tc-btn", "tc-danger-btn");

        buttons.getChildren().addAll(cancel, confirm);

        Label hint = new Label(I18n.t("tc.delete.hint"));
        hint.getStyleClass().add("tc-delete-hint");
        hint.setMaxWidth(Double.MAX_VALUE);

        modal.getChildren().addAll(title, topGap, buttons, bottomGap, hint);
        overlay.getChildren().addAll(dim, modal);
        rootStack.getChildren().add(overlay);
        pane.getChildren().add(rootStack);
        return pane;
    }
    private Region buildOverlayBackdrop() {
        Region backdrop = new Region();
        backdrop.getStyleClass().add("cy-testcase-overlay-backdrop");
        backdrop.setVisible(false);
        backdrop.setManaged(false);
        backdrop.setMouseTransparent(true);
        backdrop.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane.setAlignment(backdrop, javafx.geometry.Pos.TOP_RIGHT);
        return backdrop;
    }

    private StackPane buildOverlayWrap() {
        StackPane wrap = new StackPane();
        wrap.setPickOnBounds(false);
        wrap.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        wrap.getChildren().addAll(overlayBackdrop, hostRoot);
        StackPane.setAlignment(overlayBackdrop, javafx.geometry.Pos.TOP_RIGHT);
        StackPane.setAlignment(hostRoot, javafx.geometry.Pos.TOP_RIGHT);
        return wrap;
    }


    private static Region spacer(double h) {
        Region r = new Region();
        r.setMinHeight(h);
        r.setPrefHeight(h);
        r.setMaxHeight(h);
        return r;
    }

    private void installRightScrollWidthBinding() {
        if (spRight == null || rightScrollRoot == null) return;

        var w = spRight.widthProperty().subtract(RIGHT_SCROLL_RESERVE_PX);
        rightScrollRoot.minWidthProperty().bind(w);
        rightScrollRoot.prefWidthProperty().bind(w);
        rightScrollRoot.maxWidthProperty().bind(w);
    }

    private void installRightStackClip() {
        if (rightStack == null) return;
        Rectangle clip = new Rectangle();
        clip.setArcWidth(CASES_SHEET_RADIUS * 2.0);
        clip.setArcHeight(CASES_SHEET_RADIUS * 2.0);
        clip.widthProperty().bind(rightStack.widthProperty());
        clip.heightProperty().bind(rightStack.heightProperty());
        rightStack.setClip(clip);
    }

    private void installDigitsOnly(TextField tf) {
        if (tf == null) return;
        UnaryOperator<TextFormatter.Change> filter = ch -> {
            String next = ch.getControlNewText();
            return next.matches("\\d*") ? ch : null;
        };
        tf.setTextFormatter(new TextFormatter<>(filter));
        tf.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            String c = e.getCharacter();
            if (c != null && !c.isEmpty() && !c.matches("\\d")) e.consume();
        });
    }

    private void installSaveGateArmOnUserInput(TextField tf) {
        if (tf == null) return;
        tf.textProperty().addListener((obs, ov, nv) -> {
            if (saveGateValidationArmed) return;
            if (!safeTrim(nv).isEmpty()) {
                saveGateValidationArmed = true;
                updateSaveGateUi();
            }
        });
    }

    private void onEdit(javafx.event.ActionEvent e) {
        rightPaneCtl.onToggleEdit();
        Platform.runLater(this::updateSaveGateUi);
    }

    private void onEditPriv(javafx.event.ActionEvent e) {
        rightPaneCtl.onEditPriv();
        Platform.runLater(this::updateSaveGateUi);
    }

    private void onAddRightLabel(javafx.event.ActionEvent e) {
        rightPaneCtl.onAddRightLabel();
        Platform.runLater(this::updateSaveGateUi);
    }

    private void onAddRightTag(javafx.event.ActionEvent e) {
        rightPaneCtl.onAddRightTag();
        Platform.runLater(this::updateSaveGateUi);
    }

    private void onAddStep(javafx.event.ActionEvent e) {
        rightPaneCtl.onAddStep();
        Platform.runLater(this::updateSaveGateUi);
    }

    private void onCloseRight(javafx.event.ActionEvent e) {
        close();
    }

    private void updateSaveGateUi() {
        boolean enabled = computeCanSaveAndApplyUi();
        if (btnSaveRight == null) return;
        btnSaveRight.setDisable(!enabled);
        if (enabled) {
            btnSaveRight.getStyleClass().remove(DISABLED_BASE_CLASS);
        } else if (!btnSaveRight.getStyleClass().contains(DISABLED_BASE_CLASS)) {
            btnSaveRight.getStyleClass().add(DISABLED_BASE_CLASS);
        }
    }

    private boolean computeCanSaveAndApplyUi() {
        if (!rightPaneCtl.isOpen() || openedCaseId == null) {
            clearInvalidGlow();
            rightPaneCtl.setLastSaveBlockMessage("closed");
            return false;
        }

        boolean allowEdit = tfTop2 != null && tfTop2.isEditable();
        if (!allowEdit) {
            clearInvalidGlow();
            rightPaneCtl.setLastSaveBlockMessage("locked");
            return false;
        }

        String code = safeTrim(tfPrivTop == null ? "" : tfPrivTop.getText());
        String num = safeTrim(tfTop2 == null ? "" : tfTop2.getText());

        boolean codeOk = !code.isBlank();
        boolean numOk = !num.isBlank();
        if (!codeOk || !numOk) {
            if (saveGateValidationArmed) {
                applyInvalidGlow(tfPrivTop, !codeOk);
                applyInvalidGlow(tfTop2, !numOk);
            } else {
                clearInvalidGlow();
            }

            if (!codeOk && !numOk) rightPaneCtl.setLastSaveBlockMessage("fill.both");
            else if (!codeOk) rightPaneCtl.setLastSaveBlockMessage("fill.code");
            else rightPaneCtl.setLastSaveBlockMessage("fill.num");
            return false;
        }

        boolean dupOther = existsOtherWithSamePair(code, num, openedCaseId);
        if (dupOther) {
            if (saveGateValidationArmed) {
                applyInvalidGlow(tfPrivTop, true);
                applyInvalidGlow(tfTop2, true);
            } else {
                clearInvalidGlow();
            }
            rightPaneCtl.setLastSaveBlockMessage("duplicate");
            return false;
        }

        if (!rightPaneCtl.isDirty()) {
            clearInvalidGlow();
            rightPaneCtl.setLastSaveBlockMessage("dirty");
            return false;
        }

        clearInvalidGlow();
        rightPaneCtl.setLastSaveBlockMessage("");
        return true;
    }

    private boolean existsOtherWithSamePair(String code, String num, String selfIdOrNull) {
        String c = safeTrim(code);
        String n = safeTrim(num);
        if (c.isBlank() || n.isBlank()) return false;

        for (TestCase tc : all) {
            if (tc == null) continue;
            if (!safeTrim(tc.getCode()).equals(c)) continue;
            if (!safeTrim(tc.getNumber()).equals(n)) continue;
            if (selfIdOrNull != null && selfIdOrNull.equals(safeTrim(tc.getId()))) continue;
            return true;
        }
        return false;
    }

    private void applyInvalidGlow(TextField tf, boolean on) {
        if (tf == null) return;
        if (on) {
            if (!tf.getStyleClass().contains(INVALID_GLOW_CLASS)) tf.getStyleClass().add(INVALID_GLOW_CLASS);
        } else {
            tf.getStyleClass().remove(INVALID_GLOW_CLASS);
        }
    }

    private void clearInvalidGlow() {
        applyInvalidGlow(tfPrivTop, false);
        applyInvalidGlow(tfTop2, false);
    }

    private void installRightInlineTitle() {
        if (lbRightInlineTitle == null) return;
        lbRightInlineTitle.setMinWidth(0.0);
        lbRightInlineTitle.setPrefWidth(0.0);
        lbRightInlineTitle.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lbRightInlineTitle, Priority.ALWAYS);

        if (rightInlineStripBox != null) {
            rightInlineStripBox.setMinWidth(0.0);
            rightInlineStripBox.setPrefWidth(0.0);
            HBox.setHgrow(rightInlineStripBox, Priority.ALWAYS);
        }

        Runnable upd = this::updateRightInlineTitle;
        if (tfPrivTop != null) tfPrivTop.textProperty().addListener((o, a, b) -> upd.run());
        if (tfTop2 != null) tfTop2.textProperty().addListener((o, a, b) -> upd.run());
        if (tfTitle != null) tfTitle.textProperty().addListener((o, a, b) -> upd.run());
        lbRightInlineTitle.widthProperty().addListener((o, a, b) -> upd.run());
        Platform.runLater(upd);
    }

    private void updateRightInlineTitle() {
        if (lbRightInlineTitle == null) return;
        String code = safeTrim(tfPrivTop == null ? "" : tfPrivTop.getText());
        String num = safeTrim(tfTop2 == null ? "" : tfTop2.getText());
        String title = safeTrim(tfTitle == null ? "" : tfTitle.getText());

        String head;
        if (!code.isEmpty() && !num.isEmpty()) head = code + "-" + num;
        else if (!code.isEmpty()) head = code;
        else head = num;

        String full;
        if (!head.isEmpty() && !title.isEmpty()) full = head + " " + title;
        else if (!head.isEmpty()) full = head;
        else full = title;

        lbRightInlineTitle.setText(full);
        if (full.isBlank()) {
            lbRightInlineTitle.setTooltip(null);
            return;
        }

        if (isLabelTextClipped(lbRightInlineTitle)) {
            Tooltip tt = lbRightInlineTitle.getTooltip();
            if (tt == null) tt = new Tooltip();
            tt.setText(full);
            lbRightInlineTitle.setTooltip(tt);
        } else {
            lbRightInlineTitle.setTooltip(null);
        }
    }

    private void installTitleEllipsisField() {
        if (tfTitle == null || lbTitleDisplay == null) return;
        String p = tfTitle.getPromptText();
        tfTitlePrompt = p == null ? "" : p.trim();

        tfTitle.setMinWidth(0.0);
        tfTitle.setPrefWidth(0.0);
        tfTitle.setMaxWidth(Double.MAX_VALUE);
        lbTitleDisplay.setMinWidth(0.0);
        lbTitleDisplay.setPrefWidth(0.0);
        lbTitleDisplay.setMaxWidth(Double.MAX_VALUE);

        if (titleWrap != null) {
            titleWrap.setMinWidth(0.0);
            titleWrap.setPrefWidth(0.0);
            titleWrap.setMaxWidth(Double.MAX_VALUE);
        }

        lbTitleDisplay.setOnMouseClicked(e -> tfTitle.requestFocus());
        tfTitle.textProperty().addListener((o, a, b) -> refreshTitleOverlayTextAndTooltip());
        lbTitleDisplay.widthProperty().addListener((o, a, b) -> refreshTitleOverlayTextAndTooltip());
        tfTitle.focusedProperty().addListener((o, a, focused) -> syncTitleDisplayState());

        Platform.runLater(() -> {
            syncTitleDisplayState();
            refreshTitleOverlayTextAndTooltip();
        });
    }

    private void syncTitleDisplayState() {
        if (tfTitle == null || lbTitleDisplay == null) return;
        boolean focused = tfTitle.isFocused();
        if (focused) {
            tfTitle.setOpacity(1.0);
            tfTitle.setMouseTransparent(false);
            lbTitleDisplay.setVisible(false);
            lbTitleDisplay.setManaged(false);
        } else {
            tfTitle.setOpacity(0.0);
            tfTitle.setMouseTransparent(true);
            lbTitleDisplay.setVisible(true);
            lbTitleDisplay.setManaged(true);
        }
        refreshTitleOverlayTextAndTooltip();
    }

    private void refreshTitleOverlayTextAndTooltip() {
        if (tfTitle == null || lbTitleDisplay == null) return;

        String raw = tfTitle.getText();
        String v = raw == null ? "" : raw.trim();
        boolean placeholder = v.isEmpty();
        String shown = placeholder ? tfTitlePrompt : raw;

        lbTitleDisplay.setText(shown == null ? "" : shown);
        if (placeholder) {
            if (!lbTitleDisplay.getStyleClass().contains("tc-title-placeholder")) {
                lbTitleDisplay.getStyleClass().add("tc-title-placeholder");
            }
        } else {
            lbTitleDisplay.getStyleClass().remove("tc-title-placeholder");
        }

        if (placeholder || v.isBlank()) {
            lbTitleDisplay.setTooltip(null);
            return;
        }

        if (isLabelTextClipped(lbTitleDisplay)) {
            Tooltip tt = lbTitleDisplay.getTooltip();
            if (tt == null) tt = new Tooltip();
            tt.setText(raw);
            lbTitleDisplay.setTooltip(tt);
        } else {
            lbTitleDisplay.setTooltip(null);
        }
    }

    private static boolean isLabelTextClipped(Label lbl) {
        if (lbl == null) return false;
        String s = lbl.getText();
        if (s == null || s.isEmpty()) return false;
        double w = lbl.getWidth();
        if (w <= 0) return false;
        Insets ins = lbl.getInsets();
        double avail = w - (ins == null ? 0.0 : (ins.getLeft() + ins.getRight())) - 2.0;
        if (avail <= 0) return false;
        Text t = new Text(s);
        t.setFont(lbl.getFont());
        return t.getLayoutBounds().getWidth() > avail + 0.5;
    }

    private static int getIconSizeFromUserData(Button b, int fallback) {
        if (b == null) return fallback;
        Object ud = b.getUserData();
        if (ud == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(ud).trim());
        } catch (Exception ignore) {
            return fallback;
        }
    }

    private static String safeTrim(String v) {
        return v == null ? "" : v.trim();
    }
}
