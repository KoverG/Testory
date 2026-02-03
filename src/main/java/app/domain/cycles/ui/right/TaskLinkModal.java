// FILE: src/main/java/app/domain/cycles/ui/right/TaskLinkModal.java
package app.domain.cycles.ui.right;

import app.core.I18n;
import app.domain.cycles.ui.modal.RightAnchoredModal;
import app.ui.UiSvg;
import app.ui.UrlTitleExtractor;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URL;

/**
 * Content + logic for TaskLink modal.
 *
 * Overlay mechanics (layer/backdrop/position/anim/outside-close) is handled by {@link RightAnchoredModal}.
 */
final class TaskLinkModal {

    private static final String URL_INVALID_CLASS = "cy-url-invalid";

    // UX: delay before auto title after user stops typing
    private static final int AUTO_TITLE_DEBOUNCE_MS = 420;

    private final TaskLinkChip chip;

    private RightAnchoredModal host;

    private TextField tfTitle;
    private TextField tfUrl;
    private Button btnSave;

    // refresh button inside TITLE field
    private Button btnTitleRefresh;

    private boolean addMode = true;
    private boolean titleManuallyEdited = false;
    private boolean suppressAutoTitle = false;

    // NEW: do not show "error" until user interacted with URL field (or pressed actions)
    private boolean urlTouched = false;

    // debounce for url typing
    private PauseTransition urlDebounce;

    // --- anchor and anti-reopen guard ---
    private Node anchorNode;
    private VBox modalRoot;

    /**
     * Guard:
     * When RightAnchoredModal closes on MOUSE_PRESSED (outside click),
     * the subsequent MOUSE_CLICKED on the chip would re-open it again.
     * We detect this pattern and suppress the "reopen" click once.
     */
    private boolean suppressNextOpenClick = false;

    TaskLinkModal(TaskLinkChip chip) {
        this.chip = chip;
    }

    public void install(StackPane rightRoot) {
        install(rightRoot, null);
    }

    /**
     * IMPORTANT (requirement):
     * URL modal must be positioned relative to the chip (same as Environment modal).
     * Therefore anchorOverride is ignored intentionally.
     */
    public void install(StackPane rightRoot, Node anchorOverride) {
        if (rightRoot == null) return;

        // ✅ Always anchor to chip
        Node anchor = chip;
        this.anchorNode = anchor;

        host = new RightAnchoredModal(rightRoot, anchor, this::buildModal);

        // ✅ Match Environment modal anchoring: align by LEFT edge of the chip (not like menu/profile)
        host.setAlignByLeftEdge(true);

        host.setOnBeforeOpen(this::onBeforeOpen);
        host.setOnAfterOpen(() -> {
            // ✅ Fix: avoid initial focus landing on first field for a split second.
            // Let JavaFX finish its own "initial focus" pass, then force focus to URL.
            Platform.runLater(() -> {
                if (tfUrl != null) tfUrl.requestFocus();
            });
        });

        // Install "anti-reopen" guard BEFORE host.install() so our scene filter is registered earlier.
        installAntiReopenGuard(rightRoot);

        host.install();
        host.close();
    }

    public void toggle(boolean addMode) {
        this.addMode = addMode;

        if (host == null) return;
        host.toggle();
    }

    /** ✅ НУЖНО для вызовов из TaskLinkChip */
    public void close() {
        if (host != null) host.close();
    }

    /**
     * Called by TaskLinkChip right before opening via click.
     * If the modal was just closed by RightAnchoredModal on MOUSE_PRESSED (outside click),
     * we must not re-open it on the same click.
     */
    boolean consumeSuppressNextOpenClick() {
        if (!suppressNextOpenClick) return false;
        suppressNextOpenClick = false;
        return true;
    }

    // ===================== OPEN / CLOSE HOOKS =====================

