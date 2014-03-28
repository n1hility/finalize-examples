/**
 * This example demonstrates how finalizers do not follow the programmer's expectation.
 * It fails on IBM with no options and on OpenJDK Hotspot when JIT has aggressively
 * compiled the work method.
 *
 * <p>Using the <code>-Xcomp -XX:+TieredCompilation</code>
 * options should be enough to trigger this on OpenJDK reliably. Alternatively you can disable
 * tiered compilation and set -XX:CompileThreshold=1, at which point the second run will
 * fail.</p>
 *
 * <p>Upon failure, the example will print <code>Finalize</code> before work has completed.
 * The reason for the failure is that the JLS has very weak pre-finalization to finalization
 * ordering requirements for JVMs. A <code>finalize</code> call can happen in the middle
 * of normal method execution if the object does not have a reference on the heap. This commonly
 * occurs when the object is only held by a local variable on the stack, or when the root of the
 * reference tree is itself a local variable on the stack.
 *
 * <p>Unfortunately the only way to obtain expected behavior is to carefully utilize subtle rules
 * of the Java execution environment.
 */

public final class BrokenFinalizeExample {
    public void work() throws Exception {
        System.err.println("Work started");
        // Force a GC which when optimized, will finalize this object before work() completes
        System.gc();
        Thread.sleep(10000L);
        System.err.println("Work completed");
    }

    protected void finalize() {
        System.err.println("Finalize");
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 2; i++) {
            System.out.println("Run " + (i + 1));
            new BrokenFinalizeExample().work();
            // Force a GC in case work() is not optimized, we still see the "Finalize" message.
            System.gc();
        }
    }
}
