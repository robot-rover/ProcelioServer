package procul.studios.tool;

import picocli.CommandLine;

public class ToolVersion implements CommandLine.IVersionProvider {
    @Override
    public String[] getVersion() throws Exception {
        return new String[]{"Procelio Command Line Tool v1.0"};
    }
}
