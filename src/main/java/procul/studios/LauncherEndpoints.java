package procul.studios;

import procul.studios.pojo.response.LauncherDownload;
import procul.studios.util.Pack;
import procul.studios.util.Tuple;
import procul.studios.util.Version;
import spark.Request;

import spark.Response;

import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static procul.studios.ProcelioServer.gson;
import static procul.studios.SparkServer.ex;

public class LauncherEndpoints {
    private static final Pattern packagePattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)-(\\d+)\\.(\\d+)\\.(\\d+)");
    Configuration config;
    DiffManager differ;

    public LauncherEndpoints(Configuration config, DiffManager differ){
        this.config = config;
        this.differ = differ;
    }

    public Object fullBuild(Request req, Response res){
        try {
            OutputStream out = res.raw().getOutputStream();
            InputStream in = new BufferedInputStream(new FileInputStream(differ.getNewestPackage().getFirst()));
            int data;
            while((data = in.read()) != -1)
                out.write(data);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        res.header("Content-MD5", Base64.getEncoder().encodeToString(differ.getNewestPackage().getSecond()));
        return res.raw();
    }

    public Object getPatch(Request req, Response res){
        String version = req.params(":patch");
        if(version == null)
            return ex("Missing version in path", 400);
        Matcher m = packagePattern.matcher(version);
        if(!m.find())
            return ex("Malformed version in path", 400);
        Tuple<Version, Version> patchVersion;
        try {
            patchVersion = new Tuple<>(new Version(m.group(1), m.group(2), m.group(3)), new Version(m.group(4), m.group(5), m.group(6)));
        } catch (NumberFormatException e){
            return ex("Malformed version in path", 400);
        }
        Pack pack = differ.getPackages().stream().filter(v -> v.bridge.equals(patchVersion)).findAny().orElse(null);
        if(pack == null)
            return ex("Patch could not be found", 404);
        try {
            OutputStream out = res.raw().getOutputStream();
            InputStream in = new BufferedInputStream(new FileInputStream(pack.zip));
            int data;
            while((data = in.read()) != -1)
                out.write(data);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        res.header("Content-MD5", Base64.getEncoder().encodeToString(pack.hash));
        return res.raw();
    }

    public String getPatchList(Request req, Response res) {
        String currentVersionString = req.params(":patch");
        if (currentVersionString == null)
            return ex("Missing Version in path", 400);
        String[] version = currentVersionString.split("\\.");
        if (version.length != 3)
            ex("Malformed Version in path", 400);
        LauncherDownload result = new LauncherDownload("/launcher/build");
        List<Pack> packages = differ.getPackages();
        List<Tuple<Version, Version>> neededPackages = new ArrayList<>();
        Version goal = differ.getNewestVersion();
        Version currentVersion;
        try {
            currentVersion = new Version(version);
        } catch (NumberFormatException e){
            return ex("Malformed Version in path", 400);
        }
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
            result.patches.add("/launcher/" + neededPackages.get(i).getFirst() + "-" + neededPackages.get(i).getSecond());
        }
        return gson.toJson(result);
    }
}
