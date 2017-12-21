package procul.studios.util;

public class Version implements Comparable<Version> {
    int major;
    int minor;
    int patch;

    public Version(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public Version(String major, String minor, String patch){
        this(Integer.parseInt(major), Integer.parseInt(minor), Integer.parseInt(patch));
    }

    public Version(String[] version){
        this(version[0], version[1], version[2]);
    }

    public Version(Integer[] verion){
        this(verion[0], verion[1], verion[2]);
    }

    public Integer[] toArray() {
        return new Integer[]{major, minor, patch};
    }

    @Override
    public int compareTo(Version o) {
        if(major != o.major)
            return major - o.major;
        if(minor != o.minor)
            return minor - o.minor;
        return patch - o.patch;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null)
            return false;
        if(obj == this)
            return true;
        if(Version.class.isAssignableFrom(obj.getClass())){
            Version cast = (Version) obj;
            return major == cast.major && minor == cast.minor && patch == cast.patch;
        }
        return false;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
