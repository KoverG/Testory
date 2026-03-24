package app.domain.cycles.ui.right;

import app.core.I18n;
import app.domain.cycles.CycleCategoryStore;
import app.domain.cycles.CyclePrivateConfig;
import app.domain.cycles.repo.CycleCardJsonReader;
import app.domain.cycles.repo.CaseHistoryIndexStore;
import app.domain.cycles.repo.FileCycleRepository;
import app.domain.cycles.ui.CycleRunControls;
import app.domain.cycles.ui.CyclesViewRefs;
import app.domain.cycles.usecase.CreateCycleUseCase;
import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.cycles.usecase.CycleDraft;
import app.domain.cycles.usecase.CycleRunState;
import app.domain.testcases.ui.RightPaneAnimator;
import app.domain.testcases.ui.TestCaseCyclesAccessory;
import app.domain.testcases.ui.TestCaseOverlayHost;
import app.ui.UiSaveFeedback;
import app.ui.UiSvg;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class RightPaneCoordinator {

    private static final String I18N_NO_CASES = "cy.right.cases.none";
    private static final String I18N_COPY_TITLE_SUFFIX = "cy.copy.suffix";

    private static final String DISABLED_BASE_CLASS = "tc-disabled-base";
    private static final String SAVE_HINT_INSTALLED_KEY = "tc.save.hint.installed";
    private static final double RUN_CONTROLS_WIDTH = 225.0;
    private static final double REPORT_CAPSULE_WIDTH = 180.0;
    private static final double REPORT_CHIP_WIDTH = 84.0;

    private final CyclesViewRefs v;
    private final RightPaneAnimator anim;

    private boolean open = false;
    private Runnable onClose;

    private Runnable onAddCases;

    private Runnable onSaved;

    private Runnable onUiStateChanged;

    // Callback after delete
    private Runnable onDeleted;

    private AddedCasesListUi addedCasesList;
    private CycleCategoryAutocomplete categoryAutocomplete;
    private TestCaseOverlayHost testcaseOverlay;
    private boolean testcaseOverlayHadCycleUnderlay = false;
    private String returnCaseIdFromHistory = "";
    private Path returnCycleFileFromHistory = null;
    private TestCaseCyclesAccessory.CurrentCycleContext returnCaseContextFromHistory = null;

    // Delete mode for added cases rows
    private boolean casesDeleteMode = false;
    private boolean editMode = false;

    private final List<CycleCaseRef> selectedCases = new ArrayList<>();

    private String currentRunState = CycleRunState.IDLE;
    private long currentRunElapsedSeconds = 0L;
    private String currentRunStartedAtIso = "";
    private CycleRunControls cycleRunControls;
    private javafx.scene.layout.HBox reportActionCapsule;
    private Button btnReportRight;

    private final FileCycleRepository repo = new FileCycleRepository();
    private final CaseHistoryIndexStore caseHistoryIndexStore = new CaseHistoryIndexStore();
    private final CreateCycleUseCase createUseCase = new CreateCycleUseCase(repo);

    private Path openedFile = null;
    private CycleDraft openedDraft = null;

    private UiSaveFeedback.Handle saveFx;

    // ===================== PROFILE MODAL =====================
    private ProfileModal profileModal;

    private String currentQaResponsible = "";
    private String baselineQaResponsible = "";

    // ===================== ENVIRONMENT =====================
    private String currentEnvType = "";          // "desktop" | "mobile" | ""
    private String currentEnvUrl = "";           // Builds field value
    private List<String> currentEnvLinks = new ArrayList<>();

    private String baselineEnvType = "";
    private String baselineEnvUrl = "";
    private List<String> baselineEnvLinks = List.of();

    // Save gate

    private Label saveDisabledHintLabel;
    private String lastSaveBlockMessage = "closed";

    private String baselineTitle = "";
    private String baselineCategory = "";
    private List<String> baselineCaseIds = List.of();
    private List<CycleCaseRef> baselineCases = List.of();

    //
    private String baselineTaskLinkTitle = "";
    private String baselineTaskLinkUrl = "";

    public RightPaneCoordinator(CyclesViewRefs v) {
        this.v = v;
        this.anim = new RightPaneAnimator(v.rightRoot);
        this.anim.setDx(RightPaneAnimator.DEFAULT_DX);
        this.anim.setMs(RightPaneAnimator.DEFAULT_MS);
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void setOnAddCases(Runnable onAddCases) {
        this.onAddCases = onAddCases;
    }

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void setOnUiStateChanged(Runnable onUiStateChanged) {
        this.onUiStateChanged = onUiStateChanged;
    }

    public void setOnDeleted(Runnable onDeleted) {
        this.onDeleted = onDeleted;
    }

    public void init() {
        snapClosed();

        //
        if (v.btnMenuRight != null) {
            v.btnMenuRight.install(v.rightRoot, this::hideDeleteConfirm);

            UiSvg.setButtonSvg(v.btnMenuRight, "menu.svg", getIconSizeFromUserData(v.btnMenuRight, 14));
            v.btnMenuRight.setFocusTraversable(false);
            v.btnMenuRight.setOnCopyAction(this::copyCurrentCycle);
            v.btnMenuRight.setRunActions(
                    this::togglePrimaryCycleRunAction,
                    this::togglePauseResumeCycleRun,
                    this::resetCycleRun
            );
            v.btnMenuRight.setOnEdit(this::toggleEditModeFromMenu);
            v.btnMenuRight.setOnSource(this::openCurrentCycleSourceFile);
            v.btnMenuRight.setOnDelete(this::showDeleteConfirmIfAllowed);
        }

        if (v.btnCloseRight != null) {
            UiSvg.setButtonSvg(v.btnCloseRight, "close.svg", 14);
            v.btnCloseRight.setFocusTraversable(false);
            v.btnCloseRight.setOnAction(e -> close());
        }

        //
        if (v.btnProfileRight != null) {
            UiSvg.setButtonSvg(v.btnProfileRight, "profile.svg", getIconSizeFromUserData(v.btnProfileRight, 14));
            v.btnProfileRight.setFocusTraversable(false);

            profileModal = new ProfileModal(v.btnProfileRight);
            profileModal.install(
                    v.rightRoot,
                    () -> {
                        if (testcaseOverlay != null && testcaseOverlay.isOpen()) testcaseOverlay.close();
        if (v.btnMenuRight != null) v.btnMenuRight.closeMenu();
                        hideDeleteConfirm();
                        //
                        if (v.chipTaskLink != null) v.chipTaskLink.closeModalIfOpen();
                        //
                        if (v.chipEnvironment != null) v.chipEnvironment.closeModalIfOpen();
                    },
                    () -> currentQaResponsible,
                    name -> {
                        currentQaResponsible = safe(name);
                        updateProfileTooltip();
                        updateSaveGateUi();
                    }
            );

            updateProfileTooltip();
        }

        if (v.btnRightAddCases != null) {
            v.btnRightAddCases.setFocusTraversable(false);
            v.btnRightAddCases.setOnAction(e -> {
                if (onAddCases != null) onAddCases.run();
            });
        }

        //
        if (v.btnRightTrashCases != null) {
            UiSvg.setButtonSvg(v.btnRightTrashCases, "trash.svg", getIconSizeFromUserData(v.btnRightTrashCases, 14));
            v.btnRightTrashCases.setFocusTraversable(false);
            v.btnRightTrashCases.setTooltip(new Tooltip(I18n.t("tc.trash.delete")));
            v.btnRightTrashCases.setOnAction(e -> toggleCasesDeleteMode());
        }

        //
        initRightDelete();
        installCycleRunControls();

        // SAVE
        if (v.btnSaveRight != null) {
            v.btnSaveRight.setFocusTraversable(false);
            v.btnSaveRight.setMinWidth(RUN_CONTROLS_WIDTH);
            v.btnSaveRight.setPrefWidth(RUN_CONTROLS_WIDTH);
            v.btnSaveRight.setMaxWidth(RUN_CONTROLS_WIDTH);

            installSaveDisabledHintUnderButton();
            updateCycleRunControlsAnchor();
            v.btnSaveRight.disabledProperty().addListener((o, oldV, newV) -> updateSaveDisabledHint());
            updateSaveDisabledHint();

            saveFx = UiSaveFeedback.install(
                    v.btnSaveRight,
                    Duration.millis(1000),
                    Duration.millis(900)
            );
            saveFx.reset();

            v.btnSaveRight.setOnAction(e -> onSave());

            setLastSaveBlockMessage("closed");
            setSaveEnabled(false);
            updateSaveDisabledHint();
        }

        if (v.tfCycleTitle != null) {
            v.tfCycleTitle.textProperty().addListener((o, oldV, newV) -> updateSaveGateUi());
        }
        if (v.tfCycleCategory != null) {
            v.tfCycleCategory.textProperty().addListener((o, oldV, newV) -> onCycleCategoryInputChanged());
        }

        categoryAutocomplete = new CycleCategoryAutocomplete(v.tfCycleCategory, v.lblCycleCategoryDisplay, v.lblCycleCategoryGhost, v.fpCycleCategorySuggestions);
        categoryAutocomplete.setOnValueChanged(this::onCycleCategoryInputChanged);
        categoryAutocomplete.init();
        categoryAutocomplete.setAvailableValues(CycleCategoryStore.loadAll());

        if (v.btnAddCycleCategory != null) {
            v.btnAddCycleCategory.setFocusTraversable(false);
            v.btnAddCycleCategory.setOnAction(e -> onCategoryAction());
            updateCategoryActionButton();
        }

        addedCasesList = new AddedCasesListUi(v);
        addedCasesList.init();
        addedCasesList.setOnDeleteCase(ref -> {
            if (ref == null) return;
            removeAddedCaseById(ref.safeId());
        });
        addedCasesList.setOnOpenCase(ref -> {
            if (ref == null) return;
            openTestCaseCard(ref.safeId());
        });
        addedCasesList.setOnStatusChanged((ref, status) -> updateAddedCaseStatus(ref, status));
        addedCasesList.setOnCommentChanged((ref, comment) -> updateAddedCaseComment(ref, comment));
        addedCasesList.setDeleteMode(false);
        syncEditModeUi();

        if (v.floatingOverlayRoot != null) {
            testcaseOverlay = new TestCaseOverlayHost(v.floatingOverlayRoot);
            testcaseOverlay.bindToWidth(Bindings.max(0.0, v.root.widthProperty().subtract(v.leftStack.widthProperty()).subtract(42.0)));
            testcaseOverlay.setOnSaved(() -> {
                if (onSaved != null) onSaved.run();
            });
            testcaseOverlay.setOnDeleted(id -> {
                removeAddedCaseById(id);
                if (onSaved != null) onSaved.run();
            });
            testcaseOverlay.setOnHistoryCycleOpenRequested(this::openCycleFromOpenedCaseHistory);
            testcaseOverlay.setOnVisibilityChanged(this::syncOverlayVisibilityState);
        }

        //
        if (v.chipTaskLink != null) {
            v.chipTaskLink.setTaskLink("", "");

            //
            if (v.rightRoot instanceof StackPane sp) {
                v.chipTaskLink.install(
                        sp,
                        () -> {
                            if (testcaseOverlay != null && testcaseOverlay.isOpen()) testcaseOverlay.close();
        if (v.btnMenuRight != null) v.btnMenuRight.closeMenu();
                            hideDeleteConfirm();
                            //
                            if (profileModal != null) profileModal.close();
                            //
                            if (v.chipEnvironment != null) v.chipEnvironment.closeModalIfOpen();
                        },
                        //
                        null
                );
            }

            //
            v.chipTaskLink.setOnTaskLinkChanged(this::updateSaveGateUi);
        }

        //
        if (v.chipEnvironment != null) {
            if (v.rightRoot instanceof StackPane sp) {
                v.chipEnvironment.setCurrentSuppliers(
                        () -> "mobile".equalsIgnoreCase(safe(currentEnvType)),
                        () -> safe(currentEnvUrl),
                        () -> new ArrayList<>(currentEnvLinks),
                        () -> !safe(currentEnvType).isBlank()
                );

                v.chipEnvironment.install(
                        sp,
                        () -> {
                            if (testcaseOverlay != null && testcaseOverlay.isOpen()) testcaseOverlay.close();
        if (v.btnMenuRight != null) v.btnMenuRight.closeMenu();
                            hideDeleteConfirm();
                            if (profileModal != null) profileModal.close();
                            if (v.chipTaskLink != null) v.chipTaskLink.closeModalIfOpen();
                        }
                );

                v.chipEnvironment.setOnSaved(st -> {
                    currentEnvType = st.mobile ? "mobile" : "desktop";
                    currentEnvUrl = safe(st.value);
                    currentEnvLinks = new ArrayList<>(st.links);
                    updateSaveGateUi();
                });
            }
        }

        syncAddedCasesUi();
        updateSaveGateUi();

        refreshMenuAvailability();
    }

    private void updateProfileTooltip() {
        if (v.btnProfileRight == null) return;

        String n = safe(currentQaResponsible);
        if (n.isEmpty()) {
            v.btnProfileRight.setTooltip(null);
            return;
        }

        try {
            v.btnProfileRight.setTooltip(new Tooltip(n));
        } catch (Exception ignore) {
            // ignore
        }
    }

    public String openedCycleId() {
        if (!open) return "";
        if (openedDraft == null) return "";
        return safe(openedDraft.id);
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isEditModeEnabled() {
        return open && editMode;
    }

    private boolean canEditAddedCaseDetails() {
        return open && (editMode || CycleRunState.isRunning(currentRunState));
    }

    public void openTestCaseCard(String caseId) {
        openTestCaseCard(caseId, buildCurrentCycleContext(caseId));
    }

    public void openCycleFromHistory(String cycleId, String sourceCaseId) {
        openCycleFromHistory(cycleId, sourceCaseId, null, null);
    }

    private void openCycleFromHistory(String cycleId, String sourceCaseId, Path sourceCycleFile, TestCaseCyclesAccessory.CurrentCycleContext sourceCaseContext) {
        String targetCycleId = safe(cycleId);
        if (targetCycleId.isEmpty()) return;

        Path file = repo.rootDir().resolve(targetCycleId + ".json");
        if (!Files.exists(file)) return;

        openExistingCard(file, sourceCaseId, sourceCycleFile, sourceCaseContext);
    }

    private void openCycleFromOpenedCaseHistory(String cycleId) {
        if (testcaseOverlay == null || !testcaseOverlay.isOpen()) return;

        String sourceCaseId = safe(testcaseOverlay.openedCaseId());
        if (sourceCaseId.isEmpty()) return;

        openCycleFromHistory(cycleId, sourceCaseId, openedFile, testcaseOverlay.currentCycleContext());
    }

    public void openTestCaseCardFromList(String caseId) {
        openTestCaseCardFromList(caseId, List.of());
    }

    public void openTestCaseCardFromList(String caseId, List<String> orderedCaseIds) {
        openTestCaseCard(caseId, buildPickerPreviewCycleContext(caseId, orderedCaseIds));
    }
    public boolean isPickerPreviewCaseOpen(String caseId) {
        if (testcaseOverlay == null || !testcaseOverlay.isOpen()) return false;

        String id = safe(caseId);
        if (id.isEmpty() || !id.equals(testcaseOverlay.openedCaseId())) return false;

        TestCaseCyclesAccessory.CurrentCycleContext context = testcaseOverlay.currentCycleContext();
        return context != null && context.mode() == TestCaseCyclesAccessory.CurrentCycleMode.PICKER_PREVIEW;
    }

    public void closePickerPreviewCaseCard() {
        if (testcaseOverlay == null || !testcaseOverlay.isOpen()) return;

        TestCaseCyclesAccessory.CurrentCycleContext context = testcaseOverlay.currentCycleContext();
        if (context != null && context.mode() == TestCaseCyclesAccessory.CurrentCycleMode.PICKER_PREVIEW) {
            testcaseOverlay.close();
        }
    }

    private void openTestCaseCard(String caseId, TestCaseCyclesAccessory.CurrentCycleContext cycleContext) {
        if (testcaseOverlay == null) return;

        String id = safe(caseId);
        if (id.isEmpty()) return;

        if (testcaseOverlay.isOpen() && id.equals(testcaseOverlay.openedCaseId())) {
            testcaseOverlay.close();
            return;
        }

        testcaseOverlayHadCycleUnderlay = open && v.rightRoot != null && v.rightRoot.isVisible() && v.rightRoot.isManaged();

        if (v.btnMenuRight != null) v.btnMenuRight.closeMenu();
        hideDeleteConfirm();
        if (profileModal != null) profileModal.close();
        if (v.chipTaskLink != null) v.chipTaskLink.closeModalIfOpen();
        if (v.chipEnvironment != null) v.chipEnvironment.closeModalIfOpen();

        testcaseOverlay.openExisting(id, cycleContext);
    }

    private TestCaseCyclesAccessory.CurrentCycleContext buildCurrentCycleContext(String caseId) {
        return buildCycleContext(caseId, TestCaseCyclesAccessory.CurrentCycleMode.ADDED_CASE, getAddedCaseIds());
    }

    private TestCaseCyclesAccessory.CurrentCycleContext buildPickerPreviewCycleContext(String caseId, List<String> orderedCaseIds) {
        return buildCycleContext(caseId, TestCaseCyclesAccessory.CurrentCycleMode.PICKER_PREVIEW, orderedCaseIds);
    }

    private TestCaseCyclesAccessory.CurrentCycleContext buildCycleContext(
            String caseId,
            TestCaseCyclesAccessory.CurrentCycleMode mode,
            List<String> orderedCaseIds
    ) {
        String id = safe(caseId);
        if (id.isEmpty()) return null;

        List<String> navigationIds = sanitizeNavigationCaseIds(orderedCaseIds, id);
        String cycleId = openedDraft == null ? "" : safe(openedDraft.id);
        String cycleTitle = safe(v.tfCycleTitle == null ? "" : v.tfCycleTitle.getText());
        String createdAt = safe(v.lblCycleCreatedAt == null ? "" : v.lblCycleCreatedAt.getText());
        String status = "";
        String comment = "";
        int caseNumber = indexOfCaseId(navigationIds, id) + 1;
        int caseTotal = navigationIds.size();

        if (mode == TestCaseCyclesAccessory.CurrentCycleMode.ADDED_CASE) {
            for (CycleCaseRef ref : selectedCases) {
                if (ref == null || !id.equals(ref.safeId())) continue;
                status = ref.safeStatus();
                comment = ref.safeComment();
                break;
            }
        }

        Runnable navigatePrev = caseNumber > 1 ? () -> openAdjacentCycleCase(id, navigationIds, mode, -1) : null;
        Runnable navigateNext = caseNumber > 0 && caseNumber < caseTotal ? () -> openAdjacentCycleCase(id, navigationIds, mode, 1) : null;

        return new TestCaseCyclesAccessory.CurrentCycleContext(
                mode,
                cycleId,
                cycleTitle,
                createdAt,
                status,
                comment,
                caseNumber,
                caseTotal,
                List.copyOf(navigationIds),
                currentRunState,
                resolveCurrentElapsedSeconds(),
                currentRunStartedAtIso,
                mode == TestCaseCyclesAccessory.CurrentCycleMode.ADDED_CASE && canEditAddedCaseDetails(),
                mode == TestCaseCyclesAccessory.CurrentCycleMode.ADDED_CASE
                        ? newStatus -> updateAddedCaseStatus(new CycleCaseRef(id, ""), newStatus)
                        : null,
                mode == TestCaseCyclesAccessory.CurrentCycleMode.ADDED_CASE
                        ? newComment -> updateAddedCaseComment(new CycleCaseRef(id, ""), newComment)
                        : null,
                mode == TestCaseCyclesAccessory.CurrentCycleMode.ADDED_CASE ? this::onSave : null,
                mode == TestCaseCyclesAccessory.CurrentCycleMode.ADDED_CASE ? this::togglePrimaryCycleRunAction : null,
                mode == TestCaseCyclesAccessory.CurrentCycleMode.ADDED_CASE ? this::togglePauseResumeCycleRun : null,
                mode == TestCaseCyclesAccessory.CurrentCycleMode.ADDED_CASE ? this::resetCycleRun : null,
                navigatePrev,
                navigateNext
        );
    }

    private static List<String> sanitizeNavigationCaseIds(List<String> orderedCaseIds, String currentCaseId) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();

        if (orderedCaseIds != null) {
            for (String rawId : orderedCaseIds) {
                String id = safe(rawId);
                if (!id.isEmpty()) ids.add(id);
            }
        }

        String currentId = safe(currentCaseId);
        if (!currentId.isEmpty()) ids.add(currentId);

        return new ArrayList<>(ids);
    }

    private static int indexOfCaseId(List<String> orderedCaseIds, String caseId) {
        if (orderedCaseIds == null || orderedCaseIds.isEmpty()) return -1;

        String targetId = safe(caseId);
        for (int i = 0; i < orderedCaseIds.size(); i++) {
            if (targetId.equals(safe(orderedCaseIds.get(i)))) return i;
        }
        return -1;
    }


    private void installCycleRunControls() {
        if (v.btnSaveRight == null || cycleRunControls != null) return;

        Parent parent = v.btnSaveRight.getParent();
        if (!(parent instanceof Pane pane)) return;

        cycleRunControls = new CycleRunControls();
        cycleRunControls.setVisible(false);
        pane.getChildren().add(cycleRunControls.node());

        if (pane instanceof StackPane) {
            updateCycleRunControlsAnchor();
        }
    }

    private void updateCycleRunControlsAnchor() {
        if (cycleRunControls == null || v.btnSaveRight == null) return;
        Parent parent = cycleRunControls.node().getParent();
        if (!(parent instanceof StackPane)) return;

        Insets saveMargin = StackPane.getMargin(v.btnSaveRight);
        double bottom = saveMargin == null ? 18.0 : saveMargin.getBottom();
        double hintHeight = 0.0;
        if (saveDisabledHintLabel != null) {
            hintHeight = Math.max(0.0, saveDisabledHintLabel.prefHeight(-1));
        }

        StackPane.setAlignment(cycleRunControls.node(), Pos.BOTTOM_LEFT);
        StackPane.setMargin(cycleRunControls.node(), new Insets(0, 0, bottom + hintHeight + 4.0, 28));
        updateReportActionAnchor();
    }

    private void syncCycleRunControls() {
        boolean available = open && openedFile != null;

        if (cycleRunControls != null) {
            cycleRunControls.setActions(
                    available ? this::togglePrimaryCycleRunAction : null,
                    available ? this::togglePauseResumeCycleRun : null,
                    available ? this::resetCycleRun : null
            );
            cycleRunControls.update(
                    currentRunState,
                    resolveCurrentElapsedSeconds(),
                    currentRunStartedAtIso,
                    available
            );
        }

        if (v.btnMenuRight != null) {
            v.btnMenuRight.setRunContext(
                    currentRunState,
                    available,
                    resolveCurrentElapsedSeconds(),
                    currentRunStartedAtIso
            );
            v.btnMenuRight.setReportEnabled(canShowReportAction());
        }

        updateReportActionVisibility();
    }

    private void refreshTestcaseOverlayRunContext() {
        if (testcaseOverlay == null || !testcaseOverlay.isOpen()) return;

        String caseId = testcaseOverlay.openedCaseId();
        if (caseId == null || caseId.isBlank()) return;

        TestCaseCyclesAccessory.CurrentCycleContext currentContext = testcaseOverlay.currentCycleContext();
        if (currentContext == null) {
            testcaseOverlay.refreshCurrentCycleContext(null);
            return;
        }

        testcaseOverlay.refreshCurrentCycleContext(
                buildCycleContext(caseId, currentContext.mode(), currentContext.navigationCaseIds())
        );
    }
    private void applyRunStateFromDraft(CycleDraft draft) {
        if (draft == null) {
            currentRunState = CycleRunState.IDLE;
            currentRunElapsedSeconds = 0L;
            currentRunStartedAtIso = "";
            return;
        }
        currentRunState = CycleRunState.normalize(draft.runState);
        currentRunElapsedSeconds = Math.max(0L, draft.runElapsedSeconds);
        currentRunStartedAtIso = safe(draft.runStartedAtIso);
    }

    private void applyRunStateToDraft(CycleDraft draft) {
        if (draft == null) return;
        draft.runState = CycleRunState.normalize(currentRunState);
        draft.runElapsedSeconds = Math.max(0L, currentRunElapsedSeconds);
        draft.runStartedAtIso = safe(currentRunStartedAtIso);
    }

    private long resolveCurrentElapsedSeconds() {
        if (!CycleRunState.isRunning(currentRunState)) {
            return Math.max(0L, currentRunElapsedSeconds);
        }

        LocalDateTime startedAt = parseRunStartedAt(currentRunStartedAtIso);
        if (startedAt == null) return Math.max(0L, currentRunElapsedSeconds);

        long delta = Math.max(0L, java.time.Duration.between(startedAt, LocalDateTime.now()).getSeconds());
        return Math.max(0L, currentRunElapsedSeconds + delta);
    }

    private static LocalDateTime parseRunStartedAt(String iso) {
        try {
            if (iso == null || iso.isBlank()) return null;
            return LocalDateTime.parse(iso.trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private CycleDraft buildDraftForRunPersist() {
        CycleDraft base = openedDraft;
        if (base == null && openedFile != null && Files.exists(openedFile)) {
            base = CycleCardJsonReader.readDraft(openedFile);
        }
        if (base == null) {
            base = buildDraftFromUi();
        }

        CycleDraft copy = new CycleDraft();
        copy.id = safe(base.id);
        copy.createdAtIso = safe(base.createdAtIso);
        copy.savedAtIso = safe(base.savedAtIso);
        copy.createdAtUi = safe(base.createdAtUi);
        copy.title = safe(base.title);
        copy.category = safe(base.category);
        copy.qaResponsible = safe(base.qaResponsible);
        copy.taskLinkTitle = safe(base.taskLinkTitle);
        copy.taskLinkUrl = safe(base.taskLinkUrl);
        copy.envType = safe(base.envType);
        copy.envUrl = safe(base.envUrl);
        copy.envLinks = base.envLinks == null ? new ArrayList<>() : new ArrayList<>(base.envLinks);
        copy.cases = new ArrayList<>();
        if (base.cases != null) {
            for (CycleCaseRef ref : base.cases) {
                if (ref == null) continue;
                copy.cases.add(new CycleCaseRef(ref.safeId(), ref.safeTitleSnapshot(), ref.safeStatus(), ref.safeComment(), ref.safeStatusChangedAtIso()));
            }
        }
        applyRunStateToDraft(copy);
        return copy;
    }

    private boolean persistRunState() {
        if (openedFile == null) return false;

        CycleDraft draft = buildDraftForRunPersist();
        Path saved = createUseCase.update(openedFile, draft);
        openedFile = saved;
        openedDraft = draft;
        if (onSaved != null) onSaved.run();
        return true;
    }

    private void togglePrimaryCycleRunAction() {
        if (openedFile == null) return;

        if (CycleRunState.isActive(currentRunState)) {
            finishCycleRun();
            return;
        }

        startCycleRun();
    }

    private void startCycleRun() {
        if (openedFile == null) return;

        currentRunState = CycleRunState.RUNNING;
        currentRunElapsedSeconds = 0L;
        currentRunStartedAtIso = CycleDraft.nowIso();
        if (!persistRunState()) return;

        syncAddedCasesUi();
        syncCycleRunControls();
        refreshTestcaseOverlayRunContext();
    }

    private void finishCycleRun() {
        if (openedFile == null) return;

        currentRunElapsedSeconds = resolveCurrentElapsedSeconds();
        currentRunState = CycleRunState.FINISHED;
        currentRunStartedAtIso = "";
        if (!persistRunState()) return;

        syncAddedCasesUi();
        syncCycleRunControls();
        refreshTestcaseOverlayRunContext();
    }

    private void togglePauseResumeCycleRun() {
        if (openedFile == null) return;

        if (CycleRunState.isRunning(currentRunState)) {
            currentRunElapsedSeconds = resolveCurrentElapsedSeconds();
            currentRunState = CycleRunState.PAUSED;
            currentRunStartedAtIso = "";
        } else if (CycleRunState.isPaused(currentRunState)) {
            currentRunState = CycleRunState.RUNNING;
            currentRunStartedAtIso = CycleDraft.nowIso();
        } else {
            return;
        }

        if (!persistRunState()) return;
        syncAddedCasesUi();
        syncCycleRunControls();
        refreshTestcaseOverlayRunContext();
    }

    private void resetCycleRun() {
        if (openedFile == null) return;

        currentRunState = CycleRunState.IDLE;
        currentRunElapsedSeconds = 0L;
        currentRunStartedAtIso = "";
        if (!persistRunState()) return;

        syncAddedCasesUi();
        syncCycleRunControls();
        refreshTestcaseOverlayRunContext();
    }
    private void openAdjacentCycleCase(
            String caseId,
            List<String> orderedCaseIds,
            TestCaseCyclesAccessory.CurrentCycleMode mode,
            int offset
    ) {
        String currentId = safe(caseId);
        if (currentId.isEmpty() || offset == 0 || testcaseOverlay == null) return;

        List<String> navigationIds = sanitizeNavigationCaseIds(orderedCaseIds, currentId);
        int currentIndex = indexOfCaseId(navigationIds, currentId);
        if (currentIndex < 0) return;

        int targetIndex = currentIndex + offset;
        if (targetIndex < 0 || targetIndex >= navigationIds.size()) return;

        String targetId = safe(navigationIds.get(targetIndex));
        if (targetId.isEmpty()) return;

        testcaseOverlay.openExisting(targetId, buildCycleContext(targetId, mode, navigationIds));
    }
    public void openExistingCard(Path file) {
        openExistingCard(file, "", null, null);
    }

    private void openExistingCard(Path file, String sourceCaseId, Path sourceCycleFile, TestCaseCyclesAccessory.CurrentCycleContext sourceCaseContext) {
        if (file == null) return;

        //
        closePrimaryModals();
        resetCasesDeleteMode();
        returnCaseIdFromHistory = safe(sourceCaseId);
        returnCycleFileFromHistory = sourceCycleFile;
        returnCaseContextFromHistory = sourceCaseContext;

        if (!Files.exists(file)) {
            openCreateCard();
            return;
        }

        boolean wasOpen = open;

        CycleDraft d = CycleCardJsonReader.readDraft(file);
        if (d == null) d = new CycleDraft();

        openedFile = file;
        openedDraft = d;
        editMode = false;
        applyRunStateFromDraft(d);

        if (v.lblCycleCreatedAt != null) {
            String created = safe(d.createdAtUi);
            if (created.isEmpty()) created = "-";
            v.lblCycleCreatedAt.setText(created);
        }

        if (v.tfCycleTitle != null) {
            v.tfCycleTitle.setText(safe(d.title));
        }
        if (categoryAutocomplete != null) {
            categoryAutocomplete.syncFromValue(safe(d.category));
        }

        currentQaResponsible = safe(d.qaResponsible);
        updateProfileTooltip();

        if (v.chipTaskLink != null) {
            v.chipTaskLink.setTaskLink(safe(d.taskLinkTitle), safe(d.taskLinkUrl));
        }

        applyEnvironmentFromDraftOrRemembered(d);

        selectedCases.clear();
        if (d.cases != null) {
            for (CycleCaseRef ref : d.cases) {
                if (ref == null) continue;
                String id = ref.safeId();
                if (id.isEmpty()) continue;
                selectedCases.add(new CycleCaseRef(id, ref.safeTitleSnapshot(), ref.safeStatus(), ref.safeComment(), ref.safeStatusChangedAtIso()));
            }
        }
        syncAddedCasesUi();
        syncEditModeUi();
        syncCycleRunControls();

        captureBaselineFromCurrentUi();
        setLastSaveBlockMessage("dirty");
        setSaveEnabled(false);
        updateSaveDisabledHint();

        if (!wasOpen) {
            open = true;
            anim.show(
                    () -> {
                        v.rightRoot.setVisible(true);
                        v.rightRoot.setManaged(true);
                    },
                    null
            );
        } else {
            if (v.rightRoot != null) {
                v.rightRoot.setVisible(true);
                v.rightRoot.setManaged(true);
            }
            anim.pulseReplace();
        }

        refreshDeleteAvailability();
        refreshMenuAvailability();
        syncCycleRunControls();

        updateSaveGateUi();
    }

    public void openCreateCard() {
        boolean wasOpen = open;

        //
        closePrimaryModals();
        resetCasesDeleteMode();

        open = true;
        returnCaseIdFromHistory = "";
        returnCycleFileFromHistory = null;
        returnCaseContextFromHistory = null;

        openedFile = null;
        openedDraft = null;
        editMode = true;
        currentRunState = CycleRunState.IDLE;
        currentRunElapsedSeconds = 0L;
        currentRunStartedAtIso = "";

        currentQaResponsible = "";
        updateProfileTooltip();

        if (v.lblCycleCreatedAt != null) {
            Locale locale = Locale.forLanguageTag(I18n.lang());
            String created = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy", locale));
            v.lblCycleCreatedAt.setText(created);
        }

        if (v.tfCycleTitle != null) {
            v.tfCycleTitle.setText("");
        }
        if (categoryAutocomplete != null) {
            categoryAutocomplete.syncFromValue("");
        }

        if (v.chipTaskLink != null) {
            v.chipTaskLink.setTaskLink("", "");
        }

        applyEnvironmentFromDraftOrRemembered(null);

        selectedCases.clear();
        syncAddedCasesUi();
        syncEditModeUi();
        syncCycleRunControls();

        captureBaselineFromCurrentUi();
        setLastSaveBlockMessage("dirty");
        setSaveEnabled(false);
        updateSaveDisabledHint();

        if (!wasOpen) {
            anim.show(
                    () -> {
                        v.rightRoot.setVisible(true);
                        v.rightRoot.setManaged(true);
                    },
                    null
            );
        } else {
            if (v.rightRoot != null) {
                v.rightRoot.setVisible(true);
                v.rightRoot.setManaged(true);
            }
            anim.pulseReplace();
        }

        refreshDeleteAvailability();
        refreshMenuAvailability();

        updateSaveGateUi();
    }

    public void open() {
        openCreateCard();
    }

    public void close() {
        if (!open) return;
        String returnCaseId = safe(returnCaseIdFromHistory);
        Path returnCycleFile = returnCycleFileFromHistory;
        TestCaseCyclesAccessory.CurrentCycleContext returnCaseContext = returnCaseContextFromHistory;
        open = false;
        testcaseOverlayHadCycleUnderlay = false;
        returnCaseIdFromHistory = "";
        returnCycleFileFromHistory = null;
        returnCaseContextFromHistory = null;

        if (testcaseOverlay != null && testcaseOverlay.isOpen()) testcaseOverlay.close();
        if (v.btnMenuRight != null) v.btnMenuRight.closeMenu();
        hideDeleteConfirm();

        if (profileModal != null) profileModal.close();
        if (v.chipTaskLink != null) v.chipTaskLink.closeModalIfOpen();
        if (v.chipEnvironment != null) v.chipEnvironment.closeModalIfOpen();

        openedFile = null;
        openedDraft = null;
        currentRunState = CycleRunState.IDLE;
        currentRunElapsedSeconds = 0L;
        currentRunStartedAtIso = "";
        editMode = false;
        syncEditModeUi();
        syncCycleRunControls();

        currentQaResponsible = "";
        updateProfileTooltip();

        setLastSaveBlockMessage("closed");
        setSaveEnabled(false);
        updateSaveDisabledHint();

        resetCasesDeleteMode();

        refreshDeleteAvailability();
        refreshMenuAvailability();

        anim.hide(
                null,
                () -> {
                    v.rightRoot.setVisible(false);
                    v.rightRoot.setManaged(false);
                    syncOverlayVisibilityState();
                    if (!returnCaseId.isBlank()) {
                        restoreCaseHistoryOrigin(returnCycleFile, returnCaseId, returnCaseContext);
                        return;
                    }
                    if (onClose != null) onClose.run();
                }
        );
    }

    public void snapClosed() {
        open = false;
        testcaseOverlayHadCycleUnderlay = false;
        returnCaseIdFromHistory = "";
        returnCycleFileFromHistory = null;
        returnCaseContextFromHistory = null;

        if (testcaseOverlay != null && testcaseOverlay.isOpen()) testcaseOverlay.close();
        if (v.btnMenuRight != null) v.btnMenuRight.closeMenu();
        hideDeleteConfirm();

        if (profileModal != null) profileModal.close();
        if (v.chipTaskLink != null) v.chipTaskLink.closeModalIfOpen();
        if (v.chipEnvironment != null) v.chipEnvironment.closeModalIfOpen();

        openedFile = null;
        openedDraft = null;
        currentRunState = CycleRunState.IDLE;
        currentRunElapsedSeconds = 0L;
        currentRunStartedAtIso = "";
        editMode = false;
        syncEditModeUi();
        syncCycleRunControls();

        currentQaResponsible = "";
        updateProfileTooltip();

        setLastSaveBlockMessage("closed");
        setSaveEnabled(false);
        updateSaveDisabledHint();

        resetCasesDeleteMode();

        refreshDeleteAvailability();
        refreshMenuAvailability();

        if (v.rightRoot == null) return;

        v.rightRoot.setTranslateX(0.0);
        v.rightRoot.setOpacity(1.0);
        v.rightRoot.setMouseTransparent(false);
        v.rightRoot.setVisible(false);
        v.rightRoot.setManaged(false);
        syncOverlayVisibilityState();
    }

    private void restoreCaseHistoryOrigin(
            Path cycleFile,
            String caseId,
            TestCaseCyclesAccessory.CurrentCycleContext caseContext
    ) {
        String returnCaseId = safe(caseId);
        if (returnCaseId.isEmpty() || testcaseOverlay == null) return;

        if (cycleFile != null && Files.exists(cycleFile)) {
            openExistingCard(cycleFile, "", null, null);
            TestCaseCyclesAccessory.CurrentCycleContext restoredContext = caseContext == null
                    ? buildCurrentCycleContext(returnCaseId)
                    : buildCycleContext(returnCaseId, caseContext.mode(), caseContext.navigationCaseIds());
            testcaseOverlay.openExisting(returnCaseId, restoredContext);
            return;
        }

        testcaseOverlayHadCycleUnderlay = false;
        testcaseOverlay.openExisting(returnCaseId, caseContext);
        syncOverlayVisibilityState();
    }

    private void refreshMenuAvailability() {
        if (v.btnMenuRight == null) return;

        boolean can = open && openedFile != null && Files.exists(openedFile);

        v.btnMenuRight.setVisible(can);
        v.btnMenuRight.setManaged(can);
        v.btnMenuRight.setEditEnabled(can);
        v.btnMenuRight.setSourceEnabled(can);

        if (!can) {
            v.btnMenuRight.closeMenu();
        }
    }

    public void setAddedCases(List<CycleCaseRef> cases) {
        if (cases == null) cases = List.of();

        selectedCases.clear();
        for (CycleCaseRef ref : cases) {
            if (ref == null) continue;

            String id = ref.safeId();
            if (id.isEmpty()) continue;

            String title = ref.safeTitleSnapshot();
            selectedCases.add(new CycleCaseRef(id, title, ref.safeStatus(), ref.safeComment(), ref.safeStatusChangedAtIso()));
        }

        syncAddedCasesUi();
        updateSaveGateUi();
    }

    public List<String> getAddedCaseIds() {
        List<String> out = new ArrayList<>();
        for (CycleCaseRef ref : selectedCases) {
            if (ref == null) continue;
            String id = ref.safeId();
            if (id.isEmpty()) continue;
            out.add(id);
        }
        return out;
    }

    public boolean hasAddedCase(String idRaw) {
        String id = safe(idRaw);
        if (id.isEmpty()) return false;

        for (CycleCaseRef ref : selectedCases) {
            if (ref == null) continue;
            if (id.equals(ref.safeId())) return true;
        }
        return false;
    }

    public void addAddedCases(List<CycleCaseRef> toAdd) {
        if (toAdd == null || toAdd.isEmpty()) return;

        for (CycleCaseRef ref : toAdd) {
            if (ref == null) continue;

            String id = ref.safeId();
            if (id.isEmpty()) continue;

            if (hasAddedCase(id)) continue;

            String addedAtIso = ref.safeStatusChangedAtIso();
            if (addedAtIso.isBlank()) addedAtIso = CycleDraft.nowIso();
            selectedCases.add(new CycleCaseRef(id, ref.safeTitleSnapshot(), ref.safeStatus(), ref.safeComment(), addedAtIso));
        }

        syncAddedCasesUi();
        updateSaveGateUi();
    }

    public void removeAddedCasesByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;

        List<String> norm = new ArrayList<>();
        for (String s : ids) {
            String id = safe(s);
            if (!id.isEmpty()) norm.add(id);
        }
        if (norm.isEmpty()) return;

        selectedCases.removeIf(ref -> {
            if (ref == null) return true;
            String id = ref.safeId();
            if (id.isEmpty()) return true;

            for (String r : norm) {
                if (r.equals(id)) return true;
            }
            return false;
        });

        syncAddedCasesUi();
        updateSaveGateUi();
    }

    //
    private void removeAddedCaseById(String idRaw) {
        String id = safe(idRaw);
        if (id.isEmpty()) return;

        boolean removed = selectedCases.removeIf(ref -> ref == null || id.equals(ref.safeId()));
        if (!removed) return;

        //
        if (selectedCases.isEmpty()) {
            casesDeleteMode = false;
        }

        syncAddedCasesUi();
        updateSaveGateUi();
    }

    //
    private void updateAddedCaseStatus(CycleCaseRef ref, String status) {
        if (ref == null) return;
        if (!canEditAddedCaseDetails()) return;

        String id = ref.safeId();
        if (id.isEmpty()) return;

        String normalizedStatus = safe(status);
        boolean changed = false;

        for (int i = 0; i < selectedCases.size(); i++) {
            CycleCaseRef current = selectedCases.get(i);
            if (current == null || !id.equals(current.safeId())) continue;

            String updatedTimestamp = current.safeStatusChangedAtIso();
            if (!current.safeStatus().equals(normalizedStatus)) {
                updatedTimestamp = CycleDraft.nowIso();
            }
            CycleCaseRef updated = new CycleCaseRef(
                    current.safeId(),
                    current.safeTitleSnapshot(),
                    normalizedStatus,
                    current.safeComment(),
                    updatedTimestamp
            );
            if (!sameCaseRef(current, updated)) {
                selectedCases.set(i, updated);
                changed = true;
            }
            break;
        }

        if (!changed) return;

        syncAddedCasesUi();
        updateSaveGateUi();
    }


    private void updateAddedCaseComment(CycleCaseRef ref, String comment) {
        if (ref == null) return;
        if (!canEditAddedCaseDetails()) return;

        String id = ref.safeId();
        if (id.isEmpty()) return;

        String normalizedComment = safe(comment);
        boolean changed = false;

        for (int i = 0; i < selectedCases.size(); i++) {
            CycleCaseRef current = selectedCases.get(i);
            if (current == null || !id.equals(current.safeId())) continue;

            CycleCaseRef updated = new CycleCaseRef(
                    current.safeId(),
                    current.safeTitleSnapshot(),
                    current.safeStatus(),
                    normalizedComment,
                    current.safeStatusChangedAtIso()
            );
            if (!sameCaseRef(current, updated)) {
                selectedCases.set(i, updated);
                changed = true;
            }
            break;
        }

        if (!changed) return;

        syncAddedCasesUi();
        updateSaveGateUi();
    }

    private void toggleCasesDeleteMode() {
        if (!open) return;

        //
        if (selectedCases.isEmpty()) {
            casesDeleteMode = false;
            if (addedCasesList != null) addedCasesList.setDeleteMode(false);
            syncAddedCasesUi();
            return;
        }

        casesDeleteMode = !casesDeleteMode;

        if (addedCasesList != null) {
            addedCasesList.setDeleteMode(casesDeleteMode);
        }
    }

    private void resetCasesDeleteMode() {
        casesDeleteMode = false;
        if (addedCasesList != null) addedCasesList.setDeleteMode(false);
    }

    private void syncAddedCasesUi() {
        if (v.lblAddedCasesCount != null) {
            int n = selectedCases.size();
            if (n <= 0) {
                v.lblAddedCasesCount.setText(I18n.t(I18N_NO_CASES));
            } else {
                v.lblAddedCasesCount.setText(String.valueOf(n));
            }
        }

        if (addedCasesList != null) {
            addedCasesList.showCases(selectedCases);
            addedCasesList.setDeleteMode(casesDeleteMode);
            addedCasesList.setCaseEditAllowed(canEditAddedCaseDetails());
        }

        if (v.btnRightTrashCases != null) {
            v.btnRightTrashCases.setDisable(!editMode || selectedCases.isEmpty());
        }
    }

    private void initRightDelete() {
        if (v.btnDeleteCancel != null) {
            v.btnDeleteCancel.setFocusTraversable(false);
            v.btnDeleteCancel.setOnAction(e -> hideDeleteConfirm());
        }

        if (v.btnDeleteConfirm != null) {
            v.btnDeleteConfirm.setFocusTraversable(false);
            v.btnDeleteConfirm.setOnAction(e -> {
                hideDeleteConfirm();
                deleteCurrentCycleToTrash();
            });
        }

        if (v.deleteModal != null) {
            v.deleteModal.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> e.consume());
        }

        if (v.deleteLayer != null) {
            v.deleteLayer.setOnMouseClicked(e -> hideDeleteConfirm());
        }

        hideDeleteConfirm();
        refreshDeleteAvailability();
    }

    private void refreshDeleteAvailability() {
        if (!canDeleteCurrentCycle()) hideDeleteConfirm();
    }

    private void showDeleteConfirmIfAllowed() {
        refreshDeleteAvailability();
        if (!canDeleteCurrentCycle()) return;

        if (testcaseOverlay != null && testcaseOverlay.isOpen()) testcaseOverlay.close();
        if (v.btnMenuRight != null) v.btnMenuRight.closeMenu();
        if (profileModal != null) profileModal.close();
        if (v.chipTaskLink != null) v.chipTaskLink.closeModalIfOpen();
        if (v.chipEnvironment != null) v.chipEnvironment.closeModalIfOpen();

        if (v.deleteLayer != null) {
            v.deleteLayer.setVisible(true);
            v.deleteLayer.setManaged(true);
        }
    }

    private void hideDeleteConfirm() {
        if (v.deleteLayer != null) {
            v.deleteLayer.setVisible(false);
            v.deleteLayer.setManaged(false);
        }
    }
    private void toggleEditModeFromMenu() {
        if (!editMode) {
            enterEditMode();
            return;
        }

        boolean noPendingChanges = v.btnSaveRight == null || v.btnSaveRight.isDisabled();
        if (noPendingChanges) {
            exitEditMode();
            if (v.btnMenuRight != null) v.btnMenuRight.closeMenu();
            return;
        }

        if (v.btnMenuRight != null) {
            v.btnMenuRight.showEditApplyHint();
        }
    }


    private void enterEditMode() {
        if (!open || openedFile == null || !Files.exists(openedFile)) return;
        editMode = true;
        if (v.btnMenuRight != null) v.btnMenuRight.closeMenu();
        syncEditModeUi();
        if (v.tfCycleTitle != null) {
            v.tfCycleTitle.requestFocus();
            v.tfCycleTitle.end();
        }
    }

    private void exitEditMode() {
        editMode = false;
        resetCasesDeleteMode();
        syncEditModeUi();
    }

    private void syncEditModeUi() {
        if (v.tfCycleTitle != null) {
            v.tfCycleTitle.setEditable(editMode);
            v.tfCycleTitle.setFocusTraversable(editMode);
            v.tfCycleTitle.setMouseTransparent(!editMode);
        }
        if (v.tfCycleCategory != null) {
            v.tfCycleCategory.setEditable(editMode);
            v.tfCycleCategory.setFocusTraversable(editMode);
            v.tfCycleCategory.setMouseTransparent(!editMode);
        }
        if (categoryAutocomplete != null) {
            categoryAutocomplete.setEditable(editMode);
        }
        if (v.btnRightAddCases != null) {
            v.btnRightAddCases.setDisable(!editMode);
        }
        if (v.btnAddCycleCategory != null) {
            v.btnAddCycleCategory.setDisable(!open);
        }
        updateCategoryActionButton();
        if (v.btnRightTrashCases != null) {
            v.btnRightTrashCases.setDisable(!editMode || selectedCases.isEmpty());
        }
        if (v.btnMenuRight != null) {
            v.btnMenuRight.setEditActive(editMode);
        }
        if (addedCasesList != null) {
            addedCasesList.setCaseEditAllowed(canEditAddedCaseDetails());
        }
        if (onUiStateChanged != null) onUiStateChanged.run();
    }



    private void onCycleCategoryInputChanged() {
        updateCategoryActionButton();
        updateSaveGateUi();
    }

    private void onCategoryAction() {
        if (isCycleCategoryInputEmpty()) {
            openCycleCategoryStoreFile();
            return;
        }
        addCycleCategoryTemplate();
    }

    private void updateCategoryActionButton() {
        if (v.btnAddCycleCategory == null) return;

        boolean empty = isCycleCategoryInputEmpty();
        boolean existing = !empty
                && categoryAutocomplete != null
                && categoryAutocomplete.containsExactValue(v.tfCycleCategory == null ? "" : v.tfCycleCategory.getText());

        boolean showButton = empty || !existing;
        v.btnAddCycleCategory.setVisible(showButton);
        v.btnAddCycleCategory.setManaged(showButton);

        if (showButton) {
            String icon = empty ? "folder.svg" : "plus.svg";
            UiSvg.setButtonSvg(v.btnAddCycleCategory, icon, getIconSizeFromUserData(v.btnAddCycleCategory, 14));
        }
    }

    private boolean isCycleCategoryInputEmpty() {
        return safe(v.tfCycleCategory == null ? "" : v.tfCycleCategory.getText()).isEmpty();
    }

    private void openCycleCategoryStoreFile() {
        if (!Desktop.isDesktopSupported()) return;

        try {
            CycleCategoryStore.loadAll();
            Path file = Path.of("config", "cycle-categories.json");
            if (!Files.exists(file)) return;
            Desktop.getDesktop().open(file.toFile());
        } catch (Exception ignore) {
            // ignore
        }
    }
    private void addCycleCategoryTemplate() {
        if (!editMode || categoryAutocomplete == null) return;

        String value = categoryAutocomplete.commitCurrentValue();
        if (value.isEmpty()) return;

        CycleCategoryStore.add(value);
        categoryAutocomplete.rememberValue(value);
        updateSaveGateUi();
    }
    private boolean canDeleteCurrentCycle() {
        return open && openedFile != null && Files.exists(openedFile);
    }

    private boolean canShowReportAction() {
        return open
                && openedFile != null
                && Files.exists(openedFile)
                && CycleRunState.isFinished(currentRunState);
    }

    private void openCurrentCycleSourceFile() {
        if (openedFile == null || !Files.exists(openedFile)) return;
        if (!Desktop.isDesktopSupported()) return;

        try {
            Desktop.getDesktop().open(openedFile.toFile());
        } catch (Exception ignore) {
        }
    }

    private void closePrimaryModals() {
        if (testcaseOverlay != null && testcaseOverlay.isOpen()) testcaseOverlay.close();
        if (v.btnMenuRight != null) v.btnMenuRight.closeMenu();
        hideDeleteConfirm();
        if (profileModal != null) profileModal.close();
        if (v.chipTaskLink != null) v.chipTaskLink.closeModalIfOpen();
        if (v.chipEnvironment != null) v.chipEnvironment.closeModalIfOpen();
    }

    private void syncOverlayVisibilityState() {
        boolean testcaseOpen = testcaseOverlay != null && testcaseOverlay.isOpen();

        if (v.floatingOverlayRoot != null) {
            v.floatingOverlayRoot.setVisible(testcaseOpen);
            v.floatingOverlayRoot.setManaged(testcaseOpen);
            v.floatingOverlayRoot.setMouseTransparent(!testcaseOpen);
        }

        if (v.rightRoot == null) return;

        if (testcaseOpen) {
            if (testcaseOverlayHadCycleUnderlay) {
                v.rightRoot.setVisible(true);
                v.rightRoot.setManaged(true);
                v.rightRoot.setOpacity(1.0);
                v.rightRoot.setMouseTransparent(false);
            } else {
                v.rightRoot.setVisible(false);
                v.rightRoot.setManaged(false);
                v.rightRoot.setOpacity(1.0);
                v.rightRoot.setMouseTransparent(false);
            }
            return;
        }

        if (testcaseOverlayHadCycleUnderlay && open) {
            v.rightRoot.setVisible(true);
            v.rightRoot.setManaged(true);
            v.rightRoot.setOpacity(1.0);
            v.rightRoot.setMouseTransparent(false);
            return;
        }

        if (!open) {
            v.rightRoot.setOpacity(1.0);
            v.rightRoot.setMouseTransparent(false);
            v.rightRoot.setVisible(false);
            v.rightRoot.setManaged(false);
            testcaseOverlayHadCycleUnderlay = false;
            return;
        }

        v.rightRoot.setOpacity(1.0);
        v.rightRoot.setMouseTransparent(false);
        v.rightRoot.setVisible(true);
        v.rightRoot.setManaged(true);
    }

    private void copyCurrentCycle() {
        if (!open) return;
        if (openedFile == null || openedDraft == null) return;
        if (!Files.exists(openedFile)) return;

        CycleDraft copy = buildDraftFromUi();
        copy.id = CycleDraft.newStableId();
        copy.createdAtIso = CycleDraft.nowIso();
        copy.savedAtIso = "";
        copy.createdAtUi = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag(I18n.lang())));
        copy.title = buildCopyTitle(copy.title);
        copy.runState = CycleRunState.IDLE;
        copy.runElapsedSeconds = 0L;
        copy.runStartedAtIso = "";

        Path saved = createUseCase.create(copy);
        openExistingCard(saved);
        if (onSaved != null) onSaved.run();
    }

    private static String buildCopyTitle(String title) {
        String base = safe(title);
        String suffix = safe(I18n.t(I18N_COPY_TITLE_SUFFIX));
        if (suffix.isEmpty()) suffix = " (copy)";
        if (base.isEmpty()) return suffix.trim();
        return base + suffix;
    }

    private void deleteCurrentCycleToTrash() {
        if (openedFile == null) return;
        if (!Files.exists(openedFile)) return;

        Path src = openedFile;
        Path parent = src.getParent();
        if (parent == null) return;

        Path trashDir = parent.resolve("_trash");

        try {
            Files.createDirectories(trashDir);

            Path target = trashDir.resolve(src.getFileName());
            if (Files.exists(target)) {
                String name = String.valueOf(src.getFileName());
                String base = name;
                String ext = "";
                int dot = name.lastIndexOf('.');
                if (dot > 0 && dot < name.length() - 1) {
                    base = name.substring(0, dot);
                    ext = name.substring(dot);
                }
                String suffix = "-" + System.currentTimeMillis();
                target = trashDir.resolve(base + suffix + ext);
            }

            Files.move(src, target, StandardCopyOption.ATOMIC_MOVE);

            caseHistoryIndexStore.removeCycle(readOpenedCycleId());
            close();
            if (onDeleted != null) onDeleted.run();

        } catch (IOException ex) {
            try {
                Path target = trashDir.resolve(src.getFileName());
                if (Files.exists(target)) {
                    String name = String.valueOf(src.getFileName());
                    String base = name;
                    String ext = "";
                    int dot = name.lastIndexOf('.');
                    if (dot > 0 && dot < name.length() - 1) {
                        base = name.substring(0, dot);
                        ext = name.substring(dot);
                    }
                    String suffix = "-" + System.currentTimeMillis();
                    target = trashDir.resolve(base + suffix + ext);
                }

                Files.move(src, target);

                caseHistoryIndexStore.removeCycle(readOpenedCycleId());
                close();
                if (onDeleted != null) onDeleted.run();

            } catch (IOException ex2) {
                System.out.println("[Cycles] delete failed: " + ex2.getMessage());
                refreshDeleteAvailability();
            }
        }
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

    private void onSave() {
        if (v.btnSaveRight != null && v.btnSaveRight.isDisabled()) {
            return;
        }

        if (saveFx != null) saveFx.start();

        CycleDraft draft = buildDraftFromUi();


        Path saved;
        if (openedFile != null) {
            saved = createUseCase.update(openedFile, draft);
            openedFile = saved;
            openedDraft = draft;
        } else {
            saved = createUseCase.create(draft);
            openedFile = saved;
            openedDraft = draft;
        }

        System.out.println("[Cycles] saved: " + saved.toAbsolutePath());
        caseHistoryIndexStore.upsertCycle(draft);

        if (saveFx != null) saveFx.success();

        CycleCategoryStore.add(draft.category);
        if (categoryAutocomplete != null) categoryAutocomplete.rememberValue(draft.category);

        captureBaselineFromCurrentUi();
        exitEditMode();

        updateSaveGateUi();
        refreshDeleteAvailability();
        refreshMenuAvailability();
        syncCycleRunControls();
        refreshTestcaseOverlayRunContext();

        if (onSaved != null) onSaved.run();
    }

    private String readOpenedCycleId() {
        if (openedDraft != null && !safe(openedDraft.id).isEmpty()) {
            return safe(openedDraft.id);
        }
        if (openedFile == null) return "";

        try {
            String name = String.valueOf(openedFile.getFileName());
            if (name.endsWith(".json")) name = name.substring(0, name.length() - 5);
            return safe(name);
        } catch (Exception ignore) {
            return "";
        }
    }

    private CycleDraft buildDraftFromUi() {
        final CycleDraft d;

        if (openedDraft != null) {
            d = new CycleDraft();
            d.id = safe(openedDraft.id);
            d.createdAtIso = safe(openedDraft.createdAtIso);
        } else {
            d = CycleDraft.newWithId();
        }

        if (v.tfCycleTitle != null) {
            d.title = safe(v.tfCycleTitle.getText());
        }
        if (v.tfCycleCategory != null) {
            d.category = safe(v.tfCycleCategory.getText());
        }

        if (v.lblCycleCreatedAt != null) {
            d.createdAtUi = safe(v.lblCycleCreatedAt.getText());
        }

        d.qaResponsible = safe(currentQaResponsible);

        if (v.chipTaskLink != null) {
            d.taskLinkTitle = safe(v.chipTaskLink.getTitle());
            d.taskLinkUrl = safe(v.chipTaskLink.getUrl());
        } else {
            d.taskLinkTitle = "";
            d.taskLinkUrl = "";
        }

        //
        d.envType = safe(currentEnvType);
        d.envUrl = safe(currentEnvUrl);
        d.envLinks = new ArrayList<>(currentEnvLinks);
        applyRunStateToDraft(d);

        d.cases = new ArrayList<>();
        for (CycleCaseRef ref : selectedCases) {
            if (ref == null) continue;
            d.cases.add(new CycleCaseRef(
                    ref.safeId(),
                    ref.safeTitleSnapshot(),
                    ref.safeStatus(),
                    ref.safeComment(),
                    resolveStatusChangedAtIsoForSave(ref)
            ));
        }

        return d;
    }


    private String resolveStatusChangedAtIsoForSave(CycleCaseRef ref) {
        if (ref == null) return "";

        String currentStatus = ref.safeStatus();
        String currentTimestamp = ref.safeStatusChangedAtIso();
        CycleCaseRef previous = findOpenedDraftCaseRef(ref.safeId());

        if (previous != null) {
            if (previous.safeStatus().equals(currentStatus)) {
                String previousTimestamp = previous.safeStatusChangedAtIso();
                if (!previousTimestamp.isBlank()) return previousTimestamp;
                if (!currentTimestamp.isBlank()) return currentTimestamp;
                return openedDraft == null ? "" : safe(openedDraft.savedAtIso);
            }
            return CycleDraft.nowIso();
        }

        return currentTimestamp.isBlank() ? CycleDraft.nowIso() : currentTimestamp;
    }

    private CycleCaseRef findOpenedDraftCaseRef(String caseId) {
        if (caseId == null || caseId.isBlank()) return null;
        if (openedDraft == null || openedDraft.cases == null || openedDraft.cases.isEmpty()) return null;

        for (CycleCaseRef ref : openedDraft.cases) {
            if (ref == null) continue;
            if (caseId.equals(ref.safeId())) return ref;
        }
        return null;
    }

    private void applyEnvironmentFromDraftOrRemembered(CycleDraft d) {
        String t = (d == null) ? "" : safe(d.envType);
        String u = (d == null) ? "" : safe(d.envUrl);
        List<String> links = (d == null || d.envLinks == null) ? List.of() : List.copyOf(d.envLinks);

        boolean hasEnv = !t.isBlank() || !u.isBlank() || !links.isEmpty();

        if (hasEnv) {
            currentEnvType = t;
            currentEnvUrl = u;
            currentEnvLinks = new ArrayList<>(links);
            return;
        }

        boolean remember = CyclePrivateConfig.rememberEnvEnabled();
        if (!remember) {
            currentEnvType = "";
            currentEnvUrl = "";
            currentEnvLinks = new ArrayList<>();
            return;
        }

        boolean mob = CyclePrivateConfig.rememberedEnvMobile();

        //
        currentEnvType = mob ? "mobile" : "desktop";
        currentEnvUrl = "";
        currentEnvLinks = new ArrayList<>();
    }

    private void updateSaveGateUi() {
        computeCanSaveAndApplyUi();
    }

    private boolean computeCanSaveAndApplyUi() {
        if (!open) {
            setLastSaveBlockMessage("closed");
            setSaveEnabled(false);
            updateSaveDisabledHint();
            return false;
        }

        boolean dirty = computeDirty();
        if (!dirty) {
            setLastSaveBlockMessage("dirty");
            setSaveEnabled(false);
            updateSaveDisabledHint();
            return false;
        }

        String title = safe(v.tfCycleTitle == null ? "" : v.tfCycleTitle.getText());
        if (title.isBlank()) {
            setLastSaveBlockMessage("fill.title");
            setSaveEnabled(false);
            updateSaveDisabledHint();
            return false;
        }

        String category = safe(v.tfCycleCategory == null ? "" : v.tfCycleCategory.getText());
        if (category.isBlank()) {
            setLastSaveBlockMessage("fill.category");
            setSaveEnabled(false);
            updateSaveDisabledHint();
            return false;
        }

        setLastSaveBlockMessage("");
        setSaveEnabled(true);
        updateSaveDisabledHint();
        return true;
    }

    private void setSaveEnabled(boolean enabled) {
        if (v.btnSaveRight == null) return;

        v.btnSaveRight.setDisable(!enabled);

        if (!enabled) {
            if (!v.btnSaveRight.getStyleClass().contains(DISABLED_BASE_CLASS)) {
                v.btnSaveRight.getStyleClass().add(DISABLED_BASE_CLASS);
            }
        } else {
            v.btnSaveRight.getStyleClass().remove(DISABLED_BASE_CLASS);
        }
    }

    private void setLastSaveBlockMessage(String msg) {
        lastSaveBlockMessage = (msg == null) ? "" : msg.trim();
    }

    private void captureBaselineFromCurrentUi() {
        baselineTitle = safe(v.tfCycleTitle == null ? "" : v.tfCycleTitle.getText());
        baselineCategory = safe(v.tfCycleCategory == null ? "" : v.tfCycleCategory.getText());
        baselineCaseIds = List.copyOf(getAddedCaseIds());
        baselineCases = snapshotCases(selectedCases);

        baselineTaskLinkTitle = safe(v.chipTaskLink == null ? "" : v.chipTaskLink.getTitle());
        baselineTaskLinkUrl = safe(v.chipTaskLink == null ? "" : v.chipTaskLink.getUrl());

        baselineEnvType = safe(currentEnvType);
        baselineEnvUrl = safe(currentEnvUrl);
        baselineEnvLinks = List.copyOf(currentEnvLinks);

        baselineQaResponsible = safe(currentQaResponsible);
    }

    private void captureBaselineEmptyForCreate() {
        baselineTitle = "";
        baselineCategory = "";
        baselineCaseIds = List.of();
        baselineCases = snapshotCases(selectedCases);

        baselineTaskLinkTitle = "";
        baselineTaskLinkUrl = "";

        baselineEnvType = safe(currentEnvType);
        baselineEnvUrl = safe(currentEnvUrl);
        baselineEnvLinks = List.copyOf(currentEnvLinks);

        baselineQaResponsible = safe(currentQaResponsible);
    }

    private boolean computeDirty() {
        String nowTitle = safe(v.tfCycleTitle == null ? "" : v.tfCycleTitle.getText());
        if (!nowTitle.equals(baselineTitle)) return true;

        String nowCategory = safe(v.tfCycleCategory == null ? "" : v.tfCycleCategory.getText());
        if (!nowCategory.equals(baselineCategory)) return true;

        String nowQa = safe(currentQaResponsible);
        if (!nowQa.equals(baselineQaResponsible)) return true;

        String nowTLTitle = safe(v.chipTaskLink == null ? "" : v.chipTaskLink.getTitle());
        String nowTLUrl = safe(v.chipTaskLink == null ? "" : v.chipTaskLink.getUrl());
        if (!nowTLTitle.equals(baselineTaskLinkTitle)) return true;
        if (!nowTLUrl.equals(baselineTaskLinkUrl)) return true;

        //
        if (!safe(currentEnvType).equals(baselineEnvType)) return true;
        if (!safe(currentEnvUrl).equals(baselineEnvUrl)) return true;
        if (!List.copyOf(currentEnvLinks).equals(baselineEnvLinks)) return true;

        List<String> nowIds = getAddedCaseIds();
        if (baselineCaseIds.size() != nowIds.size()) return true;

        for (int i = 0; i < baselineCaseIds.size(); i++) {
            if (!baselineCaseIds.get(i).equals(nowIds.get(i))) return true;
        }

        List<CycleCaseRef> nowCases = snapshotCases(selectedCases);
        if (baselineCases.size() != nowCases.size()) return true;
        for (int i = 0; i < baselineCases.size(); i++) {
            if (!sameCaseRef(baselineCases.get(i), nowCases.get(i))) return true;
        }
        return false;
    }

    private static List<CycleCaseRef> snapshotCases(List<CycleCaseRef> refs) {
        if (refs == null || refs.isEmpty()) return List.of();

        List<CycleCaseRef> out = new ArrayList<>();
        for (CycleCaseRef ref : refs) {
            if (ref == null) continue;

            String id = ref.safeId();
            if (id.isEmpty()) continue;

            out.add(new CycleCaseRef(id, ref.safeTitleSnapshot(), ref.safeStatus(), ref.safeComment(), ref.safeStatusChangedAtIso()));
        }
        return List.copyOf(out);
    }

    private static boolean sameCaseRef(CycleCaseRef left, CycleCaseRef right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        return left.safeId().equals(right.safeId())
                && left.safeTitleSnapshot().equals(right.safeTitleSnapshot())
                && left.safeStatus().equals(right.safeStatus())
                && left.safeComment().equals(right.safeComment())
                && left.safeStatusChangedAtIso().equals(right.safeStatusChangedAtIso());
    }

    private void installSaveDisabledHintUnderButton() {
        if (v.btnSaveRight == null) return;

        Object already = v.btnSaveRight.getProperties().get(SAVE_HINT_INSTALLED_KEY);
        if (already instanceof Boolean b && b) return;
        v.btnSaveRight.getProperties().put(SAVE_HINT_INSTALLED_KEY, Boolean.TRUE);

        saveDisabledHintLabel = new Label();
        saveDisabledHintLabel.getStyleClass().add("tc-save-hint");
        saveDisabledHintLabel.setWrapText(true);
        saveDisabledHintLabel.setMouseTransparent(true);

        saveDisabledHintLabel.setText(" ");
        saveDisabledHintLabel.setVisible(true);
        saveDisabledHintLabel.setManaged(true);
        saveDisabledHintLabel.setOpacity(0.0);

        double h = saveDisabledHintLabel.prefHeight(-1);
        if (h <= 0) h = 14;
        saveDisabledHintLabel.setMinHeight(h);
        saveDisabledHintLabel.setPrefHeight(h);
        saveDisabledHintLabel.setMaxHeight(h);

        saveDisabledHintLabel.setAlignment(Pos.CENTER);
        saveDisabledHintLabel.setTextAlignment(TextAlignment.CENTER);
        saveDisabledHintLabel.setMaxWidth(Double.MAX_VALUE);

        Parent p0 = v.btnSaveRight.getParent();
        if (p0 instanceof VBox vb) {
            vb.setAlignment(Pos.CENTER);
            vb.setFillWidth(true);
            vb.setPickOnBounds(false);

            if (!vb.getChildren().contains(saveDisabledHintLabel)) {
                vb.getChildren().add(saveDisabledHintLabel);
            }
            updateCycleRunControlsAnchor();
            return;
        }

        Parent parent = v.btnSaveRight.getParent();
        if (!(parent instanceof Pane pane)) return;

        int idx = pane.getChildren().indexOf(v.btnSaveRight);
        if (idx < 0) return;

        Insets spMargin = null;
        Pos spAlign = null;
        if (pane instanceof StackPane) {
            spMargin = StackPane.getMargin(v.btnSaveRight);
            spAlign = StackPane.getAlignment(v.btnSaveRight);
        }

        pane.getChildren().remove(v.btnSaveRight);

        ensureReportActionCreated();

        VBox wrap = new VBox(4.0);
        wrap.setAlignment(Pos.CENTER);
        wrap.setFillWidth(true);
        wrap.setPickOnBounds(false);
        wrap.setMinWidth(Region.USE_PREF_SIZE);
        wrap.setPrefWidth(Region.USE_COMPUTED_SIZE);
        wrap.setMaxWidth(Double.MAX_VALUE);

        wrap.getChildren().addAll(v.btnSaveRight, saveDisabledHintLabel);

        if (idx > pane.getChildren().size()) idx = pane.getChildren().size();
        pane.getChildren().add(idx, wrap);

        if (pane instanceof StackPane) {
            if (spAlign != null) StackPane.setAlignment(wrap, spAlign);
            if (spMargin != null) StackPane.setMargin(wrap, spMargin);
            if (!pane.getChildren().contains(reportActionCapsule)) {
                pane.getChildren().add(reportActionCapsule);
            }
        }
        updateCycleRunControlsAnchor();
    }

    private void updateSaveDisabledHint() {
        if (v.btnSaveRight == null) return;
        if (saveDisabledHintLabel == null) return;

        if (!v.btnSaveRight.isDisabled()) {
            saveDisabledHintLabel.setText(" ");
            saveDisabledHintLabel.setOpacity(0.0);
            return;
        }

        String reason = safe(lastSaveBlockMessage);
        if (reason.isEmpty()) {
            saveDisabledHintLabel.setText(" ");
            saveDisabledHintLabel.setOpacity(0.0);
            return;
        }

        String key = "tc.save.block." + reason;
        String text = I18n.t(key);

        if (text == null || text.trim().isEmpty() || text.equals(key)) {
            text = I18n.t("tc.save.block.unknown");
        }

        saveDisabledHintLabel.setText(text);
        saveDisabledHintLabel.setOpacity(1.0);
    }

    private void ensureReportActionCreated() {
        if (reportActionCapsule != null) return;

        reportActionCapsule = new javafx.scene.layout.HBox();
        reportActionCapsule.setAlignment(Pos.CENTER);
        reportActionCapsule.setPickOnBounds(false);
        reportActionCapsule.getStyleClass().add("cy-report-capsule");
        reportActionCapsule.setMinWidth(REPORT_CAPSULE_WIDTH);
        reportActionCapsule.setPrefWidth(REPORT_CAPSULE_WIDTH);
        reportActionCapsule.setMaxWidth(REPORT_CAPSULE_WIDTH);

        String reportText = localizedTextOrFallback("cy.menu.report", "Report");

        btnReportRight = new Button(reportText);
        btnReportRight.setFocusTraversable(false);
        btnReportRight.getStyleClass().add("cy-report-chip");
        btnReportRight.setMinWidth(REPORT_CHIP_WIDTH);
        btnReportRight.setPrefWidth(REPORT_CHIP_WIDTH);
        btnReportRight.setMaxWidth(REPORT_CHIP_WIDTH);
        btnReportRight.setTooltip(new Tooltip(reportText));
        btnReportRight.setOnAction(e -> { });

        reportActionCapsule.getChildren().add(btnReportRight);
        updateReportActionVisibility();
    }

    private void updateReportActionAnchor() {
        if (reportActionCapsule == null || v.btnSaveRight == null) return;
        Parent parent = reportActionCapsule.getParent();
        if (!(parent instanceof StackPane)) return;

        Insets saveMargin = StackPane.getMargin(v.btnSaveRight);
        double bottom = saveMargin == null ? 18.0 : saveMargin.getBottom();
        double hintHeight = 0.0;
        if (saveDisabledHintLabel != null) {
            hintHeight = Math.max(0.0, saveDisabledHintLabel.prefHeight(-1));
        }

        StackPane.setAlignment(reportActionCapsule, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(reportActionCapsule, new Insets(0, 28, bottom + hintHeight + 4.0, 0));
    }

    private void updateReportActionVisibility() {
        if (reportActionCapsule == null) return;

        boolean visible = canShowReportAction();

        reportActionCapsule.setVisible(visible);
        reportActionCapsule.setManaged(visible);
    }

    private String localizedTextOrFallback(String key, String fallback) {
        String value = safe(I18n.t(key));
        return value.isEmpty() || value.equals(key) || value.equals("!" + key + "!") ? fallback : value;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
















