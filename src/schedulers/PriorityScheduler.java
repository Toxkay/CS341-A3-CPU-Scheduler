package src.schedulers;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import src.models.Process;

public class PriorityScheduler {

    private final List<Process> processes;
    private final List<String> executionOrder = new ArrayList<>();
    private final LinkedList<Process> readyQueue = new LinkedList<>();

    private final int contextSwitch;
    private final int agingInterval;

    private Process running = null;
    private int currentTime = 0;
    
    private final Map<String, Integer> enteredReadyQueueAt = new HashMap<>();
    private final Map<String, Integer> originalPriority = new HashMap<>();

    public PriorityScheduler(List<Process> processes, int contextSwitch, int agingInterval) {
        this.processes = processes;
        this.contextSwitch = contextSwitch;
        this.agingInterval = agingInterval;
    }

    public void run() {
        int completed = 0;

        while (completed < processes.size()) {
            // Add newly arrived processes
            addNewArrivals();
            applyAging();

            // Select process to run if CPU is idle
            if (running == null && !readyQueue.isEmpty()) {
                running = pollBestByPriority();
                enteredReadyQueueAt.remove(running.name);
                originalPriority.remove(running.name);

                if (executionOrder.isEmpty() ||
                    !executionOrder.get(executionOrder.size() - 1).equals(running.name)) {
                    executionOrder.add(running.name);
                }
            }

            // Execute one time unit
            if (running != null) {
                running.executeOneUnit(currentTime);
                currentTime++;

                // Time advanced: new arrivals can appear and aging progresses.
                addNewArrivals();
                applyAging();
                
                if (running.isFinished()) {
                    completed++;
                    running = null;
                    
                    if (!readyQueue.isEmpty()) {
                        for (int i = 0; i < contextSwitch; i++) {
                            currentTime++;
                            addNewArrivals();
                        }
                        applyAging();
                    }
                    continue;
                }

                // Preempt if a strictly higher-priority process is waiting.
                int bestWaitingPriority = peekBestPriority();
                if (bestWaitingPriority != Integer.MAX_VALUE && bestWaitingPriority < running.priority) {
                    enqueueRunningBack();
                    running = null;

                    for (int i = 0; i < contextSwitch; i++) {
                        currentTime++;
                        addNewArrivals();
                        applyAging();
                    }
                    continue;
                }

                // If there is another process with the same priority waiting,
                // rotate (1-unit time slice among equals).
                if (existsWaitingWithPriority(running.priority)) {
                    enqueueRunningBack();
                    running = null;

                    for (int i = 0; i < contextSwitch; i++) {
                        currentTime++;
                        addNewArrivals();
                        applyAging();
                    }
                    continue;
                }
            } else {
                currentTime++;
            }
        }
    }

    private void addNewArrivals() {
        for (Process p : processes) {
            if (p.arrivalTime <= currentTime && 
                !readyQueue.contains(p) && 
                !p.isFinished() && 
                p != running) {
                readyQueue.add(p);
                // Use the real arrival time as the start of waiting for aging.
                // If we only start counting from when we *noticed* the arrival
                // (e.g., after a context switch), aging becomes too weak and
                // can change expected preemption behavior.
                enteredReadyQueueAt.put(p.name, p.arrivalTime);
                originalPriority.put(p.name, p.priority);
            }
        }
    }

    private void applyAging() {
        if (agingInterval <= 0) return;
        
        for (Process p : readyQueue) {
            Integer enteredAt = enteredReadyQueueAt.get(p.name);
            Integer origPriority = originalPriority.get(p.name);
            
            if (enteredAt != null && origPriority != null) {
                int waitingTime = currentTime - enteredAt;
                int agingCount = waitingTime / agingInterval;
                int newPriority = Math.max(1, origPriority - agingCount);
                p.priority = newPriority;
            }
        }
    }

    private int peekBestPriority() {
        if (readyQueue.isEmpty()) return Integer.MAX_VALUE;
        int best = Integer.MAX_VALUE;
        for (Process p : readyQueue) {
            best = Math.min(best, p.priority);
        }
        return best;
    }

    private boolean existsWaitingWithPriority(int priority) {
        for (Process p : readyQueue) {
            if (p.priority == priority) return true;
        }
        return false;
    }

    private void enqueueRunningBack() {
        if (running == null) return;
        readyQueue.addLast(running);
        enteredReadyQueueAt.put(running.name, currentTime);
        originalPriority.put(running.name, running.priority);
    }

    private Process pollBestByPriority() {
        if (readyQueue.isEmpty()) return null;
        int bestPriority = peekBestPriority();
        for (int i = 0; i < readyQueue.size(); i++) {
            if (readyQueue.get(i).priority == bestPriority) {
                return readyQueue.remove(i);
            }
        }
        return readyQueue.pollFirst();
    }

    public List<String> getExecutionOrder() {
        return executionOrder;
    }

    public void printResults() {
        System.out.println("\n========== Priority Scheduling ==========");
        System.out.println("Context Switch Time: " + contextSwitch);
        System.out.println("Aging Interval: " + agingInterval);
        System.out.println("\nExecution Order:");
        System.out.println(String.join(" -> ", executionOrder));

        System.out.println("\nProcess Details:");
        System.out.println(String.format("%-10s %-12s %-12s %-15s %-15s %-10s", 
            "Process", "Arrival", "Burst", "Completion", "Turnaround", "Waiting"));
        System.out.println("-".repeat(85));

        double totalWT = 0, totalTAT = 0;

        for (Process p : processes) {
            System.out.println(String.format("%-10s %-12d %-12d %-15d %-15d %-10d",
                p.name, p.arrivalTime, p.burstTime, p.completionTime, 
                p.turnaroundTime, p.waitingTime));
            totalWT += p.waitingTime;
            totalTAT += p.turnaroundTime;
        }

        System.out.println("\n----- Averages -----");
        System.out.println(String.format("Average Waiting Time: %.2f", totalWT / processes.size()));
        System.out.println(String.format("Average Turnaround Time: %.2f", totalTAT / processes.size()));
        System.out.println("=============================================\n");
    }
}
