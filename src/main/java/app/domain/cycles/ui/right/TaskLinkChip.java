package app.domain.cycles.ui.right;

import app.core.I18n;
import app.ui.UiSvg;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.awt.Desktop;
import java.net.URI;

/**
 * Universal Task Link Chip (service-agnostic: Jira/YouTrack/etc).
 *
 * States:
 * - Empty: icon add-link, no text, no tooltip, click opens modal
 * - Filled: icon open-link, text shows title or url, tooltip shows url
 *
 * Modal:
 * - for add + edit
 * - overlay lives in rightRoot (StackPane)
 * - overlay mechanics delegated to RightAnchoredModal (inside TaskLinkModal)
 */
public final class TaskLinkChip extends HBox {

    private static final String ICON_ADD  = "link-add.svg";
    private static final String ICON_OPEN = "link-open.svg";

    private final Button iconBtn = new Button();
    private final Label text = new Label();
    private final Region spacer = new Region();

    // tooltip must be absent on icon
    private Tooltip urlTooltip;

    private String url = "";
    private String title = "";
    private boolean editable = true;
    // notify coordinator that chip data changed (so save-gate can react)
    private Runnable onTaskLinkChanged;

    public void setOnTaskLinkChanged(Runnable r) {
        this.onTaskLinkChanged = r;
    }

    // package-private: used by TaskLinkModal (same package)
    void fireChanged() {
        if (onTaskLinkChanged != null) {
            try { onTaskLinkChanged.run(); } catch (Exception ignore) {}
        }
    }

    // ===================== MODAL (content/logic separated) =====================

    private final TaskLinkModal modal = new TaskLinkModal(this);
    private Runnable beforeOpen = () -> {};

    public TaskLinkChip() {
        getStyleClass().add("cy-task-link-chip");
        setAlignment(Pos.CENTER_LEFT);

        iconBtn.getStyleClass().addAll("icon-btn", "xs");
        iconBtn.getStyleClass().add("cy-task-link-icon");

        iconBtn.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-background-insets: 0;" +
                        "-fx-border-color: transparent;" +
                        "-fx-border-width: 0;"
        );

        iconBtn.setFocusTraversable(false);
        iconBtn.setCursor(Cursor.HAND);
        iconBtn.setMinWidth(28);
        iconBtn.setPrefWidth(28);
        iconBtn.setMaxWidth(28);

        text.getStyleClass().add("cy-task-link-text");
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);

        spacer.setMinWidth(0);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(text, spacer, iconBtn);

        setTaskLink("", "");
    }

    /**
     * Must be called by RightPaneCoordinator.init() once rightRoot is ready.
     * Overlay lives inside rightRoot (same as menu modal approach).
     */
    public void install(StackPane rightRoot) {
        install(rightRoot, null, null);
    }

    /**
     * Optional hook: called right before opening modal (to close other overlays).
     */
    public void install(StackPane rightRoot, Runnable beforeOpen) {
        install(rightRoot, beforeOpen, null);
    }

    /**
     * modalAnchor is accepted by API, but TaskLinkModal will anchor to chip (requirement).
     */
    public void install(StackPane rightRoot, Runnable beforeOpen, Node modalAnchor) {
        if (beforeOpen != null) this.beforeOpen = beforeOpen;
        modal.install(rightRoot, modalAnchor);
    }

    public void closeModalIfOpen() {
        modal.close();
    }

    public void setTaskLink(String title, String url) {
        this.title = safe(title);
        this.url = safe(url);
        refreshView();
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
        refreshView();
    }

    public boolean isEditable() { return editable; }

    private void refreshView() {
        boolean filled = !this.url.isBlank();

        // важно: не накапливать обработчики
        iconBtn.setOnAction(null);
        setOnMouseClicked(null);
        text.setOnMouseClicked(null);
        spacer.setOnMouseClicked(null);

        if (!filled) {
            UiSvg.setButtonSvg(iconBtn, ICON_ADD, 12);

            // ✅ placeholder when url not set (i18n + fallback)
            String key = "cy.tasklink.chip.placeholder";
            String ph = I18n.t(key);
            if (ph == null || ph.trim().isEmpty() || ph.equals(key)) {
                String lang = safe(I18n.lang()).toLowerCase();
                ph = lang.startsWith("ru") ? "Задача" : "Task";
            }

            text.setText(ph);
            text.setVisible(true);
            text.setManaged(true);

            uninstallTooltip();

            if (editable) {
                iconBtn.setOnAction(e -> {
                    beforeOpen.run();
                    if (modal.consumeSuppressNextOpenClick()) return;
                    modal.toggle(true);
                });

                setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY) {
                        e.consume();
                        beforeOpen.run();
                        if (modal.consumeSuppressNextOpenClick()) return;
                        modal.toggle(true);
                    }
                });
            }

        } else {
            UiSvg.setButtonSvg(iconBtn, ICON_OPEN, 12);

            String show = this.title.isBlank() ? this.url : this.title;
            if (show.isBlank()) show = this.url;

            text.setText(show);
            text.setVisible(true);
            text.setManaged(true);

            installTooltip(this.url);

            // icon => open browser (ONE time)
            iconBtn.setOnAction(e -> openUrlSafe(this.url));

            // click chip/text => edit modal
            if (editable) {
                EventHandler<MouseEvent> openEdit = e -> {
                    if (e.getButton() != MouseButton.PRIMARY) return;
                    e.consume();
                    beforeOpen.run();
                    if (modal.consumeSuppressNextOpenClick()) return;
                    modal.toggle(false);
                };

                text.setOnMouseClicked(openEdit);
                spacer.setOnMouseClicked(openEdit);

                setOnMouseClicked(e -> {
                    if (e.getButton() != MouseButton.PRIMARY) return;

                    Object t = e.getTarget();
                    if (t instanceof Node node) {
                        if (isDescendantOf(node, iconBtn)) return; // icon has its own action
                    }
                    e.consume();
                    beforeOpen.run();
                    if (modal.consumeSuppressNextOpenClick()) return;
                    modal.toggle(false);
                });
            }
        }
    }

    public String getUrl() { return url; }
    public String getTitle() { return title; }

    // ===================== TOOLTIP =====================

    private void installTooltip(String url) {
        uninstallTooltip();

        String u = safe(url);
        if (u.isBlank()) return;

        urlTooltip = new Tooltip(u);

        // tooltip на чип целиком
        Tooltip.install(this, urlTooltip);

        // ❗tooltip должен быть отсутствовать на иконке
        Tooltip.uninstall(iconBtn, urlTooltip);
    }

    private void uninstallTooltip() {
        if (urlTooltip != null) {
            try { Tooltip.uninstall(this, urlTooltip); } catch (Exception ignore) {}
            try { Tooltip.uninstall(iconBtn, urlTooltip); } catch (Exception ignore) {}
        }
        urlTooltip = null;
    }

    // ===================== URL OPEN =====================

    private static void openUrlSafe(String url) {
        try {
            String u = safe(url);
            if (u.isBlank()) return;
            if (!Desktop.isDesktopSupported()) return;
            Desktop.getDesktop().browse(new URI(u));
        } catch (Exception ignore) {
        }
    }

    // ===================== HELPERS =====================

    private static boolean isDescendantOf(Node node, Node parent) {
        if (node == null || parent == null) return false;
        Node cur = node;
        while (cur != null) {
            if (cur == parent) return true;
            cur = cur.getParent();
        }
        return false;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
