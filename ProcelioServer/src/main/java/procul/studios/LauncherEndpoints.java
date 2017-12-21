package procul.studios;

import io.sigpipe.jbsdiff.Diff;
import procul.studios.pojo.response.LauncherDownload;
import procul.studios.pojo.response.Message;
import procul.studios.util.*;
import spark.Request;

import spark.Response;
import spark.utils.IOUtils;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static procul.studios.ProcelioServer.gson;
import static procul.studios.SparkServer.ex;

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
        DiffManager differ = DiffManager.diffManagers.get(OperatingSystem.values()[osIndex]);
        if(differ == null)
            ex("Unsupported Operating System", 400);
        return differ;
    }

    public String getConfig(Request req, Response res){
        return gson.toJson(config.launcherConfig);
    }

    public Object getBuildFile(Request req, Response res){
        res.header("Content-Type", "application/octet-stream");
        Version buildVersion = getVersion(req.params(":build"));
        String[] filePath = req.splat();
        if(filePath == null || filePath.length < 1 || filePath[0] == null){
            return ex("No File Specified", 400);
        }
        DiffManager differ = getDiffer(req);
        Path requestedFile = differ.buildDir.toPath().resolve("build-" + buildVersion.toString()).resolve(filePath[0]);
        if(!requestedFile.normalize().startsWith(differ.buildDir.toPath())) {
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

    public Object getIcon(Request req, Response res){
        res.header("Content-Type","image/" + FileUtils.getFileExtension(config.iconPath));
        try (OutputStream out = res.raw().getOutputStream();
             InputStream in = new FileInputStream(config.iconPath)){
            IOUtils.copyLarge(in, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res.raw();
    }

    public Object getLogo(Request req, Response res){
        res.header("Content-Type","image/" + FileUtils.getFileExtension(config.logoPath));
        try (OutputStream out = res.raw().getOutputStream();
             InputStream in = new FileInputStream(config.logoPath)){
            IOUtils.copyLarge(in, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res.raw();
    }

    public Object fullBuild(Request req, Response res){
        res.header("Content-Type", "application/zip");
        res.header("Content-MD5", DatatypeConverter.printHexBinary(getDiffer(req).getNewestBuild().hash));
        try (OutputStream out = res.raw().getOutputStream();
             InputStream in = new FileInputStream(getDiffer(req).getNewestBuild().zip)){
            IOUtils.copyLarge(in, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res.raw();
    }

    public Object getPatch(Request req, Response res){
        String version = req.params(":patch");
        Tuple<Version, Version> patchVersion = getPatchVersion(version);
        Pack pack = getDiffer(req).getPackages().stream().filter(v -> v.bridge.equals(patchVersion)).findAny().orElse(null);
        if(pack == null)
            return ex("Patch could not be found", 404);
        res.header("Content-Type", "application/zip");
        res.header("Content-MD5", DatatypeConverter.printHexBinary(pack.hash));
        res.header("Content-Length", String.valueOf(pack.length));
        try (OutputStream out = res.raw().getOutputStream();
             InputStream in = new FileInputStream(pack.zip)){
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
        List<Pack> packages = getDiffer(req).getPackages();
        List<Tuple<Version, Version>> neededPackages = new ArrayList<>();
        Version goal = getDiffer(req).getNewestVersion();
        boolean upToDate = currentVersion.equals(goal);
        LauncherDownload result = new LauncherDownload("/launcher/build", upToDate, config.launcherConfig.launcherVersion);
        if(upToDate)
            return gson.toJson(result);
        while(currentVersion != goal){
            boolean found = false;
            for(Pack pack : packages){
                if(pack.bridge.getFirst().equals(currentVersion)) {
                    found = true;
                    neededPackages.add(pack.bridge);
                    currentVersion = pack.bridge.getSecond();
                    break;
                }
            }
            if(!found){
                return gson.toJson(result);
            }
        }
        neededPackages.sort(Comparator.comparing(Tuple::getFirst));
        result.patches = new ArrayList<>();
        for(int i = 0; i < neededPackages.size(); i++){
            result.patches.add("/launcher/patch/" + neededPackages.get(i).getFirst() + "-" + neededPackages.get(i).getSecond());
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
