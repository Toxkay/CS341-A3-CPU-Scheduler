package src.schedulers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import src.models.Process;

public class AGScheduler { 

    private List<Process> processes;
    private List<String> executionOrder;

    public AGScheduler(List<Process> processes) {
        this.processes = processes;
        this.executionOrder = new ArrayList<>();
    }

    public void run() {
        // Sort by Arrival Time
        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));

        Queue<Process> readyQueue = new LinkedList<>();
        List<Process> completed = new ArrayList<>();
        List<Process> pendingArrivals = new ArrayList<>(processes);
        
        int currentTime = 0;
        int timeInCurrentQuantum = 0;
        Process currentProcess = null;

        // 1. Initial Load (t=0)
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

        while (completed.size() < processes.size()) {

            // 2. Load CPU
            if (currentProcess == null) {
                if (!readyQueue.isEmpty()) {
                    currentProcess = readyQueue.poll();
                    timeInCurrentQuantum = 0;
                } else {
                    currentTime++;
                    // Check arrivals during idle time
                    it = pendingArrivals.iterator();
                    while (it.hasNext()) {
                        Process p = it.next();
                        if (p.arrivalTime <= currentTime) {
                            readyQueue.add(p);
                            it.remove();
                        } else {
                            break;
                        }
                    }
                    continue;
                }
            }

            // 3. Record Order
            if (executionOrder.isEmpty() || 
               !executionOrder.get(executionOrder.size() - 1).equals(currentProcess.name)) {
                executionOrder.add(currentProcess.name);
            }

            // 4. Execute One Unit
            // Time moves from t to t+1
            currentProcess.executeOneUnit(currentTime);
            currentTime++;
            timeInCurrentQuantum++;

            // 5. CRITICAL: Handle Arrivals *Before* Re-queueing Current
            // This ensures new arrivals get ahead of the current process if it gets preempted/exhausted
            it = pendingArrivals.iterator();
            while (it.hasNext()) {
                Process p = it.next();
                if (p.arrivalTime <= currentTime) {
                    readyQueue.add(p);
                    it.remove();
                } else {
                    break;
                }
            }

            // 6. Check Completion
            if (currentProcess.isFinished()) {
                completed.add(currentProcess);
                currentProcess.updateQuantum(0);
                currentProcess = null;
                timeInCurrentQuantum = 0;
                continue;
            }

            // 7. AG Quantum Logic
            int Q = currentProcess.quantum;
            int limit1 = (int) Math.ceil(Q * 0.25);
            int limit2 = limit1 + (int) Math.ceil(Q * 0.25);

            boolean preempted = false;

            // --- Scenario (ii): 25% Check (Priority) ---
            if (timeInCurrentQuantum == limit1) {
                Process best = null;
                // Find strictly better priority
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
                    
                    readyQueue.add(currentProcess); // Add current to back
                    readyQueue.remove(best);        // Remove best from middle
                    currentProcess = best;          // Switch
                    timeInCurrentQuantum = 0;
                    preempted = true;
                }
            }

            // --- Scenario (iii): 50% Check (SJF) ---
            if (!preempted && timeInCurrentQuantum >= limit2) {
                Process best = null;
                // Find strictly shorter remaining time
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
                int newQ = Q + 2; // Increase Rule
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