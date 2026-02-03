// FILE: src/main/java/app/domain/cycles/ui/right/EnvironmentModal.java
package app.domain.cycles.ui.right;

import app.core.I18n;
import app.domain.cycles.CyclePrivateConfig;
import app.domain.cycles.ui.modal.RightAnchoredModal;
import app.ui.ToggleSwitch;
import app.ui.UiAutoGrowTextArea;
import app.ui.UiSvg;
import app.ui.UrlTitleExtractor;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Content + logic for Environment modal.
 *
 * Overlay mechanics (layer/backdrop/position/anim/outside-close) is handled by {@link RightAnchoredModal}.
 */
final class EnvironmentModal {

    static final class EnvState {
        final boolean mobile;
        final String value;
        final List<String> links;

        EnvState(boolean mobile, String value, List<String> links) {
            this.mobile = mobile;
            this.value = (value == null) ? "" : value.trim();
            this.links = (links == null) ? List.of() : List.copyOf(links);
        }
    }

    private static final double MODAL_W = 300.0;

    /**
     * ВАЖНО:
     * При alignment=CENTER и фиксированной ширине контента, симметричный padding L/R визуально "не двигает" контент.
     * Поэтому "боковые отступы" делаем через разницу MODAL_W и FIELD_W.
     */
    private static final double GUTTER_X = 34.0;                 // <-- регулируй это: меньше = ближе к краям
    private static final double FIELD_W = MODAL_W - (GUTTER_X * 2.0);

    // зазор только между тогглом и текстом режима
    private static final double GAP_TOGGLE_TEXT = 6.0;

    // высота по умолчанию = 1 строка
    private static final double INPUT_BASE_H = 32.0;

    private static final String ICON_DESKTOP = "desktop.svg";
    private static final String ICON_MOBILE  = "mobile.svg";

    private final EnvironmentChip chip;

    private RightAnchoredModal host;

    private ToggleSwitch tg;
    private Label lbMode;

    private CheckBox chRemember;
    private TextArea taValue;

    private Button btnOpenLink;

    // chips container near link-open.svg
    private FlowPane fpLinks;
    private final List<String> buildLinks = new ArrayList<>();

    // hint label shown when there are no chips yet
    private Label lbAddBuildHint;

    // link input (shown on demand) — стили/структура как в модалке профиля
    private StackPane linkRow;
    private TextField tfLink;
    private Button btnLinkIn;
    private boolean linkRowShown = false;

    private Button btnSave;

    private Node anchorNode;
    private VBox modalRoot;

    private boolean suppressNextOpenClick = false;

    private Runnable beforeOpen = () -> {};

    private Supplier<Boolean> currentMobileSupplier = () -> false;
    private Supplier<String> currentValueSupplier = () -> "";
    private Supplier<List<String>> currentLinksSupplier = List::of;
    private Supplier<Boolean> currentTypeSetSupplier = () -> false;

    private Consumer<EnvState> onSaved = s -> {};

    EnvironmentModal(EnvironmentChip chip) {
        this.chip = chip;
    }

    void setBeforeOpen(Runnable r) {
        this.beforeOpen = (r == null) ? () -> {} : r;
    }

    void setCurrentSuppliers(
            Supplier<Boolean> mobileSupplier,
            Supplier<String> valueSupplier,
            Supplier<List<String>> linksSupplier,
            Supplier<Boolean> typeSetSupplier
    ) {
        if (mobileSupplier != null) this.currentMobileSupplier = mobileSupplier;
        if (valueSupplier != null) this.currentValueSupplier = valueSupplier;
        if (linksSupplier != null) this.currentLinksSupplier = linksSupplier;
        if (typeSetSupplier != null) this.currentTypeSetSupplier = typeSetSupplier;
    }

    void setOnSaved(Consumer<EnvState> c) {
        this.onSaved = (c == null) ? (s -> {}) : c;
    }

    void install(StackPane rightRoot) {
        if (rightRoot == null) return;

        this.anchorNode = chip;

        host = new RightAnchoredModal(rightRoot, chip, this::buildModal);
        host.setAlignByLeftEdge(true);

        host.setOnBeforeOpen(this::onBeforeOpen);
        host.setOnAfterOpen(() -> {
            if (taValue != null) taValue.requestFocus();
        });

        installAntiReopenGuard(rightRoot);

        host.install();
        host.close();
    }

    void toggle() {
        if (host == null) return;
        host.toggle();
    }

    void close() {
        if (host != null) host.close();
    }

