package app.shell;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

public final class SettingsDialog {

    private SettingsDialog() {}

    public static void showModal(Stage owner) {
        Stage stage = new Stage();
        stage.setTitle("Settings");
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);

        VBox root = new VBox(12);
        root.setPadding(new Insets(14));
        root.getStyleClass().add("settings-modal");

        Label h = new Label("Settings (заглушка)");
        h.getStyleClass().add("settings-title");

        Label hint = new Label("Здесь позже будут секции настроек (editModeEnabled и др.).");
        hint.getStyleClass().add("muted");

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Button b1 = new Button("General");
        Button b2 = new Button("Data");
        Button b3 = new Button("Edit Mode");
        Button b4 = new Button("About");

        b1.getStyleClass().add("chip-button");
        b2.getStyleClass().add("chip-button");
        b3.getStyleClass().add("chip-button");
        b4.getStyleClass().add("chip-button");

        row.getChildren().addAll(b1, b2, b3, b4);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button close = new Button("Close");
        close.getStyleClass().add("product-button");
        close.setOnAction(e -> stage.close());
        actions.getChildren().add(close);

        root.getChildren().addAll(h, hint, row, spacer, actions);

        Scene scene = new Scene(root, 520, 240);
        scene.getStylesheets().add(SettingsDialog.class.getResource("/ui/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }
}
