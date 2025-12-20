package src.testers;

import src.models.Process;
import src.schedulers.*;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SchedulerTester {

    private static final String TEST_FOLDER = "src/tests";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        List<File> testFiles = loadTestFiles();
        if (testFiles.isEmpty()) {
            printError("No JSON test files found in '" + TEST_FOLDER + "'.");
            return;
        }

        while (true) {
            printHeader("CPU SCHEDULER TESTER");
            System.out.println("  Loaded " + testFiles.size() + " test files.");
            System.out.println("----------------------------------------");
            System.out.println("  [1] Test Shortest Job First (SJF)");
            System.out.println("  [2] Test Round Robin (RR)");
            System.out.println("  [3] Test Priority Scheduling");
            System.out.println("  [4] Test AG Scheduling");
            System.out.println("  [5] Run ALL Tests");
            System.out.println("  [0] Exit");
            System.out.println("----------------------------------------");
            System.out.print("Enter your choice: ");

            String input = scanner.nextLine();
            
            switch (input) {
                case "1": runBatch(testFiles, "SJF"); break;
                case "2": runBatch(testFiles, "RR"); break;
                case "3": runBatch(testFiles, "Priority"); break;
                case "4": runBatch(testFiles, "AG"); break;
                case "5": runBatch(testFiles, "ALL"); break;
                case "0": 
                    System.out.println("Goodbye!");
                    return;
                default:
                    System.out.println("Invalid choice, please try again.");
            }
            
            System.out.println("\nPress [ENTER] to return to menu...");
            scanner.nextLine();
        }
    }

    // =============================================================
    //                     BATCH RUNNER LOGIC
    // =============================================================

    private static void runBatch(List<File> files, String mode) {
        int totalPassed = 0;
        int totalFailed = 0;
        int filesSkipped = 0;

        for (File file : files) {
            try {
                // Read File Content
                String jsonContent = new String(Files.readAllBytes(file.toPath()));
                
                // Parse the "Input" block purely to check context/processes
                String inputBlock = extractBlock(jsonContent, "\"input\"");
                String expectedBlock = extractBlock(jsonContent, "\"expectedOutput\"");

                // If input parsing fails, skip file
                List<Process> masterList = parseProcesses(inputBlock);
                if (masterList.isEmpty()) {
                    printError("Skipping " + file.getName() + " (No processes found)");
                    continue;
                }

                // Header for this file
                System.out.println("\n" + "=".repeat(60));
                System.out.println("📂 FILE: " + file.getName());
                System.out.println("=".repeat(60));

                // Display Input Table once per file
                printProcessTable(masterList, extractInt(inputBlock, "\"quantum\"")); // heuristic display

                // Check Global Settings
                int contextSwitch = extractInt(inputBlock, "\"contextSwitch\"");
                int rrQuantum     = extractInt(inputBlock, "\"rrQuantum\"");
                int aging         = extractInt(inputBlock, "\"agingInterval\"");

                boolean filePassed = true;
                boolean testRan = false;

                // --- 1. SJF TEST ---
                if (mode.equals("SJF") || mode.equals("ALL")) {
                    if (jsonContent.contains("\"SJF\"")) {
                        testRan = true;
                        if (!runSingleTest("SJF", masterList, contextSwitch, 0, 0, 
                                           extractBlock(expectedBlock, "\"SJF\""))) {
                            filePassed = false;
                        }
                    }
                }

                // --- 2. RR TEST ---
                if (mode.equals("RR") || mode.equals("ALL")) {
                    if (jsonContent.contains("\"RR\"")) {
                        testRan = true;
                        if (!runSingleTest("RR", masterList, contextSwitch, rrQuantum, 0, 
                                           extractBlock(expectedBlock, "\"RR\""))) {
                            filePassed = false;
                        }
                    }
                }

                // --- 3. PRIORITY TEST ---
                if (mode.equals("Priority") || mode.equals("ALL")) {
                    if (jsonContent.contains("\"Priority\"")) {
                        testRan = true;
                        if (!runSingleTest("Priority", masterList, contextSwitch, 0, aging, 
                                           extractBlock(expectedBlock, "\"Priority\""))) {
                            filePassed = false;
                        }
                    }
                }

                // --- 4. AG TEST ---
                if (mode.equals("AG") || mode.equals("ALL")) {
                    // Detect if this file is for AG (supports flat or nested format)
                    boolean isNested = jsonContent.contains("\"AG\"");
                    boolean isFlat = !isNested && inputBlock.contains("\"quantum\""); // Input has quantum field

                    if (isNested || isFlat) {
                        testRan = true;
                        String expectedData = isNested ? extractBlock(expectedBlock, "\"AG\"") : expectedBlock;
                        if (!runSingleTest("AG", masterList, 0, 0, 0, expectedData)) {
                            filePassed = false;
                        }
                    }
                }

                if (!testRan) {
                    System.out.println("No relevant data found in this file for " + mode);
                    filesSkipped++;
                } else if (filePassed) {
                    totalPassed++;
                } else {
                    totalFailed++;
                }

            } catch (Exception e) {
                System.out.println("❌ Critical Error reading file: " + e.getMessage());
                totalFailed++;
            }
        }

        // Batch Summary
        System.out.println("\n" + "#".repeat(40));
        System.out.printf("  BATCH SUMMARY (%s)%n", mode);
        System.out.println("#".repeat(40));
        System.out.println("  ✅ Files Passed : " + totalPassed);
        System.out.println("  ❌ Files Failed : " + totalFailed);
        if(filesSkipped > 0) System.out.println("  Files Skipped: " + filesSkipped);
        System.out.println("#".repeat(40));
    }


    private static boolean runSingleTest(String algoName, List<Process> masterList, 
                                         int context, int quantum, int aging, String expectedJson) {
        
        System.out.println("\n🔹 Running: " + algoName + " Scheduler");
        System.out.println("-".repeat(30));

        // 1. DEEP COPY PROCESSES (Crucial: Start fresh every time)
        List<Process> freshProcesses = deepCopy(masterList);

        // 2. Select & Run Scheduler
        List<String> actualOrder;
        
        try {
            switch (algoName) {
                case "SJF":
                    SJFScheduler sjf = new SJFScheduler(freshProcesses, context);
                    sjf.run();
                    actualOrder = sjf.getExecutionOrder();
                    break;
                case "RR":
                    RoundRobinScheduler rr = new RoundRobinScheduler(freshProcesses, quantum, context);
                    rr.run();
                    actualOrder = rr.getExecutionOrder();
                    break;
                case "Priority":
                    PriorityScheduler prio = new PriorityScheduler(freshProcesses, context, aging);
                    prio.run();
                    actualOrder = prio.getExecutionOrder();
                    break;
                case "AG":
                    AGScheduler ag = new AGScheduler(freshProcesses);
                    ag.run();
                    actualOrder = ag.getExecutionOrder();
                    break;
                default: return false;
            }
        } catch (Exception e) {
            System.out.println("❌ Runtime Exception in Scheduler: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        // 3. Verify Results
        return verifyResults(freshProcesses, actualOrder, expectedJson);
    }

    // =============================================================
    //                 VERIFICATION & REPORTING
    // =============================================================

    private static boolean verifyResults(List<Process> actualProcs, List<String> actOrder, String expectedJson) {
        
        // Parse Expectations
        List<String> expOrder = parseStringArray(expectedJson, "\"executionOrder\"");
        Map<String, ExpectedStats> expStats = parseExpectedStats(expectedJson);
        double expWt = parseDouble(expectedJson, "\"averageWaitingTime\"");
        double expTat = parseDouble(expectedJson, "\"averageTurnaroundTime\"");

        boolean allGood = true;

        // A. Verify Order
        System.out.println("1. Execution Order:");
        if (actOrder.equals(expOrder)) {
            System.out.println("   ✅ Match: " + actOrder);
        } else {
            System.out.println("   ❌ FAIL");
            System.out.println("      Expected: " + expOrder);
            System.out.println("      Actual:   " + actOrder);
            allGood = false;
        }

        // B. Verify Details Table
        System.out.println("\n2. Process Details:");
        System.out.printf("   %-5s | %-12s | %-12s | %-20s | %s%n", "PID", "Wait Time", "Turnaround", "Quantum Hist.", "Status");
        System.out.println("   ----------------------------------------------------------------------------");

        // Sort for consistent display
        actualProcs.sort(Comparator.comparing(p -> p.name));

        for (Process p : actualProcs) {
            ExpectedStats exp = expStats.get(p.name);
            if (exp == null) continue;

            boolean wtOk = p.waitingTime == exp.wt;
            boolean tatOk = p.turnaroundTime == exp.tat;
            // Only check history if expected data exists (AG specific)
            boolean histOk = exp.history.isEmpty() || p.quantumHistory.equals(exp.history);

            boolean pPass = wtOk && tatOk && histOk;
            if (!pPass) allGood = false;

            String status = pPass ? "✅ OK" : "❌ FAIL";
            
            // Format strings for table
            String wtStr = String.format("%d (Ex:%d)", p.waitingTime, exp.wt);
            String tatStr = String.format("%d (Ex:%d)", p.turnaroundTime, exp.tat);
            String histStr = exp.history.isEmpty() ? "-" : "Ex:" + exp.history;

            if (pPass) {
                wtStr = String.valueOf(p.waitingTime);
                tatStr = String.valueOf(p.turnaroundTime);
                histStr = exp.history.isEmpty() ? "-" : p.quantumHistory.toString();
            }

            System.out.printf("   %-5s | %-12s | %-12s | %-20s | %s%n", 
                p.name, wtStr, tatStr, histStr, status);
        }

        // C. Verify Averages
        double actWt = actualProcs.stream().mapToInt(p -> p.waitingTime).average().orElse(0);
        double actTat = actualProcs.stream().mapToInt(p -> p.turnaroundTime).average().orElse(0);
        
        boolean wtAvgOk = Math.abs(actWt - expWt) < 0.1;
        boolean tatAvgOk = Math.abs(actTat - expTat) < 0.1;

        if(!wtAvgOk || !tatAvgOk) allGood = false;

        System.out.println("\n3. Averages:");
        System.out.printf("   Wait Time  : Act %5.2f / Exp %5.2f  [%s]%n", actWt, expWt, wtAvgOk ? "✅" : "❌");
        System.out.printf("   Turnaround : Act %5.2f / Exp %5.2f  [%s]%n", actTat, expTat, tatAvgOk ? "✅" : "❌");

        return allGood;
    }

    // =============================================================
    //                 CUSTOM JSON PARSING (No Libraries)
    // =============================================================

    // Extracts { ... } or [ ... ] block content
    private static String extractBlock(String json, String key) {
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return "";
        
        int start = -1;
        char searchChar = '{';
        
        // Find if we are looking for array or object
        for(int i = keyIdx + key.length(); i < json.length(); i++) {
            char c = json.charAt(i);
            if(c == '{') { start = i; searchChar = '{'; break; }
            if(c == '[') { start = i; searchChar = '['; break; }
        }
        if (start == -1) return "";

        char endChar = (searchChar == '{') ? '}' : ']';
        int open = 0;
        
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == searchChar) open++;
            if (c == endChar) open--;
            if (open == 0) return json.substring(start + 1, i);
        }
        return "";
    }

    private static List<Process> parseProcesses(String inputJsonBlock) {
        List<Process> list = new ArrayList<>();
        String procArrayStr = extractBlock("{\"processes\":" + inputJsonBlock + "}", "\"processes\""); // Hack to reuse extractor
        if(procArrayStr.isEmpty()) procArrayStr = extractBlock(inputJsonBlock, "\"processes\""); // Try direct

        if (procArrayStr.isEmpty()) return list;

        // Split by object end "},"
        String[] objects = procArrayStr.split("\\},\\s*\\{");
        
        for (String obj : objects) {
            String clean = obj.replace("{", "").replace("}", "");
            
            String name = extractString(clean, "\"name\"");
            int arr = extractInt(clean, "\"arrival\"");
            int bst = extractInt(clean, "\"burst\"");
            int prio = extractInt(clean, "\"priority\"");
            int quant = extractInt(clean, "\"quantum\"");

            Process p = new Process(name, arr, bst, prio);
            // Always set initial quantum (AG needs it in history)
            if (clean.contains("\"quantum\"")) {
                p.updateQuantum(quant);
            }
            list.add(p);
        }
        return list;
    }

    private static Map<String, ExpectedStats> parseExpectedStats(String json) {
        Map<String, ExpectedStats> map = new HashMap<>();
        String resultsBlock = extractBlock(json, "\"processResults\"");
        
        // Regex to separate objects { ... }
        Pattern objPat = Pattern.compile("\\{([^{}]+|(\\{[^{}]+\\}))+\\}");
        Matcher m = objPat.matcher(resultsBlock);

        while (m.find()) {
            String obj = m.group();
            String name = extractString(obj, "\"name\"");
            int wt = extractInt(obj, "\"waitingTime\"");
            int tat = extractInt(obj, "\"turnaroundTime\"");
            
            // Parse array [1, 2, 3] manually
            List<Integer> hist = new ArrayList<>();
            String histStr = extractBlock(obj, "\"quantumHistory\"");
            if (!histStr.isEmpty()) {
                Matcher numM = Pattern.compile("\\d+").matcher(histStr);
                while(numM.find()) hist.add(Integer.parseInt(numM.group()));
            }

            map.put(name, new ExpectedStats(wt, tat, hist));
        }
        return map;
    }

    private static List<String> parseStringArray(String json, String key) {
        List<String> list = new ArrayList<>();
        String block = extractBlock(json, key);
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(block);
        while (m.find()) list.add(m.group(1));
        return list;
    }

    private static String extractString(String src, String key) {
        Matcher m = Pattern.compile(key + "\\s*:\\s*\"([^\"]+)\"").matcher(src);
        return m.find() ? m.group(1) : "";
    }
    private static int extractInt(String src, String key) {
        Matcher m = Pattern.compile(key + "\\s*:\\s*(\\d+)").matcher(src);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
    private static double parseDouble(String src, String key) {
        Matcher m = Pattern.compile(key + "\\s*:\\s*([\\d\\.]+)").matcher(src);
        return m.find() ? Double.parseDouble(m.group(1)) : 0.0;
    }

    // =============================================================
    //                     UTILITIES
    // =============================================================

    private static List<File> loadTestFiles() {
        File dir = new File(TEST_FOLDER);
        if (!dir.exists() || !dir.isDirectory()) return new ArrayList<>();

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return new ArrayList<>();

        // Sort numerically (test1, test2, test10)
        Arrays.sort(files, (f1, f2) -> {
            int n1 = extractNumber(f1.getName());
            int n2 = extractNumber(f2.getName());
            if (n1 != -1 && n2 != -1) return Integer.compare(n1, n2);
            return f1.getName().compareTo(f2.getName());
        });
        return Arrays.asList(files);
    }

    private static int extractNumber(String s) {
        Matcher m = Pattern.compile("(\\d+)").matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    // Creates a fresh copy of processes so one scheduler doesn't affect another
    private static List<Process> deepCopy(List<Process> original) {
        List<Process> copy = new ArrayList<>();
        for (Process p : original) {
            Process np = new Process(p.name, p.arrivalTime, p.burstTime, p.priority);
            np.updateQuantum(p.quantum); // Carry over initial AG quantum config
            copy.add(np);
        }
        return copy;
    }

    private static void printHeader(String title) {
        System.out.println("\n========================================");
        System.out.println("   " + title);
        System.out.println("========================================");
    }

    private static void printError(String msg) {
        System.out.println("❌ ERROR: " + msg);
    }

    private static void printProcessTable(List<Process> list, int defaultQ) {
        System.out.println("Input Processes:");
        System.out.printf("  %-5s | %-7s | %-7s | %-8s | %-7s%n", "Name", "Arrival", "Burst", "Priority", "Quantum");
        System.out.println("  ---------------------------------------------------");
        list.sort(Comparator.comparing(p -> p.arrivalTime));
        for (Process p : list) {
            System.out.printf("  %-5s | %-7d | %-7d | %-8d | %-7d%n", 
                p.name, p.arrivalTime, p.burstTime, p.priority, p.quantum);
        }
    }

    // Simple data holder for expected results
    static class ExpectedStats {
        int wt, tat;
        List<Integer> history;
        public ExpectedStats(int wt, int tat, List<Integer> history) {
            this.wt = wt; this.tat = tat; this.history = history;
        }
    }
}