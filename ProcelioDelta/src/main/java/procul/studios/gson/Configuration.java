package procul.studios.gson;

import com.google.gson.JsonParseException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class Configuration {
    protected static <T extends Configuration> T loadGenericConfiguration(String path, Class<T> type) throws IOException {
        if(path == null || path.length() == 0) {
            throw new FileNotFoundException("Specified path is empty");
        }
        return loadGenericConfiguration(Paths.get(path), type);
    }

    protected static <T extends Configuration> T loadGenericConfiguration(Path path, Class<T> type) throws IOException {
        T config;
        try (Reader reader = Files.newBufferedReader(path)){
            config = GsonSerialize.gson.fromJson(reader, type);
        } catch (IOException | JsonParseException e) {
            throw new IOException("Unable to read configuration file at " + path, e);
        }
        return config;
    }
}
