package src.testers;

import java.io.File;
import src.models.Process;
import src.schedulers.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AGTester {

    private static final String TEST_FOLDER = "src/tests";

    public static void main(String[] args) {
        System.out.println("=======================================================");
        System.out.println("          AG SCHEDULER - TEST REPORT          ");
        System.out.println("=======================================================\n");

        File dir = new File(TEST_FOLDER);
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("❌ Error: Folder '" + TEST_FOLDER + "' not found.");
            return;
        }

        File[] testFiles = dir.listFiles((d, name) -> name.startsWith("AG_test") && name.endsWith(".json"));

        if (testFiles == null || testFiles.length == 0) {
            System.out.println("❌ No test files found in '" + TEST_FOLDER + "'.");
            return;
        }

        // Sort files numerically
        Arrays.sort(testFiles, (f1, f2) -> {
            int n1 = extractNumber(f1.getName());
            int n2 = extractNumber(f2.getName());
            return Integer.compare(n1, n2);
        });

        int totalFiles = testFiles.length;
        int filesPassed = 0;

        for (File file : testFiles) {
            boolean passed = runDetailedTest(file);
            if (passed) filesPassed++;
            System.out.println("\n\n"); // Spacing between files
        }

        System.out.println("#######################################################");
        System.out.printf("FINAL SUMMARY: %d / %d Files Passed.\n", filesPassed, totalFiles);
        System.out.println("#######################################################");
    }

    private static boolean runDetailedTest(File file) {
        System.out.println("_______________________________________________________");
        System.out.println(">>> FILE: " + file.getName());
        System.out.println("‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾");

        try {
            String jsonContent = new String(Files.readAllBytes(file.toPath()));
            
            // 1. Parse & Display Inputs
            List<Process> processes = parseInputProcesses(jsonContent);
            if (processes.isEmpty()) {
                System.out.println("Error: No processes found in JSON.");
                return false;
            }

            System.out.println("[ 1. INPUTS ]");
            System.out.printf("  %-5s | %-7s | %-7s | %-8s | %-7s%n", "Name", "Arrival", "Burst", "Priority", "Quantum");
            System.out.println("  ---------------------------------------------------");
            for (Process p : processes) {

                int initQ = p.quantum; 
                System.out.printf("  %-5s | %-7d | %-7d | %-8d | %-7d%n", 
                    p.name, p.arrivalTime, p.burstTime, p.priority, initQ);
            }
            System.out.println();

            // 2. Run Scheduler
            AGScheduler scheduler = new AGScheduler(processes);
            scheduler.run();

            // 3. Parse Expected
            List<String> expOrder = parseExpectedOrder(jsonContent);
            Map<String, ExpectedStats> expStats = parseExpectedStats(jsonContent);
            double expWt = parseDouble(jsonContent, "\"averageWaitingTime\"");
            double expTat = parseDouble(jsonContent, "\"averageTurnaroundTime\"");

            boolean filePassed = true;

            // 4. Compare Order
            System.out.println("[ 2. EXECUTION ORDER ]");
            List<String> actOrder = scheduler.getExecutionOrder();
            boolean orderMatch = actOrder.equals(expOrder);
            if (!orderMatch) filePassed = false;

            System.out.println("  Expected: " + expOrder);
            System.out.println("  Actual:   " + actOrder);
            System.out.println("  Status:   " + (orderMatch ? "✅ MATCH" : "❌ MISMATCH"));
            System.out.println();

            // 5. Process Metrics
            System.out.println("[ 3. PROCESS DETAILS ]");
            processes.sort(Comparator.comparing(p -> p.name));
            
            for (Process p : processes) {
                ExpectedStats exp = expStats.get(p.name);
                if (exp == null) continue;

                boolean wtMatch = p.waitingTime == exp.waitingTime;
                boolean tatMatch = p.turnaroundTime == exp.turnaroundTime;
                boolean histMatch = p.quantumHistory.equals(exp.history);
                boolean processPassed = wtMatch && tatMatch && histMatch;

                if (!processPassed) filePassed = false;

                String pStatus = processPassed ? "✅ OK" : "❌ FAIL";
                System.out.printf("  > Process %s  [%s]%n", p.name, pStatus);
                
                // Waiting Time
                System.out.printf("      Waiting Time    : Exp %-3d  | Act %-3d  %s%n", 
                    exp.waitingTime, p.waitingTime, (wtMatch ? "" : "<-- DIFF"));
                
                // Turnaround Time
                System.out.printf("      Turnaround Time : Exp %-3d  | Act %-3d  %s%n", 
                    exp.turnaroundTime, p.turnaroundTime, (tatMatch ? "" : "<-- DIFF"));
                
                // History
                System.out.printf("      Quantum History : Exp %-12s | Act %-12s %s%n", 
                    exp.history.toString(), p.quantumHistory.toString(), (histMatch ? "" : "<-- DIFF"));
                System.out.println();
            }

            // 6. Averages
            System.out.println("[ 4. AVERAGES ]");
            double actWt = processes.stream().mapToInt(p -> p.waitingTime).average().orElse(0);
            double actTat = processes.stream().mapToInt(p -> p.turnaroundTime).average().orElse(0);

            boolean avgWtMatch = Math.abs(actWt - expWt) < 0.01;
            boolean avgTatMatch = Math.abs(actTat - expTat) < 0.01;
            
            if (!avgWtMatch || !avgTatMatch) filePassed = false;

            System.out.printf("  Avg Waiting Time    : Exp %-5.1f | Act %-5.1f %s%n", 
                expWt, actWt, (avgWtMatch ? "✅" : "❌"));
            System.out.printf("  Avg Turnaround Time : Exp %-5.1f | Act %-5.1f %s%n", 
                expTat, actTat, (avgTatMatch ? "✅" : "❌"));

            // Final File Status
            System.out.println("_______________________________________________________");
            System.out.println("RESULT: " + (filePassed ? "PASSED ✅" : "FAILED ❌"));
            
            return filePassed;

        } catch (IOException e) {
            System.out.println("File Read Error: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.out.println("Execution Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ==========================================
    //       HELPER METHODS
    // ==========================================
    
    private static int extractNumber(String filename) {
        try {
            Matcher m = Pattern.compile("(\\d+)").matcher(filename);
            if (m.find()) return Integer.parseInt(m.group(1));
        } catch (Exception ignored) {}
        return 9999;
    }

    // ==========================================
    //       CUSTOM JSON PARSING LOGIC
    // ==========================================

    private static List<Process> parseInputProcesses(String json) {
        List<Process> list = new ArrayList<>();
        String processBlock = extractJsonArrayBlock(json, "\"processes\"");
        if (processBlock.isEmpty()) return list;

        String[] objects = processBlock.split("\\},\\s*\\{");
        for (String obj : objects) {
            obj = obj.replace("{", "").replace("}", "");
            String name = extractString(obj, "\"name\"");
            int arrival = extractInt(obj, "\"arrival\"");
            int burst = extractInt(obj, "\"burst\"");
            int priority = extractInt(obj, "\"priority\"");
            int quantum = extractInt(obj, "\"quantum\"");
            Process p = new Process(name, arrival, burst, priority);
            p.updateQuantum(quantum);
            list.add(p);
        }
        return list;
    }

    private static List<String> parseExpectedOrder(String json) {
        List<String> order = new ArrayList<>();
        String block = extractJsonArrayBlock(json, "\"executionOrder\"");
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(block);
        while (m.find()) order.add(m.group(1));
        return order;
    }

    private static Map<String, ExpectedStats> parseExpectedStats(String json) {
        Map<String, ExpectedStats> map = new HashMap<>();
        String resultsBlock = extractJsonArrayBlock(json, "\"processResults\"");
        Pattern objPattern = Pattern.compile("\\{([^{}]+|(\\{[^{}]+\\}))+\\}");
        Matcher m = objPattern.matcher(resultsBlock);

        while (m.find()) {
            String obj = m.group();
            String name = extractString(obj, "\"name\"");
            int wt = extractInt(obj, "\"waitingTime\"");
            int tat = extractInt(obj, "\"turnaroundTime\"");
            List<Integer> history = new ArrayList<>();
            String histBlock = extractJsonArrayBlock(obj, "\"quantumHistory\"");
            Matcher numM = Pattern.compile("\\d+").matcher(histBlock);
            while (numM.find()) history.add(Integer.parseInt(numM.group()));
            map.put(name, new ExpectedStats(wt, tat, history));
        }
        return map;
    }

    private static String extractJsonArrayBlock(String json, String key) {
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return "";
        int startBracket = json.indexOf("[", keyIdx);
        if (startBracket == -1) return "";
        int open = 0;
        for (int i = startBracket; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') open++;
            if (c == ']') open--;
            if (open == 0) return json.substring(startBracket + 1, i);
        }
        return "";
    }

    private static String extractString(String src, String key) {
        Matcher m = Pattern.compile(key + "\\s*:\\s*\"([^\"]+)\"").matcher(src);
        return m.find() ? m.group(1) : "";
    }
    private static int extractInt(String src, String key) {
        Matcher m = Pattern.compile(key + "\\s*:\\s*(\\d+)").matcher(src);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
    private static double parseDouble(String json, String key) {
        Matcher m = Pattern.compile(key + "\\s*:\\s*([\\d\\.]+)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : 0.0;
    }

    static class ExpectedStats {
        int waitingTime;
        int turnaroundTime;
        List<Integer> history;
        ExpectedStats(int wt, int tat, List<Integer> h) { this.waitingTime = wt; this.turnaroundTime = tat; this.history = h; }
    }
}