package procul.studios;

import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.gson.LauncherConfiguration;
import procul.studios.util.Version;

import java.util.List;

import static javafx.scene.input.MouseEvent.MOUSE_ENTERED;
import static javafx.scene.input.MouseEvent.MOUSE_EXITED;

public class NewsList extends ScrollPane {

    private static final Logger LOG = LoggerFactory.getLogger(NewsList.class);

    private static final int itemWidth = 200;
    private static final int itemHeight = 250;

    private static final Color backgroundColor = Color.BLACK.deriveColor(1, 1, 1, 0.5);
    private static final Background backgroundObject = new Background(new BackgroundFill(backgroundColor, CornerRadii.EMPTY, Insets.EMPTY));

    int pos = 50;


    public NewsList( List<LauncherConfiguration.Update> updates) {
        Node topNode = null;
        Pane bottomNode = null;
        Pane offsetNode = null;

        setFitToHeight(true);
        setVbarPolicy(ScrollBarPolicy.NEVER);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setPannable(true);

        HBox contentRoot = new HBox();
        contentRoot.minWidthProperty().bind(widthProperty().subtract(2));
        contentRoot.setAlignment(Pos.CENTER_RIGHT);
        contentRoot.setFillHeight(false);
        contentRoot.setSpacing(50);
        contentRoot.setPadding(new Insets(10,50,10,50));
        setHvalue(1);
        contentRoot.setOnScroll(event -> {

            if (event.getDeltaY() > 0)
                setHvalue((pos == 0 ? 0 : pos--)/50.0);
            else
                setHvalue((pos == 50 ? 50 : pos++)/50.0);

        });
//        contentRoot.setStyle("-fx-border-color: white");
        setContent(contentRoot);

        for(LauncherConfiguration.Update update : updates) {
            boolean imageBackground = false;
            StackPane updateRoot = new StackPane();
            updateRoot.setPrefSize(itemWidth, Integer.MAX_VALUE);
            updateRoot.setBackground(backgroundObject);
            updateRoot.layoutBoundsProperty().addListener((observable, oldValue, newValue) -> {
                updateRoot.setClip(new Rectangle(newValue.getWidth(), newValue.getHeight()));
            });
//            updateRoot.setClip(new Rectangle(itemWidth, itemHeight));
            updateRoot.setAlignment(Pos.TOP_CENTER);
            contentRoot.getChildren().add(updateRoot);

            if(update.image != null) {
                imageBackground = true;
                ImageView updateBackground = new ImageView();
                Image image = new Image(update.image);
                updateBackground.setImage(image);
                updateBackground.setPreserveRatio(true);
                updateBackground.setFitWidth(itemWidth);
                updateBackground.setFitHeight(itemHeight);
                updateRoot.getChildren().add(updateBackground);
            }

            VBox updateContent = new VBox();
            updateContent.setAlignment(Pos.BOTTOM_CENTER);
            updateContent.setPrefSize(itemWidth, itemHeight);
            topNode = updateContent;
            updateRoot.getChildren().add(updateContent);

            if(update.version != null) {
                StackPane versionHolder = new StackPane();
                versionHolder.setAlignment(Pos.TOP_LEFT);
                //Avoids weird group expansion by 1 pixel with version present
                versionHolder.setPadding(new Insets(1));
                updateContent.getChildren().add(versionHolder);

                DropShadow shadow = new DropShadow();
                shadow.setRadius(4);
                shadow.setOffsetX(2);
                shadow.setOffsetY(2);

                Label version = new Label("[" + new Version(update.version).toString() + "]");
                version.setId("updateVersion");
                version.setEffect(shadow);
                versionHolder.getChildren().add(version);
            }

            Pane spacer = new Pane();
            updateContent.getChildren().add(spacer);
            VBox.setVgrow(spacer, Priority.ALWAYS);

            if(update.title != null) {
                StackPane titleHolder = new StackPane();
                offsetNode = titleHolder;
                titleHolder.setId("updateTitleBackground");
                titleHolder.setAlignment(Pos.BASELINE_CENTER);
                titleHolder.setPadding(new Insets(5));
                if(imageBackground) {
                    titleHolder.setBackground(backgroundObject);
                }
                updateContent.getChildren().add(titleHolder);

                Label title = new Label(update.title);
                title.setId("updateTitle");
                title.setWrapText(true);
                title.setAlignment(Pos.BASELINE_CENTER);
                titleHolder.getChildren().add(title);
            }

            if(update.description != null) {
                StackPane descriptionHolder = new StackPane();
                descriptionHolder.setId("updateDescriptionBackground");
                descriptionHolder.setAlignment(Pos.BASELINE_CENTER);
                descriptionHolder.setPrefSize(itemWidth, Integer.MAX_VALUE);
                descriptionHolder.setPadding(new Insets(5));
                if(imageBackground)
                    descriptionHolder.setBackground(backgroundObject);
                updateRoot.layoutBoundsProperty().addListener(((observable, oldValue, newValue) -> {
                    descriptionHolder.setTranslateY(newValue.getHeight());
                }));
//                  descriptionHolder.setTranslateY(itemHeight);
                bottomNode = descriptionHolder;
                updateRoot.getChildren().add(descriptionHolder);

                Label description = new Label(update.description);
                description.setId("updateDescription");
                description.setWrapText(true);
                description.setAlignment(Pos.BASELINE_CENTER);
                descriptionHolder.getChildren().add(description);
            }

            Pane collider = new Pane();
            updateRoot.getChildren().add(collider);
            EventHandler<? super MouseEvent> mouseListener = new ScrollListener(topNode, bottomNode, offsetNode, updateContent);
            collider.setOnMouseEntered(mouseListener);
            collider.setOnMouseExited(mouseListener);

            if(update.hyperlink != null) {
                Handler handler = new Handler(v -> ProcelioLauncher.openBrowser(update.hyperlink));
                collider.addEventHandler(MouseEvent.ANY, handler);
            }
        }

    }

