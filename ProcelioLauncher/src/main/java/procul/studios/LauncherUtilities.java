package procul.studios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.regex.Pattern;

public class LauncherUtilities {

    private static final Logger LOG = LoggerFactory.getLogger(LauncherUtilities.class);

    /**
     * Recursively deletes everything inside a directory
     * @param dir File containing the top-level directory to delete
     */
    public static boolean deleteRecursive(File dir) {
        if (dir == null)
            return false;
        if (!dir.isDirectory())
            return dir.delete();
        File[] contents = dir.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteRecursive(f);
            }
        }
        return dir.delete();
    }

    /**
     * Delete all files with name matching the given regex (recursively)
     * @param dir The top level directory to delete from
     * @param regex The pattern that file names will be tested against
     */
    public static void deleteMatchingRecursive(File dir, String regex) {
        if (dir.isDirectory()) {
            File[] arr = dir.listFiles();
            for (int i = 0; i < arr.length; ++i)
                deleteMatchingRecursive(arr[i], regex);
        }
        if (dir.toPath().toString().matches(regex))
           dir.delete();
    }
}
