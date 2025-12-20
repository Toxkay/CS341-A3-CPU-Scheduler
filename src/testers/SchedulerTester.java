package src.testers;

import src.models.Process;
import src.schedulers.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

public class SchedulerTester {
    
    public static void main(String[] args) {
        System.out.println("========== CPU Scheduler Test Runner ==========\n");
        
        // Find all test files in src/tests
        List<String> testFiles = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String filename = "src/tests/test_" + i + ".json";
            if (new File(filename).exists()) {
                testFiles.add(filename);
            }
        }
        
        if (testFiles.isEmpty()) {
            System.out.println("No test files found!");
            return;
        }
        
        // Track results for each scheduler
        int sjfPassed = 0, sjfFailed = 0;
        int rrPassed = 0, rrFailed = 0;
        int priorityPassed = 0, priorityFailed = 0;
        int agPassed = 0, agFailed = 0;
        
        for (String testFile : testFiles) {
            System.out.println("\n" + "=".repeat(80));
            System.out.println("Running: " + testFile);
            System.out.println("=".repeat(80));
            
            try {
                // Read JSON file
                String content = new String(Files.readAllBytes(Paths.get(testFile)));
                JSONObject testCase = new JSONObject(content);
                
                // Test each scheduler
                System.out.println("\n[1] Testing SJF (Shortest Job First)...");
                boolean sjfResult = runSJFTest(testCase);
                if (sjfResult) sjfPassed++; else sjfFailed++;
                
                System.out.println("\n[2] Testing RR (Round Robin)...");
                boolean rrResult = runRRTest(testCase);
                if (rrResult) rrPassed++; else rrFailed++;
                
                System.out.println("\n[3] Testing Priority Scheduling...");
                boolean priorityResult = runPriorityTest(testCase);
                if (priorityResult) priorityPassed++; else priorityFailed++;
                
                System.out.println("\n[4] Testing AG Scheduling...");
                boolean agResult = runAGTest(testCase);
                if (agResult) agPassed++; else agFailed++;
                
            } catch (Exception e) {
                System.out.println("\n✗ ERROR: " + e.getMessage());
                e.printStackTrace();
                sjfFailed++; rrFailed++; priorityFailed++; agFailed++;
            }
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("FINAL TEST RESULTS:");
        System.out.println("=".repeat(80));
        System.out.println("SJF Scheduler:      Passed: " + sjfPassed + ", Failed: " + sjfFailed);
        System.out.println("RR Scheduler:       Passed: " + rrPassed + ", Failed: " + rrFailed);
        System.out.println("Priority Scheduler: Passed: " + priorityPassed + ", Failed: " + priorityFailed);
        System.out.println("AG Scheduler:       Passed: " + agPassed + ", Failed: " + agFailed);
        System.out.println("=".repeat(80));
        int totalPassed = sjfPassed + rrPassed + priorityPassed + agPassed;
        int totalFailed = sjfFailed + rrFailed + priorityFailed + agFailed;
        System.out.println("TOTAL:              Passed: " + totalPassed + ", Failed: " + totalFailed);
        System.out.println("=".repeat(80));
    }
    
