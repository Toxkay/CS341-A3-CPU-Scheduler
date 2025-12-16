import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Process {

    String name;
    int arrivalTime;
    final int burstTime;          // never changes
    int remainingTime;
    int priority;

    int quantum;                  // AG scheduling
    int waitingTime;
    int turnaroundTime;
    int completionTime;

    boolean isCompleted;

    List<Integer> quantumHistory;

    // Constructor
    Process(String name, int arrivalTime, int burstTime, int priority) {
        this.name = name;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.priority = priority;

        this.remainingTime = burstTime;
        this.isCompleted = false;

        this.waitingTime = 0;
        this.turnaroundTime = 0;
        this.completionTime = -1;

        this.quantumHistory = new ArrayList<>();
    }

    void executeOneUnit(int currentTime) {
        if (isCompleted) return;

        remainingTime--;

        if (remainingTime == 0) {
            completeProcess(currentTime + 1);
        }
    }

    private void completeProcess(int finishTime){
        isCompleted = true ;
        completionTime = finishTime;
        calculateTimes();
        quantum = 0;
    }

    private void calculateTimes(){
        turnaroundTime = completionTime - arrivalTime;
        waitingTime = turnaroundTime - burstTime;
    }

    boolean isFinished() {
        return isCompleted;
    }

    // AG helper: update quantum and record history
    void updateQuantum(int newQuantum) {
        quantum = newQuantum;
        quantumHistory.add(newQuantum);
    }

    void executeFor(int timeUnits, int startTime) {
        for (int i = 0; i < timeUnits && !isCompleted; i++) {
            executeOneUnit(startTime + i);
        }
    }

    @Override
    public String toString() {
        return "Process{" +
                "name='" + name + '\'' +
                ", arrivalTime=" + arrivalTime +
                ", burstTime=" + burstTime +
                ", remainingTime=" + remainingTime +
                ", priority=" + priority +
                ", quantum=" + quantum +
                ", waitingTime=" + waitingTime +
                ", turnaroundTime=" + turnaroundTime +
                ", completionTime=" + completionTime +
                ", isCompleted=" + isCompleted +
                ", quantumHistory=" + quantumHistory +
                '}';
    }

    public static void main(String[] args) {
        Process p = new Process("P1", 0, 5, 2);
        p.quantum = 4;

        int time = 0;
        while (!p.isFinished()) {
            p.executeOneUnit(time);
            time++;
        }

        System.out.println(p);
        System.out.println("WT = " + p.waitingTime);
        System.out.println("TAT = " + p.turnaroundTime);
    }

}

class AGScheduler {

    private final List<Process> processes;
    private final Queue<Process> readyQueue = new LinkedList<>();
    private final List<String> executionOrder = new ArrayList<>();

    AGScheduler(List<Process> processes) {
        this.processes = processes;
    }

    void run() {
        int currentTime = 0;
        int completed = 0;

        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));
        int index = 0;

        while (completed < processes.size()) {

            // Add arrived processes to ready queue
            while (index < processes.size() &&
                   processes.get(index).arrivalTime <= currentTime) {
                readyQueue.add(processes.get(index));
                index++;
            }

            // CPU idle
            if (readyQueue.isEmpty()) {
                currentTime++;
                continue;
            }

            Process current = readyQueue.poll();
            executionOrder.add(current.name);

            int q = current.quantum;
            int q25 = (int) Math.ceil(q * 0.25);
            int q50 = (int) Math.ceil(q * 0.50);

            // -------- First 25% (FCFS) --------
            int exec = Math.min(q25, current.remainingTime);
            current.executeFor(exec, currentTime);
            currentTime += exec;

            if (current.isFinished()) {
                completed++;
                continue;
            }

            // -------- Second 25% (Priority) --------
            boolean preempted = false;
            for (Process p : readyQueue) {
                if (!p.isFinished() && p.priority < current.priority) {
                    int remaining = q - exec;
                    current.updateQuantum(q + (int) Math.ceil(remaining / 2.0));
                    readyQueue.add(current);
                    preempted = true;
                    break;
                }
            }
            if (preempted) continue;

            exec = Math.min(q50 - q25, current.remainingTime);
            current.executeFor(exec, currentTime);
            currentTime += exec;

            if (current.isFinished()) {
                completed++;
                continue;
            }

            // -------- Remaining Quantum (Preemptive SJF) --------
            for (Process p : readyQueue) {
                if (!p.isFinished() && p.remainingTime < current.remainingTime) {
                    current.updateQuantum(q + (q - q50));
                    readyQueue.add(current);
                    preempted = true;
                    break;
                }
            }
            if (preempted) continue;

            exec = Math.min(q - q50, current.remainingTime);
            current.executeFor(exec, currentTime);
            currentTime += exec;

            if (current.isFinished()) {
                completed++;
            } else {
                current.updateQuantum(q + 2);
                readyQueue.add(current);
            }
        }
    }

    List<String> getExecutionOrder() {
        return executionOrder;
    }
}

class RoundRobinScheduler {

    private final List<Process> processes;
    private final int timeQuantum;
    private final int contextSwitch;
    private final List<String> executionOrder = new ArrayList<>();
    private final List<String> ganttChart = new ArrayList<>();

    RoundRobinScheduler(List<Process> processes, int timeQuantum, int contextSwitch) {
        this.processes = processes;
        this.timeQuantum = timeQuantum;
        this.contextSwitch = contextSwitch;
    }

    void run() {
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

    void printResults() {
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

    List<String> getExecutionOrder() {
        return executionOrder;
    }
    
    List<String> getGanttChart() {
        return ganttChart;
    }
}