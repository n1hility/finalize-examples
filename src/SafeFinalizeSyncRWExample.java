import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A more complex safe finalizer example that uses read/write locks and an executor
 * to perform a cleanup task. This approach may be useful for a class which is shared
 * between threads and wishes to allow concurrent long-running tasks.
 *
 * <p>This approach capitalizes on a special rule that an object is still considered
 * reachable when both the finalizer and pending invocations hold a lock on the object.
 * This lock is temporarily held to ensure the read lock acquisition (normal method call)
 * precedes the write lock (finalize call). </p>
 *
 * <p>In order to prevent blocking the finalizer thread, the finalizer needs to push the
 * task to another thread which may block. </p>
 */
public class SafeFinalizeSyncRWExample {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private static ExecutorService REAPER = Executors.newFixedThreadPool(2);

    private void work()  {
        System.err.println("Work starts");
        ReentrantReadWriteLock lock = this.lock;
        try {
            synchronized (this) {
                lock.readLock().lock();
            }
            System.gc();
            Thread.sleep(10000L);
            System.err.println("Work complete");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.readLock().unlock();
        }
    }


    /**
     * The separate cleanup task.
     *
     * <p>It must not reference the outer example class in any way.</p>
     * <p>Instead, all values including any resources should be passed via construction.</p>
     */
    private static class CleanupTask implements Runnable {
        private final ReentrantReadWriteLock lock;

        CleanupTask(ReentrantReadWriteLock lock) {
            this.lock = lock;
        }

        public void run() {
            try {
                lock.writeLock().lock();
                System.err.println("Cleaning up!");
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    protected synchronized void finalize() {
        System.err.println("Finalize scheduling clean-up");

        // Delegate to another thread so we do not block the JVM finalizer thread
        REAPER.execute(new CleanupTask(lock));
    }

     /*
      * The remaining portion of the example is purely for simulation purposes, and is not
      * actually part of the avoidance technique.
      */

    static class SimulateStackCall implements Runnable {
        private SafeFinalizeSyncRWExample safe;

        SimulateStackCall(SafeFinalizeSyncRWExample safe) {
            this.safe = safe;
        }

        public void run() {
            // Ensure the object is not heap-reachable from this Runnable
            SafeFinalizeSyncRWExample safe = this.safe;
            this.safe = null;
            safe.work();

            // Force a GC in case the reference survived the work invocation
            System.gc();
        }
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 2; i++) {
            System.out.println("Run " + (i + 1));
            SafeFinalizeSyncRWExample safe = new SafeFinalizeSyncRWExample();
            Thread t1 = new Thread(new SimulateStackCall(safe)), t2 = new Thread(new SimulateStackCall(safe));
            t1.start(); t2.start();
            // Safe can now be collected once optimized.
            t1.join(); t2.join();

            t1 = null;
            t2 = null;
            safe = null;

            // Force GC in case the work methods were not fully optimized
            System.gc();
            Thread.sleep(2000);
        }
        REAPER.shutdown();
    }
}