    private void onBeforeOpen() {
        titleManuallyEdited = false;
        suppressAutoTitle = true;

        // reset interaction state
        urlTouched = false;

        if (urlDebounce != null) urlDebounce.stop();

        if (tfTitle != null) tfTitle.setText(addMode ? "" : safe(chip.getTitle()));
        if (tfUrl != null) tfUrl.setText(addMode ? "" : safe(chip.getUrl()));

        // ✅ UX fix: do not show red border on empty URL when opening
        String curUrl = (tfUrl == null) ? "" : safe(tfUrl.getText());
        if (curUrl.isBlank()) {
            clearUrlInvalidUi();
        } else {
            // editing existing link: can show invalid immediately (data already exists)
            refreshUrlInvalidUi(true);
        }

        suppressAutoTitle = false;

        Platform.runLater(() -> {
            // no-op: RightAnchoredModal will layout itself
        });
    }

    // ===================== UI =====================

    private VBox buildModal() {
        VBox root = new VBox();
        this.modalRoot = root;

        root.setAlignment(Pos.CENTER);

        root.getStyleClass().add("tc-right-confirm");
        root.getStyleClass().add("cy-menu-modal");

        // ✅ unify modal width with menu & profile
        root.setMinWidth(300);
        root.setPrefWidth(300);
        root.setMaxWidth(300);

        root.setMinHeight(Region.USE_PREF_SIZE);
        root.setMaxHeight(Region.USE_PREF_SIZE);

        Label lbl = new Label(I18n.t("cy.tasklink.title"));
        lbl.setWrapText(false);
        lbl.setMaxWidth(Double.MAX_VALUE);
        lbl.getStyleClass().add("tc-delete-title");
        VBox.setMargin(lbl, new Insets(4, 0, 0, 0));

        Region topGap = new Region();
        topGap.setPrefHeight(20);
        topGap.setMinHeight(20);
        topGap.setMaxHeight(20);

        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);

        // ===== TITLE FIELD + refresh button inside =====
        tfTitle = new TextField();
        tfTitle.setPromptText(I18n.t("cy.tasklink.field.title.placeholder"));
        tfTitle.getStyleClass().add("cy-search");
        tfTitle.getStyleClass().add("cy-profile-name-input"); // input-with-inner-button pattern

        tfTitle.setPrefWidth(220);
        tfTitle.setMinWidth(220);
        tfTitle.setMaxWidth(220);

        // ✅ Prevent JavaFX initial focus from choosing Title field when modal appears.
        // Clicking still focuses it; this only affects traversal/initial focus.
        tfTitle.setFocusTraversable(false);

        btnTitleRefresh = new Button();
        btnTitleRefresh.setFocusTraversable(false);
        btnTitleRefresh.getStyleClass().addAll("icon-btn", "cy-profile-add-in");
        UiSvg.setButtonSvg(btnTitleRefresh, "refresh.svg", 12);

        StackPane titleWrap = new StackPane();
        titleWrap.setAlignment(Pos.CENTER);

        titleWrap.setPrefWidth(220);
        titleWrap.setMinWidth(220);
        titleWrap.setMaxWidth(220);

        StackPane.setAlignment(btnTitleRefresh, Pos.CENTER_RIGHT);
        titleWrap.getChildren().addAll(tfTitle, btnTitleRefresh);

        // ===== URL FIELD =====
        tfUrl = new TextField();
        tfUrl.setPromptText(I18n.t("cy.tasklink.field.url.placeholder"));
        tfUrl.getStyleClass().add("cy-search");
        tfUrl.getStyleClass().add("cy-tasklink-url");

        tfUrl.setPrefWidth(220);
        tfUrl.setMinWidth(220);
        tfUrl.setMaxWidth(220);

        // ✅ Ensure URL is eligible for initial focus/traversal
        tfUrl.setFocusTraversable(true);

        // debounce init
        urlDebounce = new PauseTransition(Duration.millis(AUTO_TITLE_DEBOUNCE_MS));

        tfTitle.textProperty().addListener((o, oldV, newV) -> {
            if (suppressAutoTitle) return;
            if (tfTitle != null && tfTitle.isFocused()) {
                titleManuallyEdited = true;
            }
        });

