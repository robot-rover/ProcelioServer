package procul.studios;

import org.junit.Assert;
import org.junit.Test;
import procul.studios.util.Version;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VersionTest extends DiffManager{

    public VersionTest() {
        super(Configuration.loadConfiguration(ProcelioServer.configFile));
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
