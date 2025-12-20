package src.schedulers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import src.models.Process;

public class SJFScheduler {

    private final List<Process> processes;
    private final int contextSwitch;
    private final List<String> executionOrder = new ArrayList<>();

    public SJFScheduler(List<Process> processes, int contextSwitch) {
        this.processes = processes;
        this.contextSwitch = contextSwitch;
    }

    public void run() {
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

    public List<String> getExecutionOrder() {
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