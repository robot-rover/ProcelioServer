package procul.studios.util;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Region;
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
}
