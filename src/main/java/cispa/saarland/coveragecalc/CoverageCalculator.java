package cispa.saarland.coveragecalc;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;

public class CoverageCalculator {

    private static Hashtable<String,Integer> methodsInApp;

    private static final String instumentationsFolder = "./instrumentation-appMethods/method-lists";

    private static final String coverageLogsFolder = "./instrumentation-appMethods/coverage-reports/droidbot/model";
    private static final String outReportPath = "./instrumentation-appMethods/coverage-reports/droidbot/model/CovReport-Droidbot-model.txt";


    public static void main(String args[]) {

        CoverageCalculator cc = new CoverageCalculator();

        File covLogs = new File (coverageLogsFolder);
        for (File f : covLogs.listFiles()) {
            if (f.isDirectory())
                cc.getMethodCoverage(f,outReportPath);
                //cc.getMethodCoverageEvolution(f);
        }
    }

   /* *
     * Creates a report summarizing method coverage
     * @param apkFolder: Folder containing log files with executed methods (form logcat) and one .json file with all the instrumented methods
     *                 in the app
     * @param outCovReportFile: Path to store the output report
     */
    private void getMethodCoverage (File apkFolder, String outCovReportFile) {
        methodsInApp = new Hashtable<String,Integer>();

        String apkName = apkFolder.getName();


        //File[] instrumentationJson = apkFolder.listFiles((d, name) -> name.endsWith(".json"));
        /**File[] instrumentationJson = apkFolder.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".json");
            }
        });**/

        File[] instrumentationsFolder = new File(instumentationsFolder).listFiles();
        // Get the instrumentation file of the app
        for (File instrumentationJson : instrumentationsFolder) {

            if (apkName.contains(refineInstrumentationFileName(instrumentationJson.getName()))) {

                // Get all instrumented methods in the app (stored in a json file)
                //       for (File jsonFile : instrumentationJson)
                //getInstrumentedMethods(jsonFile.getAbsolutePath());
                getInstrumentedMethods(instrumentationJson.getAbsolutePath());
                break;
            }
        }

        // Get executed methods
        //File[] execMethodsFile = apkFolder.listFiles((d, name) -> !name.endsWith(".json"));
        File[] execMethodsFile = apkFolder.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return !name.endsWith(".json");
            }
        });

        for (File logcatFile : execMethodsFile)
            getExecutedMethods(logcatFile);

        // Generate method coverage report
        genCovReport(outCovReportFile, apkName);

    }

    /**
     * Remove begining and end of instrumentation files ("instrumentation-" and ".json")
     * @param fileName
     * @return
     */
    private String refineInstrumentationFileName (String fileName) {
        return fileName.substring(16, fileName.length()-5);
    }


    private void getInstrumentedMethods(String instrumentationFile) {
        String jsonData = readFile(instrumentationFile);
        JSONObject jobj = new JSONObject(jsonData);

        JSONArray jarr = new JSONArray(jobj.getJSONArray("allMethods").toString());

        for(int i = 0; i < jarr.length(); i++) {
            String method = jarr.getString(i);
            String mRefined = method.substring(1,method.length()-1);

            if (!mRefined.startsWith("android.support."))
                methodsInApp.put(mRefined,0);
        }
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

    private void getExecutedMethods(File executedMethodsFile) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(executedMethodsFile));
            //StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                if (line.contains("[androcov]")) {
                    String[] parts = line.split("<", 2);
                    String method = parts[parts.length - 1];
                    String mRefined = method.substring(0, method.length() - 1);

                    if (methodsInApp.containsKey(mRefined))
                        methodsInApp.put(mRefined, methodsInApp.get(mRefined) + 1);
                }

                line = br.readLine();
            }

        } catch(Exception e) {
            e.printStackTrace();
        }
    }


    private void genCovReport(String outCovReportFile, String apkName) {
        int numMethods = methodsInApp.size();
        int numNonExecutedMethods = Collections.frequency(methodsInApp.values(), 0);
        int numExecutedMethods = numMethods - numNonExecutedMethods;

        double coverage =  numExecutedMethods / (double) numMethods;

        // Write report
        String header = "App\t#Methods\t#ExecMethods\tCoverage\n";
        String result = apkName+"\t\t"+numMethods+"\t\t"+numExecutedMethods+"\t\t"+String.format("%.2f", coverage)+"\n"; //Add the app pkg
        System.out.println("Coverage: "+result);
        try {
            Files.write(Paths.get(outCovReportFile), header.getBytes(), StandardOpenOption.CREATE);
            Files.write(Paths.get(outCovReportFile), result.getBytes(), StandardOpenOption.APPEND);
        }
        catch (IOException ex ){
            ex.printStackTrace();
        }
    }


    private void getMethodCoverageEvolution (File executedMethodsApp) {
        LinkedHashMap<String, Date> executedMethods = new LinkedHashMap<String, Date>();

        File[] execMethodsApp = executedMethodsApp.listFiles();
        // Get the instrumentation file of the app
        for (File logcatFile : execMethodsApp) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(logcatFile));

                String line = br.readLine();
                while (line != null) {
                    if (line.contains("[androcov]")) {
                        String[] logParts = line.split(" ");
                        String timestamp = logParts[0] + " " + logParts[1];

                        String method = logParts[logParts.length - 3] + logParts[logParts.length - 2] + logParts[logParts.length - 1];
                        String mRefined = method.substring(1, method.length() - 1);


                        try {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd hh:mm:ss.SSS");
                            Date tms = dateFormat.parse(timestamp);

                            if( !executedMethods.containsKey(mRefined) )
                                executedMethods.put(mRefined, tms);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    line = br.readLine();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("End of method");

        divideLogsByTmst (executedMethods);

    }

    private  void divideLogsByTmst (LinkedHashMap<String, Date> executedMethods) {
        ArrayList<Integer> methodsPerInterval = new ArrayList<Integer>();

        long intervalLength = 32000;
        long currentLimit=0;
        int currentCounter = 0;
        boolean first = true;

        Set set = executedMethods.entrySet();
        Iterator i = set.iterator();

        while(i.hasNext()) {
            Map.Entry me = (Map.Entry) i.next();
            //String method = me.getKey().toString();
            Date tmt = (Date) me.getValue();

            if (first) {
                currentLimit = tmt.getTime() + intervalLength;
                currentCounter++;
                first = false;
            }

            else {
                if (tmt.getTime()<=currentLimit)
                    currentCounter++;
                else {
                    methodsPerInterval.add(currentCounter);
                    currentCounter++;
                    currentLimit = currentLimit + intervalLength;
                }
            }
        }
        methodsPerInterval.add(currentCounter);
        System.out.println(methodsPerInterval.size());
        genCovEvolReport(outReportPath ,methodsPerInterval,1);
    }

    /**
     *
     * @param outCovReportFile
     * @param
     */
    private void genCovEvolReport(String outCovReportFile,  ArrayList<Integer> methodsPerInterval, int tick ) {

        // Write report
        String header = "T\t#ExecMethods\n";
        //String result = apkName+"\t\t"+numMethods+"\t\t"+numExecutedMethods+"\t\t"+String.format("%.2f", coverage)+"\n"; //Add the app pkg
        //System.out.println("Coverage: "+result);
        try {
            Files.write(Paths.get(outCovReportFile), header.getBytes(), StandardOpenOption.CREATE);

            for (int i=1; i< methodsPerInterval.size(); i++) {
                String result = i + "\t" + methodsPerInterval.get(i)+"\n" ;
                Files.write(Paths.get(outCovReportFile), result.getBytes(), StandardOpenOption.APPEND);
            }

        }
        catch (IOException ex ){
            ex.printStackTrace();
        }


    }




}
