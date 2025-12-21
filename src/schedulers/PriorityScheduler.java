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

    private static class ProcessState {
        Process p;
        int currentPriority;
        int timeInReadyQueue = 0;
        int inputOrder;

        ProcessState(Process p, int inputOrder) {
            this.p = p;
            this.currentPriority = p.priority;
            this.inputOrder = inputOrder;
        }
    }

    public PriorityScheduler(List<Process> processes, int contextSwitch, int agingInterval) {
        this.processes = processes;
        this.contextSwitch = contextSwitch;
        this.agingInterval = agingInterval;
    }

    public void run() {
        int numProcesses = processes.size();

        List<ProcessState> arrivePool = new ArrayList<>();
        for (int i = 0; i < processes.size(); i++) {
            arrivePool.add(new ProcessState(processes.get(i), i));
        }
        arrivePool.sort(Comparator.comparingInt(a -> a.p.arrivalTime));

        PriorityQueue<ProcessState> readyQueue = new PriorityQueue<>((a, b) -> {
            if (a.currentPriority != b.currentPriority) {
                return Integer.compare(a.currentPriority, b.currentPriority);
            }
            if (a.p.arrivalTime != b.p.arrivalTime) {
                return Integer.compare(a.p.arrivalTime, b.p.arrivalTime);
            }
            return Integer.compare(a.inputOrder, b.inputOrder);
        });

        int time = 0;
        int completed = 0;
        ProcessState current = null;

        while (completed < numProcesses) {
            while (!arrivePool.isEmpty() && arrivePool.get(0).p.arrivalTime <= time) {
                ProcessState ps = arrivePool.remove(0);
                ps.timeInReadyQueue = 0;
                readyQueue.add(ps);
            }

            if (current != null) {
                ProcessState top = readyQueue.peek();
                boolean finished = current.p.remainingTime <= 0;
                boolean preempted = false;

                if (top != null && !finished) {
                    if (top.currentPriority < current.currentPriority) {
                        preempted = true;
                    } else if (top.currentPriority == current.currentPriority) {
                        if (top.p.arrivalTime < current.p.arrivalTime) {
                            preempted = true;
                        } else if (top.p.arrivalTime == current.p.arrivalTime &&
                                top.inputOrder < current.inputOrder) {
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
                        current.timeInReadyQueue = 0;
                        readyQueue.add(current);
                    }

                    if (completed == numProcesses)
                        break;

                    ProcessState pending = readyQueue.poll();
                    if (pending != null) {
                        pending.timeInReadyQueue = 0;

                        for (int i = 0; i < contextSwitch; i++) {
                            time++;

                            pending.timeInReadyQueue++;
                            if (agingInterval > 0 && pending.timeInReadyQueue >= agingInterval) {
                                pending.currentPriority = Math.max(1, pending.currentPriority - 1);
                                pending.p.priority = pending.currentPriority;
                                pending.timeInReadyQueue = 0;
                            }

                            applyAging(readyQueue);

                            while (!arrivePool.isEmpty() && arrivePool.get(0).p.arrivalTime <= time) {
                                ProcessState ps = arrivePool.remove(0);
                                ps.timeInReadyQueue = 0;
                                readyQueue.add(ps);
                            }
                        }

                        ProcessState bestNow = readyQueue.peek();
                        if (bestNow != null) {
                            boolean better =
                                    bestNow.currentPriority < pending.currentPriority ||
                                            (bestNow.currentPriority == pending.currentPriority &&
                                                    (bestNow.p.arrivalTime < pending.p.arrivalTime ||
                                                            (bestNow.p.arrivalTime == pending.p.arrivalTime &&
                                                                    bestNow.inputOrder < pending.inputOrder)));

                            if (better) {
                                executionOrder.add(pending.p.name);
                                readyQueue.add(pending);
                                pending = readyQueue.poll();

                                for (int i = 0; i < contextSwitch; i++) {
                                    time++;

                                    pending.timeInReadyQueue++;
                                    if (agingInterval > 0 && pending.timeInReadyQueue >= agingInterval) {
                                        pending.currentPriority = Math.max(1, pending.currentPriority - 1);
                                        pending.p.priority = pending.currentPriority;
                                        pending.timeInReadyQueue = 0;
                                    }

                                    applyAging(readyQueue);

                                    while (!arrivePool.isEmpty() && arrivePool.get(0).p.arrivalTime <= time) {
                                        ProcessState ps = arrivePool.remove(0);
                                        ps.timeInReadyQueue = 0;
                                        readyQueue.add(ps);
                                    }
                                }
                            }
                        }

                        current = pending;
                        current.timeInReadyQueue = 0;
                        executionOrder.add(current.p.name);
                    }
                }
            } else if (!readyQueue.isEmpty()) {
                current = readyQueue.poll();
                current.timeInReadyQueue = 0;
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
        if (agingInterval <= 0 || queue.isEmpty())
            return;

        List<ProcessState> temp = new ArrayList<>();
        while (!queue.isEmpty()) {
            ProcessState ps = queue.poll();
            ps.timeInReadyQueue++;

            if (ps.timeInReadyQueue >= agingInterval) {
                ps.currentPriority = Math.max(1, ps.currentPriority - 1);
                ps.p.priority = ps.currentPriority;
                ps.timeInReadyQueue = 0;
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