package procul.studios;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.util.function.Supplier;

public class ConfigEditor extends RowEditor {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigEditor.class);
    private final LauncherSettings settings;
    private Runnable closeWindow;
    Supplier<String> installDir;
    ProcelioLauncher launcher;
    boolean useDevBuilds;
    public ConfigEditor(ProcelioLauncher launcher, LauncherSettings settings, Runnable closeWindow) {
        this.settings = settings;
        this.closeWindow = closeWindow;
        this.launcher = launcher;
        this.useDevBuilds = settings.useDevBuilds;

        installDir = addDirectoryRow("Install Directory", settings.installDir);
        addButtonRow("Modify Install", "Change the installed game (e.g. uninstall)", this::modifyInstall);
        addCheckboxRow(settings.useDevBuilds, "Opt in to dev builds", this::checkboxSet);
        addButtonRow("Legal Info", "Copyright stuff", this::licenses);

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

    private void licenses(ActionEvent ae) {
        Stage window = new Stage();
        window.setMaxHeight(800);
        window.setHeight(600);
        window.setMaxWidth(640);
        window.setScene(new Scene(new LicenseDisplayScene(window::close)));
        window.show();
    }

    private void commitChanges() {
        settings.configured = true;
        settings.installDir = installDir.get();
        settings.useDevBuilds = this.useDevBuilds;
        System.out.println(installDir.get());
        launcher.settings = settings;
        try {
            launcher.saveSettings();
            launcher.loadPaths();
        } catch (IOException e) {
            FX.dialog("Couldn't save", "Unable to save settings", Alert.AlertType.ERROR);
        }
        closeWindow.run();
    }

    private void checkboxSet(ActionEvent ae) {
        this.useDevBuilds = ((CheckBox)ae.getSource()).isSelected();
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
