package procul.studios;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.ui.FileUI;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;
import org.apache.commons.compress.compressors.CompressorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.BuildManifest;
import procul.studios.pojo.PackageManifest;
import procul.studios.util.Pack;
import procul.studios.util.Tuple;
import procul.studios.util.Version;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static procul.studios.ProcelioServer.gson;

public class DiffManager {
    private static final Logger LOG = LoggerFactory.getLogger(DiffManager.class);
    private static final Pattern buildPattern = Pattern.compile("build-(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Pattern packagePattern = Pattern.compile("diff-(\\d+)\\.(\\d+)\\.(\\d+)-(\\d+)\\.(\\d+)\\.(\\d+).zip");
    private final ZipParameters params = new ZipParameters();
    private File diffDir;
    private File buildDir;
    private File zipDir;
    private List<Tuple<Version, File>> versions;
    private List<Pack> packages;
    private Tuple<File, byte[]> currentBuild;
    private boolean hasDiffed;
    public DiffManager(Configuration config){
        hasDiffed = false;
        params.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
        params.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
        File diffManagerDir = new File(config.buildFolderPath);
        if(!diffDir.exists() && !diffDir.mkdir())
            throw new RuntimeException("Unable to create Diff Directory");
        diffDir = new File(config.buildFolderPath, "patches");
        if(!diffDir.exists() && !diffDir.mkdir())
            throw new RuntimeException("Unable to create Patch Directory");
        if(!diffDir.isDirectory())
            throw new RuntimeException("Diff Directory is a File!");
        buildDir = new File(config.buildFolderPath, "builds");
        if(!buildDir.exists() && !buildDir.mkdir())
            throw new RuntimeException("Unable to create Build Directory");
        if(!buildDir.isDirectory())
            throw new RuntimeException("Build Directory is a File!");
        zipDir = new File(config.buildFolderPath, "package");
        if(!zipDir.exists() && !zipDir.mkdir())
            throw new RuntimeException("Unable to create Zip Directory");
        if(!zipDir.isDirectory())
            throw new RuntimeException("Zip Directory is a File!");
        File[] builds = buildDir.listFiles(File::isDirectory);
        versions = new ArrayList<>();
        for(File build : builds){
            Matcher m = buildPattern.matcher(build.getName());
            if(!m.find())
                throw new RuntimeException("Unable to parse version from build folder " + build.getAbsolutePath());
            versions.add(new Tuple<>(new Version(m.group(1), m.group(2), m.group(3)), build));
        }
        versions.sort(Comparator.comparing(Tuple::getFirst));
        packages = new ArrayList<>();
    }

    public List<Tuple<Version, File>> getVersions() {
        return versions;
    }

    public List<Pack> getPackages(){
        return packages;
    }

    public Version getNewestVersion(){
        return versions.get(versions.size()-1).getFirst();
    }

    public Tuple<File, byte[]> getNewestPackage(){
        return currentBuild;
    }

    public void clearPatches(){
        deleteRecursive(diffDir);
    }

    public void clearPackages(){
        deleteRecursive(zipDir);
    }

    private void deleteRecursive(File dir){
        for(File f : dir.listFiles()){
            if(f.isDirectory())
                deleteRecursive(f);
            f.delete();
        }
    }

    public void createPatches(){
        Tuple<Version, File> from = null;
        for(Tuple<Version, File> version : versions){
            buildDiff(from, version);
            from = version;
        }
        hasDiffed = true;
    }

    private void zipFile(File sourceDirectory, File zipArc){
        if(zipArc.exists())
            LOG.info("Pack for " + sourceDirectory.getName() + " exists, skipping");
        else try {
            LOG.info("Zipping " + pathRelativeTo(zipArc.toPath(), zipDir.toPath()));
            ZipFile zipFile = new ZipFile(zipArc);
            zipFile.addFolder(sourceDirectory, params);
        } catch (ZipException e) {
            throw new RuntimeException("Unable to package " + sourceDirectory.getAbsolutePath(), e);
        }
    }

    private byte[] hashFile(File toHash){
        InputStream pack;
        MessageDigest hash;
        DigestInputStream hashStream;
        try {
            hash = MessageDigest.getInstance("MD5");
            pack = new FileInputStream(toHash);
            hashStream = new DigestInputStream(pack, hash);
            while(hashStream.read() != -1){}
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 Hash not supported on this system", e);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Zip archive disappeared", e);
        } catch (IOException e) {
            throw new RuntimeException("Error calculating pack hash for " + toHash.getAbsolutePath(), e);
        }
        return hash.digest();
    }