        // URL change: mark touched, validate (after touch), auto-title only via debounce
        tfUrl.textProperty().addListener((o, oldV, newV) -> {
            if (suppressAutoTitle) return;
            if (tfUrl == null) return;

            // any change = user interaction
            urlTouched = true;

            // ✅ show invalid only after interaction
            refreshUrlInvalidUi(false);

            if (tfTitle == null) return;
            if (titleManuallyEdited) return;

            String curUrl = safe(tfUrl.getText());

            // If empty: do not touch title; stop debounce
            if (curUrl.isBlank()) {
                if (urlDebounce != null) urlDebounce.stop();
                return;
            }

            // Debounce auto-title only when url looks valid (so we don't "jump" while typing)
            if (!looksLikeValidAbsoluteUrl(curUrl)) {
                if (urlDebounce != null) urlDebounce.stop();
                return;
            }

            scheduleDebouncedAutoTitle(curUrl);
        });

        // URL focus lost: if user touched -> validate; auto-title finalize if eligible
        tfUrl.focusedProperty().addListener((o, was, is) -> {
            if (suppressAutoTitle) return;
            if (Boolean.TRUE.equals(is)) return; // focus gained -> ignore

            // validate only after interaction
            refreshUrlInvalidUi(false);

            if (tfUrl == null || tfTitle == null) return;
            if (titleManuallyEdited) return;

            String curUrl = safe(tfUrl.getText());
            if (curUrl.isBlank()) return;
            if (!looksLikeValidAbsoluteUrl(curUrl)) return;

            if (urlDebounce != null) urlDebounce.stop();
            applyAutoTitleNow(curUrl);
        });

        // ↺ in TITLE: force regenerate title from current url (also counts as "interaction" with URL constraints)
        btnTitleRefresh.setOnAction(e -> {
            if (tfUrl == null || tfTitle == null) return;

            // user triggered an action -> allow showing validation
            urlTouched = true;

            String curUrl = safe(tfUrl.getText());

            // show invalid if empty/invalid
            refreshUrlInvalidUi(false);

            if (curUrl.isBlank()) return;
            if (!looksLikeValidAbsoluteUrl(curUrl)) return;

            // treat as explicit user action -> stop future auto-overwrites
            titleManuallyEdited = true;

            if (urlDebounce != null) urlDebounce.stop();

            String t = buildShortTitleFromUrl(curUrl);
            if (t.isBlank()) t = curUrl;

            setTitleSilently(t);
        });

        btnSave = new Button(I18n.t("cy.tasklink.save"));
        btnSave.getStyleClass().addAll("cy-modal-btn", "cy-modal-btn-primary");
        btnSave.setPrefWidth(220);
        btnSave.setMinWidth(220);
        btnSave.setMaxWidth(220);

