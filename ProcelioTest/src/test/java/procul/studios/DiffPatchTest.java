package procul.studios;

import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.delta.BuildManifest;
import procul.studios.delta.DeltaPack;
import procul.studios.util.FileUtils;
import procul.studios.util.Tuple;
import procul.studios.util.Version;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DiffPatchTest {
    private static final Logger LOG = LoggerFactory.getLogger(DiffPatchTest.class);

    TemporaryFolder temp;

    public DiffPatchTest() throws IOException {
        temp = new TemporaryFolder();
        temp.create();
    }
    public File createTempDirs(File buildDir) throws IOException {

        File base = temp.newFolder("gameBuilds");
        base.mkdir();
        File os = new File(base, "linux");
        os.mkdir();
        File builds = new File(os, "build");
        builds.mkdir();
        FileUtils.copyRecursive(buildDir.toPath(), builds.toPath());
        return base;
    }

    @Test
    public void minecraftDiffPatch() throws IOException {
        File base = createTempDirs(new File("src/test/resources/minecraft"));
        DiffManager.createDiffManagers(base.toPath());
        DiffManager diffManager = DiffManager.getDiffManagerMap().values().stream().findAny().get();

        diffManager.findPackages();
        File firstBuildDir = new File(base, "linux/build/build-1.0.0");
        Path workingDir = temp.newFolder().toPath();
        FileUtils.copyRecursive(firstBuildDir.toPath(), workingDir);

        Consumer garbage = v -> {};
        Patcher patcher = new Patcher(workingDir, null, garbage, garbage, garbage);

        List<DeltaPack> patchList = diffManager.assemblePatchList(new Version(1,0,0));
        BuildManifest current = patcher.loadManifest();
        for(DeltaPack bridge : patchList) {
            patcher.applyDelta(Files.newInputStream(bridge.getArchive()), current);
            current = patcher.loadManifest();
        }

        assertDirSame(new File(base, "linux/build/build-1.2.1"), workingDir.toFile());
    }

    @Test
    public void singleFileDiffPatch() throws IOException {
        File base = createTempDirs(new File("src/test/resources/singlefile"));
        DiffManager.createDiffManagers(base.toPath());
        DiffManager diffManager = DiffManager.getDiffManagerMap().values().stream().findAny().get();

        diffManager.findPackages();
        File firstBuildDir = new File(base, "linux/build/build-1.0.0");
        File workingDir = temp.newFolder();
        FileUtils.copyRecursive(firstBuildDir.toPath(), workingDir.toPath());

        Consumer garbage = v -> {};
        Patcher patcher = new Patcher(workingDir.toPath(), null, garbage, garbage, garbage);

        List<DeltaPack> patchList = diffManager.assemblePatchList(new Version(1,0,0));
        BuildManifest current = patcher.loadManifest();
        for(DeltaPack bridge : patchList) {
            patcher.applyDelta(Files.newInputStream(bridge.getArchive()), current);
            current = patcher.loadManifest();
        }

        assertDirSame(new File(base, "linux/build/build-1.0.1"), workingDir);
    }

    public void assertDirSame(File dir1, File dir2) throws IOException {
        Files.walkFileTree(dir1.toPath(), new OperationFileVisitor(this::assertFileSame, dir2));
    }

    public void assertFileSame(File file1, File file2) {
        if(file1.getName().equals("manifest.json") && file2.getName().equals("manifest.json")) {
            return;
        }
        Tuple<byte[], Long> hash1 = hashFile(file1);
        Tuple<byte[], Long> hash2 = hashFile(file2);
        Assert.assertArrayEquals(file1.getAbsolutePath() + " Hash", hash1.getFirst(), hash2.getFirst());
        Assert.assertEquals(file1.getAbsolutePath() + " Length", hash1.getSecond(), hash2.getSecond());
        /*try {
            List<String> lines1 = Files.readAllLines(file1.toPath());
            List<String> lines2 = Files.readAllLines(file2.toPath());
            for(int i = 0; i < lines1.size(); i++) {
                Assert.assertEquals("Line " + i, lines1.get(i), lines2.get(i));
            }
            Assert.assertEquals(file1.length() + " vs " + file2.length(),lines1.size(), lines2.size());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }*/
    }

    static class OperationFileVisitor extends SimpleFileVisitor<Path> {
        private Path sourcePath = null;
        private Path targetPath;
        BiConsumer<File, File> action;
        public OperationFileVisitor(BiConsumer<File, File> action, File targetPath) {
            this.action = action;
            this.targetPath = targetPath.toPath();
        }
        @Override
        public FileVisitResult preVisitDirectory(final Path dir,
                                                 final BasicFileAttributes attrs) throws IOException {
            if (sourcePath == null) {
                sourcePath = dir;
            }
            return FileVisitResult.CONTINUE;
        }

        public FileVisitResult visitFile(final Path file,
                                         final BasicFileAttributes attrs) throws IOException {
            action.accept(file.toFile(),
                    targetPath.resolve(sourcePath.relativize(file)).toFile());
            return FileVisitResult.CONTINUE;
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
        return new Tuple<>(hashBytes, fileLength);
    }
}


