package src.schedulers;

import src.models.Process;
import java.util.*;

public class AGScheduler { 

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
