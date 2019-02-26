package procul.studios;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.util.function.Supplier;

public class ConfigEditor extends BorderPane {
    private final LauncherSettings settings;
    private Runnable closeWindow;
    GridPane grid;
    Supplier<String> installDir;

    public ConfigEditor(LauncherSettings settings, Runnable closeWindow) {
        this.settings = settings;
        this.closeWindow = closeWindow;
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        this.setCenter(scroll);
        grid = new GridPane();
        grid.setPadding(new Insets(15));
        grid.setHgap(10);
        grid.setVgap(10);
        ColumnConstraints names = new ColumnConstraints(150);
        ColumnConstraints values = new ColumnConstraints();
        values.setFillWidth(true);
        grid.getColumnConstraints().add(names);
        grid.getColumnConstraints().add(values);
        scroll.setContent(grid);

        installDir = addRow("Install Directory", settings.installDir);
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
        settings.installDir = installDir.get();
        System.out.println(installDir.get());
        closeWindow.run();
    }

    private Supplier<String> addRow(String name, String value) {
        Label title = new Label(name);
        TextField input = new TextField(value);
        input.setPrefWidth(400);
        grid.addRow(grid.getRowCount(), title, input);
        return input::getText;
    }
}
