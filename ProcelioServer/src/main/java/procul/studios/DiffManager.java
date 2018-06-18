package procul.studios;

import io.sigpipe.jbsdiff.Diff;
import io.sigpipe.jbsdiff.InvalidHeaderException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;
import org.apache.commons.compress.compressors.CompressorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.BuildManifest;
import procul.studios.pojo.PackageManifest;
import procul.studios.util.*;
import spark.utils.IOUtils;

import java.io.*;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static procul.studios.ProcelioServer.gson;

public class DiffManager {
    public static final Map<OperatingSystem, DiffManager> diffManagers = new HashMap<>();
    private static final Logger LOG = LoggerFactory.getLogger(DiffManager.class);
    private static final Pattern buildPattern = Pattern.compile("build-(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Pattern packagePattern = Pattern.compile("diff-(\\d+)\\.(\\d+)\\.(\\d+)-(\\d+)\\.(\\d+)\\.(\\d+).zip");
    private final ZipParameters params = new ZipParameters();
    public File diffDir;
    public File buildDir;
    public File zipDir;
    private List<Tuple<Version, File>> versions;
    private List<Pack> packages;
    private Pack currentBuild;
    private boolean hasDiffed;
    private boolean buildsExist;
    public DiffManager(Configuration config, File osDir){
        hasDiffed = false;
        params.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
        params.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
        if(!osDir.exists() && !osDir.mkdir())
            throw new RuntimeException("Unable to create Diff Directory");
        diffDir = new File(osDir, "patches");
        if(!diffDir.exists() && !diffDir.mkdir())
            throw new RuntimeException("Unable to create Patch Directory");
        if(!diffDir.isDirectory())
            throw new RuntimeException("Diff Directory is a File!");
        buildDir = new File(osDir, "builds");
        if(!buildDir.exists() && !buildDir.mkdir())
            throw new RuntimeException("Unable to create Build Directory");
        if(!buildDir.isDirectory())
            throw new RuntimeException("Build Directory is a File!");
        zipDir = new File(osDir, "package");
        if(!zipDir.exists() && !zipDir.mkdir())
            throw new RuntimeException("Unable to create Zip Directory");
        if(!zipDir.isDirectory())
            throw new RuntimeException("Zip Directory is a File!");
        File[] builds = buildDir.listFiles(File::isDirectory);
        OperatingSystem os = OperatingSystem.parse(osDir.getName());
        LOG.info("DiffManager loaded for {}", os.name());
        LOG.info("Found {} builds in {}", builds.length, buildDir.getAbsolutePath());
        buildsExist = builds.length != 0;
        versions = new ArrayList<>();
        for(File build : builds){
            Matcher m = buildPattern.matcher(build.getName());
            if(!m.find())
                throw new RuntimeException("Unable to parse version from build folder " + build.getAbsolutePath());
            Tuple<Version, File> versionTuple = new Tuple<>(new Version(m.group(1), m.group(2), m.group(3)), build);
            versions.add(versionTuple);
            LOG.info("\t{}", buildDir.toPath().relativize(versionTuple.getSecond().toPath()));
        }
        versions.sort(Comparator.comparing(Tuple::getFirst));
        packages = new ArrayList<>();
        diffManagers.put(os, this);
    }

    public List<Tuple<Version, File>> getVersions() {
        return versions;
    }

    public List<Pack> getPackages(){
        return packages;
    }

    public Version getNewestVersion(){
        return versions.stream().map(Tuple::getFirst).max(Version::compareTo).get();
    }

    public Pack getNewestBuild(){
        return currentBuild;
    }

    public void clearPatches(){
        FileUtils.deleteRecursive(diffDir);
        clearPackages();
    }

    public void clearPackages(){
        FileUtils.deleteRecursive(zipDir);
    }

    public void createPatches(){
        if(!buildsExist)
            throw new RuntimeException("Cannot create patches without builds");
        Tuple<Version, File> from = null;
        for(Tuple<Version, File> version : versions){
            buildDiff(from, version);
            from = version;
        }
        hasDiffed = true;
    }

    private void zipFile(File sourceDirectory, File zipArc){
        if(zipArc.exists())
            LOG.info("Pack " + zipDir.toPath().relativize(zipArc.toPath()) + " already exists, skipping");
        else try {
            LOG.info("Zipping " + zipDir.toPath().relativize(zipArc.toPath()));
            AppZip zip = new AppZip(sourceDirectory);
            zip.zipIt(zipArc);
        } catch (IOException e) {
            LOG.error("Unable to create pack {}", zipArc.getAbsolutePath(), e);
        }
    }

    private Tuple<byte[], Long> hashFile(File toHash){
        InputStream pack;
        MessageDigest hash;
        DigestInputStream hashStream;
        long fileLength = 0;
        try {
            hash = MessageDigest.getInstance("MD5");
            pack = new FileInputStream(toHash);
            hashStream = new DigestInputStream(pack, hash);
            byte[] buffer = new byte[1024];
            int len;
            while((len = hashStream.read(buffer)) != -1){
                fileLength += len;
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 Hash not supported on this system", e);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Zip archive disappeared", e);
        } catch (IOException e) {
            throw new RuntimeException("Error calculating pack hash for " + toHash.getAbsolutePath(), e);
        }
        byte[] hashBytes = hash.digest();
        LOG.info("Calculated hash: {}", Hashing.printHexBinary(hashBytes));
        return new Tuple<>(hashBytes, fileLength);
    }

    public void generatePackages(){
        if(!hasDiffed && buildsExist)
            createPatches();

        versions = new ArrayList<>();
        for(File build : (buildsExist ? buildDir : zipDir).listFiles()){
            Matcher m = buildPattern.matcher(build.getName());
            if(!m.find()) {
                LOG.warn("Unable to parse version from folder " + build.getAbsolutePath());
                continue;
            }
            Tuple<Version, File> versionTuple = new Tuple<>(new Version(m.group(1), m.group(2), m.group(3)), build);
            versions.add(versionTuple);
        }

        if(buildsExist) {
            for (File patch : diffDir.listFiles(File::isDirectory)) {
                File zipArc = new File(zipDir, patch.getName() + ".zip");
                Pack currentPack = packages.stream().filter(v -> v.zip.equals(patch)).findAny().orElseThrow(() -> new RuntimeException("Cannot find version for diff " + patch.getAbsolutePath()));
                currentPack.zip = zipArc;
                zipFile(patch, zipArc);
                LOG.info("Hashing " + zipDir.toPath().relativize(zipArc.toPath()));
                Tuple<byte[], Long> packData = hashFile(zipArc);
                currentPack.hash = packData.getFirst();
                currentPack.length = packData.getSecond();
            }
            File newestBuild = new File(buildDir, "build-" + versions.stream().map(Tuple::getFirst).max(Version::compareTo).get());
            if(!newestBuild.exists())
                throw new RuntimeException("Newest build " + newestBuild.getAbsolutePath() + " doesn't exist to be zipped");
            File zipArc = new File(zipDir, newestBuild.getName() + ".zip");
            if(!zipArc.exists())
                zipFile(newestBuild, zipArc);
            else
                LOG.info("Pack " + zipDir.toPath().relativize(zipArc.toPath()) + " already exists, skipping");

            LOG.info("Hashing " + zipDir.toPath().relativize(zipArc.toPath()));
            Tuple<byte[], Long> packInfo = hashFile(zipArc);
            currentBuild = new Pack(null, packInfo.getFirst(), zipArc);
            currentBuild.length = packInfo.getSecond();
        } else {
            for (File zipArc : zipDir.listFiles()) {
                Matcher m = packagePattern.matcher(zipArc.getName());
                if(!m.find()) {
                    LOG.warn("Skipping package, can't parse version: {}", zipArc.getAbsolutePath());
                    continue;
                }
                LOG.info("Hashing " + zipDir.toPath().relativize(zipArc.toPath()));
                Tuple<byte[], Long> packData = hashFile(zipArc);
                Pack currentPack = new Pack(new Tuple<>(new Version(m.group(1), m.group(2), m.group(3)), new Version(m.group(4), m.group(5), m.group(6))),
                        packData.getFirst(), zipArc);
                currentPack.length = packData.getSecond();
                packages.add(currentPack);
            }
            if(versions.size() > 0) {
                File zipArc = new File(zipDir, "build-" + versions.stream().map(Tuple::getFirst).max(Version::compareTo).get() + ".zip");
                LOG.info("Hashing " + zipDir.toPath().relativize(zipArc.toPath()));
                Tuple<byte[], Long> packInfo = hashFile(zipArc);
                currentBuild = new Pack(null, packInfo.getFirst(), zipArc);
                currentBuild.length = packInfo.getSecond();
            }
        }


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
                throw new RuntimeException("Filename Version doesn't match manifest version for " + to.getSecond().getAbsolutePath());
            toManifest.init();
            buildManifest = new Tuple<>(fromManifest, toManifest);
        } catch (FileNotFoundException e){
            throw new RuntimeException("Missing Build Manifest", e);
        }
        if(diff.exists() && !diff.isDirectory())
            throw new RuntimeException("Cannot create diff " + diff.getAbsolutePath() + " because it already exists as a file");
        if(diff.exists() && diff.isDirectory()){
            LOG.info("Diff " + diff.getName() + " is already processed, skipping");
            return;
        }
        PackageManifest manifest = new PackageManifest(diff, new Tuple<>(from.getFirst(), to.getFirst()));
        manifest.newExec = buildManifest.getSecond().exec;
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
            if(buildManifest.getSecond().baseDir.toPath().relativize(newFile.toPath()).equals(Paths.get("manifest.json")))
                continue;
            if(buildManifest.getSecond().ignore.contains(manifest.getBaseDir().toPath().relativize(newFile.toPath()).toString())){
                LOG.info("Ignored " + diffDir.toPath().relativize(newFile.toPath()).toString());
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
                MessageDigest hasher = Hashing.getMessageDigest();
                FileInputStream currentFileIn = new FileInputStream(newFile);
                ByteArrayOutputStream currentFileBytes = new ByteArrayOutputStream();
                IOUtils.copyLarge(new DigestInputStream(currentFileIn, hasher), currentFileBytes);
                Path subPath = diffDir.toPath().relativize(diff.toPath().resolve(newFile.getName()));
                manifest.filesAndHashes.add(
                        Hashing.printHexBinary(hasher.digest()) + subPath.subpath(1, subPath.getNameCount())

                );
                if (oldFile == null) {
                    LOG.info("Copying " + diffDir.toPath().relativize(diff.toPath().resolve(newFile.getName())).toString());
                    Files.write(diff.toPath().resolve(newFile.getName()), currentFileBytes.toByteArray(), StandardOpenOption.CREATE);
                    //Files.copy(newFile.toPath(), diff.toPath().resolve(newFile.getName()), StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    fromOld.remove(oldFile);
                    ByteArrayOutputStream oldFileBytes = new ByteArrayOutputStream();
                    FileInputStream oldFileIn = new FileInputStream(oldFile);
                    IOUtils.copyLarge(oldFileIn, oldFileBytes);
                    try {
                        LOG.info("Diffing " + diffDir.toPath().relativize(diff.toPath().resolve(newFile.getName())).toString());
                        Diff.diff(oldFileBytes.toByteArray(), currentFileBytes.toByteArray(), new FileOutputStream(new File(diff, newFile.getName() + ".patch")));
                        //FileUI.diff(oldFile, newFile, new File(diff, newFile.getName() + ".patch"));
                    } catch (InvalidHeaderException e) {
                        throw new IOException("Invalid File Header", e);
                    } catch (CompressorException e) {
                        throw new IOException("Invalid Compressor", e);
                    }
                }

            }
        }
        for (File toRemove : fromOld) {
            if(buildManifest.getFirst().ignore.contains(manifest.getBaseDir().toPath().relativize(toRemove.toPath()).toString()))
                LOG.info("Ignored " + diffDir.toPath().relativize(toRemove.toPath()).toString());
            else
                manifest.delete.add(manifest.getBaseDir().toPath().resolve(toRemove.toPath()).toString());
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
                LOG.info("Copying " + diffDir.toPath().relativize(to.toPath().resolve(f.getName())).toString());
                Files.copy(f.toPath(), to.toPath().resolve(f.getName()), StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

}
