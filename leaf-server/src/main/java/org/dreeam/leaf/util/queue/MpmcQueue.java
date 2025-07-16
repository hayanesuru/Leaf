// Copyright (c) 2018 Aron Wieck Crown Communications GmbH, Karlsruhe, Germany
// Licensed under the terms of MIT license and the Apache License (Version 2.0).

package org.dreeam.leaf.util.queue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class MpmcQueue<T> {
    private static final long VERSION_MASK = 0xFFFF_0000_0000_0000L;
    private static final long INDEX_MASK = 0x0000_FFFF_FFFF_0000L;
    private static final long PENDING_MASK = 0x0000_0000_0000_00FFL;
    private static final long DONE_MASK = 0x0000_0000_0000_FF00L;
    private static final long FAST_PATH_MASK = INDEX_MASK | DONE_MASK;
    private static final long MAX_IN_PROGRESS = 16L;
    private static final int MAX_CAPACITY = 1 << 30;
    private static final int VERSION_SHIFT = 48;
    private static final int INDEX_SHIFT = 16;
    private static final int PARALLELISM = Runtime.getRuntime().availableProcessors();

    private static final VarHandle READ;
    private static final VarHandle WRITE;

    private final long mask;
    private final long capacity;
    private final T[] buffer;

    @SuppressWarnings("unused")
    private final Padded padded1 = new Padded();
    @SuppressWarnings("FieldMayBeFinal")
    private volatile long reads = 0L;
    @SuppressWarnings("unused")
    private final Padded padded2 = new Padded();
    @SuppressWarnings("FieldMayBeFinal")
    private volatile long writes = 0L;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            READ = l.findVarHandle(MpmcQueue.class, "reads", long.class);
            WRITE = l.findVarHandle(MpmcQueue.class, "writes", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public MpmcQueue(Class<T> clazz, int capacity) {
        if (capacity <= 0 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException();
        }

        this.mask = (1L << (Integer.SIZE - Integer.numberOfLeadingZeros(capacity - 1))) - 1L;
        //noinspection unchecked
        this.buffer = (clazz == Object.class)
            ? (T[]) new Object[(int) (mask + 1L)]
            : (T[]) java.lang.reflect.Array.newInstance(clazz, (int) (mask + 1L));
        this.capacity = mask + 1L;
    }

    private static long version(long state) {
        return (state & VERSION_MASK) >>> VERSION_SHIFT;
    }

    private static long index(long state) {
        return (state & INDEX_MASK) >>> INDEX_SHIFT;
    }

    private static long pending(long state) {
        return state & PENDING_MASK;
    }

    private static long done(long state) {
        return (state & DONE_MASK) >>> 8;
    }

    private static long createState(long version, long index, long done, long pending) {
        return version << VERSION_SHIFT | index << INDEX_SHIFT | done << 8 | pending;
    }

    private void spinWait(final int attempts) {
        if (attempts == 0) {
        } else if (PARALLELISM != 1 && (attempts & 31) != 31) {
            Thread.onSpinWait();
        } else {
            Thread.yield();
        }
    }

    public boolean send(final T item) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot enqueue null item");
        }

        long write = (long) WRITE.getAcquire(this);
        boolean success;
        long newWrite = 0L;
        long index = 0L;
        int attempts = 0;

        while (true) {
            spinWait(attempts++);
            final long writeVersion = version(write);
            final long writePending = pending(write);
            final long writeIndex = index(write);
            final long currentRead = (long) READ.getAcquire(this);
            final long readIndex = index(currentRead);
            final long currentItems = readIndex <= writeIndex
                ? writeIndex - readIndex
                : writeIndex + capacity - readIndex;
            if (currentItems + writePending >= mask) {
                success = false;
                break;
            }
            if (writePending == MAX_IN_PROGRESS) {
                write = (long) WRITE.getAcquire(this);
                continue;
            }
            index = (writeIndex + writePending) & mask;
            if (((writeIndex + writePending + 1L) & mask) == readIndex) {
                success = false;
                break;
            }
            newWrite = createState(
                writeVersion + 1L,
                writeIndex,
                done(write),
                writePending + 1L
            );
            if (WRITE.weakCompareAndSetAcquire(this, write, newWrite)) {
                success = true;
                break;
            }
            write = (long) WRITE.getVolatile(this);
        }
        if (!success) {
            return false;
        }
        buffer[(int) index] = item;
        /*
        if ((newWrite & FAST_PATH_MASK) == (index << INDEX_SHIFT) && index < mask) {
            WRITE.getAndAddRelease(this, (1L << INDEX_SHIFT) - 1L);
            return true;
        }
        */
        write = newWrite;
        while (true) {
            final long p = pending(write);
            final long d = done(write);
            final long i = index(write);
            final long v = version(write);
            final long n = d + 1L == p
                ? createState(v + 1L, (i + p) & mask, 0L, 0L)
                : i == index
                ? createState(v, (i + 1L) & mask, d, p - 1L)
                : createState(v, i, d + 1L, p);
            if (WRITE.weakCompareAndSetRelease(this, write, n)) {
                break;
            }
            write = (long) WRITE.getVolatile(this);
            spinWait(attempts++);
        }
        return true;
    }

    public T recv() {
        long read = (long) READ.getAcquire(this);
        boolean success;
        long index = 0L;
        long newRead = 0L;
        int attempts = 0;
        while (true) {
            spinWait(attempts++);
            final long readVersion = version(read);
            final long readPending = pending(read);
            final long writeIndex = index((long) WRITE.getAcquire(this));
            final long readIndex = index(read);
            final long currentItems = readIndex <= writeIndex
                ? writeIndex - readIndex
                : writeIndex + capacity - readIndex;
            if (currentItems == 0L) {
                success = false;
                break;
            }

            if (readPending == MAX_IN_PROGRESS) {
                read = (long) READ.getAcquire(this);
                continue;
            }

            index = (readIndex + readPending) & mask;
            if (index == writeIndex) {
                success = false;
                break;
            }
            newRead = createState(
                readVersion + 1L,
                readIndex,
                done(read),
                readPending + 1L
            );
            if (READ.weakCompareAndSetAcquire(this, read, newRead)) {
                success = true;
                break;
            }
            read = (long) READ.getVolatile(this);
        }
        if (!success) {
            return null;
        }
        final T result = buffer[(int) index];
        buffer[(int) index] = null;
        /*
        if ((newRead & FAST_PATH_MASK) == (index << INDEX_SHIFT) && index < mask) {
            READ.getAndAddRelease(this, (1L << INDEX_SHIFT) - 1L);
            return result;
        }
        */
        read = newRead;
        while (true) {
            final long p = pending(read);
            final long d = done(read);
            final long i = index(read);
            final long v = version(read);
            final long n = d + 1L == p
                ? createState(v + 1L, (i + p) & mask, 0L, 0L)
                : i == index
                ? createState(v, (i + 1L) & mask, d, p - 1L)
                : createState(v, i, d + 1L, p);
            if (READ.weakCompareAndSetRelease(this, read, n)) {
                break;
            }
            read = (long) READ.getVolatile(this);
            spinWait(attempts++);
        }
        return result;
    }

    public int length() {
        final long readCounters = (long) READ.getVolatile(this);
        final long writeCounters = (long) WRITE.getVolatile(this);
        final long readIndex = index(readCounters);
        final long writeIndex = index(writeCounters);
        return (int) ((readIndex <= writeIndex
            ? writeIndex - readIndex
            : writeIndex + capacity - readIndex) - pending(readCounters));
    }

    public boolean isEmpty() {
        final long readCounters = (long) READ.getVolatile(this);
        final long writeCounters = (long) WRITE.getVolatile(this);
        final long readIndex = index(readCounters);
        final long writeIndex = index(writeCounters);
        final long writePending = pending(writeCounters);
        final long currentItems = readIndex <= writeIndex
            ? writeIndex - readIndex
            : writeIndex + capacity - readIndex;
        return currentItems == 0L && writePending == 0L;
    }

    public int remaining() {
        final long readCounters = (long) READ.getVolatile(this);
        final long writeCounters = (long) WRITE.getVolatile(this);
        final long readIndex = index(readCounters);
        final long writeIndex = index(writeCounters);
        final long len = readIndex <= writeIndex ?
            writeIndex - readIndex :
            writeIndex + capacity - readIndex;
        return (int) (mask - len - pending(writeCounters));
    }

    @SuppressWarnings("unused")
    private final static class Padded {
        private byte i0, i1, i2, i3, i4, i5, i6, i7, i8, i9, i10, i11, i12, i13, i14, i15;
        private byte j0, j1, j2, j3, j4, j5, j6, j7, j8, j9, j10, j11, j12, j13, j14, j15;
        private byte k0, k1, k2, k3, k4, k5, k6, k7, k8, k9, k10, k11, k12, k13, k14, k15;
        private byte l0, l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12, l13, l14, l15;
        private byte m0, m1, m2, m3, m4, m5, m6, m7, m8, m9, m10, m11, m12, m13, m14, m15;
        private byte n0, n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12, n13, n14, n15;
        private byte o0, o1, o2, o3, o4, o5, o6, o7, o8, o9, o10, o11, o12, o13, o14, o15;
        private byte p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15;
    }
}
