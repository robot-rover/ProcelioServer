package procul.studios.util;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

public class FX {
    public static void listenForSize(Stage stage){
        ChangeListener<Number> stageSizeListener = (observable, oldValue, newValue) ->
                System.out.println("Height: " + stage.getHeight() + " Width: " + stage.getWidth());

        stage.widthProperty().addListener(stageSizeListener);
        stage.heightProperty().addListener(stageSizeListener);
    }


    public static void dialog(String title, String content, Alert.AlertType type){
        if(!Platform.isFxApplicationThread()){
            Platform.runLater(() -> dialog(title, content, type));
            return;
        }
        Alert dialog = new Alert(type);
        dialog.setContentText(content);
        dialog.setTitle(title);
        //Fix for Linux text cutoff
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        dialog.show();
    }

    public static Optional<String> prompt(String title, String content){
        if(!Platform.isFxApplicationThread()){
            CountDownLatch latch = new CountDownLatch(1);
            final String[] result = new String[]{null};
            Platform.runLater(() -> {result[0] = prompt(title, content).orElse(null); latch.countDown();});
            try {
                latch.await();
            } catch (InterruptedException e) {
                return Optional.empty();
            }
            return Optional.ofNullable(result[0]);
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setContentText(content);
        dialog.setTitle(title);
        //Fix for Linux text cutoff
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        return dialog.showAndWait();
    }

    public static Optional<Boolean> accept(String title, String content) {
        if(!Platform.isFxApplicationThread()){
            CountDownLatch latch = new CountDownLatch(1);
            final Boolean[] result = new Boolean[]{false};
            Platform.runLater(() -> {result[0] = accept(title, content).orElse(false); latch.countDown();});
            try {
                latch.await();
            } catch (InterruptedException e) {
                return Optional.empty();
            }
            return Optional.ofNullable(result[0]);
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setContentText(content);

        //Fix for Linux text cutoff
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.get() == ButtonType.OK){
            return Optional.of(true);
        } else {
            return Optional.of(false);
        }
    }

    public static Optional<Boolean> acceptLong(String title, String scroll, String content) {
        if(!Platform.isFxApplicationThread()){
            CountDownLatch latch = new CountDownLatch(1);
            final Boolean[] result = new Boolean[]{false};
            Platform.runLater(() -> {result[0] = acceptLong(title, scroll, content).orElse(false); latch.countDown();});
            try {
                latch.await();
            } catch (InterruptedException e) {
                return Optional.empty();
            }
            return Optional.ofNullable(result[0]);
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        VBox alertRoot = new VBox();
        alert.getDialogPane().setContent(alertRoot);
        alert.getDialogPane().setMaxWidth(600);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        alertRoot.getChildren().add(scrollPane);

        Label alertText = new Label(scroll);
        alertText.setWrapText(true);
        scrollPane.setContent(alertText);

        Label notScroll = new Label(content);
        alertRoot.getChildren().add(notScroll);

        //Fix for Linux text cutoff
        //alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.get() == ButtonType.OK){
            return Optional.of(true);
        } else {
            return Optional.of(false);
        }
    }
}
