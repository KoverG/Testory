package app.domain.cycles.ui.right;

import app.core.I18n;
import app.core.PrivateRootConfig;
import app.domain.cycles.CyclePrivateConfig;
import app.domain.cycles.ui.modal.RightAnchoredModal;
import app.ui.UiComboBox;
import app.ui.UiSvg;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Profile modal for right card (Cycles).
 */
final class ProfileModal {

    private static final double FIELD_W = 220.0;

    // ✅ UX polish: placeholder opacity to hint "non-standard state"
    private static final double COMBO_HINT_OPACITY = 0.62;

    private final Button anchorBtn;
    private RightAnchoredModal host;

    private Runnable onBeforeOpen = () -> {};
    private Supplier<String> currentNameSupplier = () -> "";
    private Consumer<String> onSaved = s -> {};

    // UI
    private ComboBox<String> cbUsers;
    private CheckBox chRemember;
    private TextField tfName;
    private Button btnAddUser;
    private Button btnSave;

    // UX state
    @SuppressWarnings("FieldCanBeLocal")
    private boolean savedThisSession = false;

    // ✅ Pending additions via "+" that are not persisted until Save
    private final Set<String> pendingAddedNames = new HashSet<>();

    // i18n placeholders for ComboBox (rendered via buttonCell, not promptText)
    private String comboHintDefault = "";
    private String comboHintOutside = "";
    private String comboHintEmptyList = "";
    private String comboHintCurrent = "";

    ProfileModal(Button anchorBtn) {
        this.anchorBtn = anchorBtn;
    }

    public void install(
            StackPane rightRoot,
            Runnable onBeforeOpen,
            Supplier<String> currentNameSupplier,
            Consumer<String> onSaved
    ) {
        if (rightRoot == null || anchorBtn == null) return;

        this.onBeforeOpen = onBeforeOpen == null ? () -> {} : onBeforeOpen;
        this.currentNameSupplier = currentNameSupplier == null ? () -> "" : currentNameSupplier;
        this.onSaved = onSaved == null ? s -> {} : onSaved;

        host = new RightAnchoredModal(rightRoot, anchorBtn, this::buildModal);
        host.setOnBeforeOpen(() -> {
            this.onBeforeOpen.run();
            syncUiOnOpen();
        });
        host.install();
        host.close();

        anchorBtn.setOnAction(e -> host.toggle());
    }

    public void close() {
        if (host != null) host.close();
    }

