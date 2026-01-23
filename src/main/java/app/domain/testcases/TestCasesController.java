// FILE: src/main/java/app/domain/testcases/TestCasesController.java
package app.domain.testcases;

import app.core.I18n;
import app.domain.testcases.repo.TestCaseCardStore;
import app.domain.testcases.repo.TestCaseIndexStore;
import app.domain.testcases.ui.LeftDeleteConfirm;
import app.domain.testcases.ui.RightChipFactory;
import app.domain.testcases.ui.RightDeleteConfirm;
import app.domain.testcases.ui.SmoothScrollSupport;
import app.domain.testcases.ui.TestCaseRightPane;
import app.domain.testcases.ui.TestCasesSheets;
import app.domain.testcases.ui.TestCasesTrashOverlay;
import app.ui.UiSvg;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.function.UnaryOperator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestCasesController {

    private static final double CASES_SHEET_RADIUS = 18.0;

    private static final double RIGHT_SCROLL_RESERVE_PX = 10.0;

    // ✅ LEFT: резервируем место под вертикальный скроллбар ListView, чтобы строки не скакали
    private static final double LEFT_SCROLL_RESERVE_PX = 10.0;

    // ✅ ТЗ: визуальный отступ слева у строки (меньше резерва справа!)
    private static final double LEFT_CONTENT_INSET_PX = 4.0;

    // ✅ компенсируем translateX, чтобы справа снова оставалось место под скроллбар и ничего не обрезалось
    private static final double LEFT_EFFECTIVE_RESERVE_PX = LEFT_SCROLL_RESERVE_PX + LEFT_CONTENT_INSET_PX;

    private static final String ICON_SORT   = "sort.svg";
    private static final String ICON_FOLDER = "folder.svg";

    // ✅ 1) Замена: вместо search.svg используем close.svg
    private static final String ICON_SEARCH = "close.svg";

    private static final String ICON_PENCIL = "pencil.svg";
    private static final String ICON_CLOSE  = "close.svg";
    private static final String ICON_CHECK  = "check.svg";
    private static final String ICON_PLUS   = "plus.svg";
    private static final String ICON_GRIP   = "grip.svg";

    // ✅ NEW: delete icon
    private static final String ICON_TRASH  = "trash.svg";

    private static final String INVALID_GLOW_CLASS = "tc-invalid-glow";

    // ✅ UI: общий модификатор disabled-стиля для кнопок (см. styles.css: .tc-disabled-base)
    private static final String DISABLED_BASE_CLASS = "tc-disabled-base";

    // ✅ TRASH overlay spacer item marker
    private static final String TRASH_SPACER_ID = "__TRASH_SPACER__";

    // ===================== TRASH LIST SHIFT (LEFT) =====================
    // ✅ УМЕНЬШЕНО: нужно только для появления чекбоксов слева
    private static final double TRASH_SHIFT_PX = 26.0;
    private static final double TRASH_ANIM_MS  = 170.0;

    private final DoubleProperty trashShiftPx = new SimpleDoubleProperty(0.0);
    private Timeline trashShiftAnim;

    private boolean trashOutsideCloseInstalled = false;

    // ✅ состояние чекбоксов по id (нужно для клика + select-all)
    private final Map<String, BooleanProperty> trashChecks = new HashMap<>();
    // ================================================================

    // IMPORTANT: order must match TestCaseSorter indices:
    // 0 createdNewest, 1 createdOldest, 2 savedRecent, 3 code, 4 numberAsc, 5 numberDesc, 6 titleAsc, 7 titleDesc
    private static final List<String> SORT_KEYS = List.of(
            "tc.sort.createdNewest",
            "tc.sort.createdOldest",
            "tc.sort.savedRecent",
            "tc.sort.code",
            "tc.sort.numberAsc",
            "tc.sort.numberDesc",
            "tc.sort.titleAsc",
            "tc.sort.titleDesc"
    );

    @FXML private TextField tfSearch;
    @FXML private Button btnSearch;

    @FXML private Button btnFolder;
    @FXML private Button btnCreate;

    @FXML private ListView<TestCase> lvCases;

    @FXML private VBox casesSheet;

    @FXML private StackPane leftStack;
    @FXML private Button btnTrash;
    @FXML private StackPane filterSheet;
    @FXML private StackPane sortSheet;

    // RIGHT
    @FXML private ScrollPane spRight;
    @FXML private VBox rightScrollRoot;

    @FXML private VBox rightPane;
    @FXML private VBox rightCard;

    @FXML private HBox rightTopRow;

    @FXML private Button btnEdit;      // global edit button

    @FXML private Button btnEditPriv;
    @FXML private TextField tfPrivTop;
    @FXML private TextField tfTop2;

    // inline title
    @FXML private VBox rightInlineStripBox;
    @FXML private Label lbRightInlineTitle;

    @FXML private StackPane rightStack;

    // title field + overlay
    @FXML private StackPane titleWrap;
    @FXML private TextField tfTitle;
    @FXML private Label lbTitleDisplay;

    @FXML private FlowPane fpRightLabels;
    @FXML private TextField tfRightLabel;
    @FXML private Button btnAddRightLabel;

    @FXML private FlowPane fpRightTags;
    @FXML private TextField tfRightTag;
    @FXML private Button btnAddRightTag;

    @FXML private TextArea taRightDescription;

    @FXML private Button btnAddStep;
    @FXML private VBox stepsBox;

    @FXML private Button btnCloseRight;
    @FXML private Button btnSaveRight;

    @FXML private Button btnFilter;
    @FXML private Button btnSort;

    // ✅ NEW (from your updated FXML)
    @FXML private Button btnDeleteRight;
    @FXML private StackPane deleteLayer;
    @FXML private VBox deleteModal;
    @FXML private Button btnDeleteConfirm;
    @FXML private Button btnDeleteCancel;

    @FXML private Region rightScrollBottomSpacer;

    private TestCasesTrashOverlay trashOverlay;

    // ✅ computed spacer height for ListView bottom "scroll room"
    private double trashSpacerPx = 0.0;

    private final SmoothScrollSupport smoothScroll = new SmoothScrollSupport();

    private TestCasesSheets sheets;
    private TestCaseRightPane rightPaneCtl;
    private RightChipFactory chipFactory;

    // ✅ delete confirm as separate class
    private RightDeleteConfirm deleteConfirm;
    private LeftDeleteConfirm leftDeleteConfirm;

    private List<TestCase> all = new ArrayList<>();

    private boolean rightNewOpen = false;
    private String rightOpenCaseId = null;

    // ✅ Валидацию (красную рамку) показываем только после первого пользовательского ввода
    private boolean saveGateValidationArmed = false;

    // overlay placeholder (prompt) for tfTitle
    private String tfTitlePrompt = "";

    // applied search text
    private String appliedSearch = "";

    // ===================== SEARCH UX =====================
    // ✅ 3) Debounce-поиск: Enter или если не вводили дольше 1 секунды.
    private double searchIdleDelayMs = 1000.0;

    private final PauseTransition searchIdleTimer = new PauseTransition();
    private boolean searchProgrammaticChange = false;
    // =====================================================

    @FXML
    private void initialize() {
        System.out.println("[TestCasesController] initialize OK " + getClass().getName());

        installCasesSheetClip();
        installRightStackClip();
        installDigitsOnly(tfTop2);

        // ✅ ARM: включаем показ tc-invalid-glow только после первого ввода пользователем
        installSaveGateArmOnUserInput(tfPrivTop);
        installSaveGateArmOnUserInput(tfTop2);

        if (btnSort != null) UiSvg.setButtonSvg(btnSort, ICON_SORT, getIconSizeFromFxml(btnSort, 14));
        if (btnFolder != null) UiSvg.setButtonSvg(btnFolder, ICON_FOLDER, getIconSizeFromFxml(btnFolder, 14));

        // ✅ 1) Иконка поиска -> close.svg
        if (btnSearch != null) {
            int base = getIconSizeFromFxml(btnSearch, 14);
            int scaled = Math.max(1, Math.round(base / 1.5f));
            UiSvg.setButtonSvg(btnSearch, ICON_SEARCH, scaled);
        }

        // ✅ NEW: trash icon
        if (btnDeleteRight != null) {
            UiSvg.setButtonSvg(btnDeleteRight, ICON_TRASH, getIconSizeFromFxml(btnDeleteRight, 14));
        }
        if (btnTrash != null) {
            UiSvg.setButtonSvg(btnTrash, ICON_TRASH, getIconSizeFromFxml(btnTrash, 14));
        }

        // ✅ Поиск: Enter + idle debounce (+ управление видимостью close-кнопки)
        installSearchBehavior();

        // ✅ ВАЖНО: передаем lvCases как casesList, чтобы скрывать скроллбар при открытом sheet
        sheets = new TestCasesSheets(leftStack, filterSheet, sortSheet, casesSheet, lvCases, smoothScroll, SORT_KEYS);
        sheets.setOnApplyFilters(this::applyFiltersToList);
        sheets.setOnSortChanged(() -> {
            if (btnSort != null) btnSort.setText(sheets.currentSortText());
            applyFiltersToList();
        });

        // ✅ чтобы при клике по btnFilter/btnSort (при открытой модалке) не было "закрылось на press -> открылось на release"
        sheets.setOutsideCloseConsumeTargets(btnFilter, btnSort, btnTrash);
        sheets.init();

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

                // ✅ NEW: scroll context
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
        rightPaneCtl.setOnSaved(this::reloadFromDisk);

        // ✅ NEW: пересчитывать save-gate при любых изменениях в правой форме (title/desc/labels/tags/steps)
        rightPaneCtl.setOnUserChanged(() -> Platform.runLater(this::updateSaveGateUi));

        // TRASH overlay
        trashOverlay = new TestCasesTrashOverlay(leftStack);
        trashOverlay.setAnchor(casesSheet);
        trashOverlay.init(btnTrash);
        trashOverlay.setOnSpacerChanged(() -> Platform.runLater(this::applyFiltersToList));
        trashOverlay.setOnOpenChanged(on -> {
            setTrashModeAnimated(on);

            // опционально: при закрытии снимаем select-all (а сами чекбоксы оставляем как есть)
            if (!on && trashOverlay != null && trashOverlay.selectAllCheckBox() != null) {
                trashOverlay.selectAllCheckBox().setSelected(false);
            }

            // ✅ при открытии/закрытии сразу обновить доступность Delete + модалки
            refreshDeleteAvailability();
        });

        // ✅ FIX: btnTrash не фокусируемый (focusTraversable=false), поэтому фокус остаётся в tfSearch.
        // Принудительно переводим фокус на leftStack на PRESS (как у sheet'ов по ощущению).
        if (btnTrash != null) {
            btnTrash.addEventFilter(MouseEvent.MOUSE_PRESSED, ev -> {
                if (leftStack != null) {
                    leftStack.setFocusTraversable(true);
                    leftStack.requestFocus();
                }
            });
        }

        // ✅ LEFT: confirm delete for trash overlay (у тебя класс уже новый, без init/setOnConfirm)
        leftDeleteConfirm = new LeftDeleteConfirm(leftStack, this::deleteSelectedTrashChecked);

        // ✅ открываем только когда trash-mode активен И есть выбранные чекбоксы
        leftDeleteConfirm.setCanOpenSupplier(() ->
                trashOverlay != null
                        && trashOverlay.isOpen()
                        && hasAnyTrashChecked()
        );

        // ✅ кнопка "Удалить" в overlay -> открываем confirm
        trashOverlay.setOnDelete(() -> {
            // откроется только если canOpenSupplier true
            leftDeleteConfirm.open();
        });

        // ✅ клики внутри самого trash overlay не считаются "outside" (для filter/sort это полезно)
        if (trashOverlay != null) {
            sheets.addOutsideInsideRoot(trashOverlay.overlayRoot());
        }

        // ✅ overlay checkbox => select all
        if (trashOverlay != null && trashOverlay.selectAllCheckBox() != null) {
            trashOverlay.selectAllCheckBox().setOnAction(e -> {
                boolean v = trashOverlay.selectAllCheckBox().isSelected();
                setAllTrashChecks(v);
            });
        }

        // save-gate
        rightPaneCtl.setCanSaveSupplier(this::updateSaveGateAndReturn);

        // ✅ NEW: init delete confirm (separate class) — ОДИН РАЗ (убрал дубликат)
        initDeleteConfirm();

        installRightInlineTitle();
        installTitleEllipsisField();

        setupCasesList();
        applyI18nStaticTexts();
        reloadFromDisk();

        // realtime validate (оставляем как было)
        if (tfPrivTop != null) tfPrivTop.textProperty().addListener((o, a, b) -> updateSaveGateUi());
        if (tfTop2 != null) tfTop2.textProperty().addListener((o, a, b) -> updateSaveGateUi());

        Platform.runLater(() -> {
            installRightScrollNoShift();
            updateSaveGateUi();
            updateSearchButtonVisibility();

            // ✅ Убираем автофокус с поиска при входе на экран
            if (leftStack != null) {
                leftStack.setFocusTraversable(true);
                leftStack.requestFocus();
            }

            // ✅ after scene ready: refresh delete availability
            refreshDeleteAvailability();

            // ✅ NEW: закрытие trash только по клику ВНЕ leftStack (клик по списку не закрывает)
            installTrashOutsideClose();
        });
    }

    // ===================== TRASH STATE =====================

    private BooleanProperty trashCheckProp(String id) {
        String k = safeTrim(id);
        if (k.isBlank()) k = "__NO_ID__";

        return trashChecks.computeIfAbsent(k, x -> {
            BooleanProperty p = new SimpleBooleanProperty(false);
            // ✅ ВАЖНО: как только меняется любая галка — обновляем disabled для кнопки Delete и доступность модалки
            p.addListener((obs, ov, nv) -> refreshDeleteAvailability());
            return p;
        });
    }

    private boolean hasAnyTrashChecked() {
        if (trashChecks.isEmpty()) return false;
        for (BooleanProperty p : trashChecks.values()) {
            if (p != null && p.get()) return true;
        }
        return false;
    }

    private void setAllTrashChecks(boolean v) {
        if (lvCases == null) return;
        for (TestCase tc : lvCases.getItems()) {
            if (tc == null) continue;
            if (isTrashSpacer(tc)) continue;
            trashCheckProp(tc.getId()).set(v);
        }
        if (lvCases != null) lvCases.refresh();

        // ✅ после массового выделения сразу обновляем доступность Delete
        refreshDeleteAvailability();
    }

    // ===================== TRASH SHIFT ANIMATION =====================

    private void setTrashModeAnimated(boolean on) {
        double target = on ? TRASH_SHIFT_PX : 0.0;

        if (trashShiftAnim != null) trashShiftAnim.stop();

        trashShiftAnim = new Timeline(
                new KeyFrame(Duration.millis(TRASH_ANIM_MS),
                        new KeyValue(trashShiftPx, target, Interpolator.EASE_BOTH)
                )
        );
        trashShiftAnim.play();

        if (lvCases != null) lvCases.refresh();
    }

    // ✅ trash закрываем только по клику ВНЕ leftStack (не через Sheets), чтобы список не "сбрасывал" режим
    private void installTrashOutsideClose() {
        if (trashOutsideCloseInstalled) return;
        if (leftStack == null) return;

        var scene = leftStack.getScene();
        if (scene == null) return;

        trashOutsideCloseInstalled = true;

        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (trashOverlay == null || !trashOverlay.isOpen()) return;

            Object t = e.getTarget();
            if (!(t instanceof Node n)) return;

            // ✅ клик по кнопке корзины НЕ должен закрывать режим на PRESS
            if (btnTrash != null && isDescendantOf(n, btnTrash)) return;

            // клик внутри leftStack (включая список) — НЕ закрываем
            if (isDescendantOf(n, leftStack)) return;

            // клик вне leftStack — закрываем
            trashOverlay.close();
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

    // ===================== DELETE CONFIRM (separate class) =====================

    private void initDeleteConfirm() {
        if (rightPane == null || rightStack == null) return;
        if (btnDeleteRight == null || deleteLayer == null || deleteModal == null) return;
        if (btnDeleteConfirm == null || btnDeleteCancel == null) return;

        // ✅ защита от повторной инициализации (чтобы не навесить handlers дважды)
        if (deleteConfirm != null) return;

        // ⚠️ ВАЖНО: порядок кнопок (cancel, confirm) — чтобы не перепутать обработчики
        deleteConfirm = new RightDeleteConfirm(
                rightPane,
                rightStack,
                deleteLayer,
                deleteModal,
                btnDeleteRight,
                btnDeleteCancel,
                btnDeleteConfirm
        );

        deleteConfirm.setCanOpenSupplier(() ->
                rightPaneCtl != null
                        && rightPaneCtl.isOpen()
                        && !rightNewOpen
                        && rightOpenCaseId != null
        );

        deleteConfirm.setCurrentFileSupplier(() -> {
            if (rightOpenCaseId == null) return null;
            return TestCaseCardStore.fileOf(rightOpenCaseId);
        });

        deleteConfirm.setAfterDeleted(() -> {
            rightOpenCaseId = null;
            rightNewOpen = false;

            if (lvCases != null) lvCases.getSelectionModel().clearSelection();
            if (rightPaneCtl != null) rightPaneCtl.close();

            reloadFromDisk();
        });
    }

    private void refreshDeleteAvailability() {
        // ===================== RIGHT DELETE =====================
        boolean rightVisible =
                rightPaneCtl != null
                        && rightPaneCtl.isOpen()
                        && !rightNewOpen
                        && rightOpenCaseId != null;

        if (btnDeleteRight != null) {
            // ✅ disable + tc-disabled-base в одном месте
            setRightDeleteEnabled(rightVisible);

            // ✅ НЕ ЛОМАЕМ: логика показа остаётся как была
            btnDeleteRight.setVisible(rightVisible);
            btnDeleteRight.setManaged(rightVisible);
        }

        if (deleteConfirm != null) {
            deleteConfirm.refreshAvailability(rightVisible);
        }

        // ===================== LEFT TRASH DELETE =====================
        boolean anyChecked = hasAnyTrashChecked();

        if (trashOverlay != null) {
            // ✅ disabled, если ничего не выбрано
            trashOverlay.setDeleteEnabled(anyChecked);
        }
    }

    // ===================== SEARCH =====================

    private void installSearchBehavior() {
        if (tfSearch == null) return;

        searchIdleTimer.setDuration(Duration.millis(searchIdleDelayMs));
        searchIdleTimer.setOnFinished(e -> applySearchNow());

        tfSearch.setOnAction(e -> {
            searchIdleTimer.stop();
            applySearchNow();
        });

        tfSearch.textProperty().addListener((o, a, b) -> {
            if (searchProgrammaticChange) {
                updateSearchButtonVisibility();
                return;
            }

            updateSearchButtonVisibility();

            searchIdleTimer.stop();
            searchIdleTimer.playFromStart();
        });

        updateSearchButtonVisibility();
    }

    private void applySearchNow() {
        appliedSearch = safeTrim(tfSearch == null ? "" : tfSearch.getText());
        applyFiltersToList();
    }

    private void clearSearchAndReset() {
        searchIdleTimer.stop();

        searchProgrammaticChange = true;
        try {
            if (tfSearch != null) tfSearch.setText("");
        } finally {
            searchProgrammaticChange = false;
        }

        appliedSearch = "";
        applyFiltersToList();

        updateSearchButtonVisibility();

        if (tfSearch != null) Platform.runLater(tfSearch::requestFocus);
    }

    private void updateSearchButtonVisibility() {
        if (btnSearch == null) return;

        String v = safeTrim(tfSearch == null ? "" : tfSearch.getText());
        boolean show = !v.isBlank();

        btnSearch.setVisible(show);
        btnSearch.setManaged(show);
        btnSearch.setDisable(!show);
    }

    @FXML
    public void onSearch(ActionEvent e) {
        clearSearchAndReset();
    }

    private void installDigitsOnly(TextField tf) {
        if (tf == null) return;

        UnaryOperator<TextFormatter.Change> filter = change -> {
            String next = change.getControlNewText();
            if (next == null || next.isEmpty()) return change;

            for (int i = 0; i < next.length(); i++) {
                char c = next.charAt(i);
                if (c < '0' || c > '9') return null;
            }
            return change;
        };

        tf.setTextFormatter(new TextFormatter<>(filter));

        String cur = tf.getText();
        if (cur != null && !cur.isBlank()) {
            String cleaned = cur.replaceAll("[^0-9]", "");
            if (!cleaned.equals(cur)) tf.setText(cleaned);
        }
    }

    private void installSaveGateArmOnUserInput(TextField tf) {
        if (tf == null) return;

        tf.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if (saveGateValidationArmed) return;

            saveGateValidationArmed = true;
            clearInvalidGlow();
            Platform.runLater(this::updateSaveGateUi);
        });
    }

    private void resetSaveGateValidation() {
        saveGateValidationArmed = false;
        clearInvalidGlow();
    }

    // ===================== LEFT LIST (visual) =====================

    private void setupCasesList() {
        if (lvCases == null) return;

        lvCases.setCellFactory(lv -> new ListCell<>() {

            private final StackPane cellRoot = new StackPane();

            private final HBox row = new HBox();
            private final Label title = new Label();

            private final CheckBox cbTrashRow = new CheckBox();

            private String boundId = null;

            {
                cellRoot.setPickOnBounds(false);

                row.getStyleClass().add("tc-case-row");

                title.getStyleClass().add("tc-case-title");
                title.setMinWidth(0.0);
                title.setPrefWidth(0.0);
                title.setMaxWidth(Double.MAX_VALUE);
                title.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);

                HBox.setHgrow(title, Priority.ALWAYS);
                row.getChildren().add(title);

                var w = lvCases.widthProperty()
                        .subtract(LEFT_EFFECTIVE_RESERVE_PX)
                        .subtract(trashShiftPx);

                row.minWidthProperty().bind(w);
                row.prefWidthProperty().bind(w);
                row.maxWidthProperty().bind(w);

                row.translateXProperty().bind(trashShiftPx.add(LEFT_CONTENT_INSET_PX));

                cbTrashRow.getStyleClass().add("tc-trash-check");
                cbTrashRow.setFocusTraversable(false);

                cbTrashRow.managedProperty().bind(trashShiftPx.greaterThan(0.5));
                cbTrashRow.visibleProperty().bind(trashShiftPx.greaterThan(0.5));

                cbTrashRow.opacityProperty().bind(
                        Bindings.when(trashShiftPx.lessThanOrEqualTo(0.5))
                                .then(0.0)
                                .otherwise(Bindings.min(1.0, trashShiftPx.divide(TRASH_SHIFT_PX)))
                );

                StackPane.setAlignment(cbTrashRow, javafx.geometry.Pos.CENTER_LEFT);
                StackPane.setAlignment(row, javafx.geometry.Pos.CENTER_LEFT);

                StackPane.setMargin(cbTrashRow, new Insets(0, 0, 0, 2));

                cellRoot.getChildren().addAll(cbTrashRow, row);

                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

                setOnMouseClicked(ev -> {
                    if (ev.getButton() != MouseButton.PRIMARY) return;
                    if (ev.getClickCount() != 1) return;
                    if (rightPaneCtl == null) return;

                    Object t = ev.getTarget();
                    if (t instanceof Node n && isDescendantOf(n, cbTrashRow)) return;

                    TestCase item = getItem();
                    if (item == null || isEmpty()) return;

                    if (isTrashSpacer(item)) return;

                    lvCases.getSelectionModel().select(getIndex());
                    rightNewOpen = false;

                    String id = item.getId();

                    if (rightPaneCtl.isOpen()
                            && rightOpenCaseId != null
                            && rightOpenCaseId.equals(id)) {

                        rightOpenCaseId = null;
                        lvCases.getSelectionModel().clearSelection();
                        rightPaneCtl.close();

                        resetSaveGateValidation();
                        updateSaveGateUi();

                        refreshDeleteAvailability();
                        return;
                    }

                    rightOpenCaseId = id;

                    resetSaveGateValidation();

                    Path file = TestCaseCardStore.fileOf(id);
                    rightPaneCtl.openExisting(file);

                    updateSaveGateUi();
                    refreshDeleteAvailability();
                });
            }

            @Override
            protected void updateItem(TestCase item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    unbindTrashRow();
                    setGraphic(null);
                    title.setText("");
                    title.setTooltip(null);
                    setMouseTransparent(false);
                    setDisable(false);
                    return;
                }

                if (isTrashSpacer(item)) {
                    unbindTrashRow();

                    Region r = new Region();
                    double h = trashSpacerPx;
                    if (h <= 0) h = 1;

                    r.setMinHeight(h);
                    r.setPrefHeight(h);
                    r.setMaxHeight(h);

                    r.setMouseTransparent(true);
                    setMouseTransparent(true);
                    setDisable(true);

                    setGraphic(r);
                    title.setText("");
                    title.setTooltip(null);
                    return;
                }

                setMouseTransparent(false);
                setDisable(false);

                bindTrashRow(item.getId());

                String text = formatCaseListTitle(item);
                title.setText(text);
                setGraphic(cellRoot);

                if (text.isBlank()) {
                    title.setTooltip(null);
                    return;
                }

                Platform.runLater(() -> {
                    if (isLabelTextClipped(title)) {
                        Tooltip tt = title.getTooltip();
                        if (tt == null) tt = new Tooltip();
                        tt.setText(text);
                        title.setTooltip(tt);
                    } else {
                        title.setTooltip(null);
                    }
                });
            }

            private void bindTrashRow(String id) {
                String key = safeTrim(id);
                if (key.isBlank()) key = "__NO_ID__";

                if (key.equals(boundId)) return;

                unbindTrashRow();

                boundId = key;
                BooleanProperty p = trashCheckProp(boundId);
                cbTrashRow.selectedProperty().bindBidirectional(p);
            }

            private void unbindTrashRow() {
                if (boundId != null) {
                    try {
                        BooleanProperty p = trashCheckProp(boundId);
                        cbTrashRow.selectedProperty().unbindBidirectional(p);
                    } catch (Exception ignored) {}
                }
                boundId = null;
                cbTrashRow.setSelected(false);
            }
        });
    }

    private void reloadFromDisk() {
        all = TestCaseCardStore.loadAll();

        TestCaseIndexStore.rebuild(all);

        LabelStore.saveAllFromTestCases(all);
        TagStore.saveAllFromTestCases(all);

        if (sheets != null) sheets.refreshFilterChipsIfOpen();

        applyFiltersToList();

        updateSaveGateUi();
        refreshDeleteAvailability();
    }

    private void applyI18nStaticTexts() {
        if (tfSearch != null) tfSearch.setPromptText(I18n.t("tc.search.placeholder"));
        if (btnCreate != null) btnCreate.setText(I18n.t("tc.btn.create"));
        if (btnFilter != null) btnFilter.setText(I18n.t("tc.btn.filter"));
        if (btnSort != null) btnSort.setText(sheets.currentSortText());
        if (btnSaveRight != null) btnSaveRight.setText(I18n.t("tc.btn.save"));

        if (btnEdit != null) btnEdit.setText(I18n.t("tc.btn.edit"));

        if (tfTitle != null) {
            String p = tfTitle.getPromptText();
            tfTitlePrompt = p == null ? "" : p.trim();
        }

        Platform.runLater(this::refreshTitleOverlayTextAndTooltip);
    }

    private void applyFiltersToList() {
        if (lvCases == null) return;

        var result = app.domain.testcases.query.TestCaseQuery.apply(
                all,
                sheets.appliedLabels(),
                sheets.appliedTags(),
                sheets.selectedSortIndex()
        );

        String q = safeTrim(appliedSearch).toLowerCase();
        if (!q.isBlank()) {
            List<TestCase> filtered = new ArrayList<>();
            for (TestCase tc : result) {
                String shown = formatCaseListTitle(tc);
                if (shown.toLowerCase().contains(q)) filtered.add(tc);
            }
            result = filtered;
        }

        trashSpacerPx = (trashOverlay == null) ? 0.0 : trashOverlay.scrollSpacerPx();

        if (trashSpacerPx > 0.5) {
            ArrayList<TestCase> withSpacer = new ArrayList<>(result);
            withSpacer.add(createTrashSpacerItem());
            lvCases.getItems().setAll(withSpacer);
        } else {
            lvCases.getItems().setAll(result);
        }
    }

    private TestCase createTrashSpacerItem() {
        TestCase tc = new TestCase();
        tc.setId(TRASH_SPACER_ID);
        tc.setCode("");
        tc.setNumber("");
        tc.setTitle("");
        return tc;
    }

    private boolean isTrashSpacer(TestCase tc) {
        return tc != null && TRASH_SPACER_ID.equals(safeTrim(tc.getId()));
    }

    private void installRightScrollNoShift() {
        if (spRight == null || rightScrollRoot == null) return;

        var w = spRight.widthProperty().subtract(RIGHT_SCROLL_RESERVE_PX);
        rightScrollRoot.minWidthProperty().bind(w);
        rightScrollRoot.prefWidthProperty().bind(w);
        rightScrollRoot.maxWidthProperty().bind(w);
    }

    private void installCasesSheetClip() {
        if (casesSheet == null) return;

        Rectangle clip = new Rectangle();
        clip.setArcWidth(CASES_SHEET_RADIUS * 2.0);
        clip.setArcHeight(CASES_SHEET_RADIUS * 2.0);

        clip.widthProperty().bind(casesSheet.widthProperty());
        clip.heightProperty().bind(casesSheet.heightProperty());

        casesSheet.setClip(clip);
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

    private int getIconSizeFromFxml(Button btn, int def) {
        if (btn == null) return def;
        Object ud = btn.getUserData();
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

    private String formatCaseListTitle(TestCase tc) {
        if (tc == null) return "";

        String code = safeTrim(tc.getCode());
        String num = safeTrim(tc.getNumber());
        String title = safeTrim(tc.getTitle());

        String head;
        if (!code.isEmpty() && !num.isEmpty()) head = code + "-" + num;
        else if (!code.isEmpty()) head = code;
        else head = safeTrim(tc.getId());

        if (!title.isEmpty()) return head + " " + title;
        return head;
    }

    private static String safeTrim(String v) {
        return v == null ? "" : v.trim();
    }

    // ===================== SAVE GATE =====================

    private void updateSaveGateUi() {
        updateSaveGateAndReturn();
        refreshDeleteAvailability();
    }

    private boolean updateSaveGateAndReturn() {
        boolean ok = computeCanSaveAndApplyUi();
        return ok;
    }

    // ✅ единая точка управления disabled + styleClass для Save (tc-disabled-base)
    private void setSaveEnabled(boolean enabled) {
        if (btnSaveRight == null) return;

        btnSaveRight.setDisable(!enabled);

        if (!enabled) {
            if (!btnSaveRight.getStyleClass().contains(DISABLED_BASE_CLASS)) {
                btnSaveRight.getStyleClass().add(DISABLED_BASE_CLASS);
            }
        } else {
            btnSaveRight.getStyleClass().remove(DISABLED_BASE_CLASS);
        }
    }

    // ✅ единая точка управления disabled + styleClass для RIGHT Delete (tc-disabled-base)
    private void setRightDeleteEnabled(boolean enabled) {
        if (btnDeleteRight == null) return;

        btnDeleteRight.setDisable(!enabled);

        if (!enabled) {
            if (!btnDeleteRight.getStyleClass().contains(DISABLED_BASE_CLASS)) {
                btnDeleteRight.getStyleClass().add(DISABLED_BASE_CLASS);
            }
        } else {
            btnDeleteRight.getStyleClass().remove(DISABLED_BASE_CLASS);
        }
    }

    private boolean computeCanSaveAndApplyUi() {
        // 1) правая панель закрыта => Save disabled, причина "closed"
        if (rightPaneCtl == null || !rightPaneCtl.isOpen()) {
            setSaveEnabled(false);
            clearInvalidGlow();
            if (rightPaneCtl != null) rightPaneCtl.setLastSaveBlockMessage("closed");
            return false;
        }

        // 2) если нельзя редактировать => Save disabled, причина "locked"
        boolean allowEdit = tfTop2 != null && tfTop2.isEditable();
        if (!allowEdit) {
            setSaveEnabled(false);
            clearInvalidGlow();
            if (rightPaneCtl != null) rightPaneCtl.setLastSaveBlockMessage("locked");
            return false;
        }

        boolean createFlow = rightNewOpen;
        boolean existingMode = !rightNewOpen && rightOpenCaseId != null;

        String code = safeTrim(tfPrivTop == null ? "" : tfPrivTop.getText());
        String num = safeTrim(tfTop2 == null ? "" : tfTop2.getText());

        boolean codeOk = !code.isBlank();
        boolean numOk = !num.isBlank();

        // 3) обязательные поля не заполнены => подсветка конкретных полей + точная причина
        if (!codeOk || !numOk) {
            if (saveGateValidationArmed) {
                applyInvalidGlow(tfPrivTop, !codeOk);
                applyInvalidGlow(tfTop2, !numOk);
            } else {
                clearInvalidGlow();
            }

            setSaveEnabled(false);

            if (rightPaneCtl != null) {
                if (!codeOk && !numOk) rightPaneCtl.setLastSaveBlockMessage("fill.both");
                else if (!codeOk) rightPaneCtl.setLastSaveBlockMessage("fill.code");
                else rightPaneCtl.setLastSaveBlockMessage("fill.num");
            }
            return false;
        }

        // 4) проверка дубликата пары code+num
        boolean dupOther = existsOtherWithSamePair(code, num, existingMode ? rightOpenCaseId : null);

        if (createFlow) {
            if (dupOther) {
                if (saveGateValidationArmed) {
                    // на дубликате подсвечиваем оба поля, потому что конфликтует пара
                    applyInvalidGlow(tfPrivTop, true);
                    applyInvalidGlow(tfTop2, true);
                } else {
                    clearInvalidGlow();
                }

                setSaveEnabled(false);
                if (rightPaneCtl != null) rightPaneCtl.setLastSaveBlockMessage("duplicate");
                return false;
            }

            clearInvalidGlow();
            setSaveEnabled(true);
            if (rightPaneCtl != null) rightPaneCtl.setLastSaveBlockMessage("");
            return true;
        }

        if (existingMode) {
            if (dupOther) {
                if (saveGateValidationArmed) {
                    applyInvalidGlow(tfPrivTop, true);
                    applyInvalidGlow(tfTop2, true);
                } else {
                    clearInvalidGlow();
                }

                setSaveEnabled(false);
                if (rightPaneCtl != null) rightPaneCtl.setLastSaveBlockMessage("duplicate");
                return false;
            }

            // ✅ NEW: в existing-mode Save только если есть реальные изменения
            if (rightPaneCtl != null && !rightPaneCtl.isDirty()) {
                clearInvalidGlow();
                setSaveEnabled(false);
                rightPaneCtl.setLastSaveBlockMessage("dirty");
                return false;
            }

            clearInvalidGlow();
            setSaveEnabled(true);
            if (rightPaneCtl != null) rightPaneCtl.setLastSaveBlockMessage("");
            return true;
        }

        // 5) странный режим (не create и не existing) => Save disabled + причина "mode"
        setSaveEnabled(false);
        clearInvalidGlow();
        if (rightPaneCtl != null) rightPaneCtl.setLastSaveBlockMessage("mode");
        return false;
    }

    private boolean existsOtherWithSamePair(String code, String num, String selfIdOrNull) {
        String c = safeTrim(code);
        String n = safeTrim(num);
        if (c.isBlank() || n.isBlank()) return false;

        for (TestCase tc : all) {
            if (tc == null) continue;

            String tcCode = safeTrim(tc.getCode());
            String tcNum = safeTrim(tc.getNumber());

            if (!tcCode.equals(c)) continue;
            if (!tcNum.equals(n)) continue;

            String id = safeTrim(tc.getId());
            if (selfIdOrNull != null && selfIdOrNull.equals(id)) continue;

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

    // ===================== UI: inline title / title overlay =====================

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
        String num  = safeTrim(tfTop2 == null ? "" : tfTop2.getText());
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

        lbTitleDisplay.setOnMouseClicked(e -> {
            if (tfTitle != null) tfTitle.requestFocus();
        });

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

        boolean isPlaceholder = v.isEmpty();
        String shown = isPlaceholder ? tfTitlePrompt : raw;

        lbTitleDisplay.setText(shown == null ? "" : shown);

        if (isPlaceholder) {
            if (!lbTitleDisplay.getStyleClass().contains("tc-title-placeholder")) {
                lbTitleDisplay.getStyleClass().add("tc-title-placeholder");
            }
        } else {
            lbTitleDisplay.getStyleClass().remove("tc-title-placeholder");
        }

        if (isPlaceholder) {
            lbTitleDisplay.setTooltip(null);
            return;
        }

        String full = raw == null ? "" : raw.trim();
        if (full.isBlank()) {
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
        double tw = t.getLayoutBounds().getWidth();

        return tw > avail + 0.5;
    }

    @FXML
    private void onOpenCasesFolder() {
        Path dir = Path.of("test_resources", "test_cases");

        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (IOException ignored) {}

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop d = Desktop.getDesktop();
                if (d.isSupported(Desktop.Action.OPEN)) {
                    d.open(dir.toFile());
                    return;
                }
            }
        } catch (Exception ignored) {}

        String os = System.getProperty("os.name", "").toLowerCase();

        try {
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", dir.toAbsolutePath().toString()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", dir.toAbsolutePath().toString()).start();
            } else {
                new ProcessBuilder("xdg-open", dir.toAbsolutePath().toString()).start();
            }
        } catch (Exception ignored) {}
    }

    @FXML
    public void onCreate(ActionEvent e) {
        System.out.println("[TestCasesController] onCreate fired");

        if (rightPaneCtl == null) return;

        if (rightPaneCtl.isOpen() && rightNewOpen) {
            rightNewOpen = false;
            rightOpenCaseId = null;
            rightPaneCtl.close();

            resetSaveGateValidation();
            updateSaveGateUi();

            refreshDeleteAvailability();
            return;
        }

        rightNewOpen = true;
        rightOpenCaseId = null;

        if (lvCases != null) lvCases.getSelectionModel().clearSelection();

        resetSaveGateValidation();

        rightPaneCtl.openNew();
        updateSaveGateUi();

        refreshDeleteAvailability();
    }

    @FXML
    public void onEdit(ActionEvent e) {
        if (rightPaneCtl != null) rightPaneCtl.onToggleEdit();
        Platform.runLater(this::updateSaveGateUi);
    }

    @FXML
    public void onFilter(ActionEvent e) {
        if (trashOverlay != null && trashOverlay.isOpen()) trashOverlay.close();
        if (sheets != null) sheets.toggleFilter();
    }

    @FXML
    public void onSort(ActionEvent e) {
        if (trashOverlay != null && trashOverlay.isOpen()) trashOverlay.close();
        if (sheets != null) sheets.toggleSort();
    }

    @FXML
    public void onEditPriv(ActionEvent e) {
        if (rightPaneCtl != null) rightPaneCtl.onEditPriv();
        Platform.runLater(this::updateSaveGateUi);
    }

    @FXML
    public void onAddRightLabel(ActionEvent e) {
        if (rightPaneCtl != null) rightPaneCtl.onAddRightLabel();
        Platform.runLater(this::updateSaveGateUi); // ✅ NEW
    }

    @FXML
    public void onAddRightTag(ActionEvent e) {
        if (rightPaneCtl != null) rightPaneCtl.onAddRightTag();
        Platform.runLater(this::updateSaveGateUi); // ✅ NEW
    }

    @FXML
    public void onAddStep(ActionEvent e) {
        if (rightPaneCtl != null) rightPaneCtl.onAddStep();
        Platform.runLater(this::updateSaveGateUi); // ✅ NEW
    }

    @FXML
    public void onCloseRight(ActionEvent e) {
        rightNewOpen = false;
        rightOpenCaseId = null;
        if (rightPaneCtl != null) rightPaneCtl.close();

        resetSaveGateValidation();
        updateSaveGateUi();

        refreshDeleteAvailability();
    }

    private void deleteSelectedTrashChecked() {
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, BooleanProperty> e : trashChecks.entrySet()) {
            if (e.getValue() != null && e.getValue().get()) {
                String id = safeTrim(e.getKey());
                if (!id.isBlank() && !TRASH_SPACER_ID.equals(id)) ids.add(id);
            }
        }

        if (ids.isEmpty()) return;

        for (String id : ids) {
            try {
                Path src = TestCaseCardStore.fileOf(id);
                if (src == null || !Files.exists(src)) continue;

                Path trashDir = src.getParent().resolve("_trash");
                if (!Files.exists(trashDir)) Files.createDirectories(trashDir);

                Path dst = trashDir.resolve(src.getFileName());
                Files.move(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
        }

        for (String id : ids) {
            BooleanProperty p = trashChecks.get(id);
            if (p != null) p.set(false);
        }
        if (trashOverlay != null && trashOverlay.selectAllCheckBox() != null) {
            trashOverlay.selectAllCheckBox().setSelected(false);
        }

        reloadFromDisk();

        // ✅ после удаления — пересчитать доступность Delete (на всякий)
        refreshDeleteAvailability();
    }
}