    public void generatePackages(){
        if(!hasDiffed)
            createPatches();
        for(File patch : diffDir.listFiles(File::isDirectory)){
            File zipArc = new File(zipDir, patch.getName() + ".zip");
            Pack currentPack = packages.stream().filter(v -> v.zip.equals(patch)).findAny().orElseThrow(() -> new RuntimeException("Cannot find version for diff " + patch.getAbsolutePath()));
            currentPack.zip = zipArc;
            if(!zipArc.exists())
                zipFile(patch, zipArc);
            else
                LOG.info("Pack " + pathRelativeTo(zipArc.toPath(), zipDir.toPath()) + " already exists, skipping");
            LOG.info("Hashing " + pathRelativeTo(zipArc.toPath(), zipDir.toPath()));
            currentPack.hash = hashFile(zipArc);
        }

        File newestBuild = new File(buildDir, "build-" + versions.get(versions.size()-1).getFirst());
        if(!newestBuild.exists())
            throw new RuntimeException("Newest build " + newestBuild.getAbsolutePath() + " doesn't exist to be zipped");
        File zipArc = new File(zipDir, newestBuild.getName() + ".zip");
        currentBuild = new Tuple<>(zipArc, null);
        if(!zipArc.exists())
            zipFile(newestBuild, zipArc);
        else
            LOG.info("Pack " + pathRelativeTo(zipArc.toPath(), zipDir.toPath()) + " already exists, skipping");
        LOG.info("Hashing " + pathRelativeTo(zipArc.toPath(), zipDir.toPath()));
        currentBuild.setSecond(hashFile(zipArc));
    }

    private Path pathRelativeTo(Path file, Path relativeTo){
        return file.subpath(relativeTo.getNameCount(), file.getNameCount());
    }

