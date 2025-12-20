package src.testers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import src.models.Process;
import src.schedulers.AGScheduler;

public class AGTester {

    // Define the folder name where JSON files are stored
    private static final String TEST_FOLDER = "src/tests";

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("    AG SCHEDULER TESTER");
        System.out.println("==========================================\n");

        // 1. Locate the 'test cases' folder
        File dir = new File(TEST_FOLDER);

        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("❌ Error: Folder '" + TEST_FOLDER + "' not found.");
            System.out.println("   Please create a folder named '" + TEST_FOLDER + "' in your project root");
            System.out.println("   and place your .json files inside it.");
            return;
        }

        // 2. Find files inside the folder
        File[] testFiles = dir.listFiles((d, name) -> name.startsWith("AG_test") && name.endsWith(".json"));

        if (testFiles == null || testFiles.length == 0) {
            System.out.println("❌ No test files found in '" + TEST_FOLDER + "/' matching 'AG_test*.json'.");
            return;
        }

        // 3. Sort files numerically
        Arrays.sort(testFiles, (f1, f2) -> {
            int n1 = extractNumber(f1.getName());
            int n2 = extractNumber(f2.getName());
            return Integer.compare(n1, n2);
        });

        int totalFiles = testFiles.length;
        int filesPassed = 0;

        System.out.println("Loaded " + totalFiles + " test cases from '" + TEST_FOLDER + "/' directory.\n");

        // 4. Run Loop
        for (File file : testFiles) {
            System.out.println(">>> TESTING: " + file.getName());
            boolean passed = runTest(file);
            
            if (passed) {
                System.out.println("RESULT: [ PASSED ] ✅");
                filesPassed++;
            } else {
                System.out.println("RESULT: [ FAILED ] ❌");
            }
            System.out.println("------------------------------------------\n");
        }

        System.out.println("==========================================");
        System.out.printf("SUMMARY: %d/%d Files Passed.\n", filesPassed, totalFiles);
        System.out.println("==========================================");
    }

    private static boolean runTest(File file) {
        try {
            String jsonContent = new String(Files.readAllBytes(file.toPath()));
            
            // 1. Parse Input
            List<Process> processes = parseInputProcesses(jsonContent);
            if (processes.isEmpty()) {
                System.out.println("   Error: No processes found in JSON.");
                return false;
            }

            // 2. Run Scheduler
            AGScheduler scheduler = new AGScheduler(processes);
            scheduler.run();

            // 3. Parse Expected Output
            List<String> expOrder = parseExpectedOrder(jsonContent);
            Map<String, ExpectedStats> expStats = parseExpectedStats(jsonContent);
            double expWt = parseDouble(jsonContent, "\"averageWaitingTime\"");
            double expTat = parseDouble(jsonContent, "\"averageTurnaroundTime\"");

            boolean filePassed = true;

            // --- CHECKORDER ---
            List<String> actOrder = scheduler.getExecutionOrder();
            
            System.out.println("   [Order Preview]: " + actOrder);

            if (!actOrder.equals(expOrder)) {
                filePassed = false;
                System.out.println("   ❌ Order Mismatch:");
                System.out.println("      Expected: " + expOrder);
            } 

            // --- CHECK PROCESS DETAILS ---
            processes.sort(Comparator.comparing(p -> p.name));
            for (Process p : processes) {
                ExpectedStats exp = expStats.get(p.name);
                if (exp == null) continue;

                boolean wtMatch = p.waitingTime == exp.waitingTime;
                boolean tatMatch = p.turnaroundTime == exp.turnaroundTime;
                boolean histMatch = p.quantumHistory.equals(exp.history);

                if (!wtMatch || !tatMatch || !histMatch) {
                    filePassed = false;
                    System.out.println("   ❌ " + p.name + " Failed:");
                    if (!wtMatch) System.out.printf("      WT: Exp %d | Act %d\n", exp.waitingTime, p.waitingTime);
                    if (!tatMatch) System.out.printf("      TAT: Exp %d | Act %d\n", exp.turnaroundTime, p.turnaroundTime);
                    if (!histMatch) System.out.printf("      Hist: Exp %s | Act %s\n", exp.history, p.quantumHistory);
                }
            }

            // --- CHECK AVERAGES ---
            double actWt = processes.stream().mapToInt(p -> p.waitingTime).average().orElse(0);
            double actTat = processes.stream().mapToInt(p -> p.turnaroundTime).average().orElse(0);

            if (Math.abs(actWt - expWt) >= 0.01) {
                filePassed = false;
                System.out.printf("   ❌ Avg WT: Exp %.2f | Act %.2f\n", expWt, actWt);
            }

            if (Math.abs(actTat - expTat) >= 0.01) {
                filePassed = false;
                System.out.printf("   ❌ Avg TAT: Exp %.2f | Act %.2f\n", expTat, actTat);
            }

            return filePassed;

        } catch (IOException e) {
            System.out.println("   File Read Error: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.out.println("   Execution Error: " + e.getMessage());
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
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
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