    boolean consumeSuppressNextOpenClick() {
        if (!suppressNextOpenClick) return false;
        suppressNextOpenClick = false;
        return true;
    }

    // ===================== OPEN / CLOSE HOOKS =====================

    private void onBeforeOpen() {
        try { beforeOpen.run(); } catch (Exception ignore) {}

        boolean remember = CyclePrivateConfig.rememberEnvEnabled();
        boolean rememberedMobile = CyclePrivateConfig.rememberedEnvMobile();

        boolean curMobile = false;
        String curValue = "";
        List<String> curLinks = List.of();
        boolean curTypeSet = false;

        try { curMobile = Boolean.TRUE.equals(currentMobileSupplier.get()); } catch (Exception ignore) {}
        try { curValue = safe(currentValueSupplier.get()); } catch (Exception ignore) {}
        try {
            List<String> xs = currentLinksSupplier.get();
            curLinks = (xs == null) ? List.of() : List.copyOf(xs);
        } catch (Exception ignore) {
            curLinks = List.of();
        }
        try { curTypeSet = Boolean.TRUE.equals(currentTypeSetSupplier.get()); } catch (Exception ignore) {}

        boolean hasExplicitEnv = curTypeSet || !curValue.isBlank() || (curLinks != null && !curLinks.isEmpty());

        boolean initialMobile = hasExplicitEnv
                ? curMobile
                : (remember ? rememberedMobile : curMobile);

        // ✅ IMPORTANT: builds value must NOT be remembered globally
        String initialValue = hasExplicitEnv ? curValue : "";

        if (chRemember != null) chRemember.setSelected(remember);

        // selected=true => Desktop/Web
        if (tg != null) tg.setSelected(!initialMobile);

        if (taValue != null) taValue.setText(initialValue);

        // ✅ restore links from cycle state if present (persisted in draft)
        buildLinks.clear();
        if (fpLinks != null) fpLinks.getChildren().clear();

        if (hasExplicitEnv && curLinks != null && !curLinks.isEmpty()) {
            for (String u : curLinks) {
                addBuildLinkFromState(u);
            }
        }

        syncBuildHint();

        // скрываем link input при каждом открытии
        linkRowShown = false;
        if (linkRow != null) {
            linkRow.setVisible(false);
            linkRow.setManaged(false);
        }
        if (tfLink != null) tfLink.clear();

        syncModeLabel();

        // после установки текста — попросим хост пересчитать геометрию (TextArea сам перерассчитает высоту)
        if (taValue != null) {
            Platform.runLater(this::requestModalAndBackdropLayout);
        }
    }

    private void onSave() {
        boolean mobile = tg != null && !tg.isSelected();
        String value = (taValue == null) ? "" : safe(taValue.getText());

        boolean remember = chRemember != null && chRemember.isSelected();

        // ✅ remember ONLY toggle (envType: mobile/desktop)
        CyclePrivateConfig.setRememberEnv(remember, mobile);

        // ✅ persist to cycle draft: toggle + builds + links
        try { onSaved.accept(new EnvState(mobile, value, buildLinks)); } catch (Exception ignore) {}

        if (host != null) host.close();
    }

    // ===================== UI BUILD =====================

