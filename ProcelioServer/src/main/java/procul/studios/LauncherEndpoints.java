package procul.studios;

import procul.studios.delta.DeltaPack;
import procul.studios.pojo.response.LauncherDownload;
import procul.studios.util.Hashing;
import procul.studios.util.OperatingSystem;
import procul.studios.util.Tuple;
import procul.studios.util.Version;
import spark.Request;
import spark.Response;
import spark.utils.IOUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static procul.studios.SparkServer.ex;
import static procul.studios.gson.GsonSerialize.gson;

public class LauncherEndpoints {
    private static final Pattern packagePattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)-(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Pattern versionPattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    Configuration config;

    public LauncherEndpoints(Configuration config){
        this.config = config;
    }

    private DiffManager getDiffer(Request req){
        if(req.headers("X-Operating-System") == null){
            ex("Missing Operating System Header", 400);
        }
        int osIndex = 0;
        try {
            if ((osIndex = Integer.parseInt(req.headers("X-Operating-System"))) >= OperatingSystem.values().length) {
                ex("Invalid Operating System Value", 400);
            }
        } catch (NumberFormatException e){
            ex("Invalid Operating System Value", 400);
        }
        DiffManager differ = DiffManager.getDiffManagerMap().get(OperatingSystem.values()[osIndex]);
        if(differ == null)
            ex("Unsupported Operating System", 400);
        return differ;
    }

    public String getConfig(Request req, Response res){
        return gson.toJson(config.launcherConfig);
    }

    //todo: reimplement using build package (can't guarantee that builds are present)
    public Object getBuildFile(Request req, Response res){
        res.header("Content-Type", "application/octet-stream");
        Version buildVersion = getVersion(req.params(":build"));
        String[] filePath = req.splat();
        if(filePath == null || filePath.length < 1 || filePath[0] == null){
            return ex("No File Specified", 400);
        }
        DiffManager differ = getDiffer(req);
        Path requestedFile = differ.getBuildDir().resolve("build-" + buildVersion.toString()).resolve(filePath[0]);
        if(!requestedFile.normalize().startsWith(differ.getBuildDir())) {
            return ex(requestedFile.toString() + " is not a valid file", 400);
        }
        File file = requestedFile.toFile();
        if(!file.exists())
            return ex("The file " + requestedFile.toString() + " does not exist", 404);
        try (OutputStream out = res.raw().getOutputStream();
             InputStream in = new FileInputStream(file)){
            IOUtils.copyLarge(in, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res.raw();
    }

    public Object fullBuild(Request req, Response res){
        if(getDiffer(req).getNewestBuild() == null)
            return ex("No build available", 404);
        res.header("Content-Type", "application/zip");
        res.header("Content-MD5", Hashing.printHexBinary(getDiffer(req).getNewestBuild().getHash()));
        res.header("Content-Length", String.valueOf(getDiffer(req).getNewestBuild().getLength()));
        try (InputStream in = Files.newInputStream(getDiffer(req).getNewestBuild().getArchive())){
            OutputStream out = res.raw().getOutputStream();
            IOUtils.copyLarge(in, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res.raw();
    }

    public Object getPatch(Request req, Response res){
        String version = req.params(":patch");
        Tuple<Version, Version> patchVersion = getPatchVersion(version);
        DeltaPack pack = getDiffer(req).getDeltaPacks().stream().filter(v -> v.getVersionBridge().equals(patchVersion)).findAny().orElse(null);
        if(pack == null)
            return ex("Patch could not be found", 404);
        res.header("Content-Type", "application/zip");
        res.header("Content-MD5", Hashing.printHexBinary(pack.getHash()));
        res.header("Content-Length", String.valueOf(pack.getLength()));
        try (OutputStream out = res.raw().getOutputStream();
             InputStream in = Files.newInputStream(pack.getArchive())){
            IOUtils.copyLarge(in, out);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return res.raw();
    }

    public String getPatchList(Request req, Response res) {
        String currentVersionString = req.params(":patch");
        if (currentVersionString == null)
            return gson.toJson(new LauncherDownload("/launcher/build", false, config.launcherConfig.launcherVersion));
        Version currentVersion = getVersion(currentVersionString);
        List<DeltaPack> neededPackages = getDiffer(req).assemblePatchList(currentVersion);
        boolean upToDate = currentVersion.equals(getDiffer(req).getNewestVersion());
        LauncherDownload result = new LauncherDownload("/launcher/build", upToDate, config.launcherConfig.launcherVersion);
        if(upToDate || neededPackages.size() == 0)
            return gson.toJson(result);
        result.patches = new ArrayList<>();
        for(int i = 0; i < neededPackages.size(); i++){
            result.patches.add("/launcher/patch/" + neededPackages.get(i).getSource() + "-" + neededPackages.get(i).getTarget());
        }
        return gson.toJson(result);
    }

    public Tuple<Version, Version> getPatchVersion(String version){
        Tuple<Version, Version> patchVersion = null;
        if(version == null)
            ex("Missing version in path", 400);
        Matcher m = packagePattern.matcher(version);
        if(!m.find())
            ex("Malformed version in path", 400);
        try {
            patchVersion = new Tuple<>(new Version(m.group(1), m.group(2), m.group(3)), new Version(m.group(4), m.group(5), m.group(6)));
        } catch (NumberFormatException e){
            ex("Malformed version in path", 400);
        }
        return patchVersion;
    }

    public Version getVersion(String versionString){
        Version version = null;
        if(versionString == null)
            ex("Missing version in path", 400);
        Matcher m = versionPattern.matcher(versionString);
        if(!m.find())
            ex("Malformed version in path", 400);
        try {
            version = new Version(m.group(1), m.group(2), m.group(3));
        } catch (NumberFormatException e){
            ex("Malformed version in path", 400);
        }
        return version;
    }
}
