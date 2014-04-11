/**
 * A safe finalize example using synchronization to keep the object reachable.
 *
 * This capitalizes on a special rule that an object is still considered reachable
 * when pending invocations hold a lock on the object that will also be acquired under
 * the finalizer. The major drawback of this approach is that it requires that all work
 * methods can only be executed serially. On the other hand it is the simplest solution,
 * and lock biasing could reduce the cost of the locking.
 *
 * Note that while it appears to allow for stalling of the finalizer thread, the
 * above rule does not allow this to happen. The finalizer will not be executed
 * until after all locks on the object are released, and at that point there is nothing
 * to block on. Therefore its safe to have potentially long running or blocking
 * pre-finalize work method calls.
 */
public final class SafeFinalizeSyncExample {

    private synchronized void work() throws Exception {
        System.err.println("Work starts");
        System.gc();
        Thread.sleep(10000L);
        System.err.println("Work complete");
    }

    protected synchronized void finalize() {
        System.err.println("Finalize");
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 2; i++) {
            System.out.println("Run " + (i + 1));
            new SafeFinalizeSyncExample().work();
            System.gc();
            Thread.sleep(2000);
        }
    }
}
