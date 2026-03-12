package app.domain.cycles.ui.right;

import app.core.I18n;
import app.domain.cycles.CyclePrivateConfig;
import app.domain.cycles.repo.CycleCardJsonReader;
import app.domain.cycles.repo.FileCycleRepository;
import app.domain.cycles.ui.CyclesViewRefs;
import app.domain.cycles.usecase.CreateCycleUseCase;
import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.cycles.usecase.CycleDraft;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RightPaneCoordinator {

    private static final String I18N_NO_CASES = "cy.right.cases.none";
    private static final String I18N_COPY_TITLE_SUFFIX = "cy.copy.suffix";

    private static final String DISABLED_BASE_CLASS = "tc-disabled-base";
    private static final String SAVE_HINT_INSTALLED_KEY = "tc.save.hint.installed";

    private final CyclesViewRefs v;
    private final RightPaneAnimator anim;

    private boolean open = false;
    private Runnable onClose;

    private Runnable onAddCases;

    private Runnable onSaved;

    // РІСљвЂ¦ NEW: callback after delete
    private Runnable onDeleted;

    private AddedCasesListUi addedCasesList;
    private TestCaseOverlayHost testcaseOverlay;
    private boolean testcaseOverlayHadCycleUnderlay = false;

    // РІСљвЂ¦ NEW: delete mode for added cases rows
    private boolean casesDeleteMode = false;

    private final List<CycleCaseRef> selectedCases = new ArrayList<>();

    private final FileCycleRepository repo = new FileCycleRepository();
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

    // ===================== SAVE GATE (Р С”Р В°Р С” TestCases) =====================

    private Label saveDisabledHintLabel;
    private String lastSaveBlockMessage = "closed";

    private String baselineTitle = "";
    private List<String> baselineCaseIds = List.of();
    private List<CycleCaseRef> baselineCases = List.of();

    // РІСљвЂ¦ NEW: task link baseline participates in dirty detection
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

    public void setOnDeleted(Runnable onDeleted) {
        this.onDeleted = onDeleted;
    }

    public void init() {
        snapClosed();

        // РІСљвЂ¦ MENU: Р С”Р Р…Р С•Р С—Р С”Р В° РЎРѓР В°Р СР В° Р Т‘Р ВµРЎР‚Р В¶Р С‘РЎвЂљ/РЎР‚Р С‘РЎРѓРЎС“Р ВµРЎвЂљ Р СР С•Р Т‘Р В°Р В»Р С”РЎС“ Р С‘ overlay
        if (v.btnMenuRight != null) {
            v.btnMenuRight.install(v.rightRoot, this::hideDeleteConfirm);

            UiSvg.setButtonSvg(v.btnMenuRight, "menu.svg", getIconSizeFromUserData(v.btnMenuRight, 14));
            v.btnMenuRight.setFocusTraversable(false);
            v.btnMenuRight.setOnCopyAction(this::copyCurrentCycle);
        }

        if (v.btnCloseRight != null) {
            UiSvg.setButtonSvg(v.btnCloseRight, "close.svg", 14);
            v.btnCloseRight.setFocusTraversable(false);
            v.btnCloseRight.setOnAction(e -> close());
        }

        // РІСљвЂ¦ PROFILE BUTTON + MODAL
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
                        // Р В·Р В°Р С”РЎР‚РЎвЂ№Р Р†Р В°Р ВµР С Р СР С•Р Т‘Р В°Р В»Р С”РЎС“ TaskLink, Р ВµРЎРѓР В»Р С‘ Р С•Р Р…Р В° Р В±РЎвЂ№Р В»Р В° Р С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљР В°
                        if (v.chipTaskLink != null) v.chipTaskLink.closeModalIfOpen();
                        // Р В·Р В°Р С”РЎР‚РЎвЂ№Р Р†Р В°Р ВµР С Р СР С•Р Т‘Р В°Р В»Р С”РЎС“ Environment, Р ВµРЎРѓР В»Р С‘ Р С•Р Р…Р В° Р В±РЎвЂ№Р В»Р В° Р С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљР В°
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

        // РІСљвЂ¦ NEW: top trash button toggles delete mode
        if (v.btnRightTrashCases != null) {
            UiSvg.setButtonSvg(v.btnRightTrashCases, "trash.svg", getIconSizeFromUserData(v.btnRightTrashCases, 14));
            v.btnRightTrashCases.setFocusTraversable(false);
            v.btnRightTrashCases.setTooltip(new Tooltip(I18n.t("tc.trash.delete")));
            v.btnRightTrashCases.setOnAction(e -> toggleCasesDeleteMode());
        }

        // РІСљвЂ¦ DELETE: svg + open confirm overlay (Р С”Р В°Р С” TestCases)
        initRightDelete();

        // SAVE
        if (v.btnSaveRight != null) {
            v.btnSaveRight.setFocusTraversable(false);

            installSaveDisabledHintUnderButton();
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
            testcaseOverlay.setOnVisibilityChanged(this::syncOverlayVisibilityState);
        }

        // РІСљвЂ¦ init task link chip
        if (v.chipTaskLink != null) {
            v.chipTaskLink.setTaskLink("", "");

            // РІСљвЂ¦ IMPORTANT: install overlay root for modal
            if (v.rightRoot instanceof StackPane sp) {
                v.chipTaskLink.install(
                        sp,
                        () -> {
                            if (testcaseOverlay != null && testcaseOverlay.isOpen()) testcaseOverlay.close();
        if (v.btnMenuRight != null) v.btnMenuRight.closeMenu();
                            hideDeleteConfirm();
                            // Р В·Р В°Р С”РЎР‚РЎвЂ№Р Р†Р В°Р ВµР С Р С—РЎР‚Р С•РЎвЂћР С‘Р В»РЎРЉ-Р СР С•Р Т‘Р В°Р В»Р С”РЎС“, Р ВµРЎРѓР В»Р С‘ Р В±РЎвЂ№Р В»Р В° Р С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљР В°
                            if (profileModal != null) profileModal.close();
                            // Р В·Р В°Р С”РЎР‚РЎвЂ№Р Р†Р В°Р ВµР С env-Р СР С•Р Т‘Р В°Р В»Р С”РЎС“, Р ВµРЎРѓР В»Р С‘ Р В±РЎвЂ№Р В»Р В° Р С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљР В°
                            if (v.chipEnvironment != null) v.chipEnvironment.closeModalIfOpen();
                        },
                        // РІСљвЂ¦ must NOT anchor to menu button; modal anchors to chip
                        null
                );
            }

            // РІСљвЂ¦ any change inside chip should participate in save-gate
            v.chipTaskLink.setOnTaskLinkChanged(this::updateSaveGateUi);
        }

        // РІСљвЂ¦ init environment chip (RightAnchoredModal) + binding to state
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

    public void openTestCaseCard(String caseId) {
        openTestCaseCard(caseId, true);
    }

    public void openTestCaseCardFromList(String caseId) {
        openTestCaseCard(caseId, false);
    }

    private void openTestCaseCard(String caseId, boolean showCurrentCycleStatus) {
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

        testcaseOverlay.openExisting(id, showCurrentCycleStatus ? buildCurrentCycleContext(id) : null);
    }

    private TestCaseCyclesAccessory.CurrentCycleContext buildCurrentCycleContext(String caseId) {
        String id = safe(caseId);
        String cycleId = openedDraft == null ? "" : safe(openedDraft.id);
        String cycleTitle = safe(v.tfCycleTitle == null ? "" : v.tfCycleTitle.getText());
        String createdAt = safe(v.lblCycleCreatedAt == null ? "" : v.lblCycleCreatedAt.getText());
        String status = "";
        String comment = "";
        int caseNumber = 0;
        int caseTotal = selectedCases.size();

        for (int i = 0; i < selectedCases.size(); i++) {
            CycleCaseRef ref = selectedCases.get(i);
            if (ref == null || !id.equals(ref.safeId())) continue;
            status = ref.safeStatus();
            comment = ref.safeComment();
            caseNumber = i + 1;
            break;
        }

        Runnable navigatePrev = caseNumber > 1 ? () -> openAdjacentCycleCase(id, -1) : null;
        Runnable navigateNext = caseNumber > 0 && caseNumber < caseTotal ? () -> openAdjacentCycleCase(id, 1) : null;

        return new TestCaseCyclesAccessory.CurrentCycleContext(
                cycleId,
                cycleTitle,
                createdAt,
                status,
                comment,
                caseNumber,
                caseTotal,
                newStatus -> updateAddedCaseStatus(new CycleCaseRef(id, ""), newStatus),
                newComment -> updateAddedCaseComment(new CycleCaseRef(id, ""), newComment),
                this::onSave,
                navigatePrev,
                navigateNext
        );
    }

    private void openAdjacentCycleCase(String caseId, int offset) {
        String currentId = safe(caseId);
        if (currentId.isEmpty() || offset == 0 || testcaseOverlay == null) return;

        for (int i = 0; i < selectedCases.size(); i++) {
            CycleCaseRef ref = selectedCases.get(i);
            if (ref == null || !currentId.equals(ref.safeId())) continue;

            int targetIndex = i + offset;
            if (targetIndex < 0 || targetIndex >= selectedCases.size()) return;

            CycleCaseRef target = selectedCases.get(targetIndex);
            if (target == null || target.safeId().isEmpty()) return;

            String targetId = target.safeId();
            testcaseOverlay.openExisting(targetId, buildCurrentCycleContext(targetId));
            return;
        }
    }

    public void openExistingCard(Path file) {
        if (file == null) return;

        // РІСљвЂ¦ switching card => close primary modals from previous card
        closePrimaryModals();
        resetCasesDeleteMode();

        if (!Files.exists(file)) {
            openCreateCard();
            return;
        }

        boolean wasOpen = open;

        CycleDraft d = CycleCardJsonReader.readDraft(file);
        if (d == null) d = new CycleDraft();

        openedFile = file;
        openedDraft = d;

        if (v.lblCycleCreatedAt != null) {
            String created = safe(d.createdAtUi);
            if (created.isEmpty()) created = "РІР‚вЂќ";
            v.lblCycleCreatedAt.setText(created);
        }

        if (v.tfCycleTitle != null) {
            v.tfCycleTitle.setText(safe(d.title));
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
                selectedCases.add(new CycleCaseRef(id, ref.safeTitleSnapshot(), ref.safeStatus(), ref.safeComment()));
            }
        }
        syncAddedCasesUi();

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

        updateSaveGateUi();
    }

    public void openCreateCard() {
        boolean wasOpen = open;

        // РІСљвЂ¦ switching to create-card => close primary modals from previous card
        closePrimaryModals();
        resetCasesDeleteMode();

        open = true;

        openedFile = null;
        openedDraft = null;

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

        if (v.chipTaskLink != null) {
            v.chipTaskLink.setTaskLink("", "");
        }

        applyEnvironmentFromDraftOrRemembered(null);

        selectedCases.clear();
        syncAddedCasesUi();

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
        open = false;
        testcaseOverlayHadCycleUnderlay = false;

        if (testcaseOverlay != null && testcaseOverlay.isOpen()) testcaseOverlay.close();
        if (v.btnMenuRight != null) v.btnMenuRight.closeMenu();
        hideDeleteConfirm();

        if (profileModal != null) profileModal.close();
        if (v.chipTaskLink != null) v.chipTaskLink.closeModalIfOpen();
        if (v.chipEnvironment != null) v.chipEnvironment.closeModalIfOpen();

        openedFile = null;
        openedDraft = null;

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
                    if (onClose != null) onClose.run();
                }
        );
    }

    public void snapClosed() {
        open = false;
        testcaseOverlayHadCycleUnderlay = false;

        if (testcaseOverlay != null && testcaseOverlay.isOpen()) testcaseOverlay.close();
        if (v.btnMenuRight != null) v.btnMenuRight.closeMenu();
        hideDeleteConfirm();

        if (profileModal != null) profileModal.close();
        if (v.chipTaskLink != null) v.chipTaskLink.closeModalIfOpen();
        if (v.chipEnvironment != null) v.chipEnvironment.closeModalIfOpen();

        openedFile = null;
        openedDraft = null;

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

    private void refreshMenuAvailability() {
        if (v.btnMenuRight == null) return;

        boolean can = open && openedFile != null && Files.exists(openedFile);

        v.btnMenuRight.setVisible(can);
        v.btnMenuRight.setManaged(can);

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
            selectedCases.add(new CycleCaseRef(id, title, ref.safeStatus(), ref.safeComment()));
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

            selectedCases.add(new CycleCaseRef(id, ref.safeTitleSnapshot(), ref.safeStatus(), ref.safeComment()));
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

    // РІСљвЂ¦ NEW: delete single case by id (row trash button)
    private void removeAddedCaseById(String idRaw) {
        String id = safe(idRaw);
        if (id.isEmpty()) return;

        boolean removed = selectedCases.removeIf(ref -> ref == null || id.equals(ref.safeId()));
        if (!removed) return;

        // Р ВµРЎРѓР В»Р С‘ РЎРѓР С—Р С‘РЎРѓР С•Р С” РЎРѓРЎвЂљР В°Р В» Р С—РЎС“РЎРѓРЎвЂљРЎвЂ№Р С РІР‚вЂќ Р Р†РЎвЂ№РЎвЂ¦Р С•Р Т‘Р С‘Р С Р С‘Р В· delete-mode
        if (selectedCases.isEmpty()) {
            casesDeleteMode = false;
        }

        syncAddedCasesUi();
        updateSaveGateUi();
    }

    // РІСљвЂ¦ NEW: toggle delete-mode (top trash button)
    private void updateAddedCaseStatus(CycleCaseRef ref, String status) {
        if (ref == null) return;

        String id = ref.safeId();
        if (id.isEmpty()) return;

        String normalizedStatus = safe(status);
        boolean changed = false;

        for (int i = 0; i < selectedCases.size(); i++) {
            CycleCaseRef current = selectedCases.get(i);
            if (current == null || !id.equals(current.safeId())) continue;

            CycleCaseRef updated = new CycleCaseRef(current.safeId(), current.safeTitleSnapshot(), normalizedStatus, current.safeComment());
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
                    normalizedComment
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

        // Р ВµРЎРѓР В»Р С‘ РЎРѓР С—Р С‘РЎРѓР С•Р С” Р С—РЎС“РЎРѓРЎвЂљ РІР‚вЂќ Р С—РЎР‚Р С•РЎРѓРЎвЂљР С• Р С‘Р С–Р Р…Р С•РЎР‚
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
        }

        if (v.btnRightTrashCases != null) {
            v.btnRightTrashCases.setDisable(selectedCases.isEmpty());
        }
    }

    private void initRightDelete() {
        if (v.btnDeleteRight != null) {
            UiSvg.setButtonSvg(v.btnDeleteRight, "trash.svg", getIconSizeFromUserData(v.btnDeleteRight, 10));
            v.btnDeleteRight.setFocusTraversable(false);
            v.btnDeleteRight.setOnAction(e -> showDeleteConfirmIfAllowed());
        }

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
        if (v.btnDeleteRight == null) return;

        boolean can = open && openedFile != null && Files.exists(openedFile);

        v.btnDeleteRight.setVisible(can);
        v.btnDeleteRight.setManaged(can);

        if (!can) hideDeleteConfirm();
    }

    private void showDeleteConfirmIfAllowed() {
        refreshDeleteAvailability();
        if (v.btnDeleteRight == null || !v.btnDeleteRight.isVisible()) return;

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

        if (saveFx != null) saveFx.success();

        captureBaselineFromCurrentUi();

        updateSaveGateUi();
        refreshDeleteAvailability();
        refreshMenuAvailability();

        if (onSaved != null) onSaved.run();
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

        // РІСљвЂ¦ env persistence
        d.envType = safe(currentEnvType);
        d.envUrl = safe(currentEnvUrl);
        d.envLinks = new ArrayList<>(currentEnvLinks);

        d.cases = new ArrayList<>(selectedCases);

        return d;
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

        // РІСљвЂ¦ Remembered env keeps ONLY type (mobile/desktop). Builds value must be empty.
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

        String nowQa = safe(currentQaResponsible);
        if (!nowQa.equals(baselineQaResponsible)) return true;

        String nowTLTitle = safe(v.chipTaskLink == null ? "" : v.chipTaskLink.getTitle());
        String nowTLUrl = safe(v.chipTaskLink == null ? "" : v.chipTaskLink.getUrl());
        if (!nowTLTitle.equals(baselineTaskLinkTitle)) return true;
        if (!nowTLUrl.equals(baselineTaskLinkUrl)) return true;

        // РІСљвЂ¦ env dirty detection (toggle+builds+links)
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

            out.add(new CycleCaseRef(id, ref.safeTitleSnapshot(), ref.safeStatus(), ref.safeComment()));
        }
        return List.copyOf(out);
    }

    private static boolean sameCaseRef(CycleCaseRef left, CycleCaseRef right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        return left.safeId().equals(right.safeId())
                && left.safeTitleSnapshot().equals(right.safeTitleSnapshot())
                && left.safeStatus().equals(right.safeStatus())
                && left.safeComment().equals(right.safeComment());
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

        VBox wrap = new VBox(4.0);
        wrap.setAlignment(Pos.CENTER);
        wrap.setFillWidth(true);

        wrap.setPickOnBounds(false);

        wrap.setMinWidth(Region.USE_PREF_SIZE);
        wrap.setPrefWidth(Region.USE_COMPUTED_SIZE);
        wrap.setMaxWidth(Region.USE_PREF_SIZE);

        wrap.getChildren().addAll(v.btnSaveRight, saveDisabledHintLabel);

        if (idx > pane.getChildren().size()) idx = pane.getChildren().size();
        pane.getChildren().add(idx, wrap);

        if (pane instanceof StackPane) {
            if (spAlign != null) StackPane.setAlignment(wrap, spAlign);
            if (spMargin != null) StackPane.setMargin(wrap, spMargin);
        }
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

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
















