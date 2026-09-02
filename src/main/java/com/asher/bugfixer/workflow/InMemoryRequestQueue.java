package com.asher.bugfixer.workflow;

import com.asher.bugfixer.domain.BugFixRequest;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local-only queue. Production deployment must replace this with Pub/Sub or another durable queue. */
public final class InMemoryRequestQueue {
    private final BlockingQueue<BugFixRequest> requests = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread worker;

    public void enqueue(BugFixRequest request) {
        requests.add(request);
        System.out.println("Bug-fix webhook queued: issue=" + request.issueKey()
                + " delivery=" + request.deliveryId() + " queueDepth=" + requests.size());
    }

    public void startWorker(BugFixWorkflow workflow) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        System.out.println("Bug-fix worker started.");
        worker = Thread.ofVirtual().name("bug-fix-worker").start(() -> {
            while (running.get()) {
                try {
                    BugFixRequest request = requests.take();
                    System.out.println("Bug-fix workflow started: issue=" + request.issueKey()
                            + " delivery=" + request.deliveryId());
                    var result = workflow.execute(request);
                    System.out.println("Bug-fix workflow " + request.issueKey() + " finished with "
                            + result.status() + ": " + result.message() + " " + String.join(" | ", result.notes()));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception exception) {
                    System.err.println("Bug-fix workflow failed: " + exception.getClass().getSimpleName()
                            + ": " + exception.getMessage());
                }
            }
        });
    }

    public void stop() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
        }
    }
}
