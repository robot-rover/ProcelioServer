package procul.studios;

import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.JUnit4;
import procul.studios.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VersionTest extends DiffManager{

    static TemporaryFolder tempFolder = new TemporaryFolder();
    static {
        try {
            tempFolder.create();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public VersionTest() {
        super(Configuration.loadConfiguration(ProcelioServer.configFile), tempFolder.getRoot());
    }

    @Test
    public void testOrdering(){
        List<Version> versions = Arrays.asList(
                new Version(1,0,0),
                new Version(1,1,0),
                new Version(2,1,0),
                new Version(0,1,1),
                new Version(10,0,0));
        String list = versions.stream().sorted().map(Version::toString).collect(Collectors.joining(" "));
        Assert.assertEquals("Version List not sorted correctly", "0.1.1 1.0.0 1.1.0 2.1.0 10.0.0", list);

    }
}
