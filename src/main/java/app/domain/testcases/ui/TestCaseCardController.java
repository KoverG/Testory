// FILE: src/main/java/app/domain/testcases/ui/TestCaseCardController.java
package app.domain.testcases.ui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Reusable controller for testcase right card fragment.
 *
 * This controller is UI-only:
 * - owns @FXML references of the card nodes
 * - exposes them via getters to the host screen
 * - delegates user actions via callbacks (set by host)
 *
 * It does NOT create TestCaseRightPane and does NOT contain business logic.
 */
public class TestCaseCardController {

    // Root
    @FXML private VBox rightCard;

    // Top row
    @FXML private HBox rightTopRow;

    @FXML private HBox taskLinkHost;
    @FXML private TextField tfPrivTop;
    @FXML private Button btnEditPriv;

    @FXML private TextField tfTop2;

    @FXML private VBox rightInlineStripBox;
    @FXML private Label lbRightInlineTitle;
    @FXML private Region rightInlineStrip;

    @FXML private Button btnEdit;
    @FXML private Button btnCloseRight;

    // Body
    @FXML private VBox rightBody;

    @FXML private StackPane titleWrap;
    @FXML private TextField tfTitle;
    @FXML private Label lbTitleDisplay;

    @FXML private StackPane rightStack;

    @FXML private ScrollPane spRight;
    @FXML private VBox rightScrollRoot;

    @FXML private FlowPane fpRightLabels;
    @FXML private TextField tfRightLabel;
    @FXML private Button btnAddRightLabel;

    @FXML private FlowPane fpRightTags;
    @FXML private TextField tfRightTag;
    @FXML private Button btnAddRightTag;

    @FXML private TextArea taRightDescription;

    @FXML private VBox stepsBlock;
    @FXML private Label lbStepsTitle;
    @FXML private VBox stepsBox;
    @FXML private Button btnAddStep;

    @FXML private VBox rightScrollContent;
    @FXML private Region rightScrollBottomSpacer;

    // Bottom overlay buttons
    @FXML private HBox cycleNavigationBox;
    @FXML private Button btnCyclePrev;
    @FXML private Button btnCycleNext;
    @FXML private Button btnDeleteRight;
    @FXML private Button btnSaveRight;

    // ===================== callbacks =====================

    private Consumer<ActionEvent> onEditPriv;
    private Consumer<ActionEvent> onEdit;
    private Consumer<ActionEvent> onCloseRight;
    private Consumer<ActionEvent> onAddRightLabel;
    private Consumer<ActionEvent> onAddRightTag;
    private Consumer<ActionEvent> onAddStep;

    public void setOnEditPriv(Consumer<ActionEvent> cb) { this.onEditPriv = cb; }
    public void setOnEdit(Consumer<ActionEvent> cb) { this.onEdit = cb; }
    public void setOnCloseRight(Consumer<ActionEvent> cb) { this.onCloseRight = cb; }
    public void setOnAddRightLabel(Consumer<ActionEvent> cb) { this.onAddRightLabel = cb; }
    public void setOnAddRightTag(Consumer<ActionEvent> cb) { this.onAddRightTag = cb; }
    public void setOnAddStep(Consumer<ActionEvent> cb) { this.onAddStep = cb; }

    // ===================== FXML handlers (called by fragment FXML) =====================

    @FXML
    private void onEditPriv(ActionEvent e) {
        if (onEditPriv != null) onEditPriv.accept(e);
    }

    @FXML
    private void onEdit(ActionEvent e) {
        if (onEdit != null) onEdit.accept(e);
    }

    @FXML
    private void onCloseRight(ActionEvent e) {
        if (onCloseRight != null) onCloseRight.accept(e);
    }

    @FXML
    private void onAddRightLabel(ActionEvent e) {
        if (onAddRightLabel != null) onAddRightLabel.accept(e);
    }

    @FXML
    private void onAddRightTag(ActionEvent e) {
        if (onAddRightTag != null) onAddRightTag.accept(e);
    }

    @FXML
    private void onAddStep(ActionEvent e) {
        if (onAddStep != null) onAddStep.accept(e);
    }

    // ===================== getters for host screen =====================

    public VBox rightCard() { return rightCard; }
    public HBox rightTopRow() { return rightTopRow; }

    public HBox taskLinkHost() { return taskLinkHost; }
    public TextField tfPrivTop() { return tfPrivTop; }
    public Button btnEditPriv() { return btnEditPriv; }
    public TextField tfTop2() { return tfTop2; }

    public VBox rightInlineStripBox() { return rightInlineStripBox; }
    public Label lbRightInlineTitle() { return lbRightInlineTitle; }
    public Region rightInlineStrip() { return rightInlineStrip; }

    public Button btnEdit() { return btnEdit; }
    public Button btnCloseRight() { return btnCloseRight; }

    public VBox rightBody() { return rightBody; }

    public StackPane titleWrap() { return titleWrap; }
    public TextField tfTitle() { return tfTitle; }
    public Label lbTitleDisplay() { return lbTitleDisplay; }

    public StackPane rightStack() { return rightStack; }

    public ScrollPane spRight() { return spRight; }
    public VBox rightScrollRoot() { return rightScrollRoot; }

    public FlowPane fpRightLabels() { return fpRightLabels; }
    public TextField tfRightLabel() { return tfRightLabel; }
    public Button btnAddRightLabel() { return btnAddRightLabel; }

    public FlowPane fpRightTags() { return fpRightTags; }
    public TextField tfRightTag() { return tfRightTag; }
    public Button btnAddRightTag() { return btnAddRightTag; }

    public TextArea taRightDescription() { return taRightDescription; }

    public VBox stepsBlock() { return stepsBlock; }
    public Label lbStepsTitle() { return lbStepsTitle; }
    public VBox stepsBox() { return stepsBox; }
    public Button btnAddStep() { return btnAddStep; }

    public VBox rightScrollContent() { return rightScrollContent; }
    public Region rightScrollBottomSpacer() { return rightScrollBottomSpacer; }

    public HBox cycleNavigationBox() { return cycleNavigationBox; }
    public Button btnCyclePrev() { return btnCyclePrev; }
    public Button btnCycleNext() { return btnCycleNext; }
    public Button btnDeleteRight() { return btnDeleteRight; }
    public Button btnSaveRight() { return btnSaveRight; }
}