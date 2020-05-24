package procul.studios;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.io.File;
import java.util.Optional;


public class InstallationModifier extends RowEditor {
    private final LauncherSettings settings;
    private Runnable closeWindow;
    String path;
    Button cancel;
    public InstallationModifier(LauncherSettings settings, String path, Runnable closeWindow) {
        this.settings = settings;
        this.closeWindow = closeWindow;
        this.path = path;

        addStyledButtonRow("Uninstall",
                "Uninstall game, leaving local data (e.g. settings)",
                "-fx-border-color: #ff0000; -fx-background-color: #cc4444; ",
                "", this::partialUninstall);
        HBox buttons = new HBox();
        buttons.setAlignment(Pos.BASELINE_RIGHT);
        buttons.setSpacing(15);
        buttons.setPadding(new Insets(10));
        this.setBottom(buttons);

        cancel = new Button("Return");
        cancel.setCancelButton(true);
        cancel.setPadding(new Insets(5, 15, 5, 15));
        cancel.addEventHandler(ActionEvent.ACTION, event -> closeWindow.run());
        buttons.getChildren().add(cancel);

    }

    private void commitChanges() {
        if (closeWindow != null)
            closeWindow.run();
    }



    private void partialUninstall(ActionEvent ae) {
        System.out.println("Removing game");

        File f = new File(path);
        if (!f.exists() || !f.isDirectory()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Error: Invalid path");
            a.setHeaderText(null);
            Label label = new Label("Could not find directory\n"+path);
            label.setWrapText(true);
            a.getDialogPane().setContent(label);

            a.showAndWait();

            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Procelio Game Install?");
        alert.setHeaderText("Do you want to delete data?");
        alert.setContentText("All files in "+path+" will be cleared");

        Label label = new Label("Will delete all data in\n"+path);
        label.setWrapText(true);
        alert.getDialogPane().setContent(label);

        ButtonType buttonTypeOne = new ButtonType("YES");
        ButtonType buttonTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(buttonTypeOne, buttonTypeCancel);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.orElse(null) == buttonTypeOne){
            // ... user chose "One"
            LauncherUtilities.deleteRecursive(f);
            commitChanges();
        }  else {
            // ... user chose CANCEL or closed the dialog
        }
    }
}
