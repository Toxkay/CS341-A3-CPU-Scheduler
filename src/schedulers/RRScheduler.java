package src.schedulers;

import src.models.Process;
import java.util.*;

public class RRScheduler {

    private final List<Process> processes;
    private final int timeQuantum;
    private final int contextSwitch;
    private final List<String> executionOrder = new ArrayList<>();
    private final List<String> ganttChart = new ArrayList<>();

    public RRScheduler(List<Process> processes, int timeQuantum, int contextSwitch) {
        this.processes = processes;
        this.timeQuantum = timeQuantum;
        this.contextSwitch = contextSwitch;
    }

    public void run() {
        Queue<Process> readyQueue = new LinkedList<>();
        List<Process> processesCopy = new ArrayList<>();
        
        // Create copies of processes to preserve original data
        for (Process p : processes) {
            Process copy = new Process(p.name, p.arrivalTime, p.burstTime, p.priority);
            processesCopy.add(copy);
        }
        
        // Sort by arrival time
        processesCopy.sort(Comparator.comparingInt(p -> p.arrivalTime));
        
        int currentTime = 0;
        int completed = 0;
        int index = 0;
        Process lastExecuted = null;

        while (completed < processesCopy.size()) {
            
            // Add all processes that have arrived by current time to ready queue
            while (index < processesCopy.size() && 
                   processesCopy.get(index).arrivalTime <= currentTime) {
                readyQueue.add(processesCopy.get(index));
                index++;
            }

            // If no process is ready, CPU is idle - advance time to next arrival
            if (readyQueue.isEmpty()) {
                if (index < processesCopy.size()) {
                    currentTime = processesCopy.get(index).arrivalTime;
                }
                continue;
            }

            // Get the next process from ready queue
            Process current = readyQueue.poll();
            
            // Add context switch time if there was a previous process
            if (lastExecuted != null && !lastExecuted.name.equals(current.name)) {
                currentTime += contextSwitch;
                ganttChart.add("CS"); // Context Switch
            }

            // Execute process for quantum time or remaining time (whichever is less)
            int executeTime = Math.min(timeQuantum, current.remainingTime);
            executionOrder.add(current.name);
            ganttChart.add(current.name + "(" + currentTime + "-" + (currentTime + executeTime) + ")");
            
            current.executeFor(executeTime, currentTime);
            currentTime += executeTime;
            lastExecuted = current;

            // Add newly arrived processes during execution
            while (index < processesCopy.size() && 
                   processesCopy.get(index).arrivalTime <= currentTime) {
                readyQueue.add(processesCopy.get(index));
                index++;
            }

            // If process is not finished, add it back to ready queue
            if (!current.isFinished()) {
                readyQueue.add(current);
            } else {
                completed++;
            }
        }
        
        // Copy results back to original processes
        for (int i = 0; i < processes.size(); i++) {
            processes.get(i).completionTime = processesCopy.get(i).completionTime;
            processes.get(i).turnaroundTime = processesCopy.get(i).turnaroundTime;
            processes.get(i).waitingTime = processesCopy.get(i).waitingTime;
        }
    }

    public void printResults() {
        System.out.println("\n========== Round Robin Scheduling ==========");
        System.out.println("Time Quantum: " + timeQuantum);
        System.out.println("Context Switch Time: " + contextSwitch);
        System.out.println("\nExecution Order:");
        System.out.println(String.join(" -> ", executionOrder));
        
        System.out.println("\nGantt Chart:");
        for (String entry : ganttChart) {
            System.out.print(entry + " ");
        }
        System.out.println();
        
        System.out.println("\nProcess Details:");
        System.out.println(String.format("%-10s %-12s %-12s %-15s %-15s %-18s", 
            "Process", "Arrival", "Burst", "Completion", "Turnaround", "Waiting"));
        System.out.println("-------------------------------------------------------------------------------------");
        
        double totalWaitingTime = 0;
        double totalTurnaroundTime = 0;
        
        for (Process p : processes) {
            System.out.println(String.format("%-10s %-12d %-12d %-15d %-15d %-18d",
                p.name, p.arrivalTime, p.burstTime, p.completionTime, 
                p.turnaroundTime, p.waitingTime));
            totalWaitingTime += p.waitingTime;
            totalTurnaroundTime += p.turnaroundTime;
        }
        
        System.out.println("\n----- Averages -----");
        System.out.println("Average Waiting Time: " + 
            String.format("%.2f", totalWaitingTime / processes.size()));
        System.out.println("Average Turnaround Time: " + 
            String.format("%.2f", totalTurnaroundTime / processes.size()));
        System.out.println("=============================================\n");
    }

    public List<String> getExecutionOrder() {
        return executionOrder;
    }
    
    public List<String> getGanttChart() {
        return ganttChart;
    }
}
