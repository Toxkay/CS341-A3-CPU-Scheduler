package src.schedulers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import src.models.Process;

public class PriorityScheduler {

    private final List<Process> processes;
    private final List<String> executionOrder = new ArrayList<>();
    private final List<Process> readyQueue = new ArrayList<>();

    private final int contextSwitch;
    private final int agingInterval;

    private Process running = null;
    private int currentTime = 0;
    
    private final Map<String, Integer> waitingSince = new HashMap<>();
    private final Map<String, Integer> originalPriority = new HashMap<>();
    private final Map<String, Boolean> justArrived = new HashMap<>();

    public PriorityScheduler(List<Process> processes, int contextSwitch, int agingInterval) {
        this.processes = processes;
        this.contextSwitch = contextSwitch;
        this.  agingInterval = agingInterval;
    }

    public void run() {
        int completed = 0;

        while (completed < processes.size()) {
            addNewArrivals();
            applyAging();
            sortReadyQueueByPriority();

            if (running != null && !  readyQueue.isEmpty()) {
                Process highest = readyQueue.get(0);
                boolean shouldPreempt = (highest.priority < running.priority) ||
                                       (highest.priority == running.priority && highest.arrivalTime < running.arrivalTime) ||
                                       (highest.  priority == running.priority && highest.  arrivalTime == running.arrivalTime && highest.name.compareTo(running.name) < 0);
                
                if (shouldPreempt) {
                    readyQueue.add(running);
                    waitingSince.put(running.name, currentTime);
                    justArrived.put(running. name, false);
                    originalPriority.put(running.  name, running.priority);
                    running = null;
                    currentTime += contextSwitch;
                    continue;
                }
            }

            if (running == null && !readyQueue.isEmpty()) {
                running = readyQueue.  remove(0);
                waitingSince.remove(running.name);
                originalPriority.remove(running.  name);
                justArrived.remove(running. name);
                
                if (executionOrder.isEmpty() || 
                    !  executionOrder.get(executionOrder.size() - 1).equals(running.name)) {
                    executionOrder.add(running.name);
                }
            }

            if (running != null) {
                running.executeOneUnit(currentTime);
                currentTime++;
                
                if (running.isFinished()) {
                    completed++;
                    running = null;
                    
                    if (!  readyQueue.isEmpty()) {
                        currentTime += contextSwitch;
                    }
                }
            } else {
                currentTime++;
            }
        }
    }

    private void addNewArrivals() {
        for (Process p : processes) {
            if (p.arrivalTime <= currentTime && 
                !  readyQueue.contains(p) && 
                ! p.isFinished() && 
                p != running) {
                readyQueue.add(p);
                if (!  waitingSince.containsKey(p.name)) {
                    waitingSince.put(p.  name, currentTime);
                    originalPriority.put(p.name, p.priority);
                    justArrived.put(p.name, true);  // Mark as just arrived
                }
            }
        }
    }

    private void applyAging() {
        if (agingInterval <= 0) return;
        
        for (Process p :   readyQueue) {
            // Skip aging for processes that just arrived
            if (justArrived.getOrDefault(p.name, false)) {
                continue;
            }
            
            Integer waitStart = waitingSince.get(p.name);
            Integer origPri = originalPriority.  get(p.name);
            
            if (waitStart != null && origPri != null) {
                int waitTime = currentTime - waitStart;
                int ageCount = waitTime / agingInterval;
                int newPri = Math.max(1, origPri - ageCount);
                p.priority = newPri;
            }
        }
        
        // Mark arrived processes as no longer just arrived
        justArrived.replaceAll((k, v) -> false);
    }

    private void sortReadyQueueByPriority() {
        readyQueue.sort(new Comparator<Process>() {
            @Override
            public int compare(Process p1, Process p2) {
                if (p1.priority != p2.priority) {
                    return Integer.compare(p1.priority, p2.priority);
                } else if (p1.arrivalTime != p2.arrivalTime) {
                    return Integer.compare(p1.arrivalTime, p2.arrivalTime);
                } else {
                    return p1.name.compareTo(p2.name);
                }
            }
        });
    }

    public List<String> getExecutionOrder() {
        return executionOrder;
    }

    public void printResults() {
        System.out. println("\n========== Priority Scheduling ==========");
        System.out.println("Execution Order: " + executionOrder);

        double totalWT = 0, totalTAT = 0;

        for (Process p : processes) {
            System.out.println(p. name +
                    " WT=" + p. waitingTime +
                    " TAT=" + p.  turnaroundTime);
            totalWT += p.waitingTime;
            totalTAT += p.turnaroundTime;
        }

        System.out.println("Average WT = " + totalWT / processes.size());
        System.out.println("Average TAT = " + totalTAT / processes.size());
        System.out.println("=========================================\n");
    }
}