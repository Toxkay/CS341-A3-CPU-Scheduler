package src.schedulers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import src.models.Process;

public class PriorityScheduler {

    private final List<Process> processes;
    private final List<String> executionOrder = new ArrayList<>();

    private final int contextSwitch;
    private final int agingInterval;

    // ProcessState to track current priority and age timer separately
    private static class ProcessState {
        Process p;
        int currentPriority;
        int ageTimer = 0;

        ProcessState(Process p) {
            this.p = p;
            this.currentPriority = p.priority;
        }
    }

    public PriorityScheduler(List<Process> processes, int contextSwitch, int agingInterval) {
        this.processes = processes;
        this.contextSwitch = contextSwitch;
        this.agingInterval = agingInterval;
    }

    public void run() {
        int numProcesses = processes.size();

        // Create arrival pool sorted by arrival time
        List<ProcessState> arrivePool = new ArrayList<>();
        for (Process p : processes) {
            arrivePool.add(new ProcessState(p));
        }
        arrivePool.sort(Comparator.comparingInt(a -> a.p.arrivalTime));

        // Priority queue for ready processes
        PriorityQueue<ProcessState> readyQueue = new PriorityQueue<>((a, b) -> {
            if (a.currentPriority != b.currentPriority)
                return Integer.compare(a.currentPriority, b.currentPriority);
            if (a.p.arrivalTime != b.p.arrivalTime)
                return Integer.compare(a.p.arrivalTime, b.p.arrivalTime);
            return Integer.compare(a.p.priority, b.p.priority);  // Original priority as final tie-breaker
        });

        int time = 0;
        int completed = 0;
        ProcessState current = null;

        while (completed < numProcesses) {
            // Move arrived processes to ready queue
            while (!arrivePool.isEmpty() && arrivePool.get(0).p.arrivalTime <= time) {
                readyQueue.add(arrivePool.remove(0));
            }

            if (current != null) {
                ProcessState top = readyQueue.peek();
                boolean finished = current.p.remainingTime <= 0;
                boolean preempted = false;

                // Check for preemption
                if (top != null && !finished) {
                    if (current.currentPriority > top.currentPriority) {
                        preempted = true;
                    } else if (top.currentPriority == current.currentPriority) {
                        if (top.p.arrivalTime < current.p.arrivalTime) {
                            preempted = true;
                        } else if (top.p.arrivalTime == current.p.arrivalTime &&
                                top.p.priority < current.p.priority) {
                            preempted = true;
                        }
                    }
                }

                if (finished || preempted) {
                    if (finished) {
                        current.p.completionTime = time;
                        current.p.turnaroundTime = time - current.p.arrivalTime;
                        current.p.waitingTime = current.p.turnaroundTime - current.p.burstTime;
                        current.p.isCompleted = true;
                        completed++;
                    } else {
                        readyQueue.add(current);
                    }

                    if (completed == numProcesses) break;

                    ProcessState pending = readyQueue.poll();
                    pending.ageTimer = 0;

                    // Context switch with aging
                    for (int i = 0; i < contextSwitch; i++) {
                        time++;
                        applyAging(readyQueue);
                        while (!arrivePool.isEmpty() && arrivePool.get(0).p.arrivalTime <= time) {
                            readyQueue.add(arrivePool.remove(0));
                        }
                    }

                    // Check if better process arrived during context switch
                    ProcessState bestNow = readyQueue.peek();
                    if (bestNow != null) {
                        boolean betterPrio = bestNow.currentPriority < pending.currentPriority;
                        boolean betterArrival = (bestNow.currentPriority == pending.currentPriority &&
                                bestNow.p.arrivalTime < pending.p.arrivalTime);
                        boolean betterOrig = (bestNow.currentPriority == pending.currentPriority &&
                                bestNow.p.arrivalTime == pending.p.arrivalTime &&
                                bestNow.p.priority < pending.p.priority);

                        if (betterPrio || betterArrival || betterOrig) {
                            executionOrder.add(pending.p.name);
                            readyQueue.add(pending);
                            pending = readyQueue.poll();
                            pending.ageTimer = 0;

                            // Another context switch
                            for (int i = 0; i < contextSwitch; i++) {
                                time++;
                                applyAging(readyQueue);
                            }
                        }
                    }

                    current = pending;
                    executionOrder.add(current.p.name);
                }
            } else if (!readyQueue.isEmpty()) {
                current = readyQueue.poll();
                current.ageTimer = 0;
                executionOrder.add(current.p.name);
            } else {
                time++;
                continue;
            }

            if (current != null) {
                current.p.remainingTime--;
                time++;
                applyAging(readyQueue);
            }
        }
    }

    private void applyAging(PriorityQueue<ProcessState> queue) {
        if (agingInterval <= 0 || queue.isEmpty()) return;
        
        List<ProcessState> temp = new ArrayList<>();
        while (!queue.isEmpty()) {
            ProcessState ps = queue.poll();
            ps.ageTimer++;
            if (ps.ageTimer >= agingInterval) {
                ps.currentPriority = Math.max(1, ps.currentPriority - 1);
                ps.ageTimer = 0;
            }
            temp.add(ps);
        }
        queue.addAll(temp);
    }

    public List<String> getExecutionOrder() {
        return executionOrder;
    }

    public void printResults() {
        System.out.println("\n========== Priority Scheduling ==========");
        System.out.println("Execution Order: " + executionOrder);

        double totalWT = 0, totalTAT = 0;

        for (Process p : processes) {
            System.out.println(p.name +
                    " WT=" + p.waitingTime +
                    " TAT=" + p.turnaroundTime);
            totalWT += p.waitingTime;
            totalTAT += p.turnaroundTime;
        }

        System.out.println("Average WT = " + totalWT / processes.size());
        System.out.println("Average TAT = " + totalTAT / processes.size());
        System.out.println("=========================================\n");
    }
}