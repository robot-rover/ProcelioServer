package procul.studios.util;

import java.util.Locale;

public enum OperatingSystem {
    UNKNOWN(),
    WINDOWS("win"),
    MACINTOSH("mac"),
    LINUX("nix", "nux", "aix");

    public String[] terms;

    OperatingSystem(String... indicies){
        this.terms = indicies;
    }

    public static OperatingSystem parse(String osName){
        if(osName == null)
            return UNKNOWN;
        for(OperatingSystem os : OperatingSystem.values()){
            for(String index : os.terms){
                if(osName.contains(index))
                    return os;
            }
        }
        return UNKNOWN;
    }

    public static OperatingSystem get(){
        return parse(System.getProperty("os.name").toLowerCase(Locale.ENGLISH));
    }
    
    public String getIndex(){
        return String.valueOf(this.ordinal());
    }
}
