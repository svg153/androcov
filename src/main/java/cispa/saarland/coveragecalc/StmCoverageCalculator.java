package cispa.saarland.coveragecalc;

import javafx.util.Pair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;

public class StmCoverageCalculator {

    private static int numStatementsInApp;

    private static Date firstT=null;
    private static Date lastT=null;

    private static ArrayList<Pair<String, Date>> executedStatements;

    private static final String instumentationsFolder = "./instrumentation-statements/statements-list";

    private static final String coverageLogsFolder = "./instrumentation-statements/coverage-reports";
    private static final String outReportPath = "./instrumentation-statements/coverage-reports/StmCovReport.txt";


    public static void main(String args[]) {

        StmCoverageCalculator cc = new StmCoverageCalculator();

        File covLogs = new File (coverageLogsFolder);
        for (File f : covLogs.listFiles()) {
            if (f.isDirectory())
                cc.getStmCoverage(f,outReportPath);
        }
    }

   /* *
     * Creates a report summarizing statement coverage
     * @param apkFolder: Folder containing log files with executed statements (form logcat) and one .json file with all the instrumented stms
     *                 in the app
     * @param outCovReportFile: Path to store the output report
     */
    private void getStmCoverage (File apkFolder, String outCovReportFile) {

        numStatementsInApp=0;
        executedStatements = new ArrayList();

        String apkName = apkFolder.getName();

        File[] instrumentationsFolder = new File(instumentationsFolder).listFiles();

        // Get the instrumentation file of the app
        for (File instrumentationJson : instrumentationsFolder) {

            if (apkName.contains(refineInstrumentationFileName(instrumentationJson.getName()))) {

                // Get all instrumented stms in the app (stored in a json file)
                 numStatementsInApp = getNumAppStms(instrumentationJson.getAbsolutePath());
                break;
            }
        }

        // Get executed methods
        File[] execMethodsFile = apkFolder.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return !name.endsWith(".json");
            }
        });

        for (File logcatFile : execMethodsFile)
            getExecutedStmsEvol(logcatFile);

        // Generate statement coverage report
        genCovStmReport(outCovReportFile, apkName);

        // Generate coverage evolution report
        genCovStmEvolReport(outCovReportFile, apkName, 30000);
    }

    /**
     * Remove begining and end of instrumentation files ("instrumentation-" and ".json")
     * @param fileName
     * @return
     */
    private String refineInstrumentationFileName (String fileName) {
        return fileName.substring(16, fileName.length()-5);
    }

    private int getNumAppStms(String instrumentationFile) {
        String jsonData = readFile(instrumentationFile);
        JSONObject jobj = new JSONObject(jsonData);

        JSONArray jarr = new JSONArray(jobj.getJSONArray("allMethods").toString() );
        return jarr.length();
    }



    public static String readFile(String filename) {
        String result = "";
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                line = br.readLine();
            }
            result = sb.toString();
        } catch(Exception e) {
            e.printStackTrace();
        }
        return result;
    }



    private void getExecutedStmsEvol(File executedMethodsFile) {
       boolean isFirst = true;
        try {

            BufferedReader br = new BufferedReader(new FileReader(executedMethodsFile));

            String line = br.readLine();
            while (line != null) {
                if (line.contains("[androcov]")) {
                    String[] parts = line.split("uuid=", 2);

                    // Get uiuid
                    String uuid=parts[1];

                    // Get timestamp
                    String[] logParts = parts[0].split(" ");
                    String timestamp = logParts[0] + " " + logParts[1];

                    SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
                    Date tms = dateFormat.parse(timestamp);
                    lastT = tms;

                    if (isFirst) {
                        firstT=tms;
                        isFirst=false;
                    }

                    // Add the statement if it wasn't executed before
                    boolean found = false;
                    for (Pair <String,Date> stm : executedStatements)
                    {
                        if (stm.getKey().equals(uuid))
                            found=true;
                    }

                    if (!found) {
                        Pair <String, Date> stmExec = new Pair<String, Date>(uuid, tms);
                        executedStatements.add(stmExec);
                    }
                }
                line = br.readLine();
            }
            System.out.println();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }


    private void genCovStmReport(String outCovReportFile, String apkName) {
        System.out.println("Generating coverage report.....");
        try {

            int numExecutedStms = executedStatements.size();
            double coverage =  numExecutedStms / (double) numStatementsInApp;

            // Write coverage report
            String header = "App\t#StatementsInApp\t#ExecStatements\tCoverage\n";
            String result = apkName+"\t\t"+numStatementsInApp+"\t\t"+numExecutedStms+"\t\t"+String.format("%.2f", coverage)+"\n";
            System.out.println("Coverage: "+result);

            Files.write(Paths.get(outCovReportFile), header.getBytes(), StandardOpenOption.CREATE);
            Files.write(Paths.get(outCovReportFile), result.getBytes(), StandardOpenOption.APPEND);

        }
        catch (IOException ex ){
            ex.printStackTrace();
        }
    }


    /**
     * Generate the report with the cummulative statement coverage evolution
     * @param outCovReportFile
     * @param
     */
    private void genCovStmEvolReport(String outCovReportFile, String apkName, long tick) {
        System.out.println("Generating coverage evolution file.....");
        String outFileReport = outCovReportFile+"-"+apkName+"-EVOL.txt";

        String header = "T\t#ExecMethods\n";

        try {
            Files.write(Paths.get(outFileReport), header.getBytes(), StandardOpenOption.CREATE);

            ArrayList<Integer> methodsPerInterval = divideLogsByTmst(executedStatements, tick);

            for (int i=0; i< methodsPerInterval.size(); i++) {
                String result = i + "\t" + methodsPerInterval.get(i)+"\n" ;
                Files.write(Paths.get(outFileReport), result.getBytes(), StandardOpenOption.APPEND);
            }

        }
        catch (IOException ex ){
            ex.printStackTrace();
        }
    }


    /**
     * Groups the statments by intervals of time
     * @param executedStatements
     * @param intervalLength
     * @return
     */
    private  ArrayList<Integer> divideLogsByTmst (ArrayList<Pair<String, Date>> executedStatements, long intervalLength) {
        System.out.println(">> Dividing logs by timestamp......");

            ArrayList<Integer> methodsPerInterval = new ArrayList();
            methodsPerInterval.add(0);

        try {
            int currentCounter = 0;
            long startTime = executedStatements.get(0).getValue().getTime();


            long currentLimit = startTime + intervalLength;
            Iterator<Pair<String, Date>> it = executedStatements.iterator();

            while (it.hasNext()) {
                if (currentLimit <= lastT.getTime()) {
                    Pair<String, Date> stm = it.next();

                    if (stm.getValue().getTime() <= currentLimit) {
                        currentCounter++;
                    } else {
                        methodsPerInterval.add(currentCounter);
                        currentCounter++;
                        currentLimit = currentLimit + intervalLength;
                    }
                }

                //This is the last iteration
                else {
                    currentLimit = lastT.getTime();
                    System.out.println("**In the else!!!!");
                }
            }

            while (currentLimit <= lastT.getTime()) {
                methodsPerInterval.add(currentCounter);
                currentLimit = currentLimit + intervalLength;
            }

        }
        catch(Exception ex) {ex.printStackTrace();}

        return methodsPerInterval;
    }


}
