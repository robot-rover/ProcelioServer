package procul.studios;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.function.Supplier;

public class RowEditor extends BorderPane {
    GridPane grid;

    public RowEditor() {
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
    }

    /**
     * Add a single text element to a new row
     * @param text The value of the text to add
     */
    protected void addTextRow(String text) {
        Text desc = new Text(text);
        grid.addRow(grid.getRowCount(), desc);
    }

    /**
     * Add an unstyled row with a button and description to the grid
     * @param button The text inside the button
     * @param description The description (to the right of the button)
     * @param ae The callback for when the button gets clicked
     */
    protected void addButtonRow(String button, String description, EventHandler<ActionEvent> ae) {
        Button b = new Button(button);
        b.setOnAction(ae);
        Text desc = new Text(description);
        grid.addRow(grid.getRowCount(), b, desc);
    }

    /**
     * Add a row with a styled button & description to the grid
     * @param button The text in the button
     * @param description The description (to the right of the button
     * @param buttonStyle The style of the button
     * @param descriptionStyle The style of the description
     * @param ae The callback for when the button gets clicked
     */
    protected void addStyledButtonRow(String button, String description,
                                    String buttonStyle, String descriptionStyle, EventHandler<ActionEvent> ae) {
        Button b = new Button(button);
        b.setOnAction(ae);
        b.setStyle(buttonStyle);
        Text desc = new Text(description);
        desc.setStyle(descriptionStyle);
        grid.addRow(grid.getRowCount(), b, desc);
    }

    /**
     * Add a row with a text input field
     * @param name The value of the label of the field
     * @param value The starting value of the text field
     * @return A supplier for getting the value of the input field
     */
    protected Supplier<String> addTextInputRow(String name, String value) {
        Label title = new Label(name);
        TextField input = new TextField(value);
        input.setPrefWidth(400);
        grid.addRow(grid.getRowCount(), title, input);
        return input::getText;
    }

    /**
     * Add a row with a text input field and directory-picker
     * @param name The label of the row
     * @param value The initial directory path
     * @return A supplier to get the value of the row (MAY NOT BE VALID PATH)
     */
    protected Supplier<String> addDirectoryRow(String name, String value) {
        Label title = new Label(name);
        TextField input = new TextField(value);
        input.setPrefWidth(400);
        DirectoryChooser dc = new DirectoryChooser();
        File f = new File(value);
        if (f.isDirectory())
            dc.setInitialDirectory(f);

        ImageView dirpick = new ImageView(ImageResources.load("pick_dir_small.png"));
        dirpick.setPreserveRatio(true);
        dirpick.setFitWidth(input.getHeight());
        dirpick.setFitHeight(input.getHeight());
        dirpick.setOnMouseClicked(e -> {
            File selectedDirectory = dc.showDialog(new Stage());
            input.setText(selectedDirectory.toString());
        });


        grid.addRow(grid.getRowCount(), title, input, dirpick);
        return input::getText;
    }
}
