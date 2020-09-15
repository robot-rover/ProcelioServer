package procul.studios.delta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.util.GameVersion;
import procul.studios.util.Version;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static procul.studios.gson.GsonSerialize.gson;

/**
 * Represents a versioned file tree
 * Supports changing the version by applying a delta whose source version matches this tree's current version
 * Can be used by {@link Delta} to create a delta
 */
public class Build implements Comparable<Build> {
    private static final Logger LOG = LoggerFactory.getLogger(Build.class);
    public static final Pattern pattern = Pattern.compile("build-(\\d+)\\.(\\d+)\\.(\\d+)");
    private final Path baseDirectory;
    Set<Path> fileTree;

    public Path getBaseDirectory() {
        return baseDirectory;
    }

    public BuildManifest getManifest() {
        return manifest;
    }

    BuildManifest manifest;
    GameVersion version;

    @Override
    public String toString() {
        return "Build " + getVersion();
    }

    /**
     * Create a new versioned file tree
     * @param directory the base directory of the tree
     * @throws IOException if the base directory doesn't have a manifest.json or if the manifest is invalid
     */
    public Build(Path directory) throws IOException {
        this.baseDirectory = directory;
        try {
            manifest = gson.fromJson(Files.newBufferedReader(baseDirectory.resolve("manifest.json")), BuildManifest.class);
            if(manifest.version == null || manifest.version.length < 3)
                throw new IOException("baseDirectory " + baseDirectory + " manifest.json has no valid version");
            version = new GameVersion(manifest.version[0], manifest.version[1], manifest.version[2], manifest.dev);
        } catch (NoSuchFileException e) {
            throw new IOException("baseDirectory " + baseDirectory + " doesn't have a manifest.json", e);
        }
        LOG.info("Loaded {}", this);
        fileTree = new LinkedHashSet<>();
        loadFileTree(baseDirectory);
    }

    private void loadFileTree(Path directory) throws IOException {
        for(Path path : Files.newDirectoryStream(directory)) {
            // Ignore manifest.json
            if(directory.equals(baseDirectory) && path.getFileName().endsWith("manifest.json"))
                continue;
            if (Files.isDirectory(path)) {
                loadFileTree(path);
            } else {
                fileTree.add(baseDirectory.relativize(path));
            }
        }
    }

    /**
     * Get a list of the relative paths of files
     * this does not include the manifest
     */
    public Set<Path> getFileList() {
        return fileTree;
    }

    public GameVersion getVersion() {
        return version;
    }

    @Override
    public int compareTo(Build o) {
        if(o == null)
            return 1;
        return getVersion().compareTo(o.getVersion());
    }
}
