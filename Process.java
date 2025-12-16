import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Comparator;

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