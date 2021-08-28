import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class NodeExtensionSetup {
    private static final String[] PORT_FLAG = {"--port", "-p"};
    private static final String[] FILE_FLAG = {"--filename", "-f"};
    private static final String[] COOKIE_FLAG = {"--auth-token", "-c"};
    private static final String[] MIN_VERSION_FLAG = {"--min-version", "-v"};
    private static final String[] NPM_RUN_FLAG = {"--npm-run", "-npm"};
    private static final String[] EXTENSION_FLAG = {"--extension", "-e"};

    public static void main(String[] args) throws IOException {
        runSetup(args);
    }

    private static String getArgument(String[] args, String... arg) {
        for (int i = 0; i < args.length - 1; i++) {
            for (String str : arg) {
                if (args[i].equalsIgnoreCase(str)) {
                    return args[i+1];
                }
            }
        }
        return null;
    }

    private static void runSetup(String[] args) throws IOException {
        try {
            String extensionScript = getArgument(args, EXTENSION_FLAG);
            String npmRun = getArgument(args, NPM_RUN_FLAG);
            if (extensionScript == null && npmRun == null) throw new Exception("Node.js extension file or npm run must be defined in the run args (-e script.js OR -npm start)");
            String minVersion = getArgument(args, MIN_VERSION_FLAG);
            if (minVersion == null) throw new Exception("Minimum Node.js version has to be defined in the run args (-v 15.0.0)");
            String port = getArgument(args, PORT_FLAG);
            if (port == null) throw new Exception("G-Earth extension port must be defined in run args (-p 9092)");

            if(!isNodeInstalledAndUpToDate(minVersion)) installNode(minVersion);

            runNpmInstall();

            runExtension(args);
            clearCache();
        } catch (Exception e) {
            new JFXPanel(); // Create JavaFX thread
            Platform.runLater(() -> {
                try {
                    new File("error.txt");
                    FileOutputStream fos = new FileOutputStream(new File("error.txt"));
                    Alert error = new Alert(Alert.AlertType.ERROR);
                    error.setTitle("Error while setting up Node.js extension!");
                    error.setHeaderText("Error in setup of " + new File(URLDecoder.decode(NodeExtensionSetup.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF8")).getParentFile().getParentFile().getName());
                    e.printStackTrace(new PrintStream(fos));
                    error.setContentText(e.getMessage());
                    error.showAndWait();
                    fos.close();
                } catch (IOException ignored) {};
            });
        }
    }

    private static boolean isNodeInstalledAndUpToDate(String minVersion) {
        try {
            ProcessBuilder pb = new ProcessBuilder("node", "--version");
            Process p = pb.start();

            String error = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8)).readLine();
            System.out.println(error);
            if(error != null) return false;
            String version = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)).readLine();
            System.out.println(version);
            version = version.replace("v", "");

            return isNewerOrEqual(version, minVersion);
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean isNewerOrEqual(String actualVersion, String minVersion) {
        String[] actualSplit = actualVersion.trim().split("\\.");
        String[] minSplit = minVersion.trim().split("\\.");

        for(int i = 0; i < actualSplit.length && i < minSplit.length; i++) {
            int vDif = Integer.parseInt(actualSplit[i]) - Integer.parseInt(minSplit[i]);
            if(vDif > 0) return true;
            if(vDif < 0) return false;
        }

        return true;
    }

    private static void installNode(String minVersion) throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        if(os.contains("win")) {
            installOnWindows(minVersion);
        } else if(os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            installOnLinux(minVersion);
        } else if(os.contains("mac")) {
            installOnMac(minVersion);
        } else {
            throw new Exception("Unsupported Operating System");
        }
    }

    private static boolean requestContinueApproval(String title, String header, String content) throws ExecutionException, InterruptedException {
        final FutureTask<Boolean> requestTask = new FutureTask<>(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);

            AtomicBoolean result = new AtomicBoolean(false);
            final Button ok = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
            ok.addEventFilter(ActionEvent.ACTION, event -> result.set(true));
            final Button cancel = (Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL);
            cancel.addEventFilter(ActionEvent.ACTION, event -> result.set(false));

            alert.showAndWait();

            return result.get();
        });

        new JFXPanel(); // Create JavaFX thread
        Platform.runLater(requestTask);

        return requestTask.get();
    }

    private static void installOnWindows(String minVersion) throws Exception {
        if(!requestContinueApproval("Install/Update Node.js",
                "Node.js not found or newer version required",
                "Do you want to install/update Node.js?\nExtension will be launched once installation is completed")) throw new Exception("Node.js installation rejected, extension wont be able to run!");
        // Alternative: Install Chocolatey or Nuget (Windows package manager)
        try {
            String downloadUrl = String.format("https://nodejs.org/dist/v%s/node-v%s-x64.msi", minVersion, minVersion);
            File installerExe = downloadCacheFile(new URL(downloadUrl), "nodeInstaller.msi");
            ProcessBuilder pb = new ProcessBuilder("msiexec", "/i", installerExe.toString(), "/quiet", "/norestart");
            pb.directory(new File(URLDecoder.decode(NodeExtensionSetup.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF8")).getParentFile());
            Process p = pb.start();
            p.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
            throw new Exception("Error while installing Node.js");
        }
    }

    private static void installOnLinux(String minVersion) throws Exception {
        // Too complicated to include all distributions
        try {
            if(!requestContinueApproval("Install/Update Node.js",
                    new File(URLDecoder.decode(NodeExtensionSetup.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF8")).getParentFile().getParentFile().getName() + " requires Python to be installed/updated!",
                    String.format("Use your local package manager to update Node.js to a version of %s or higher, afterwards click OK\n(If you click OK before installing/updating, the extension will not run this time)", minVersion))){
                throw new Exception("Setup cancelled, extension will most likely not launch!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Error while requesting Node.js installation");
        }
    }

    private static void installOnMac(String minVersion) throws Exception {
        if(!requestContinueApproval("Install/Update Node.js",
                "Python not found or newer version required",
                "Do you want to install/update Node.js?\nExtension will be launched once installation is completed")) throw new Exception("Node.js installation rejected, extension wont be able to run!");
        // Alternative: Install Brew (Mac/Linux package manager)
        try {
            String downloadUrl = String.format("https://nodejs.org/dist/v%s/node-v%s.pkg", minVersion, minVersion);
            File installerPkg = downloadCacheFile(new URL(downloadUrl), "nodeInstaller.pkg");
            ProcessBuilder pb = new ProcessBuilder("installer", "-pkg", installerPkg.toString(), "-target", "CurrenUserHomeDirectory");
            pb.directory(new File(URLDecoder.decode(NodeExtensionSetup.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF8")).getParentFile());
            Process p = pb.start();
            p.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
            throw new Exception("Error while installing Node.js");
        }

    }

    private static File downloadCacheFile(URL url, String fileName) throws IOException {
        String cachePath = "setupCache/";
        Files.createDirectories(Paths.get(cachePath));
        File file = new File(cachePath + fileName);
        InputStream is = url.openStream();
        FileOutputStream fos = new FileOutputStream(file);

        int length = -1;
        byte[] buffer = new byte[1024];
        while((length = is.read(buffer)) > -1) {
            fos.write(buffer, 0, length);
        }
        fos.close();
        is.close();
        return file;
    }

    private static void clearCache() {
        File cacheFolder = new File("setupCache");
        if(cacheFolder.exists()) {
            emptyDirectory(cacheFolder);
        }
        cacheFolder.delete();
    }

    private static void emptyDirectory(File dir) {
        File[] files = dir.listFiles();
        if(files != null) {
            for (File file : files) {
                if(file.isDirectory()) emptyDirectory(file);
                file.delete();
            }
        }
    }

    private static void runNpmInstall() throws Exception {
        try {
            File req = new File("package.json");
            if (req.exists()) {
                ProcessBuilder pb = new ProcessBuilder("npm" + (System.getProperty("os.name").toLowerCase().contains("win") ? ".cmd" : ""), "install");
                pb.directory(new File(URLDecoder.decode(NodeExtensionSetup.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF8")).getParentFile());
                Process p = pb.start();
                p.waitFor();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Error while installing modules, extension might not work!");
        }
    }

    private static void runExtension(String[] args) throws Exception {
        String npmRun = getArgument(args, NPM_RUN_FLAG);
        String cookie = getArgument(args, COOKIE_FLAG);
        String file = getArgument(args, FILE_FLAG);
        String port = getArgument(args, PORT_FLAG);
        ArrayList<String> command;
        if(npmRun != null) {
            command = new ArrayList<>(Arrays.asList("npm" + (System.getProperty("os.name").toLowerCase().contains("win") ? ".cmd" : ""), "run", npmRun, "--", "--", "--", "--",  "-p", port));
        } else {
            String extensionScript = getArgument(args, EXTENSION_FLAG);
            command = new ArrayList<>(Arrays.asList("node.exe", extensionScript, "-p", port));
        }

        if(file != null) command.addAll(Arrays.asList("-f", file));
        if(cookie != null) command.addAll(Arrays.asList("-c", cookie));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(URLDecoder.decode(NodeExtensionSetup.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF8")).getParentFile());
        pb.start();
    }
}
