import org.jooq.util.derby.sys.Sys;
import procul.studios.Configuration;

import java.io.File;
import java.util.Arrays;
import java.util.Base64;

public class Base64Testing {
    public static void main(String[] args){
        Configuration config = Configuration.loadConfiguration(new File("config.json"));
        System.out.println(config.getServerKey());
        System.out.println(config.getKeystorePass());
    }
}
