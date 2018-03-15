package xyz.ylimit.androcov;

/*
 * Created by LiYC on 2015/7/18.
 * Package: DERG
 */
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

public class Config {
    static final String PROJECT_NAME = "androcov";

    // Directory for input
    static String inputAPK = "";

    // Directory for result output
    static String outputDirPath = "";
    static String tempDirPath = "";
    static String outputAPKPath = "";
    static String outputResultPath = "";

    static String forceAndroidJarPath = "";

    private static String appPackage ;
    static String refinedPackage;

    static boolean parseArgs(String[] args) {
        Options options = new Options();
        Option output_opt = Option.builder("o").argName("directory").required()
                .longOpt("output").hasArg().desc("path to output dir").build();
        Option input_opt = Option.builder("i").argName("APK").required()
                .longOpt("input").hasArg().desc("path to target APK").build();
        Option help_opt = Option.builder("h").desc("print this help message")
                .longOpt("help").build();

        options.addOption(output_opt);
        options.addOption(input_opt);
        options.addOption(help_opt);

        CommandLineParser parser = new DefaultParser();

        try {
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("i")) {
                Config.inputAPK = cmd.getOptionValue('i');
                File codeDirFile = new File(Config.inputAPK);
                if (!codeDirFile.exists()) {
                    throw new ParseException("Target APK does not exist.");
                }

                appPackage = Util.extractPackageName(new File(inputAPK));
                refinedPackage = Util.refinePackage(appPackage);
            }
            if (cmd.hasOption('o')) {
                Config.outputDirPath = cmd.getOptionValue('o');
                File outputDir = new File(Config.outputDirPath);
                Config.outputDirPath = outputDir.getAbsolutePath();
                Config.tempDirPath = String.format("%s/temp", Config.outputDirPath);
                Config.outputResultPath = String.format("%s/instrumentation-"+appPackage+".json", Config.outputDirPath);

                if (!outputDir.exists() && !outputDir.mkdirs()) {
                    throw new ParseException("Error generating output directory.");
                }
                File tempDir = new File(Config.tempDirPath);
                if (tempDir.exists()) {
                    try {
                        FileUtils.forceDelete(tempDir);
                    } catch (IOException e) {
                        throw new ParseException("Error deleting temp directory.");
                    }
                }
                if (!tempDir.mkdirs()) {
                    throw new ParseException("Error generating temp directory.");
                }
            }

            Path androidJarPath = Paths.get(System.getenv("ANDROID_HOME"))
                    .resolve("platforms")
                    .resolve("android-25")
                    .resolve("android.jar")
                    .toAbsolutePath();
            Config.forceAndroidJarPath = androidJarPath.toString();

            if (!Files.exists(androidJarPath))
                throw new ParseException("android.jar does not exist.");

            if (cmd.hasOption("h")) {
                throw new ParseException("print help message.");
            }
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setOptionComparator(Comparator.comparingInt(o -> o.getOpt().length()));
            formatter.printHelp(Config.PROJECT_NAME, options, true);
            return false;
        }

        File logFile = new File(String.format("%s/androcov.log", Config.outputDirPath));

        try {
            FileHandler fh = new FileHandler(logFile.getAbsolutePath());
            fh.setFormatter(new SimpleFormatter());
            xyz.ylimit.androcov.Util.LOGGER.addHandler(fh);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // get output APK name
        String apkName = new File(inputAPK).getName();
        Config.outputAPKPath = String.format("%s/%s", Config.outputDirPath, apkName);

        xyz.ylimit.androcov.Util.LOGGER.info("finish parsing arguments");
        xyz.ylimit.androcov.Util.LOGGER.info(String.format("[inputAPK]%s, [outputDir]%s", Config.inputAPK, Config.outputDirPath));
        return true;
    }
}