    private VBox buildModal() {
        VBox root = new VBox();
        root.setAlignment(Pos.CENTER);

        root.getStyleClass().add("tc-right-confirm");
        root.getStyleClass().add("cy-menu-modal");

        root.setMinWidth(300);
        root.setPrefWidth(300);
        root.setMaxWidth(300);

        root.setMinHeight(Region.USE_PREF_SIZE);
        root.setMaxHeight(Region.USE_PREF_SIZE);

        Label lbl = new Label(I18n.t("cy.profile.title"));
        lbl.setWrapText(true);
        lbl.setMaxWidth(300 - 28);
        lbl.getStyleClass().add("tc-delete-title");
        VBox.setMargin(lbl, new Insets(4, 0, 0, 0));

        Region topGap = new Region();
        topGap.setPrefHeight(20);
        topGap.setMinHeight(20);
        topGap.setMaxHeight(20);

        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setMinHeight(16);
        box.setMaxWidth(Double.MAX_VALUE);

        cbUsers = new ComboBox<>();
        cbUsers.getStyleClass().addAll("cy-profile-combo", "app-combo");
        UiComboBox.install(cbUsers, 0.92);

        // ✅ placeholders via i18n (rendered by buttonCell)
        comboHintDefault = safe(I18n.t("cy.profile.combo.placeholder"));
        comboHintOutside = safe(I18n.t("cy.profile.combo.outsideList"));
        comboHintEmptyList = safe(I18n.t("cy.profile.combo.emptyList"));
        comboHintCurrent = comboHintDefault;

        // keep promptText (actual hint is buttonCell)
        cbUsers.setPromptText(comboHintDefault);

        cbUsers.setPrefWidth(FIELD_W);
        cbUsers.setMinWidth(FIELD_W);
        cbUsers.setMaxWidth(FIELD_W);

        // dropdown cells: normal items
        cbUsers.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
                setOpacity(1.0);
            }
        });

        applyComboButtonCell();

        tfName = new TextField();
        tfName.getStyleClass().addAll("cy-search", "cy-profile-name-input");
        tfName.setPromptText(I18n.t("cy.profile.input.placeholder"));
        tfName.setMinWidth(0.0);
        tfName.setPrefWidth(FIELD_W);
        tfName.setMaxWidth(Double.MAX_VALUE);

        // ✅ UX: prevent empty popup when list is empty (robust for skin+keyboard)
        cbUsers.setOnShowing(e -> {
            if (isUsersListEmpty()) {
                // hide() inside onShowing sometimes doesn't prevent showing -> do it in next tick
                Platform.runLater(() -> {
                    if (isUsersListEmpty()) cbUsers.hide();
                });
            }
        });

        EventHandler<MouseEvent> blockEmptyPopupMouse = e -> {
            if (isUsersListEmpty()) {
                e.consume();
                requestFocusNameField();
            }
        };
        cbUsers.addEventFilter(MouseEvent.MOUSE_PRESSED, blockEmptyPopupMouse);
        cbUsers.addEventFilter(MouseEvent.MOUSE_RELEASED, blockEmptyPopupMouse);
        cbUsers.addEventFilter(MouseEvent.MOUSE_CLICKED, blockEmptyPopupMouse);

        cbUsers.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!isUsersListEmpty()) return;

            KeyCode c = e.getCode();
            // keys that can open popup
            if (c == KeyCode.SPACE || c == KeyCode.ENTER || c == KeyCode.DOWN || c == KeyCode.UP) {
                e.consume();
                requestFocusNameField();
                Platform.runLater(() -> {
                    if (isUsersListEmpty()) cbUsers.hide();
                });
            }
        });

        btnAddUser = new Button();
        btnAddUser.getStyleClass().addAll("cy-profile-add-in", "icon-btn");
        btnAddUser.setFocusTraversable(false);
        UiSvg.setButtonSvg(btnAddUser, "plus.svg", 12);

        Tooltip.install(btnAddUser, new Tooltip(I18n.t("cy.profile.addUser.tooltip")));
        btnAddUser.setOnAction(e -> onAddUser());

        StackPane nameRow = new StackPane();
        nameRow.setAlignment(Pos.CENTER);
        nameRow.setPickOnBounds(false);

        nameRow.setPrefWidth(FIELD_W);
        nameRow.setMinWidth(FIELD_W);
        nameRow.setMaxWidth(FIELD_W);

        StackPane.setAlignment(btnAddUser, Pos.CENTER_RIGHT);
        nameRow.getChildren().addAll(tfName, btnAddUser);

        chRemember = new CheckBox(I18n.t("cy.profile.remember"));
        chRemember.setFocusTraversable(false);
        chRemember.getStyleClass().addAll(
                "tc-trash-check",
                "cy-profile-remember",
                "cy-profile-remember-sm",
                "cy-profile-remember-xs"
        );

        chRemember.setAlignment(Pos.CENTER_LEFT);
        chRemember.setMaxWidth(FIELD_W);

        VBox nameAndRemember = new VBox(4);
        nameAndRemember.setAlignment(Pos.CENTER_LEFT);
        nameAndRemember.setPrefWidth(FIELD_W);
        nameAndRemember.setMinWidth(FIELD_W);
        nameAndRemember.setMaxWidth(FIELD_W);
        nameAndRemember.getChildren().addAll(nameRow, chRemember);

        btnSave = new Button(I18n.t("cy.profile.save"));
        btnSave.getStyleClass().addAll("cy-modal-btn", "cy-modal-btn-primary");
        btnSave.setPrefWidth(FIELD_W);
        btnSave.setMinWidth(FIELD_W);
        btnSave.setMaxWidth(FIELD_W);
        btnSave.setOnAction(e -> onSave());

        box.getChildren().addAll(cbUsers, nameAndRemember, btnSave);
        root.getChildren().addAll(lbl, topGap, box);

        cbUsers.setOnAction(e -> {
            String sel = safe(cbUsers.getSelectionModel().getSelectedItem());
            if (!sel.isEmpty()) {
                tfName.setText(sel);
                tfName.positionCaret(tfName.getText().length());
            }
            updateUiByCurrentName();
        });

        tfName.textProperty().addListener((o, a, b) -> updateUiByCurrentName());

        EventHandler<KeyEvent> onEnter = e -> {
            if (e.getCode() == KeyCode.ENTER) {
                onSave();
                e.consume();
            }
        };
        cbUsers.addEventHandler(KeyEvent.KEY_PRESSED, onEnter);
        tfName.addEventHandler(KeyEvent.KEY_PRESSED, onEnter);
        btnSave.addEventHandler(KeyEvent.KEY_PRESSED, onEnter);

        syncUiOnOpen();
        return root;
    }

    private void syncUiOnOpen() {
        if (cbUsers == null || chRemember == null || tfName == null) return;

        savedThisSession = false;
        pendingAddedNames.clear();

        List<String> users = PrivateRootConfig.qaUsers();
        cbUsers.getItems().setAll(users);

        boolean remember = CyclePrivateConfig.rememberQaEnabled();
        String remembered = safe(CyclePrivateConfig.rememberedQaName());

        chRemember.setSelected(remember);

        String cur = safe(currentNameSupplier.get());
        String initial = !cur.isEmpty()
                ? cur
                : (remember ? remembered : "");

        tfName.setText(initial);

        clearComboSelectionHard();
        updateUiByCurrentName();

        // ✅ UX: focus always on input when modal opens
        requestFocusNameField();
    }

    private void onAddUser() {
        String n = safe(tfName.getText());
        if (n.isEmpty()) return;

        String match = findMatch(new ArrayList<>(cbUsers.getItems()), n);
        if (!match.isEmpty()) {
            cbUsers.getSelectionModel().select(match);
            updateUiByCurrentName();
            return;
        }

        cbUsers.getItems().add(n);
        cbUsers.getSelectionModel().select(n);

        pendingAddedNames.add(n);
        updateUiByCurrentName();
    }

    private void onSave() {
        if (btnSave != null && btnSave.isDisabled()) return;

        String name = safe(tfName.getText());
        String sel = safe(cbUsers == null ? "" : cbUsers.getSelectionModel().getSelectedItem());

        if (name.isEmpty() && sel.isEmpty()) return;

        boolean remember = chRemember.isSelected();

        if (remember) {
            CyclePrivateConfig.setRememberQa(true, name);
        } else {
            CyclePrivateConfig.setRememberQa(false, "");
        }

        if (!name.isEmpty() && isPendingAddedName(name)) {
            PrivateRootConfig.addQaUser(name);
        }

        savedThisSession = true;
        pendingAddedNames.clear();

        onSaved.accept(name);
        if (host != null) host.close();
    }

    private void updateUiByCurrentName() {
        if (cbUsers == null || tfName == null) return;

        String name = safe(tfName.getText());
        List<String> users = new ArrayList<>(cbUsers.getItems());
        boolean listEmpty = users.isEmpty();

        if (name.isEmpty()) {
            setComboHint(listEmpty ? comboHintEmptyList : comboHintDefault);
            clearComboSelectionHard();
        } else {
            String match = findMatch(users, name);
            if (match.isEmpty()) {
                // ✅ UX FIX:
                // если список пуст — остаёмся в emptyList, а не "outsideList"
                setComboHint(listEmpty ? comboHintEmptyList : comboHintOutside);
                clearComboSelectionHard();
            } else {
                setComboHint(comboHintDefault);
                String curSel = safe(cbUsers.getSelectionModel().getSelectedItem());
                if (!match.equalsIgnoreCase(curSel)) {
                    cbUsers.getSelectionModel().select(match);
                }
            }
        }

        updateAddUserVisibility();
        updateSaveEnabled();
    }

    private void updateAddUserVisibility() {
        if (cbUsers == null || tfName == null || btnAddUser == null) return;

        String n = safe(tfName.getText());
        List<String> users = new ArrayList<>(cbUsers.getItems());

        boolean show = !n.isEmpty() && findMatch(users, n).isEmpty();
        btnAddUser.setVisible(show);
        btnAddUser.setManaged(show);
    }

    private void updateSaveEnabled() {
        if (btnSave == null || cbUsers == null || tfName == null) return;

        String sel = safe(cbUsers.getSelectionModel().getSelectedItem());
        String name = safe(tfName.getText());

        boolean disable = sel.isEmpty() && name.isEmpty();
        btnSave.setDisable(disable);
    }

    private void setComboHint(String hint) {
        String h = safe(hint);
        if (h.isEmpty()) h = comboHintDefault;

        if (!h.equals(comboHintCurrent)) {
            comboHintCurrent = h;
            applyComboButtonCell();
        }
        cbUsers.setPromptText(comboHintCurrent);
    }

    private void applyComboButtonCell() {
        if (cbUsers == null) return;

        cbUsers.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                boolean showHint = empty || item == null || safe(item).isEmpty();
                if (showHint) {
                    setText(comboHintCurrent);
                    setOpacity(COMBO_HINT_OPACITY);
                } else {
                    setText(item);
                    setOpacity(1.0);
                }
            }
        });
    }

    private void clearComboSelectionHard() {
        if (cbUsers == null) return;
        try { cbUsers.getSelectionModel().clearSelection(); } catch (Exception ignore) {}
        try { cbUsers.setValue(null); } catch (Exception ignore) {}
    }

    private boolean isPendingAddedName(String name) {
        String n = safe(name);
        if (n.isEmpty()) return false;

        for (String p : pendingAddedNames) {
            if (p != null && p.trim().equalsIgnoreCase(n)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUsersListEmpty() {
        return cbUsers == null || cbUsers.getItems() == null || cbUsers.getItems().isEmpty();
    }

    private void requestFocusNameField() {
        if (tfName == null) return;
        Platform.runLater(() -> {
            try {
                tfName.requestFocus();
                tfName.positionCaret(tfName.getText() == null ? 0 : tfName.getText().length());
            } catch (Exception ignore) {}
        });
    }

    private static String findMatch(List<String> users, String name) {
        String n = safe(name);
        if (n.isEmpty()) return "";

        for (String u : users) {
            if (u != null && u.trim().equalsIgnoreCase(n)) {
                return u.trim();
            }
        }
        return "";
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