        btnSave.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> {
                            if (tfUrl == null) return true;
                            String u = safe(tfUrl.getText());
                            if (u.isBlank()) return true;
                            return !looksLikeValidAbsoluteUrl(u);
                        },
                        tfUrl.textProperty()
                )
        );

        btnSave.setOnAction(e -> onSave());

        EventHandler<KeyEvent> enter = ev -> {
            if (ev.getCode() == KeyCode.ENTER) {
                ev.consume();
                onSave();
            }
        };
        tfTitle.addEventFilter(KeyEvent.KEY_PRESSED, enter);
        tfUrl.addEventFilter(KeyEvent.KEY_PRESSED, enter);

        box.getChildren().addAll(titleWrap, tfUrl, btnSave);

        root.getChildren().addAll(lbl, topGap, box);

        return root;
    }

    private void scheduleDebouncedAutoTitle(String urlSnapshot) {
        if (urlDebounce == null) return;

        urlDebounce.stop();
        urlDebounce.setOnFinished(ev -> {
            if (suppressAutoTitle) return;
            if (tfUrl == null || tfTitle == null) return;
            if (titleManuallyEdited) return;

            String cur = safe(tfUrl.getText());
            if (!cur.equals(urlSnapshot)) return; // user kept typing
            if (cur.isBlank()) return;
            if (!looksLikeValidAbsoluteUrl(cur)) return;

            applyAutoTitleNow(cur);
        });
        urlDebounce.playFromStart();
    }

    private void applyAutoTitleNow(String curUrl) {
        String autoTitle = buildShortTitleFromUrl(curUrl);
        if (autoTitle.isBlank()) autoTitle = curUrl;
        setTitleSilently(autoTitle);
    }

    private void setTitleSilently(String title) {
        if (tfTitle == null) return;
        suppressAutoTitle = true;
        try {
            tfTitle.setText(title == null ? "" : title);
        } finally {
            suppressAutoTitle = false;
        }
    }

    private void onSave() {
        if (tfUrl == null) return;

        // user explicitly tries to save -> now it's ok to show validation
        urlTouched = true;

        String u = safe(tfUrl.getText());
        if (u.isBlank()) {
            refreshUrlInvalidUi(false);
            return;
        }

        if (!looksLikeValidAbsoluteUrl(u)) {
            refreshUrlInvalidUi(false);
            return;
        }

        String t = (tfTitle == null) ? "" : safe(tfTitle.getText());

        chip.setTaskLink(t, u);
        chip.fireChanged();

        if (host != null) host.close();
    }

    private void applyUrlInvalidUi() {
        if (tfUrl == null) return;
        if (!tfUrl.getStyleClass().contains(URL_INVALID_CLASS)) {
            tfUrl.getStyleClass().add(URL_INVALID_CLASS);
        }
    }

    private void clearUrlInvalidUi() {
        if (tfUrl == null) return;
        tfUrl.getStyleClass().remove(URL_INVALID_CLASS);
    }

    /**
     * @param showEvenIfNotTouched if true - validate regardless of urlTouched (used for existing data on open)
     */
    private void refreshUrlInvalidUi(boolean showEvenIfNotTouched) {
        if (tfUrl == null) return;

        String curUrl = safe(tfUrl.getText());

        // If we haven't interacted yet -> don't show any error on empty/invalid.
        if (!showEvenIfNotTouched && !urlTouched) {
            clearUrlInvalidUi();
            return;
        }

        // After interaction (or forced on open for existing):
        if (curUrl.isBlank()) {
            applyUrlInvalidUi();
            return;
        }

        if (looksLikeValidAbsoluteUrl(curUrl)) {
            clearUrlInvalidUi();
        } else {
            applyUrlInvalidUi();
        }
    }

    // ===================== ANTI-REOPEN GUARD =====================

    private void installAntiReopenGuard(StackPane rightRoot) {
        if (rightRoot == null) return;

        // Install once per instance
        rightRoot.sceneProperty().addListener((o, oldS, newS) -> {
            if (newS != null) attachGuardToScene(newS);
        });

        Scene sc = rightRoot.getScene();
        if (sc != null) attachGuardToScene(sc);
    }

    private void attachGuardToScene(Scene sc) {
        // We attach a filter (capture phase).
        // If the modal is open and user presses on chip, RightAnchoredModal will close it on press,
        // and chip click would re-open it. We mark "suppressNextOpenClick" here.
        sc.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (host == null) return;
            if (modalRoot == null) return;

            Object t = e.getTarget();
            if (!(t instanceof Node node)) return;

            // If click is inside modal -> ignore
            if (isDescendantOf(node, modalRoot)) return;

            // If click is on anchor (now chip) -> ignore
            if (anchorNode != null && isDescendantOf(node, anchorNode)) return;

            // If click is on the chip -> we want: second click closes, not reopens
            if (isDescendantOf(node, chip)) {
                // Mark: "do not open on the click that follows this press"
                suppressNextOpenClick = true;
            }
        });
    }

    private static boolean isDescendantOf(Node node, Node parent) {
        if (node == null || parent == null) return false;
        Node cur = node;
        while (cur != null) {
            if (cur == parent) return true;
            cur = cur.getParent();
        }
        return false;
    }

    // ===================== HELPERS =====================

    private static String safe(String s) {
        if (s == null) return "";
        return s.trim();
    }

    private static boolean looksLikeValidAbsoluteUrl(String rawUrl) {
        String u = safe(rawUrl);
        if (u.isBlank()) return false;
        try {
            URL url = new URL(u);
            String proto = safe(url.getProtocol());
            if (proto.isBlank()) return false;
            if (!proto.equalsIgnoreCase("http") && !proto.equalsIgnoreCase("https")) return false;
            String host = safe(url.getHost());
            return !host.isBlank();
        } catch (Exception ignore) {
            return false;
        }
    }

    private static String buildShortTitleFromUrl(String rawUrl) {
        // ✅ universal extractor (no network)
        return UrlTitleExtractor.extractDisplayTitle(rawUrl);
    }
}
