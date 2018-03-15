package xyz.ylimit.androcov;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

/**
 * Created by liyc on 12/23/15.
 * useful utils
 */
public class Util {

    static final Logger LOGGER = Logger.getLogger(Config.PROJECT_NAME);

    private static String pathToAAPT = System.getenv("ANDROID_HOME") + "/build-tools/25.0.2";


    /**
     * Extracts the package name from an apk file
     * Update "pathToAAPT" to the local path of the android build tools!!!!!
     * @param pathToAPK: path to the apk file
     * @return package name
     */
    static String extractPackageName(File pathToAPK) {
        Process aapt = null;
        String output = null;
        InputStream adbout = null;
        try {
            aapt = Runtime.getRuntime().exec(pathToAAPT +"/aapt dump badging "+pathToAPK);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        try {
            adbout = aapt.getInputStream();
            output= IOUtils.toString(adbout);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        output = output.split("'")[1];

        return output;
    }


    /**
     * In the package has more than 2 parts, it returns the 2 first parts
     * @param pkg
     * @return
     */
    static String refinePackage(String pkg) {
        String parts [] = pkg.split("\\.");
        if(parts.length>2)
            return parts[0]+"."+parts[1];

        else return pkg;
    }


    public static String getTimeString() {
        long timeMillis = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-hhmmss");
        Date date = new Date(timeMillis);
        return sdf.format(date);
    }

    public static void logException(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        Util.LOGGER.warning(sw.toString());
    }

    public static float safeDivide(int obfuscated, int total) {
        if (total <= 0) return 1;
        return (float) obfuscated / total;
    }

    public static void signAPK(String apkPath) {
        Runtime r = Runtime.getRuntime();
        Path keystore = Paths.get("debug.keystore").toAbsolutePath();
        try {
            Util.copyResourceToFile(Util.class, keystore.getFileName().toString(), keystore);

            Path keystorePath = Paths.get(String.format("%s/debug.keystore", Config.tempDirPath)).toAbsolutePath();
            String signCmd = String.format(
                    "jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -storepass android " +
                            "-keystore %s %s androiddebugkey", keystorePath, apkPath);
            Files.copy(keystore, keystorePath);
            Process p = r.exec(signCmd);

            ReadStream s1 = new ReadStream("stdin", p.getInputStream());
            ReadStream s2 = new ReadStream("stderr", p.getErrorStream());
            s1.start();
            s2.start();

            p.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Export a resource embedded into a Jar file to the local file path.
     * The given resource name has to include the pass of the resource.
     * "/resname" will lead to searching the resource in the root folder "resname" will search in the classes package.
     *
     * @param resourceName ie.: "/SmartLibrary.dll"
     * @return The path to the exported resource
     * @throws Exception
     */
    public static void copyResourceToFile(Class askingClass, String resourceName, Path outFile) throws IOException {
        InputStream stream = null;
        OutputStream resStreamOut = null;
        //String jarFolder;
        try {
            stream = askingClass.getClassLoader().getResourceAsStream(resourceName);//note that each / is a directory down in the "jar tree" been the jar the root of the tree
            if (stream == null) {
                throw new RuntimeException("Cannot get resource \"" + resourceName + "\" from Jar file.");
            }

            int readBytes;
            byte[] buffer = new byte[4096];
            resStreamOut = new FileOutputStream(outFile.toFile());
            while ((readBytes = stream.read(buffer)) > 0) {
                resStreamOut.write(buffer, 0, readBytes);
            }
        } finally {
            stream.close();
            resStreamOut.close();
        }
    }
}