    private static boolean runSJFTest(JSONObject testCase) throws Exception {
        String testName = testCase.getString("name");
        JSONObject input = testCase.getJSONObject("input");
        int contextSwitch = input.getInt("contextSwitch");
        JSONArray processesJson = input.getJSONArray("processes");

        // Create processes
        List<Process> processes = new ArrayList<>();
        for (int i = 0; i < processesJson.length(); i++) {
            JSONObject p = processesJson.getJSONObject(i);
            processes.add(new Process(
                p.getString("name"),
                p.getInt("arrival"),
                p.getInt("burst"),
                p.getInt("priority")
            ));
        }

        // Run SJF (preemptive SRTF) scheduler
        SJFScheduler scheduler = new SJFScheduler(processes, contextSwitch);
        scheduler.run();

        // Get expected output
        JSONObject expectedOutput = testCase.getJSONObject("expectedOutput");
        JSONObject sjfExpected = expectedOutput.getJSONObject("SJF");

        // Verify execution order
        JSONArray expectedOrder = sjfExpected.getJSONArray("executionOrder");
        List<String> actualOrder = scheduler.getExecutionOrder();

        System.out.println("\n--- VERIFICATION ---");
        System.out.println("Expected execution order: " + jsonArrayToList(expectedOrder));
        System.out.println("Actual execution order:   " + actualOrder);

        boolean executionOrderMatch = compareExecutionOrder(expectedOrder, actualOrder);
        if (!executionOrderMatch) {
            System.out.println("⚠ Execution order does not match!");
        } else {
            System.out.println("✓ Execution order matches!");
        }

        // Verify process results
        JSONArray expectedResults = sjfExpected.getJSONArray("processResults");
        boolean resultsMatch = verifyProcessResults(processes, expectedResults);

        // Verify averages
        double expectedAvgWT = sjfExpected.getDouble("averageWaitingTime");
        double expectedAvgTAT = sjfExpected.getDouble("averageTurnaroundTime");

        double actualAvgWT = processes.stream()
            .mapToDouble(p -> p.waitingTime)
            .average()
            .orElse(0);
        double actualAvgTAT = processes.stream()
            .mapToDouble(p -> p.turnaroundTime)
            .average()
            .orElse(0);

        System.out.println("\nExpected Avg WT: " + expectedAvgWT + " | Actual: " +
            String.format("%.1f", actualAvgWT));
        System.out.println("Expected Avg TAT: " + expectedAvgTAT + " | Actual: " +
            String.format("%.1f", actualAvgTAT));

        boolean avgMatch = Math.abs(expectedAvgWT - actualAvgWT) < 0.2 &&
                          Math.abs(expectedAvgTAT - actualAvgTAT) < 0.2;

        if (!avgMatch) {
            System.out.println("⚠ Averages do not match!");
        } else {
            System.out.println("✓ Averages match!");
        }

        boolean passed = executionOrderMatch && resultsMatch && avgMatch;
        System.out.println(passed ? "✓ SJF TEST PASSED" : "✗ SJF TEST FAILED");
        return passed;
    }
    
    // Round Robin scheduler test (IMPLEMENTED)
    private static boolean runRRTest(JSONObject testCase) throws Exception {
        String testName = testCase.getString("name");
        JSONObject input = testCase.getJSONObject("input");
        int contextSwitch = input.getInt("contextSwitch");
        int rrQuantum = input.getInt("rrQuantum");
        JSONArray processesJson = input.getJSONArray("processes");
        
        // Create processes
        List<Process> processes = new ArrayList<>();
        for (int i = 0; i < processesJson.length(); i++) {
            JSONObject p = processesJson.getJSONObject(i);
            processes.add(new Process(
                p.getString("name"),
                p.getInt("arrival"),
                p.getInt("burst"),
                p.getInt("priority")
            ));
        }
        
        // Run Round Robin scheduler
        RoundRobinScheduler scheduler = new RoundRobinScheduler(
            processes, rrQuantum, contextSwitch
        );
        scheduler.run();
        scheduler.printResults();
        
        // Get expected output
        JSONObject expectedOutput = testCase.getJSONObject("expectedOutput");
        JSONObject rrExpected = expectedOutput.getJSONObject("RR");
        
        // Verify execution order
        JSONArray expectedOrder = rrExpected.getJSONArray("executionOrder");
        List<String> actualOrder = scheduler.getExecutionOrder();
        
        System.out.println("\n--- VERIFICATION ---");
        System.out.println("Expected execution order: " + jsonArrayToList(expectedOrder));
        System.out.println("Actual execution order:   " + actualOrder);
        
        boolean executionOrderMatch = compareExecutionOrder(expectedOrder, actualOrder);
        if (!executionOrderMatch) {
            System.out.println("⚠ Execution order does not match!");
        } else {
            System.out.println("✓ Execution order matches!");
        }
        
        // Verify process results
        JSONArray expectedResults = rrExpected.getJSONArray("processResults");
        boolean resultsMatch = verifyProcessResults(processes, expectedResults);
        
        // Verify averages
        double expectedAvgWT = rrExpected.getDouble("averageWaitingTime");
        double expectedAvgTAT = rrExpected.getDouble("averageTurnaroundTime");
        
        double actualAvgWT = processes.stream()
            .mapToDouble(p -> p.waitingTime)
            .average()
            .orElse(0);
        double actualAvgTAT = processes.stream()
            .mapToDouble(p -> p.turnaroundTime)
            .average()
            .orElse(0);
        
        System.out.println("\nExpected Avg WT: " + expectedAvgWT + " | Actual: " + 
            String.format("%.1f", actualAvgWT));
        System.out.println("Expected Avg TAT: " + expectedAvgTAT + " | Actual: " + 
            String.format("%.1f", actualAvgTAT));
        
        boolean avgMatch = Math.abs(expectedAvgWT - actualAvgWT) < 0.2 &&
                          Math.abs(expectedAvgTAT - actualAvgTAT) < 0.2;
        
        if (!avgMatch) {
            System.out.println("⚠ Averages do not match!");
        } else {
            System.out.println("✓ Averages match!");
        }
        
        boolean passed = executionOrderMatch && resultsMatch && avgMatch;
        System.out.println(passed ? "✓ RR TEST PASSED" : "✗ RR TEST FAILED");
        return passed;
    }
    
