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
    private static Map<OperatingSystem, DiffManager> diffManagers;
    private static final Logger LOG = LoggerFactory.getLogger(DiffManager.class);
    private static final Pattern buildPattern = Pattern.compile("build-(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Pattern patchPattern = Pattern.compile("diff-(\\d+)\\.(\\d+)\\.(\\d+)-(\\d+)\\.(\\d+)\\.(\\d+).zip");

    private static final ZipParameters params = new ZipParameters();
    static {
        params.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
        params.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
    }

    private final File patchDir;
    private final File buildDir;
    private final File zipDir;

    private final List<Tuple<Version, File>> buildVersions;
    private final List<Pack> packages;
    private Pack currentBuild;

    private boolean hasDiffed;
    private boolean buildsExist;
    OperatingSystem os;

    public static Map<OperatingSystem, DiffManager> getDiffManagerMap() {
        return diffManagers;
    }

    public static Map<OperatingSystem, DiffManager> createDiffManagers(File buildDir) throws IOException {
        if (!buildDir.exists() && !buildDir.mkdirs()) {
            throw new IOException("Cannot Create Directory");
        }
        diffManagers = new HashMap<>();
        for (File osDir : buildDir.listFiles(File::isDirectory)) {
            DiffManager manager = new DiffManager(osDir);
            diffManagers.put(manager.os, manager);
        }
        return diffManagers;
    }

    public static Collection<DiffManager> getDiffManagers() {
        return diffManagers.values();
    }

    private DiffManager(File osDir){
        hasDiffed = false;

        os = OperatingSystem.parse(osDir.getName());

        if(!osDir.exists())
            throw new RuntimeException("Operating System Directory does not exist");
        try {
            buildDir = new File(osDir, "builds");
            checkDir(buildDir);
            patchDir = new File(osDir, "patches");
            checkDir(patchDir);
            zipDir = new File(osDir, "packages");
            checkDir(zipDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot Start Diff Manager", e);
        }

        File[] builds = buildDir.listFiles(File::isDirectory);

        LOG.info("{} - DiffManager loaded for {}", os.name(), os.name());
        LOG.info("{} - Found {} builds in {}", os.name(), builds.length, buildDir.getAbsolutePath());
        buildsExist = builds.length != 0;

        buildVersions = parseVersions(builds);
        packages = new ArrayList<>();
        diffManagers.put(os, this);
    }

    private List<Tuple<Version, File>> parseVersions(File[] files) {
        List<Tuple<Version, File>> parsedVersions = new ArrayList<>();
        for(File build : files){
            Matcher m = buildPattern.matcher(build.getName());
            if(!m.find()) {
                LOG.warn("Unable to parse version from build folder {}", build.getAbsolutePath());
            }
            Tuple<Version, File> versionTuple = new Tuple<>(new Version(m.group(1), m.group(2), m.group(3)), build);
            parsedVersions.add(versionTuple);
            LOG.info("{} - \t{}", os.name(), buildDir.toPath().relativize(versionTuple.getSecond().toPath()));
        }
        parsedVersions.sort(Comparator.comparing(Tuple::getFirst));
        return parsedVersions;
    }

    private static void checkDir(File directory) throws IOException {
        if(!directory.exists()) {
            if(directory.mkdirs())
                return;
            else
                throw new IOException("The directory " + directory.getAbsolutePath() + " does not exist and cannot be created");
        } else {
            if(directory.isDirectory())
                return;
            else
                throw new IOException("The directory " + directory.getAbsolutePath() + " exists and is a file");
        }
    }

    public List<Pack> getPackages(){
        return packages;
    }

    public Version getNewestVersion() {
        return currentBuild.bridge.getSecond();
    }

    public File getBuildDir() {
        return buildDir;
    }

    public Pack getNewestBuild(){
        return currentBuild;
    }

    public void clearPatches(){
        FileUtils.deleteRecursive(patchDir);
        hasDiffed = false;
        clearPackages();
    }

    public void clearPackages(){
        FileUtils.deleteRecursive(zipDir);
    }

    public void createPatches(){
        if(!buildsExist)
            throw new RuntimeException("Cannot create patches without builds");
        Tuple<Version, File> from = null;
        for(Tuple<Version, File> version : buildVersions){
            buildDiff(from, version);
            from = version;
        }
        hasDiffed = true;
    }

    private void zipFile(File sourceDirectory, File zipArc){
        if(zipArc.exists())
            LOG.info("{} - Pack {} already exists, skipping", os.name(), zipDir.toPath().relativize(zipArc.toPath()));
        else try {
            LOG.info("{} - Zipping {}", os.name(), zipDir.toPath().relativize(zipArc.toPath()));
            AppZip zip = new AppZip(sourceDirectory);
            zip.zipTo(zipArc);
        } catch (IOException e) {
            LOG.error("{} - Unable to create pack {}", os.name(), zipArc.getAbsolutePath(), e);
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
        LOG.info("{} - Calculated hash: {}", os.name(), Hashing.printHexBinary(hashBytes));
        return new Tuple<>(hashBytes, fileLength);
    }

    public void createPackages(){
        if(!hasDiffed && buildsExist)
            createPatches();

        if(buildsExist) {
            for (File patch : patchDir.listFiles(File::isDirectory)) {
                File zipArc = new File(zipDir, patch.getName() + ".zip");
                zipFile(patch, zipArc);
                LOG.info("{} - Hashing {}", os.name(), zipDir.toPath().relativize(zipArc.toPath()));
                Tuple<byte[], Long> packData = hashFile(zipArc);
                Matcher m = patchPattern.matcher(zipArc.getName());
                if(!m.find()) {
                    LOG.warn("Unable to parse versions from patch {}", patch.getAbsolutePath());
                    continue;
                }
                Tuple<Version, Version> bridge = new Tuple<>(new Version(m.group(1), m.group(2), m.group(3)), new Version(m.group(4), m.group(5), m.group(6)));
                packages.add(new Pack(bridge, packData.getFirst(), zipArc, packData.getSecond()));
            }
            Version mostRecentVersion = buildVersions.stream().map(Tuple::getFirst).max(Version::compareTo).get();
            File newestBuild = new File(buildDir, "build-" + mostRecentVersion);
            if(!newestBuild.exists())
                throw new RuntimeException("Newest build " + newestBuild.getAbsolutePath() + " doesn't exist to be zipped");
            File zipArc = new File(zipDir, newestBuild.getName() + ".zip");
            if(!zipArc.exists())
                zipFile(newestBuild, zipArc);
            else
                LOG.info("{} - Package {} already exists, skipping", os.name(), zipDir.toPath().relativize(zipArc.toPath()));

            LOG.info("{} - Hashing {}", os.name(), zipDir.toPath().relativize(zipArc.toPath()));
            Tuple<byte[], Long> packInfo = hashFile(zipArc);
            currentBuild = new Pack(new Tuple<>(null, mostRecentVersion), packInfo.getFirst(), zipArc, packInfo.getSecond());
        } else {
            for (File zipArc : zipDir.listFiles()) {
                Matcher m = patchPattern.matcher(zipArc.getName());
                if(!m.find()) {
                    Matcher buildMatch = buildPattern.matcher(zipArc.getName());
                    if(!buildMatch.find()) {
                        LOG.warn("{} - Skipping package, can't parse version: {}", os.name(), zipArc.getAbsolutePath());
                        continue;
                    }
                    Version buildVersion = new Version(buildMatch.group(1), buildMatch.group(2), buildMatch.group(3));
                    if(currentBuild != null && currentBuild.bridge.getSecond().compareTo(buildVersion) > 0)
                        continue;
                    LOG.info("{} - Hashing {}", os.name(), zipDir.toPath().relativize(zipArc.toPath()));
                    Tuple<byte[], Long> packInfo = hashFile(zipArc);
                    currentBuild = new Pack(new Tuple<>(null, buildVersion), packInfo.getFirst(), zipArc, packInfo.getSecond());
                } else {
                    LOG.info("{} - Hashing {}", os.name(), zipDir.toPath().relativize(zipArc.toPath()));
                    Tuple<byte[], Long> packData = hashFile(zipArc);
                    Pack currentPack = new Pack(new Tuple<>(new Version(m.group(1), m.group(2), m.group(3)), new Version(m.group(4), m.group(5), m.group(6))),
                            packData.getFirst(), zipArc, packData.getSecond());
                    packages.add(currentPack);
                }
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
            diff = new File(patchDir, "diff-" + from.getFirst().toString() + "-" + to.getFirst().toString());
        }
        Tuple<BuildManifest, BuildManifest> versionBridge;
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
            versionBridge = new Tuple<>(fromManifest, toManifest);
        } catch (FileNotFoundException e){
            throw new RuntimeException("Missing Build Manifest", e);
        }
        if(diff.exists() && !diff.isDirectory())
            throw new RuntimeException("Cannot create diff " + diff.getAbsolutePath() + " because it already exists as a file");
        if(diff.exists() && diff.isDirectory()){
            LOG.info("{} - Diff {} is already processed, skipping", os.name(), diff.getName());
            return;
        }
        PackageManifest manifest = new PackageManifest(diff, new Tuple<>(from.getFirst(), to.getFirst()));
        manifest.newExec = versionBridge.getSecond().exec;
        manifest.ignore.addAll(versionBridge.getFirst().ignore);
        manifest.ignore.remove("manifest.json");

        if(!diff.mkdir())
            throw new RuntimeException("Unable to create directory for diff " + diff.getAbsolutePath());

        try {
            recursiveDiff(from.getSecond(), to.getSecond(), diff, manifest, versionBridge);
            File manifestFile = new File(diff, "manifest.json");
            Files.write(manifestFile.toPath(), gson.toJson(manifest).getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create diff " + diff.getAbsolutePath(), e);
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
                LOG.info("{} - Ignored {}", os.name(), patchDir.toPath().relativize(newFile.toPath()).toString());
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
                Path subPath = patchDir.toPath().relativize(diff.toPath().resolve(newFile.getName()));
                manifest.filesAndHashes.add(
                        Hashing.printHexBinary(hasher.digest()) + subPath.subpath(1, subPath.getNameCount())

                );
                if (oldFile == null) {
                    LOG.info("{} - Copying {}", os.name(), patchDir.toPath().relativize(diff.toPath().resolve(newFile.getName())).toString());
                    Files.write(diff.toPath().resolve(newFile.getName()), currentFileBytes.toByteArray(), StandardOpenOption.CREATE);
                    //Files.copy(newFile.toPath(), diff.toPath().resolve(newFile.getName()), StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    fromOld.remove(oldFile);
                    ByteArrayOutputStream oldFileBytes = new ByteArrayOutputStream();
                    FileInputStream oldFileIn = new FileInputStream(oldFile);
                    IOUtils.copyLarge(oldFileIn, oldFileBytes);
                    try {
                        LOG.info("{} - Diffing {}", os.name(), patchDir.toPath().relativize(diff.toPath().resolve(newFile.getName())).toString());
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
                LOG.info("{} - Ignored {}", os.name(), patchDir.toPath().relativize(toRemove.toPath()).toString());
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
                LOG.info("{} - Copying {}", os.name(), patchDir.toPath().relativize(to.toPath().resolve(f.getName())).toString());
                Files.copy(f.toPath(), to.toPath().resolve(f.getName()), StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

}
