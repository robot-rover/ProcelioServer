package procul.studios;

import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class ImageResources {
    private static HashMap<String, Image> images = new HashMap<>();
    private static final Logger LOG = LoggerFactory.getLogger(ImageResources.class);

    public static Image load(String name) {
        if (images.containsKey(name))
            return images.get(name);
        var resource = ClassLoader.getSystemResourceAsStream(name);
        if (resource == null)
            return load("missing.png");
        Image i = new Image(resource);
        images.put(name, i);
        return i;
    }
}
