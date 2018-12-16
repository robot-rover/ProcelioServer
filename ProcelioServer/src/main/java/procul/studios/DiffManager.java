package procul.studios;

import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.delta.*;
import procul.studios.util.OperatingSystem;
import procul.studios.util.Version;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiffManager {
    private static Map<OperatingSystem, DiffManager> diffManagers;
    private static final Logger LOG = LoggerFactory.getLogger(DiffManager.class);

    private static final ZipParameters params = new ZipParameters();
    static {
        params.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
        params.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
    }

    private final Path deltaDir;
    private final Path buildDir;
    private final Path packDir;

    private final ExecutorService exec;
    private final List<DeltaPack> deltaPacks;
    private List<BuildPack> buildPacks;
    private BuildPack newestBuild;

    private boolean hasDiffed;
    OperatingSystem os;

    public static Map<OperatingSystem, DiffManager> getDiffManagerMap() {
        return diffManagers;
    }

    public static Map<OperatingSystem, DiffManager> createDiffManagers(Path baseDir, ExecutorService exec) throws IOException {
        if (!Files.isDirectory(baseDir)) {
            throw new IOException("Base Directory " + baseDir + " does not exist");
        }
        diffManagers = new HashMap<>();
        for (Path osDir : Files.newDirectoryStream(baseDir)) {
            if(!Files.isDirectory(osDir))
                continue;
            DiffManager manager = new DiffManager(osDir, exec);
            diffManagers.put(manager.os, manager);
        }
        return diffManagers;
    }

    public static Map<OperatingSystem, DiffManager> createDiffManagers(Path baseDir) throws IOException {
        return createDiffManagers(baseDir, Executors.newSingleThreadExecutor());
    }

    private DiffManager(Path osDir, ExecutorService exec) throws IOException {
        hasDiffed = false;

        os = OperatingSystem.parse(osDir.getFileName().toString());

        if(!Files.isDirectory(osDir))
            throw new RuntimeException("Operating System Directory " + osDir + " does not exist");
        buildDir = osDir.resolve("build");
        if(!Files.isDirectory(buildDir))
            Files.createDirectory(buildDir);
        deltaDir = osDir.resolve("delta");
        if(!Files.isDirectory(deltaDir))
            Files.createDirectory(deltaDir);
        packDir = osDir.resolve("pack");
        if(!Files.isDirectory(packDir))
            Files.createDirectory(packDir);

        LOG.info("{} - DiffManager loaded for {}", os.name(), os.name());

        deltaPacks = new ArrayList<>();
        buildPacks = new ArrayList<>();
        this.exec = exec;
    }

    public List<DeltaPack> getDeltaPacks(){
        return deltaPacks;
    }

    public BuildPack getNewestBuild() {
        return newestBuild;
    }

    public Version getNewestVersion() {
        return newestBuild.getVersion();
    }

    public Path getBuildDir() {
        return buildDir;
    }

    public void createPatches() throws IOException {
        List<Build> builds = new ArrayList<>();
        for(Path buildPath : Files.newDirectoryStream(buildDir)) {
            builds.add(new Build(buildPath));
        }

        if(builds.isEmpty())
            return;
        Collections.sort(builds);
        Build newestBuild = null;
        Build last = null;
        List<Delta> deltas = new ArrayList<>();
        for(Build build : builds){
            if(build.compareTo(newestBuild) > 0)
                newestBuild = build;
            if(last != null) {
                deltas.add(new Delta(deltaDir, last, build, exec));
            }
            last = build;
        }
        if(newestBuild != null) {
            new Pack(newestBuild.getBaseDirectory(), packDir);
        }

        for (Path delta : Files.newDirectoryStream(deltaDir)) {
            new Pack(delta, packDir);
        }

        hasDiffed = true;
    }

    public void findPackages() throws IOException {
        if(!hasDiffed)
            createPatches();
        for (Path packPath : Files.newDirectoryStream(packDir)) {
            Pack pack = Pack.createPackFromExisting(packPath);
            if(pack == null) {
                LOG.warn("{} - Unable to load existing pack {}", os.name(), packPath);
            }
            if(pack instanceof BuildPack) {
                BuildPack buildPack = (BuildPack) pack;
                buildPacks.add(buildPack);
                if(buildPack.compareTo(newestBuild) > 0)
                    newestBuild = buildPack;
            } else if(pack instanceof DeltaPack) {
                deltaPacks.add((DeltaPack) pack);
            }
        }
        if(newestBuild == null) {
            throw new IOException("No build packs present in package folder");
        }
    }

    public List<DeltaPack> assemblePatchList(Version currentVersion) {
        List<DeltaPack> neededPackages = new ArrayList<>();
        Version newestVersion = getNewestVersion();
        while(!currentVersion.equals(newestVersion)){
            boolean found = false;
            for(DeltaPack pack : getDeltaPacks()){
                if(pack.getSource().equals(currentVersion)) {
                    found = true;
                    neededPackages.add(pack);
                    currentVersion = pack.getTarget();
                    break;
                }
            }
            if(!found) {
                return null;
            }
        }
        return neededPackages;
    }

}