    private VBox buildModal() {
        VBox root = new VBox(10);
        this.modalRoot = root;

        root.setAlignment(Pos.TOP_CENTER);

        root.getStyleClass().add("tc-right-confirm");
        root.getStyleClass().add("cy-menu-modal");

        root.setMinWidth(MODAL_W);
        root.setPrefWidth(MODAL_W);
        root.setMaxWidth(MODAL_W);

        // Вертикальные отступы — чтобы "дышало".
        // Боковые отступы регулируем через GUTTER_X + FIELD_W (см. константы).
        root.setPadding(new Insets(14, 0, 14, 0));

        Label lbl = new Label(I18n.t("cy.env.title"));
        lbl.getStyleClass().add("tc-delete-title");

        Label themeColorSource = new Label();
        themeColorSource.getStyleClass().add("cy-meta-value");
        themeColorSource.setVisible(false);
        themeColorSource.setManaged(false);

        tg = new ToggleSwitch();
        tg.setAnimated(true);
        tg.setFocusTraversable(false);

        Node icDesktop = UiSvg.createSvg(ICON_DESKTOP, 12);
        Node icMobile = UiSvg.createSvg(ICON_MOBILE, 12);

        bindToTheme(icDesktop, themeColorSource);
        bindToTheme(icMobile, themeColorSource);

        // Mobile — слева, Desktop/Web — справа
        tg.setGraphicOff(icMobile);
        tg.setGraphicOn(icDesktop);

        lbMode = new Label();
        lbMode.setWrapText(false);

        // ✅ нужен ТОЛЬКО цвет как у cy.env.remember (без изменения высоты/шрифта)
        lbMode.getStyleClass().add("cy-profile-remember");

        syncModeLabel();
        tg.selectedProperty().addListener((o, ov, nv) -> syncModeLabel());

        chRemember = new CheckBox(I18n.t("cy.env.remember"));
        chRemember.setFocusTraversable(false);

        // стиль чекбокса (как в профиле)
        chRemember.getStyleClass().addAll(
                "tc-trash-check",
                "cy-profile-remember",
                "cy-profile-remember-sm",
                "cy-profile-remember-xs"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Region gapToggleText = new Region();
        gapToggleText.setMinWidth(GAP_TOGGLE_TEXT);
        gapToggleText.setPrefWidth(GAP_TOGGLE_TEXT);
        gapToggleText.setMaxWidth(GAP_TOGGLE_TEXT);

        HBox firstRow = new HBox(0);
        firstRow.setAlignment(Pos.CENTER_LEFT);
        firstRow.setPrefWidth(FIELD_W);
        firstRow.setMinWidth(FIELD_W);
        firstRow.setMaxWidth(FIELD_W);
        firstRow.getChildren().addAll(tg, gapToggleText, lbMode, spacer, chRemember);

        // ===== Builds field (expandable, no scrollbars) =====
        taValue = new TextArea();
        taValue.setPromptText(I18n.t("cy.env.field.placeholder"));

        // .cy-search в CSS фиксирует max-height: 32px (под TextField), поэтому на TextArea его НЕ вешаем.
        taValue.getStyleClass().add("cy-env-value-area");

        taValue.setWrapText(true);

        // по умолчанию 1 строка (32px), дальше растёт
        taValue.setPrefRowCount(1);
        taValue.setPrefHeight(INPUT_BASE_H);

        // КЛЮЧ: высота должна следовать prefHeight (иначе текст уезжает "под рамку")
        taValue.setMinHeight(Region.USE_PREF_SIZE);
        taValue.setMaxHeight(Region.USE_PREF_SIZE);

        taValue.setMinWidth(0.0);

        // отключаем скроллбары: только политики (без managed/visible)
        disableTextAreaScrollBars(taValue);

        // авто-рост как в taRightDescription (wrap-aware)
        UiAutoGrowTextArea.installWrapAutoGrow(taValue, INPUT_BASE_H, this::requestModalAndBackdropLayout);

        taValue.setPrefWidth(FIELD_W);
        taValue.setMinWidth(FIELD_W);
        taValue.setMaxWidth(FIELD_W);

        // ===== open link button + chips / hint =====
        btnOpenLink = new Button();
        btnOpenLink.getStyleClass().add("icon-btn");
        btnOpenLink.setFocusTraversable(false);
        UiSvg.setButtonSvg(btnOpenLink, "link-open.svg", 12);

        // размер кнопки = высота тоггла (квадрат)
        btnOpenLink.minHeightProperty().bind(tg.heightProperty());
        btnOpenLink.prefHeightProperty().bind(tg.heightProperty());
        btnOpenLink.maxHeightProperty().bind(tg.heightProperty());

        btnOpenLink.minWidthProperty().bind(tg.heightProperty());
        btnOpenLink.prefWidthProperty().bind(tg.heightProperty());
        btnOpenLink.maxWidthProperty().bind(tg.heightProperty());

        btnOpenLink.setOnAction(e -> toggleLinkRow());

        lbAddBuildHint = new Label(I18n.t("cy.env.build.link.hint"));
        lbAddBuildHint.setWrapText(false);
        // ✅ только цвет (как Desktop/Web / Mobile)
        lbAddBuildHint.getStyleClass().add("cy-profile-remember");
        lbAddBuildHint.setMinWidth(0.0);
        lbAddBuildHint.setMaxWidth(Double.MAX_VALUE);

        // ✅ fix: вертикально по центру кнопки link-open.svg
        lbAddBuildHint.setAlignment(Pos.CENTER_LEFT);
        lbAddBuildHint.minHeightProperty().bind(btnOpenLink.heightProperty());
        lbAddBuildHint.prefHeightProperty().bind(btnOpenLink.heightProperty());
        lbAddBuildHint.maxHeightProperty().bind(btnOpenLink.heightProperty());

        fpLinks = new FlowPane();
        fpLinks.setHgap(6);
        fpLinks.setVgap(6);
        fpLinks.setAlignment(Pos.TOP_LEFT);
        fpLinks.setPrefWrapLength(Math.max(1.0, FIELD_W - 36.0));
        fpLinks.setMinWidth(0.0);
        fpLinks.setMaxWidth(Double.MAX_VALUE);

        HBox openRow = new HBox(8, btnOpenLink, lbAddBuildHint, fpLinks);
        // ВАЖНО: кнопка должна оставаться "на своём месте", не центрируясь по высоте относительно многострочных чипов
        openRow.setAlignment(Pos.TOP_LEFT);
        openRow.setFillHeight(false);

        HBox.setHgrow(lbAddBuildHint, Priority.ALWAYS);
        HBox.setHgrow(fpLinks, Priority.ALWAYS);

        openRow.setPrefWidth(FIELD_W);
        openRow.setMinWidth(FIELD_W);
        openRow.setMaxWidth(FIELD_W);

        // initial state (no chips)
        syncBuildHint();

        // ===== link input row (same styles as profile modal) =====
        tfLink = new TextField();
        tfLink.getStyleClass().addAll("cy-search", "cy-profile-name-input");
        tfLink.setPromptText(I18n.t("cy.env.link.placeholder")); // i18n
        tfLink.setMinWidth(0.0);
        tfLink.setPrefWidth(FIELD_W);
        tfLink.setMaxWidth(Double.MAX_VALUE);

        btnLinkIn = new Button();
        btnLinkIn.getStyleClass().addAll("cy-profile-add-in", "icon-btn");
        btnLinkIn.setFocusTraversable(false);
        UiSvg.setButtonSvg(btnLinkIn, "plus.svg", 12);

        btnLinkIn.setOnAction(e -> onAddLink());

        tfLink.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                onAddLink();
            }
        });

        linkRow = new StackPane();
        linkRow.setAlignment(Pos.CENTER);
        linkRow.setPickOnBounds(false);

        linkRow.setPrefWidth(FIELD_W);
        linkRow.setMinWidth(FIELD_W);
        linkRow.setMaxWidth(FIELD_W);

        StackPane.setAlignment(btnLinkIn, Pos.CENTER_RIGHT);
        linkRow.getChildren().addAll(tfLink, btnLinkIn);

        // hidden by default
        linkRowShown = false;
        linkRow.setVisible(false);
        linkRow.setManaged(false);

        VBox valueBlock = new VBox(6);
        valueBlock.setAlignment(Pos.TOP_LEFT);
        valueBlock.setPrefWidth(FIELD_W);
        valueBlock.setMinWidth(FIELD_W);
        valueBlock.setMaxWidth(FIELD_W);
        valueBlock.getChildren().addAll(taValue, openRow, linkRow);

        btnSave = new Button(I18n.t("cy.env.save"));
        btnSave.getStyleClass().addAll("cy-modal-btn", "cy-modal-btn-primary");
        btnSave.setPrefWidth(FIELD_W);
        btnSave.setMinWidth(FIELD_W);
        btnSave.setMaxWidth(FIELD_W);
        btnSave.setOnAction(e -> onSave());

        // В TextArea ENTER — новая строка. Сохранение — Ctrl+Enter / Cmd+Enter.
        EventHandler<KeyEvent> onSaveHotkey = e -> {
            if (e.getCode() == KeyCode.ENTER && (e.isControlDown() || e.isMetaDown())) {
                e.consume();
                onSave();
            }
        };
        taValue.addEventHandler(KeyEvent.KEY_PRESSED, onSaveHotkey);

        EventHandler<KeyEvent> onEnterBtn = e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                onSave();
            }
        };
        btnSave.addEventHandler(KeyEvent.KEY_PRESSED, onEnterBtn);

        root.getChildren().addAll(
                lbl,
                firstRow,
                valueBlock,
                btnSave,
                themeColorSource
        );

        return root;
    }

    private void toggleLinkRow() {
        linkRowShown = !linkRowShown;

        if (linkRow != null) {
            linkRow.setVisible(linkRowShown);
            linkRow.setManaged(linkRowShown);
        }

        requestModalAndBackdropLayout();

        if (linkRowShown && tfLink != null) {
            Platform.runLater(() -> {
                try { tfLink.requestFocus(); } catch (Exception ignore) {}
            });
        }
    }

    private void onAddLink() {
        if (tfLink == null) return;

        String raw = safe(tfLink.getText());
        if (raw.isBlank()) return;

        String url = normalizeUrlForBrowse(raw);
        if (url.isBlank()) return;

        // simple duplicate guard
        if (buildLinks.contains(url)) {
            tfLink.clear();
            return;
        }

        String title = safe(UrlTitleExtractor.extractDisplayTitle(raw));
        if (title.isBlank()) title = raw;

        BuildLinkChip chip = new BuildLinkChip(title, url);

        buildLinks.add(url);
        if (fpLinks != null) fpLinks.getChildren().add(chip);

        tfLink.clear();

        syncBuildHint();

        if (linkRowShown) {
            Platform.runLater(() -> {
                try { tfLink.requestFocus(); } catch (Exception ignore) {}
            });
        }

        requestModalAndBackdropLayout();
    }

    private void addBuildLinkFromState(String rawUrl) {
        String url = safe(rawUrl);
        if (url.isBlank()) return;
        if (buildLinks.contains(url)) return;

        String title = safe(UrlTitleExtractor.extractDisplayTitle(url));
        if (title.isBlank()) title = url;

        BuildLinkChip chip = new BuildLinkChip(title, url);

        buildLinks.add(url);
        if (fpLinks != null) fpLinks.getChildren().add(chip);
    }

    private void syncBuildHint() {
        boolean hasChips = (fpLinks != null && !fpLinks.getChildren().isEmpty())
                || (!buildLinks.isEmpty());

        if (lbAddBuildHint != null) {
            lbAddBuildHint.setVisible(!hasChips);
            lbAddBuildHint.setManaged(!hasChips);
        }
        if (fpLinks != null) {
            fpLinks.setVisible(hasChips);
            fpLinks.setManaged(hasChips);
        }
    }

    private static String normalizeUrlForBrowse(String rawUrl) {
        String u = safe(rawUrl);
        if (u.isBlank()) return "";
        String lower = u.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) return u;
        return "https://" + u;
    }

    private void syncModeLabel() {
        if (lbMode == null || tg == null) return;
        lbMode.setText(
                tg.isSelected()
                        ? I18n.t("cy.env.mode.desktop")
                        : I18n.t("cy.env.mode.mobile")
        );
    }

    private void requestModalAndBackdropLayout() {
        if (modalRoot == null) return;

        modalRoot.requestLayout();

        Parent p = modalRoot.getParent();
        if (p != null) {
            p.requestLayout();
            Platform.runLater(() -> {
                try {
                    p.applyCss();
                    p.layout();
                } catch (Exception ignore) {
                }
            });
        }

        // ✅ critical: RightAnchoredModal sizes/positions UNMANAGED nodes manually,
        // so when content changes its preferred height (auto-grow), we must ask host to recalc.
        if (host != null) {
            host.requestRelayout();
        }
    }

    // ===================== ANTI-REOPEN GUARD =====================

    private void installAntiReopenGuard(StackPane rightRoot) {
        if (rightRoot == null) return;

        rightRoot.sceneProperty().addListener((o, oldS, newS) -> {
            if (newS != null) attachGuardToScene(newS);
        });

        Scene sc = rightRoot.getScene();
        if (sc != null) attachGuardToScene(sc);
    }

    private void attachGuardToScene(Scene sc) {
        sc.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (host == null || modalRoot == null) return;

            Object t = e.getTarget();
            if (!(t instanceof Node node)) return;

            if (isDescendantOf(node, modalRoot)) return;
            if (anchorNode != null && isDescendantOf(node, anchorNode)) return;

            if (isDescendantOf(node, chip)) {
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

    // ===================== DISABLE SCROLLBARS =====================

    private static void disableTextAreaScrollBars(TextArea ta) {
        if (ta == null) return;

        ta.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                Platform.runLater(() -> applyNoScrollPolicies(ta));
            }
        });

        Skin<?> s = ta.getSkin();
        if (s != null) {
            Platform.runLater(() -> applyNoScrollPolicies(ta));
        }
    }

    private static void applyNoScrollPolicies(TextArea ta) {
        if (ta == null) return;

        Node spNode = ta.lookup(".scroll-pane");
        if (spNode instanceof ScrollPane sp) {
            sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            sp.setFitToWidth(true);
        }
    }

    // ===================== THEME SVG BINDING =====================

    private static void bindToTheme(Node n, Label themeColorSource) {
        if (n == null || themeColorSource == null) return;

        if (n instanceof SVGPath p) {
            ChangeListener<Paint> apply = (obs, o, v) -> {};
            themeColorSource.textFillProperty().addListener(apply);
            p.fillProperty().bind(themeColorSource.textFillProperty());
            p.setMouseTransparent(true);
        } else {
            n.setMouseTransparent(true);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