    private void buildDiff(Tuple<Version, File> from, Tuple<Version, File> to){
        boolean base = from == null;
        if(to == null)
            throw new RuntimeException("Cannot create a diff to nothing");
        int baseIndex = new File(".").toPath().getNameCount() + 1;
        File diff;
        if(base){
            return;
        } else {
            diff = new File(diffDir, "diff-" + from.getFirst().toString() + "-" + to.getFirst().toString());
            packages.add(new Pack(new Tuple<>(from.getFirst(), to.getFirst()), null, diff));
        }
        Tuple<BuildManifest, BuildManifest> buildManifest;
        try {
            BuildManifest fromManifest = gson.fromJson(new InputStreamReader(new FileInputStream(new File(from.getSecond(), "manifest.json"))), BuildManifest.class);
            fromManifest.baseDir = from.getSecond();
            if(fromManifest.version != null && !new Version(fromManifest.version).equals(from.getFirst()))
                throw new RuntimeException("Filename Version doesn't match manifest version for " + from.getSecond().getAbsolutePath());
            fromManifest.init();
            BuildManifest toManifest = gson.fromJson(new InputStreamReader(new FileInputStream(new File(to.getSecond(), "manifest.json"))), BuildManifest.class);
            toManifest.baseDir = to.getSecond();
            if(toManifest.version != null && !new Version(toManifest.version).equals(to.getFirst()))
                throw new RuntimeException("Filename Version doesn't match manifest version for " + from.getSecond().getAbsolutePath());
            toManifest.init();
            buildManifest = new Tuple<>(fromManifest, toManifest);
        } catch (FileNotFoundException e){
            throw new RuntimeException("Missing Build Manifest", e);
        }
        if(diff.exists() && !diff.isDirectory())
            throw new RuntimeException("Cannot create diff " + diff.getAbsolutePath() + " because it already exists as a file");
        if(diff.exists() && diff.isDirectory()){
            LOG.info("Diff for " + diff.getName() + " is already processed, skipping");
            return;
        }
        PackageManifest manifest = new PackageManifest(diff, new Tuple<>(from.getFirst(), to.getFirst()));
        manifest.ignore.addAll(buildManifest.getFirst().ignore);
        manifest.ignore.remove("manifest.json");

        if(!diff.mkdir())
            throw new RuntimeException("Unable to create directory for diff " + diff.getAbsolutePath());
        if(base) {
            try {
                recursiveCopy(to.getSecond(), diff);
            } catch (IOException e) {
                throw new RuntimeException("Unable to create diff " + diff.getAbsolutePath(), e);
            }
        } else {
            try {
                recursiveDiff(from.getSecond(), to.getSecond(), diff, manifest, buildManifest);
                File manifestFile = new File(diff, "manifest.json");
                manifestFile.createNewFile();
                Files.write(manifestFile.toPath(), gson.toJson(manifest).getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Unable to create diff " + diff.getAbsolutePath(), e);
            }
        }
    }

    private void recursiveDiff(File old, File current, File diff, PackageManifest manifest, Tuple<BuildManifest, BuildManifest> buildManifest) throws IOException {
        if(!old.isDirectory() || !current.isDirectory() || !diff.isDirectory())
            throw new RuntimeException("Cannot recursive copy from a directory");
        List<File> fromOld = new ArrayList<>(Arrays.asList(old.listFiles()));
        List<File> fromCurrent = Arrays.asList(current.listFiles());
        for(int i = 0; i < fromCurrent.size(); i++){
            File newFile = fromCurrent.get(i);
            if(buildManifest.getSecond().ignore.contains(pathRelativeTo(newFile.toPath(), manifest.getBaseDir().toPath()).toString())){
                LOG.info("Ignored " + pathRelativeTo(newFile.toPath(), diffDir.toPath()).toString());
                continue;
            }
            File oldFile = fromOld.stream().filter(v -> v.getName().equals(newFile.getName())).findAny().orElse(null);
            if(newFile.isDirectory()) {
                File dirInDiff = new File(diff, newFile.getName());
                if(dirInDiff.exists())
                    throw new RuntimeException("Directory already exists!");
                if(!dirInDiff.mkdir())
                    throw new RuntimeException("Cannot create diff directory");
                if(oldFile == null) {
                    recursiveCopy(newFile, dirInDiff);
                } else {
                    fromOld.remove(oldFile);
                    recursiveDiff(oldFile, newFile, dirInDiff, manifest, buildManifest);
                }
            } else {
                if (oldFile == null) {
                    LOG.info("Copying " + pathRelativeTo(diff.toPath().resolve(newFile.getName()), diffDir.toPath()).toString());
                    Files.copy(newFile.toPath(), diff.toPath().resolve(newFile.getName()), StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    fromOld.remove(oldFile);
                    try {
                        LOG.info("Diffing " + pathRelativeTo(diff.toPath().resolve(newFile.getName()), diffDir.toPath()).toString());
                        FileUI.diff(oldFile, newFile, new File(diff, newFile.getName() + ".patch"));
                    } catch (InvalidHeaderException e) {
                        throw new IOException("Invalid File Header", e);
                    } catch (CompressorException e) {
                        throw new IOException("Invalid Compressor", e);
                    }
                }
            }
        }
        for (File toRemove : fromOld) {
            if(buildManifest.getFirst().ignore.contains(pathRelativeTo(toRemove.toPath(), manifest.getBaseDir().toPath()).toString()))
                LOG.info("Ignored " + pathRelativeTo(toRemove.toPath(), diffDir.toPath()).toString());
            else
                manifest.delete.add(pathRelativeTo(toRemove.toPath(), manifest.getBaseDir().toPath()).toString());
        }

    }

    private void recursiveCopy(File from, File to) throws IOException {
        if(!from.isDirectory() || !to.isDirectory())
            throw new RuntimeException("Cannot recursive copy from a file");
        for(File f : from.listFiles()){
            if(f.isDirectory()){
                File target = new File(to, f.getName());
                if(!target.mkdir())
                    throw new IOException("Cannot make dir inside recursive copy");
                recursiveCopy(f, target);
            } else {
                LOG.info("Copying " + pathRelativeTo(to.toPath().resolve(f.getName()), diffDir.toPath()).toString());
                Files.copy(f.toPath(), to.toPath().resolve(f.getName()), StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

}