    // TODO: Implement Priority scheduler test
    private static boolean runPriorityTest(JSONObject testCase) throws Exception {
        System.out.println("⚠ Priority Scheduler not implemented yet - SKIPPING");
        return false;
    }
    
    // TODO: Implement AG scheduler test
    private static boolean runAGTest(JSONObject testCase) throws Exception {
        System.out.println("⚠ AG Scheduler not implemented yet - SKIPPING");
        return false;
    }
    
    // Helper methods for verification
    private static List<String> jsonArrayToList(JSONArray arr) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(arr.getString(i));
        }
        return list;
    }
    
    private static boolean compareExecutionOrder(JSONArray expected, List<String> actual) {
        if (expected.length() != actual.size()) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if (!expected.getString(i).equals(actual.get(i))) {
                return false;
            }
        }
        return true;
    }
    
    private static boolean verifyProcessResults(List<Process> processes, JSONArray expectedResults) {
        boolean allMatch = true;
        
        System.out.println("\nProcess Results Comparison:");
        System.out.println(String.format("%-8s %-20s %-20s %-20s %-20s", 
            "Process", "Expected WT", "Actual WT", "Expected TAT", "Actual TAT"));
        System.out.println("-".repeat(90));
        
        for (int i = 0; i < expectedResults.length(); i++) {
            JSONObject expected = expectedResults.getJSONObject(i);
            String name = expected.getString("name");
            int expectedWT = expected.getInt("waitingTime");
            int expectedTAT = expected.getInt("turnaroundTime");
            
            // Find matching process
            Process actual = processes.stream()
                .filter(p -> p.name.equals(name))
                .findFirst()
                .orElse(null);
            
            if (actual == null) {
                System.out.println(name + ": Process not found!");
                allMatch = false;
                continue;
            }
            
            boolean wtMatch = actual.waitingTime == expectedWT;
            boolean tatMatch = actual.turnaroundTime == expectedTAT;
            
            String wtStatus = wtMatch ? "" : " ✗";
            String tatStatus = tatMatch ? "" : " ✗";
            
            System.out.println(String.format("%-8s %-20s %-20s %-20s %-20s",
                name,
                expectedWT + wtStatus,
                actual.waitingTime,
                expectedTAT + tatStatus,
                actual.turnaroundTime
            ));
            
            if (!wtMatch || !tatMatch) {
                allMatch = false;
            }
        }
        
        if (allMatch) {
            System.out.println("✓ All process results match!");
        }
        
        return allMatch;
    }
}