    private void debug(Node node) {
        node.setStyle("-fx-border-color: black");
    }

    private class ScrollListener implements EventHandler<MouseEvent> {
        ScrollTransition current = null;
        Node topNode;
        Pane bottomNode;
        Pane offsetNode;
        Node clip;

        public ScrollListener(Node topNode, Pane bottomNode, Pane offsetNode, Node clip) {
            this.topNode = topNode;
            this.bottomNode = bottomNode;
            this.offsetNode = offsetNode;
            this.clip = clip;
        }
        @Override
        public void handle(MouseEvent event) {
            double scrollOffest = 0;
            if(offsetNode != null) {
                bottomNode.setMaxHeight(clip.getLayoutBounds().getHeight() - offsetNode.getHeight());
                scrollOffest = offsetNode.getHeight();
            }
            if(current != null) {
                current.stop();
            }

            if(event.getEventType().equals(MOUSE_ENTERED)) {
                current = new ScrollTransition(-clip.getLayoutBounds().getHeight() + scrollOffest, topNode, bottomNode, clip);
            } else if (event.getEventType().equals(MOUSE_EXITED)) {
                current = new ScrollTransition(0, topNode, bottomNode, clip);
            }
            current.play();
        }
    }

    private class ScrollTransition extends Transition {

        private final Node topNode;
        private final Node bottomNode;
        private final Node clip;
        double scrollStart;
        double scrollDelta;

        public ScrollTransition(double scrollTo, Node topNode, Node bottomNode, Node clip) {
            this.topNode = topNode;
            this.bottomNode = bottomNode;
            this.clip = clip;

            scrollStart = topNode.getTranslateY();
            scrollDelta = scrollTo - scrollStart;
            setCycleDuration(Duration.seconds(0.5/* * Math.abs(Math.min(scrollTo, 1) - scrollStart)*/));
            //setCycleDuration(Duration.seconds(3));
            setInterpolator(new Interpolator() {
                @Override
                protected double curve(double t) {
                    if(t < 0.5)
                        return 2 * t * t;
                    else
                        return -2*Math.pow((t-1.), 2.) + 1.;
                }
            });
        }

        @Override
        protected void interpolate(double frac) {
            if(topNode != null)
                topNode.setTranslateY(scrollStart + (scrollDelta * frac));
            if(bottomNode != null)
                bottomNode.setTranslateY(scrollStart + clip.getLayoutBounds().getHeight() + (scrollDelta * frac));
        }
    }

    public class Handler implements EventHandler<MouseEvent> {

        private final EventHandler<MouseEvent> onClickedEventHandler;

        private boolean dragging = false;

        public Handler(EventHandler<MouseEvent> onClickedEventHandler) {
            this.onClickedEventHandler = onClickedEventHandler;
        }

        @Override
        public void handle(MouseEvent event) {
            if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
                dragging = false;
            }
            else if (event.getEventType() == MouseEvent.DRAG_DETECTED) {
                dragging = true;
            }
            else if (event.getEventType() == MouseEvent.MOUSE_CLICKED) {
                if (!dragging) {
                    onClickedEventHandler.handle(event);
                }
            }

        }
    }
}
