package xyz.ylimit.androcov;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import soot.*;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.StringConstant;
import soot.jimple.internal.JIdentityStmt;
import soot.options.Options;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


/**
 * Instrument statements in apk
 */
public class InstrumenterStatement {
    private static HashSet<String> allMethods = new HashSet<>();

    static void configSoot() throws IOException {
//        Options.v().set_prepend_classpath(true);
        Options.v().set_allow_phantom_refs(true);
//        Options.v().set_whole_program(true);
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_output_dir(Config.outputDirPath);
        Options.v().set_debug(true);
        Options.v().set_validate(true);
        Options.v().set_output_format(Options.output_format_dex);

        List<String> process_dirs = new ArrayList<>();
        process_dirs.add(Config.inputAPK);

        // NOTE: If you change the CoverageHelper.java class, recompile it!
        Path coverageHelperPath = Paths.get("CoverageHelper.class").toAbsolutePath();
        Util.copyResourceToFile(InstrumenterStatement.class, coverageHelperPath.getFileName().toString(), coverageHelperPath);
        String helperDirPath = String.format("%s/CoverageHelper", Config.tempDirPath);
        String helperClassPath = String.format("%s/CoverageHelper/xyz/ylimit/androcov/CoverageHelper.class",
                Config.tempDirPath);
        Path helperClassFile = Paths.get(helperClassPath).toAbsolutePath();
        Files.createDirectories(helperClassFile.getParent());

        if (!Files.exists(helperClassFile.getParent()))
            throw new IOException("Fail to create directory");

        Files.copy(coverageHelperPath, helperClassFile);
        process_dirs.add(helperDirPath);

        Options.v().set_process_dir(process_dirs);
        Options.v().set_force_android_jar(Config.forceAndroidJarPath);
        Options.v().set_force_overwrite(true);
    }

    static void instrument() {
        Util.LOGGER.info("Start instrumenting...");

    Scene.v().loadNecessaryClasses();
    final SootClass helperClass = Scene.v().getSootClass("xyz.ylimit.androcov.CoverageHelper");
    final SootMethod helperMethod = helperClass.getMethodByName("reach");

    PackManager.v().getPack("jtp").add(new Transform("jtp.androcov", new BodyTransformer() {
        @Override
        protected void internalTransform(final Body b, String phaseName, @SuppressWarnings("rawtypes") Map options) {
            final PatchingChain units = b.getUnits();
            // important to use snapshotIterator here
            if (b.getMethod().getDeclaringClass() == helperClass) return;
            String methodSig = b.getMethod().getSignature();

            if (methodSig.startsWith("<" + Config.refinedPackage)) {


                // perform instrumentation here
                for (Iterator iter = units.snapshotIterator(); iter.hasNext(); ) {
                    final Unit u = (Unit) iter.next();
                    UUID uuid = UUID.randomUUID();
                    // Instrument statements
                    if (!(u instanceof JIdentityStmt)) {
                        allMethods.add(u + " uuid=" + uuid);
                        InvokeStmt logStatement = Jimple.v().newInvokeStmt(
                                Jimple.v().newStaticInvokeExpr(helperMethod.makeRef(), StringConstant.v(methodSig + " uuid=" + uuid)));
                        units.insertBefore(logStatement, u);
                    }
                }
                b.validate();
            }
        }
    }));

    PackManager.v().runPacks();
    PackManager.v().writeOutput();
    if (new File(Config.outputAPKPath).exists()) {
        Util.LOGGER.info("finish instrumenting");
        Util.signAPK(Config.outputAPKPath);
        Util.LOGGER.info("finish signing");
        Util.LOGGER.info("instrumented apk: " + Config.outputAPKPath);
    } else {
        Util.LOGGER.warning("error instrumenting");
    }

    }

    static void output() {
        HashMap<String, Object> outputMap = new HashMap<>();
        outputMap.put("outputAPK", Config.outputAPKPath);
        outputMap.put("allMethods", allMethods);
        File instrumentResultFile = new File(Config.outputResultPath);
        JSONObject resultJson = new JSONObject(outputMap);
        try {
            FileUtils.writeStringToFile(instrumentResultFile, resultJson.toString(2), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
