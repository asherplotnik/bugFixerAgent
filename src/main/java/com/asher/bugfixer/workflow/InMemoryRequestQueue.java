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
    }

    public void startWorker(BugFixWorkflow workflow) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        worker = Thread.ofVirtual().name("bug-fix-worker").start(() -> {
            while (running.get()) {
                try {
                    workflow.execute(requests.take());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception exception) {
                    System.err.println("Bug-fix workflow failed: " + exception.getMessage());
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
