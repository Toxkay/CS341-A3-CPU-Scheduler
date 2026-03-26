package src.models;

import java.util.ArrayList;
import java.util.List;

public class Process {

    public String name;
    public int arrivalTime;
    public final int burstTime;          // never changes
    public int remainingTime;
    public int priority;

    public int quantum;                  // AG scheduling
    public int waitingTime;
    public int turnaroundTime;
    public int completionTime;

    public boolean isCompleted;

    public List<Integer> quantumHistory;

    // Constructor
    public Process(String name, int arrivalTime, int burstTime, int priority) {
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

    public void executeOneUnit(int currentTime) {
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

    public boolean isFinished() {
        return isCompleted;
    }

    // AG helper: update quantum and record history
    public void updateQuantum(int newQuantum) {
        quantum = newQuantum;
        quantumHistory.add(newQuantum);
    }

    public void executeFor(int timeUnits, int startTime) {
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
}
