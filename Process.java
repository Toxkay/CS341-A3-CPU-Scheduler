import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

    private List<Process> processes;
    private List<String> executionOrder;

    public AGScheduler(List<Process> processes) {
        this.processes = processes;
        this.executionOrder = new ArrayList<>();
    }

    public void run() {
        // Sort by Arrival Time first
        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));

        Queue<Process> readyQueue = new LinkedList<>();
        List<Process> completed = new ArrayList<>();
        
        // Helper to manage arrivals
        List<Process> pendingArrivals = new ArrayList<>(processes);
        
        int currentTime = 0;
        int timeInCurrentQuantum = 0;
        Process currentProcess = null;

        while (completed.size() < processes.size()) {

            // A. Handle Arrivals
            // Add any process that has arrived by 'currentTime' to the ready queue
            Iterator<Process> it = pendingArrivals.iterator();
            while (it.hasNext()) {
                Process p = it.next();
                if (p.arrivalTime <= currentTime) {
                    readyQueue.add(p);
                    it.remove();
                } else {
                    break; 
                }
            }

            // B. CPU Load
            if (currentProcess == null) {
                if (!readyQueue.isEmpty()) {
                    currentProcess = readyQueue.poll();
                    timeInCurrentQuantum = 0;
                } else {
                    currentTime++;
                    continue;
                }
            }

            // C. Record Execution Order
            if (executionOrder.isEmpty() || 
               !executionOrder.get(executionOrder.size() - 1).equals(currentProcess.name)) {
                executionOrder.add(currentProcess.name);
            }

            // D. Execute for 1 unit
            currentProcess.executeOneUnit(currentTime);
            currentTime++;
            timeInCurrentQuantum++;

            // E. Check Completion
            if (currentProcess.isFinished()) {
                completed.add(currentProcess);
                currentProcess.updateQuantum(0);
                currentProcess = null;
                timeInCurrentQuantum = 0;
                continue;
            }

            // F. AG Logic
            int Q = currentProcess.quantum;
            
            // Correct calculation of limits based on the AG rules
            int limit1 = (int) Math.ceil(Q * 0.25);
            int limit2 = limit1 + (int) Math.ceil(Q * 0.25); 

            boolean preempted = false;

            // --- Scenario (ii): 25% Check (Priority) ---
            if (timeInCurrentQuantum == limit1) {
                Process best = null;
                for (Process p : readyQueue) {
                    if (p.priority < currentProcess.priority) {
                        if (best == null || p.priority < best.priority) {
                            best = p;
                        }
                    }
                }

                if (best != null) {
                    int unused = Q - timeInCurrentQuantum;
                    int newQ = Q + (int) Math.ceil(unused / 2.0); // Mean Rule
                    currentProcess.updateQuantum(newQ);
                    
                    readyQueue.add(currentProcess);
                    readyQueue.remove(best);
                    currentProcess = best;
                    timeInCurrentQuantum = 0;
                    preempted = true;
                }
            }

            // --- Scenario (iii): 50% Check (SJF) ---
            if (!preempted && timeInCurrentQuantum >= limit2) {
                Process best = null;
                for (Process p : readyQueue) {
                    if (p.remainingTime < currentProcess.remainingTime) {
                        if (best == null || p.remainingTime < best.remainingTime) {
                            best = p;
                        }
                    }
                }

                if (best != null) {
                    int unused = Q - timeInCurrentQuantum;
                    int newQ = Q + unused; // Sum Rule
                    currentProcess.updateQuantum(newQ);

                    readyQueue.add(currentProcess);
                    readyQueue.remove(best);
                    currentProcess = best;
                    timeInCurrentQuantum = 0;
                    preempted = true;
                }
            }

            // --- Scenario (i): Quantum Exhausted ---
            if (!preempted && timeInCurrentQuantum == Q) {
                int newQ = Q + 2;
                currentProcess.updateQuantum(newQ);
                
                readyQueue.add(currentProcess);
                currentProcess = null;
                timeInCurrentQuantum = 0;
            }
        }
    }

    public List<String> getExecutionOrder() {
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
class SJFScheduler {

    private final List<Process> processes;
    private final int contextSwitch;
    private final List<String> executionOrder = new ArrayList<>();

    SJFScheduler(List<Process> processes, int contextSwitch) {
        this.processes = processes;
        this.contextSwitch = contextSwitch;
    }

    void run() {
        // Work on copies, then copy results back by process name
        Map<String, Process> originalsByName = new HashMap<>();
        for (Process p : processes) {
            originalsByName.put(p.name, p);
        }

        List<Process> copies = new ArrayList<>();
        for (Process p : processes) {
            copies.add(new Process(p.name, p.arrivalTime, p.burstTime, p.priority));
        }

        copies.sort(Comparator
                .comparingInt((Process p) -> p.arrivalTime)
                .thenComparing(p -> p.name));

        List<Process> ready = new ArrayList<>();
        int currentTime = 0;
        int completed = 0;
        int nextArrivalIndex = 0;
        Process current = null;
        String lastExecutedName = null;

        while (completed < copies.size()) {

            // If CPU is idle, fast-forward to next arrival 
            if (current == null && ready.isEmpty() && nextArrivalIndex < copies.size()
                    && currentTime < copies.get(nextArrivalIndex).arrivalTime) {
                currentTime = copies.get(nextArrivalIndex).arrivalTime;
            }

            // Add arrivals up to current time
            while (nextArrivalIndex < copies.size() && copies.get(nextArrivalIndex).arrivalTime <= currentTime) {
                ready.add(copies.get(nextArrivalIndex));
                nextArrivalIndex++;
            }

            // If no current, dispatch next 
            if (current == null) {
                if (ready.isEmpty()) {
                    currentTime++;
                    continue;
                }

                if (lastExecutedName != null && contextSwitch > 0) {
                    // Context switch time happens before selecting next after a completion
                    for (int i = 0; i < contextSwitch; i++) {
                        currentTime++;
                        while (nextArrivalIndex < copies.size() && copies.get(nextArrivalIndex).arrivalTime <= currentTime) {
                            ready.add(copies.get(nextArrivalIndex));
                            nextArrivalIndex++;
                        }
                    }
                }

                current = selectShortestRemaining(ready);
                if (current == null) {
                    currentTime++;
                    continue;
                }

                if (executionOrder.isEmpty() || !executionOrder.get(executionOrder.size() - 1).equals(current.name)) {
                    executionOrder.add(current.name);
                }
                lastExecutedName = current.name;
            }

            // Execute one time unit
            current.executeOneUnit(currentTime);
            currentTime++;

            // Add any arrivals after this time unit
            while (nextArrivalIndex < copies.size() && copies.get(nextArrivalIndex).arrivalTime <= currentTime) {
                ready.add(copies.get(nextArrivalIndex));
                nextArrivalIndex++;
            }

            // Completion
            if (current.isFinished()) {
                completed++;
                lastExecutedName = current.name;
                current = null;
                continue;
            }

            // Preemptive SJF (SRTF): after each unit
            Process best = peekShortestRemaining(ready);
            if (best != null && best.remainingTime < current.remainingTime) {
                // Switch to best
                ready.remove(best);
                ready.add(current);

                // Context switch time on preemption (arrivals during CS are added, but we do not reconsider selection)
                if (contextSwitch > 0) {
                    for (int i = 0; i < contextSwitch; i++) {
                        currentTime++;
                        while (nextArrivalIndex < copies.size() && copies.get(nextArrivalIndex).arrivalTime <= currentTime) {
                            ready.add(copies.get(nextArrivalIndex));
                            nextArrivalIndex++;
                        }
                    }
                }

                current = best;
                if (executionOrder.isEmpty() || !executionOrder.get(executionOrder.size() - 1).equals(current.name)) {
                    executionOrder.add(current.name);
                }
                lastExecutedName = current.name;
            }
        }

        // Copy results back to originals by name
        for (Process copy : copies) {
            Process original = originalsByName.get(copy.name);
            if (original != null) {
                original.completionTime = copy.completionTime;
                original.turnaroundTime = copy.turnaroundTime;
                original.waitingTime = copy.waitingTime;
            }
        }
    }

    List<String> getExecutionOrder() {
        return executionOrder;
    }

    private static Process selectShortestRemaining(List<Process> ready) {
        if (ready.isEmpty()) return null;
        int bestIndex = 0;
        for (int i = 1; i < ready.size(); i++) {
            Process a = ready.get(bestIndex);
            Process b = ready.get(i);
            if (b.remainingTime < a.remainingTime) {
                bestIndex = i;
            } else if (b.remainingTime == a.remainingTime) {
                if (b.arrivalTime < a.arrivalTime) {
                    bestIndex = i;
                } else if (b.arrivalTime == a.arrivalTime && b.name.compareTo(a.name) < 0) {
                    bestIndex = i;
                }
            }
        }
        return ready.remove(bestIndex);
    }

    private static Process peekShortestRemaining(List<Process> ready) {
        if (ready.isEmpty()) return null;
        Process best = ready.get(0);
        for (int i = 1; i < ready.size(); i++) {
            Process p = ready.get(i);
            if (p.remainingTime < best.remainingTime) {
                best = p;
            } else if (p.remainingTime == best.remainingTime) {
                if (p.arrivalTime < best.arrivalTime) {
                    best = p;
                } else if (p.arrivalTime == best.arrivalTime && p.name.compareTo(best.name) < 0) {
                    best = p;
                }
            }
        }
        return best;
    }
}