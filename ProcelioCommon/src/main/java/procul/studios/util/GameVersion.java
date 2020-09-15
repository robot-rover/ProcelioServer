package procul.studios.util;

public class GameVersion implements Comparable<GameVersion> {
    public int major;
    public int minor;
    public int patch;
    public boolean dev_build;

    public GameVersion(int major, int minor, int patch, boolean dev) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.dev_build = dev;
    }

    @Override
    public int compareTo(GameVersion o) {
        if(o == null)
            return 1;
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
        if(GameVersion.class.isAssignableFrom(obj.getClass())){
            GameVersion cast = (GameVersion) obj;
            return major == cast.major && minor == cast.minor && patch == cast.patch &&dev_build == cast.dev_build;
        }
        return false;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (dev_build ? "dev" : "");
    }
}
