// FILE: src/main/java/app/domain/cycles/ui/left/LeftPaneCoordinator.java
package app.domain.cycles.ui.left;

import app.core.I18n;
import app.domain.cycles.ui.CyclesViewRefs;
import app.domain.cycles.ui.list.CaseListItem;
import app.domain.cycles.ui.list.CycleListItem;
import app.domain.cycles.ui.list.ListPresenter;
import app.domain.cycles.ui.right.RightPaneCoordinator;
import app.domain.testcases.TestCase;
import app.domain.testcases.repo.TestCaseCardStore;
import app.ui.UiSvg;
import app.ui.confirm.LeftDeleteConfirm;
import app.domain.cycles.ui.overlay.LeftListActionOverlay;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

import java.awt.Desktop;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LeftPaneCoordinator {

    private static final String ICON_SEARCH = "close.svg";
    private static final String ICON_FOLDER = "folder.svg";

    // Cycles mode
    private static final String ICON_TRASH  = "trash.svg";

    // Cases picker mode
    private static final String ICON_CASES_PICK = "plus.svg";

    // ✅ Папка циклов. JSON-файлы внутри = источник данных списка циклов.
    private static final Path CYCLES_ROOT = Path.of("test_resources", "cycles");

    // ✅ имя подпапки-мусорки (как в TestCases: "_trash")
    private static final String TRASH_DIR_NAME = "_trash";

    // ===================== I18N =====================
    private static final String I18N_CY_TOGGLE_TO_CASES  = "cy.btn.toggle.cases";
    private static final String I18N_CY_TOGGLE_TO_CYCLES = "cy.btn.toggle.cycles";

    // ✅ delete confirm texts for cycles (NOT tc.*)
    private static final String I18N_CY_TRASH_TITLE = "cy.trash.delete.title";
    private static final String I18N_CY_TRASH_HINT  = "cy.trash.delete.hint";
    // ===============================================

    // ===================== SEARCH UX =====================
    private double searchIdleDelayMs = 1000.0;
    // ===================================

    // ===================== TRASH MODE (shared) =====================
    private static final String TRASH_SPACER_ID = "__TRASH_SPACER__";
    private static final double TRASH_SHIFT_PX = 26.0;
    private static final double TRASH_ANIM_MS  = 170.0;

    private final DoubleProperty trashShiftPx = new SimpleDoubleProperty(0.0);
    private Timeline trashShiftAnim;

    // ✅ close overlay only on click OUTSIDE leftStack (like TestCasesController)
    private boolean trashOutsideCloseInstalled = false;
    // ===============================================================

    private final CyclesViewRefs v;
    private final RightPaneCoordinator right;
    private final ListPresenter list;

    private LeftMode mode = LeftMode.CYCLES_LIST;
    private LeftPaneActions actions;

    private final PauseTransition searchIdleTimer = new PauseTransition();
    private boolean searchProgrammaticChange = false;
    private String appliedSearch = "";

    // ====== DATA ======
    // ✅ cycles: только из файловой системы
    private final ObservableList<CycleListItem> cycleAll = FXCollections.observableArrayList();
    private final ObservableList<CycleListItem> cycleView = FXCollections.observableArrayList();

    // cases: как было
    private final ObservableList<CaseListItem> caseAll = FXCollections.observableArrayList();
    private final ObservableList<CaseListItem> caseView = FXCollections.observableArrayList();

    // ====== CHECKS ======
    private final Map<String, BooleanProperty> trashChecks = new HashMap<>();
    private final Map<String, BooleanProperty> cycleTrashChecks = new HashMap<>();

    // ====== UNIVERSAL overlay (cases + cycles) ======
    private LeftListActionOverlay casesAddOverlay;
    private LeftListActionOverlay cyclesTrashOverlay;

    // ====== confirm delete (same mechanic as TestCases, but texts differ) ======
    private LeftDeleteConfirm cyclesDeleteConfirm;

    public LeftPaneCoordinator(CyclesViewRefs v, RightPaneCoordinator right) {
        this.v = v;
        this.right = right;
        this.list = new ListPresenter(v.lvLeft);
    }

    public void init() {
        if (v.btnFolder != null) UiSvg.setButtonSvg(v.btnFolder, ICON_FOLDER, getIconSizeFromFxml(v.btnFolder, 14));
        if (v.btnSearch != null) UiSvg.setButtonSvg(v.btnSearch, ICON_SEARCH, getIconSizeFromFxml(v.btnSearch, 14));
        if (v.btnTrash != null)  UiSvg.setButtonSvg(v.btnTrash, ICON_TRASH,  getIconSizeFromFxml(v.btnTrash, 14));

        v.btnCreate.setOnAction(e -> { if (actions != null) actions.onCreate(); });
        v.btnFilter.setOnAction(e -> { if (actions != null) actions.onFilter(); });
        v.btnSort.setOnAction(e -> { if (actions != null) actions.onSort(); });

        v.lvLeft.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && actions != null) actions.onOpenSelected();
        });

        // keep existing selection UX
        if (v.lvLeft != null) {
            v.lvLeft.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                if (mode == LeftMode.CYCLES_LIST) refreshCyclesDeleteAvailability();
            });
        }

        v.btnFolder.setOnAction(e -> openCyclesFolder());

        if (v.btnToggleLeftList != null) {
            v.btnToggleLeftList.setOnAction(e -> toggleMode());
            syncToggleButtonText();
        }

        // ====== CASES PICKER overlay (universal) ======
        casesAddOverlay = new LeftListActionOverlay(v.leftStack, v.casesSheet, "Добавить");
        casesAddOverlay.setOnOpenChanged(open -> {
            animateTrashShift(open);
            updateCasesTrashSpacerItem();
            refreshAddAvailability();
        });
        casesAddOverlay.setOnSpacerChanged(this::updateCasesTrashSpacerItem);

        casesAddOverlay.selectAllCheckBox().selectedProperty().addListener((obs, oldV, newV) -> {
            setAllTrashChecks(newV != null && newV);
            refreshAddAvailability();
        });

        // По умолчанию как раньше: UI-only (логика будет в coordinator/другом месте)
        casesAddOverlay.setOnAdd(() -> {});

        // ====== CYCLES trash overlay (Delete) ======
        cyclesTrashOverlay = new LeftListActionOverlay(v.leftStack, v.casesSheet, "Удалить");
        cyclesTrashOverlay.setOnOpenChanged(open -> {
            // опционально: при закрытии снимаем select-all (а сами чекбоксы оставляем как есть)
            if (!open && cyclesTrashOverlay != null && cyclesTrashOverlay.selectAllCheckBox() != null) {
                cyclesTrashOverlay.selectAllCheckBox().setSelected(false);
            }

            // если подтверждение было открыто — закрываем вместе с overlay
            if (!open && cyclesDeleteConfirm != null) {
                cyclesDeleteConfirm.close();
            }

            animateTrashShift(open);
            updateCyclesTrashSpacerItem();
            refreshCyclesDeleteAvailability();
        });
        cyclesTrashOverlay.setOnSpacerChanged(this::updateCyclesTrashSpacerItem);
        cyclesTrashOverlay.selectAllCheckBox().selectedProperty().addListener((obs, oldV, newV) -> {
            setAllCycleTrashChecks(newV != null && newV);
            refreshCyclesDeleteAvailability();
        });

        // ====== confirm delete (как в TestCases), но с i18n для Cycles ======
        cyclesDeleteConfirm = new LeftDeleteConfirm(v.leftStack, this::deleteSelectedCyclesChecked);
        cyclesDeleteConfirm.setCanOpenSupplier(() ->
                cyclesTrashOverlay != null
                        && cyclesTrashOverlay.isOpen()
                        && hasAnyCycleTrashChecked()
        );
        // ✅ texts for cycles (not tc.*)
        cyclesDeleteConfirm.setTextKeys(I18N_CY_TRASH_TITLE, I18N_CY_TRASH_HINT);

        // кнопка "Удалить" в overlay -> открываем confirm
        cyclesTrashOverlay.setOnDelete(() -> {
            if (cyclesDeleteConfirm != null) cyclesDeleteConfirm.open();
        });

        installSearchBehavior();
        applyMode(LeftMode.CYCLES_LIST);

        // ✅ after scene ready: install outside-close for trash overlay (cycles)
        Platform.runLater(this::installTrashOutsideClose);
    }

    public void setMode(LeftMode newMode) {
        if (this.mode == newMode) return;
        applyMode(newMode);
    }

    public LeftMode mode() {
        return mode;
    }

    private void toggleMode() {
        if (mode == LeftMode.CYCLES_LIST) applyMode(LeftMode.CASES_PICKER);
        else applyMode(LeftMode.CYCLES_LIST);
    }

    private void syncToggleButtonText() {
        if (v.btnToggleLeftList == null) return;
        v.btnToggleLeftList.setText(
                mode == LeftMode.CYCLES_LIST
                        ? I18n.t(I18N_CY_TOGGLE_TO_CASES)
                        : I18n.t(I18N_CY_TOGGLE_TO_CYCLES)
        );
    }

    private void applyMode(LeftMode newMode) {
        this.mode = newMode;

        // close other overlay when leaving its mode
        if (newMode != LeftMode.CASES_PICKER && casesAddOverlay != null && casesAddOverlay.isOpen()) {
            casesAddOverlay.close();
        }
        if (newMode != LeftMode.CYCLES_LIST && cyclesTrashOverlay != null && cyclesTrashOverlay.isOpen()) {
            cyclesTrashOverlay.close();
        }
        if (newMode != LeftMode.CYCLES_LIST && cyclesDeleteConfirm != null && cyclesDeleteConfirm.isOpen()) {
            cyclesDeleteConfirm.close();
        }

        animateTrashShift(false);
        trashShiftPx.set(0.0);

        // ✅ FIX: сбросить items перед setItems другого типа (чтобы старые cells ушли в empty=true)
        if (v.lvLeft != null) {
            v.lvLeft.setItems(FXCollections.observableArrayList());
            v.lvLeft.getSelectionModel().clearSelection();
        }

        if (newMode == LeftMode.CYCLES_LIST) {
            actions = new CyclesListActions(v, right, () -> applyMode(LeftMode.CASES_PICKER));

            if (v.btnTrash != null) {
                UiSvg.setButtonSvg(v.btnTrash, ICON_TRASH, getIconSizeFromFxml(v.btnTrash, 14));
                v.btnTrash.setDisable(false);
                if (cyclesTrashOverlay != null) {
                    cyclesTrashOverlay.init(v.btnTrash);
                    cyclesTrashOverlay.setButtonText("Удалить");
                }
                refreshCyclesDeleteAvailability();
            }

            reloadCyclesFromDisk();
            applyFiltersToList();

            list.showCycles(cycleView);

            installCyclesListCellFactory();
            updateCyclesTrashSpacerItem();

            refreshCyclesDeleteAvailability();

        } else {
            actions = new CasesPickerActions(v, right, () -> applyMode(LeftMode.CYCLES_LIST));

            if (v.btnTrash != null) {
                UiSvg.setButtonSvg(v.btnTrash, ICON_CASES_PICK, getIconSizeFromFxml(v.btnTrash, 14));
                v.btnTrash.setDisable(false);

                if (casesAddOverlay != null) {
                    casesAddOverlay.init(v.btnTrash);
                    casesAddOverlay.setButtonText("Добавить");
                }
            }

            reloadCasesFromDisk();
            applyFiltersToList();

            list.showCases(caseView);

            installCasesPickerCellFactory();
            updateCasesTrashSpacerItem();

            refreshAddAvailability();
        }

        syncToggleButtonText();
        updateSearchButtonVisibility();
    }

    // ===================== OUTSIDE CLOSE (cycles trash) =====================

    private void installTrashOutsideClose() {
        if (trashOutsideCloseInstalled) return;
        if (v == null || v.leftStack == null) return;

        var scene = v.leftStack.getScene();
        if (scene == null) return;

        trashOutsideCloseInstalled = true;

        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            // only in cycles list mode
            if (mode != LeftMode.CYCLES_LIST) return;

            if (cyclesTrashOverlay == null || !cyclesTrashOverlay.isOpen()) return;

            Object t = e.getTarget();
            if (!(t instanceof Node n)) return;

            // click on trash button should not close mode on PRESS
            if (v.btnTrash != null && isDescendantOf(n, v.btnTrash)) return;

            // click inside leftStack should NOT close (including list / overlay)
            if (isDescendantOf(n, v.leftStack)) return;

            // click outside leftStack -> close overlay + confirm
            if (cyclesDeleteConfirm != null) cyclesDeleteConfirm.close();
            cyclesTrashOverlay.close();
        });
    }

    private static boolean isDescendantOf(Node node, Node root) {
        if (node == null || root == null) return false;
        Node cur = node;
        while (cur != null) {
            if (cur == root) return true;
            cur = cur.getParent();
        }
        return false;
    }

    // ===================== LIST CELLS (trash-mode) =====================

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void installCyclesListCellFactory() {
        if (v.lvLeft == null) return;

        TrashModeListSupport.install(
                v.lvLeft,
                (ObservableList) cycleView,
                trashShiftPx,
                (Map) cycleTrashChecks,
                TRASH_SPACER_ID,
                () -> cyclesTrashOverlay != null ? cyclesTrashOverlay.scrollSpacerPx() : 1.0,
                it -> ((CycleListItem) it).id(),
                it -> ((CycleListItem) it).title(),
                this::refreshCyclesDeleteAvailability
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void installCasesPickerCellFactory() {
        if (v.lvLeft == null) return;

        TrashModeListSupport.install(
                v.lvLeft,
                (ObservableList) caseView,
                trashShiftPx,
                (Map) trashChecks,
                TRASH_SPACER_ID,
                () -> casesAddOverlay != null ? casesAddOverlay.scrollSpacerPx() : 1.0,
                it -> ((CaseListItem) it).id(),
                it -> ((CaseListItem) it).title(),
                this::refreshAddAvailability
        );
    }

    // ===================== CYCLES trash-mode =====================

    private void updateCyclesTrashSpacerItem() {
        if (mode != LeftMode.CYCLES_LIST) return;
        if (cyclesTrashOverlay == null) return;

        TrashModeListSupport.ensureSpacerRow(
                cycleView,
                TRASH_SPACER_ID,
                CycleListItem::id,
                () -> cyclesTrashOverlay.isOpen() && cyclesTrashOverlay.scrollSpacerPx() > 0.0,
                () -> new CycleListItem(TRASH_SPACER_ID, "")
        );

        Platform.runLater(() -> v.lvLeft.refresh());
    }

    private void reloadCyclesFromDisk() {
        cycleAll.clear();

        try {
            Files.createDirectories(CYCLES_ROOT);
        } catch (Exception ignored) {}

        ArrayList<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(CYCLES_ROOT, "*.json")) {
            for (Path p : ds) {
                if (p == null) continue;
                if (!Files.isRegularFile(p)) continue;
                files.add(p);
            }
        } catch (Exception ignored) {}

        files.sort(Comparator.comparing(p -> safeTrim(p.getFileName() == null ? "" : p.getFileName().toString()).toLowerCase()));

        for (Path p : files) {
            String fileName = p.getFileName() == null ? "" : p.getFileName().toString();
            if (fileName.isBlank()) continue;

            String title = fileName;
            if (title.toLowerCase().endsWith(".json")) {
                title = title.substring(0, title.length() - 5);
            }

            // id = имя файла (нужно для удаления), title = для UI
            cycleAll.add(new CycleListItem(fileName, title));
        }
    }

    private void refreshCyclesDeleteAvailability() {
        if (mode != LeftMode.CYCLES_LIST) return;
        if (cyclesTrashOverlay == null) return;

        boolean any = hasAnyCycleTrashChecked();

        cyclesTrashOverlay.setDeleteEnabled(any);
        if (cyclesDeleteConfirm != null) cyclesDeleteConfirm.refreshAvailability(any);
    }

    private boolean hasAnyCycleTrashChecked() {
        for (CycleListItem it : cycleView) {
            if (it == null) continue;
            if (TRASH_SPACER_ID.equals(it.id())) continue;

            BooleanProperty p = cycleTrashChecks.get(it.id());
            if (p != null && p.get()) return true;
        }
        return false;
    }

    private void setAllCycleTrashChecks(boolean value) {
        for (CycleListItem it : cycleView) {
            if (it == null) continue;
            if (TRASH_SPACER_ID.equals(it.id())) continue;

            BooleanProperty p = cycleTrashChecks.computeIfAbsent(it.id(), k -> new SimpleBooleanProperty(false));
            p.set(value);
        }
    }

    private void deleteSelectedCyclesChecked() {
        if (mode != LeftMode.CYCLES_LIST) return;

        ArrayList<String> toDelete = new ArrayList<>();
        for (CycleListItem it : cycleView) {
            if (it == null) continue;
            String id = safeTrim(it.id());
            if (id.isEmpty() || TRASH_SPACER_ID.equals(id)) continue;

            BooleanProperty p = cycleTrashChecks.get(id);
            if (p != null && p.get()) toDelete.add(id);
        }

        if (toDelete.isEmpty()) {
            refreshCyclesDeleteAvailability();
            return;
        }

        // ✅ move to trash folder (same as TestCasesController.deleteSelectedTrashChecked)
        Path trashDir = CYCLES_ROOT.resolve(TRASH_DIR_NAME);
        try {
            if (!Files.exists(trashDir)) Files.createDirectories(trashDir);
        } catch (Exception ignored) {}

        for (String fileName : toDelete) {
            try {
                Path src = CYCLES_ROOT.resolve(fileName);
                if (!Files.exists(src)) continue;

                Path dst = trashDir.resolve(src.getFileName());
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {}
        }

        for (String fileName : toDelete) {
            BooleanProperty p = cycleTrashChecks.get(fileName);
            if (p != null) p.set(false);
        }

        reloadCyclesFromDisk();
        applyFiltersToList();

        if (cyclesTrashOverlay != null && cyclesTrashOverlay.isOpen()) {
            cyclesTrashOverlay.close();
        }

        animateTrashShift(false);
        trashShiftPx.set(0.0);

        updateCyclesTrashSpacerItem();
        refreshCyclesDeleteAvailability();
    }

    // ===================== CASES picker (existing) =====================

    private void refreshAddAvailability() {
        if (mode != LeftMode.CASES_PICKER) return;
        if (casesAddOverlay == null) return;

        boolean any = false;
        for (CaseListItem it : caseView) {
            if (it == null) continue;
            if (TRASH_SPACER_ID.equals(it.id())) continue;

            BooleanProperty p = trashChecks.get(it.id());
            if (p != null && p.get()) {
                any = true;
                break;
            }
        }

        casesAddOverlay.setAddEnabled(any);
    }

    private void setAllTrashChecks(boolean value) {
        for (CaseListItem it : caseView) {
            if (it == null) continue;
            if (TRASH_SPACER_ID.equals(it.id())) continue;

            BooleanProperty p = trashChecks.computeIfAbsent(it.id(), k -> new SimpleBooleanProperty(false));
            p.set(value);
        }
    }

    // ===================== VIEW APPLY (shared) =====================

    private void applyFiltersToList() {
        // cycles
        cycleView.clear();
        cycleView.addAll(cycleAll);

        // cases
        caseView.clear();
        caseView.addAll(caseAll);

        // search
        if (appliedSearch != null && !appliedSearch.isBlank()) {
            String q = appliedSearch.trim().toLowerCase();

            cycleView.removeIf(it -> it == null || it.id() == null || it.title() == null || !it.title().toLowerCase().contains(q));
            caseView.removeIf(it -> it == null || it.id() == null || it.title() == null || !it.title().toLowerCase().contains(q));
        }

        // ensure spacer rows are at bottom (if present)
        moveSpacerToEnd(cycleView, CycleListItem::id);
        moveSpacerToEnd(caseView, CaseListItem::id);
    }

    private static <T> void moveSpacerToEnd(ObservableList<T> items, java.util.function.Function<T, String> idGetter) {
        if (items == null || items.isEmpty()) return;

        int idx = -1;
        for (int i = 0; i < items.size(); i++) {
            T it = items.get(i);
            if (it == null) continue;
            String id = idGetter.apply(it);
            if (TRASH_SPACER_ID.equals(id)) { idx = i; break; }
        }

        if (idx >= 0 && idx != items.size() - 1) {
            T spacer = items.remove(idx);
            items.add(spacer);
        }
    }

    // ===================== UI + SEARCH =====================

    private void installSearchBehavior() {
        if (v.tfSearch == null) return;

        searchIdleTimer.setDuration(Duration.millis(searchIdleDelayMs));
        searchIdleTimer.setOnFinished(e -> applySearchNow());

        v.tfSearch.textProperty().addListener((obs, oldV, newV) -> {
            if (searchProgrammaticChange) return;
            searchIdleTimer.playFromStart();
            updateSearchButtonVisibility();
        });

        if (v.btnSearch != null) {
            v.btnSearch.setOnAction(e -> {
                clearSearch();
                if (actions != null) actions.onSearch("");
            });
        }
    }

    private void updateSearchButtonVisibility() {
        if (v.btnSearch == null || v.tfSearch == null) return;

        String t = v.tfSearch.getText();
        boolean has = t != null && !t.isBlank();

        v.btnSearch.setVisible(has);
        v.btnSearch.setManaged(has);
    }

    private void clearSearch() {
        if (v.tfSearch == null) return;

        searchProgrammaticChange = true;
        try {
            v.tfSearch.setText("");
        } finally {
            searchProgrammaticChange = false;
        }

        appliedSearch = "";
        applyFiltersToList();
    }

    private void applySearchNow() {
        if (v.tfSearch == null) return;

        String q = v.tfSearch.getText();
        q = q == null ? "" : q.trim();

        appliedSearch = q;

        applyFiltersToList();

        if (actions != null) actions.onSearch(q);
    }

    // ===================== ANIM (shared) =====================

    private void animateTrashShift(boolean open) {
        double target = open ? TRASH_SHIFT_PX : 0.0;

        if (trashShiftAnim != null) trashShiftAnim.stop();

        trashShiftAnim = new Timeline(
                new KeyFrame(Duration.millis(TRASH_ANIM_MS),
                        new KeyValue(trashShiftPx, target)
                )
        );
        trashShiftAnim.playFromStart();
    }

    // ===================== CASES picker data (existing) =====================

    private void updateCasesTrashSpacerItem() {
        if (mode != LeftMode.CASES_PICKER) return;
        if (casesAddOverlay == null) return;

        TrashModeListSupport.ensureSpacerRow(
                caseView,
                TRASH_SPACER_ID,
                CaseListItem::id,
                () -> casesAddOverlay.isOpen() && casesAddOverlay.scrollSpacerPx() > 0.0,
                () -> new CaseListItem(TRASH_SPACER_ID, "")
        );

        Platform.runLater(() -> v.lvLeft.refresh());
    }

    private void reloadCasesFromDisk() {
        caseAll.clear();

        List<TestCase> all = TestCaseCardStore.loadAll();
        for (TestCase tc : all) {
            if (tc == null) continue;
            String id = safeTrim(tc.getId());
            if (id.isEmpty()) continue;

            String title = buildLeftTitle(tc);
            caseAll.add(new CaseListItem(id, title));
        }
    }

    private String buildLeftTitle(TestCase tc) {
        if (tc == null) return "";

        String code = s(tc.getCode());
        String num  = s(tc.getNumber());
        String title = s(tc.getTitle());

        String head;
        if (!code.isEmpty() && !num.isEmpty()) head = code + "-" + num;
        else if (!code.isEmpty()) head = code;
        else head = safeTrim(tc.getId());

        if (!title.isEmpty()) return head + " " + title;
        return head;
    }

    // ===================== FOLDER OPEN =====================

    private void openCyclesFolder() {
        try {
            Files.createDirectories(CYCLES_ROOT);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(CYCLES_ROOT.toFile());
            }
        } catch (Exception ignored) {}
    }

    private static String safeTrim(String v) {
        return v == null ? "" : v.trim();
    }

    private static String s(String v) {
        return v == null ? "" : v.trim();
    }

    private static int getIconSizeFromFxml(javafx.scene.Node node, int def) {
        if (node == null) return def;
        Object ud = node.getUserData();
        if (ud == null) return def;

        String s = String.valueOf(ud).trim();
        if (s.isEmpty()) return def;

        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : def;
        } catch (Exception ignore) {
            return def;
        }
    }
}
