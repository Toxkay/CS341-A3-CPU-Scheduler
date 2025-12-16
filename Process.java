import java.util.ArrayList;
import java.util.List;

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
