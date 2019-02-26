package sais;

import io.sigpipe.jbsdiff.DiffSettings;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

public class SaisDiffSettings implements DiffSettings {
    private final String compression;

    public SaisDiffSettings() {
        compression = CompressorStreamFactory.BZIP2;
    }

    @Override
    public String getCompression() {
        return compression;
    }

    @Override
    public int[] sort(byte[] input) {
        int[] output = new int[input.length + 1];
        Sais.suffixsort(input, output, input.length);
        return output;
    }
}
