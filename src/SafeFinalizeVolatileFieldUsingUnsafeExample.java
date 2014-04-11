import java.lang.reflect.Field;

import sun.misc.Unsafe;

/**
 * A non-portable safe finalize example that relies on piggybacking of an ordered write.
 *
 * <p>The use of the volatile field ensures a reference to "this" is held until the
 * end of the work method. Since the field is volatile, the instruction can not be
 * reordered. In addition, the field is copied to a public static field during
 * finalization to prevent a theoretical optimizer from eliminating the field.</p>
 *
 * <p>In order to minimize the cost of this write, <code>sun.misc.Unsafe</code>
 * is used to perform a lazy write.</p>
 *
 * <p>The advantage of this approach is that does not involve any form of locking, and only uses a cheap
 * barrier, which is free on many platforms, including x86.</p>
 */
public final class SafeFinalizeVolatileFieldUsingUnsafeExample {
    static int STATIC_COUNTER = 0;
    private static Unsafe unsafe;
    private static long counterOffset;

    // Initialize with current static field value for an additional optimizer safe-guard
    volatile int counter = STATIC_COUNTER;


    static {
        Field field = null;
        try {
            field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
            field = SafeFinalizeVolatileFieldUsingUnsafeExample.class.getDeclaredField("counter");
            counterOffset = unsafe.objectFieldOffset(field);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public void work() throws Exception {
        try {
            System.err.println("Work starting");
            System.gc();

            // Do some work here, potentially blocking (simulate with sleep)
            Thread.sleep(10000L);
            System.err.println("Work completed");
        } finally {
            // Ordered write should prevent the instruction from reordering,
            // but only costs a StoreStore barrier (no-op on x86)
            // vs a more expensive StoreLoad.
            unsafe.putOrderedInt(this, counterOffset, counter + 1);
        }
    }

    protected void finalize() throws Throwable {
        super.finalize();
        // Copy value to a public static for an additional optimizer safe-guard
        // Only needs to be done IF the finalizer is freeing the resource, if the user properly freed
        // the resource it can be skipped.
        STATIC_COUNTER = counter;
        System.err.println("Finalize");
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 2; i++) {
            System.out.println("Run " + (i + 1));
            new SafeFinalizeVolatileFieldUsingUnsafeExample().work();
            System.gc();
            Thread.sleep(2000);
        }
    }

}
