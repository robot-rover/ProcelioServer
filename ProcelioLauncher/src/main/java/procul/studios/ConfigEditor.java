package procul.studios;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.function.Supplier;

public class ConfigEditor extends RowEditor {
    private final LauncherSettings settings;
    private Runnable closeWindow;
    Supplier<String> installDir;

    public ConfigEditor(LauncherSettings settings, Runnable closeWindow) {
        this.settings = settings;
        this.closeWindow = closeWindow;

        installDir = addDirectoryRow("Install Directory", settings.installDir);
        addButtonRow("Modify Install", "Change the installed game (e.g. uninstall)", this::modifyInstall);

        HBox buttons = new HBox();
        buttons.setAlignment(Pos.BASELINE_RIGHT);
        buttons.setSpacing(15);
        buttons.setPadding(new Insets(10));
        this.setBottom(buttons);

        Button accept = new Button("Accept");
        accept.setDefaultButton(true);
        accept.setPadding(new Insets(5, 15, 5, 15));
        accept.addEventHandler(ActionEvent.ACTION, event -> commitChanges());
        buttons.getChildren().add(accept);

        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setPadding(new Insets(5, 15, 5, 15));
        cancel.addEventHandler(ActionEvent.ACTION, event -> closeWindow.run());
        buttons.getChildren().add(cancel);

    }

    private void commitChanges() {
        settings.configured = true;
        settings.installDir = installDir.get();
        System.out.println(installDir.get());
        closeWindow.run();
    }

    private void modifyInstall(ActionEvent ae) {
        Stage window = new Stage();
        window.setMaxHeight(480);
        window.setHeight(400);
        window.setMaxWidth(640);
        window.setScene(new Scene(new InstallationModifier(settings, installDir.get(), window::close)));
        window.show();
    }


}
