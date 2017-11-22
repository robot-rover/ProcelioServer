import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageTesting {
    static final String pathToResources = "src/test/resources/";
    public static void main(String args[]){
        File resources = new File(pathToResources);
        if(!resources.isDirectory())
            throw new RuntimeException("Resources should be a directory!");
        //noinspection ConstantConditions
        for(File file : resources.listFiles()){
            if(!file.isDirectory())
                process(file);
        }
    }

    public static void process(File filePath){
        BufferedImage avatar;
        try {
            avatar = ImageIO.read(filePath);
        } catch (IOException e) {
            new Exception("Errored on Image: " + filePath.toString(), e).printStackTrace();
            return;
        }
        BufferedImage resized = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int sx1, sx2, sy1, sy2, offset;
        if(avatar.getWidth() > avatar.getHeight()){
            sy1 = 0;
            sy2 = avatar.getHeight();
            offset = (avatar.getWidth()-avatar.getHeight())/2;
            sx1 = offset;
            sx2 = offset + avatar.getHeight();
        } else {
            sx1 = 0;
            sx2 = avatar.getWidth();
            offset = (avatar.getHeight() - avatar.getWidth())/2;
            sy1 = offset;
            sy2 = offset + avatar.getWidth();
        }
        g.drawImage(avatar, 0, 0, 256, 256, sx1, sy1, sx2, sy2, null);
        g.dispose();
        File output = new File(pathToResources + "output/" + filePath.getName());
        String[] filenameSplit = filePath.getName().split("\\.");
        try {
            ImageIO.write(resized, filenameSplit[filenameSplit.length-1], output);
        } catch (IOException e) {
            new Exception("Errored on Image: " + output.toString(), e).printStackTrace();
        }
    }
}
