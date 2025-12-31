package app.domain.testcases;

import app.core.I18n;
import app.domain.testcases.repo.TestCaseCardStore;
import app.domain.testcases.repo.TestCaseIndexStore;
import app.domain.testcases.ui.RightChipFactory;
import app.domain.testcases.ui.SmoothScrollSupport;
import app.domain.testcases.ui.TestCaseRightPane;
import app.domain.testcases.ui.TestCasesSheets;
import app.ui.UiSvg;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.function.UnaryOperator;
import java.util.ArrayList;
import java.util.List;

public class TestCasesController {

    private static final double CASES_SHEET_RADIUS = 18.0;
    private static final double RIGHT_SCROLL_RESERVE_PX = 10.0;

    private static final String ICON_SORT   = "sort.svg";
    private static final String ICON_FOLDER = "folder.svg";
    private static final String ICON_SEARCH = "search.svg";

    private static final String ICON_PENCIL = "pencil.svg";
    private static final String ICON_CLOSE  = "close.svg";
    private static final String ICON_CHECK  = "check.svg";
    private static final String ICON_PLUS   = "plus.svg";
    private static final String ICON_GRIP   = "grip.svg";

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

    private final SmoothScrollSupport smoothScroll = new SmoothScrollSupport();

    private TestCasesSheets sheets;
    private TestCaseRightPane rightPaneCtl;
    private RightChipFactory chipFactory;

    private List<TestCase> all = new ArrayList<>();

    private boolean rightNewOpen = false;
    private String rightOpenCaseId = null;

    // overlay placeholder (prompt) for tfTitle
    private String tfTitlePrompt = "";

    // applied search text
    private String appliedSearch = "";

    @FXML
    private void initialize() {
        System.out.println("[TestCasesController] initialize OK " + getClass().getName());

        installCasesSheetClip();
        installRightStackClip();
        installDigitsOnly(tfTop2);

        if (btnSort != null) UiSvg.setButtonSvg(btnSort, ICON_SORT, getIconSizeFromFxml(btnSort, 14));
        if (btnFolder != null) UiSvg.setButtonSvg(btnFolder, ICON_FOLDER, getIconSizeFromFxml(btnFolder, 14));

        if (btnSearch != null) UiSvg.setButtonSvg(btnSearch, ICON_SEARCH, getIconSizeFromFxml(btnSearch, 14));

        // Enter in tfSearch triggers search
        if (tfSearch != null) {
            tfSearch.setOnAction(e -> onSearch(null));
        }

        sheets = new TestCasesSheets(leftStack, filterSheet, sortSheet, casesSheet, smoothScroll, SORT_KEYS);
        sheets.setOnApplyFilters(this::applyFiltersToList);
        sheets.setOnSortChanged(() -> {
            if (btnSort != null) btnSort.setText(sheets.currentSortText());
            applyFiltersToList(); // сортировка должна сразу применяться к текущему набору
        });
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

        installRightInlineTitle();
        installTitleEllipsisField();

        setupCasesList();
        applyI18nStaticTexts();
        reloadFromDisk();

        Platform.runLater(this::installRightScrollNoShift);
    }

    @FXML
    public void onSearch(ActionEvent e) {
        appliedSearch = safeTrim(tfSearch == null ? "" : tfSearch.getText());
        applyFiltersToList();
    }

    private void installDigitsOnly(TextField tf) {
        if (tf == null) return;

        UnaryOperator<TextFormatter.Change> filter = change -> {
            String next = change.getControlNewText();
            if (next == null || next.isEmpty()) return change; // разрешаем очистку

            for (int i = 0; i < next.length(); i++) {
                char c = next.charAt(i);
                if (c < '0' || c > '9') return null; // запрещаем всё кроме 0-9
            }
            return change;
        };

        tf.setTextFormatter(new TextFormatter<>(filter));

        // если в поле уже могло попасть что-то нецифровое (например, из данных) — подчистим
        String cur = tf.getText();
        if (cur != null && !cur.isBlank()) {
            String cleaned = cur.replaceAll("[^0-9]", "");
            if (!cleaned.equals(cur)) tf.setText(cleaned);
        }
    }

    private void setupCasesList() {
        if (lvCases == null) return;

        lvCases.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(TestCase item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(formatCaseListTitle(item));
                }
            }

            {
                setOnMouseClicked(ev -> {
                    if (ev.getButton() != MouseButton.PRIMARY) return;
                    if (ev.getClickCount() != 1) return;
                    if (rightPaneCtl == null) return;

                    TestCase item = getItem();
                    if (item == null || isEmpty()) return;

                    lvCases.getSelectionModel().select(getIndex());
                    rightNewOpen = false;

                    String id = item.getId();

                    if (rightPaneCtl.isOpen()
                            && rightOpenCaseId != null
                            && rightOpenCaseId.equals(id)) {

                        rightOpenCaseId = null;
                        lvCases.getSelectionModel().clearSelection();
                        rightPaneCtl.close();
                        return;
                    }

                    rightOpenCaseId = id;
                    Path file = TestCaseCardStore.fileOf(id);
                    rightPaneCtl.openExisting(file);
                });
            }
        });
    }

    private void reloadFromDisk() {
        all = TestCaseCardStore.loadAll();

        TestCaseIndexStore.rebuild(all);

        LabelStore.saveAllFromTestCases(all);
        TagStore.saveAllFromTestCases(all);

        if (sheets != null) sheets.refreshFilterChipsIfOpen();

        applyFiltersToList(); // важное: применяем текущие фильтры+сортировку после reload
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

        lvCases.getItems().setAll(result);
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

        // 1) Desktop.open (если поддерживается)
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop d = Desktop.getDesktop();
                if (d.isSupported(Desktop.Action.OPEN)) {
                    d.open(dir.toFile());
                    return;
                }
            }
        } catch (Exception ignored) {}

        // 2) Fallback по ОС
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
            return;
        }

        rightNewOpen = true;
        rightOpenCaseId = null;

        if (lvCases != null) lvCases.getSelectionModel().clearSelection();

        rightPaneCtl.openNew();
    }

    @FXML
    public void onEdit(ActionEvent e) {
        if (rightPaneCtl != null) rightPaneCtl.onToggleEdit();
    }

    @FXML
    public void onFilter(ActionEvent e) {
        if (sheets != null) sheets.toggleFilter();
    }

    @FXML
    public void onSort(ActionEvent e) {
        if (sheets != null) sheets.toggleSort();
    }

    @FXML
    public void onEditPriv(ActionEvent e) {
        if (rightPaneCtl != null) rightPaneCtl.onEditPriv();
    }

    @FXML
    public void onAddRightLabel(ActionEvent e) {
        if (rightPaneCtl != null) rightPaneCtl.onAddRightLabel();
    }

    @FXML
    public void onAddRightTag(ActionEvent e) {
        if (rightPaneCtl != null) rightPaneCtl.onAddRightTag();
    }

    @FXML
    public void onAddStep(ActionEvent e) {
        if (rightPaneCtl != null) rightPaneCtl.onAddStep();
    }

    @FXML
    public void onCloseRight(ActionEvent e) {
        rightNewOpen = false;
        rightOpenCaseId = null;
        if (rightPaneCtl != null) rightPaneCtl.close();
    }
}
