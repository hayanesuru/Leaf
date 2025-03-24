package org.dreeam.leaf.util.math;

import net.minecraft.util.Mth;

/**
 * A replacement for the sine angle lookup table used in {@link Mth}, both reducing the size of LUT and improving
 * the access patterns for common paired sin/cos operations.
 * <p>
 * sin(-x) = -sin(x)
 * ... to eliminate negative angles from the LUT.
 * <p>
 * sin(x) = sin(pi/2 - x)
 * ... to eliminate supplementary angles from the LUT.
 * <p>
 * Using these identities allows us to reduce the LUT from 64K entries (256 KB) to just 16K entries (64 KB), enabling
 * it to better fit into the CPU's caches at the expense of some cycles on the fast path. The implementation has been
 * tightly optimized to avoid branching where possible and to use very quick integer operations.
 * <p>
 * Generally speaking, reducing the size of a lookup table is always a good optimization, but since we need to spend
 * extra CPU cycles trying to maintain parity with vanilla, there is the potential risk that this implementation ends
 * up being slower than vanilla when the lookup table is able to be kept in cache memory.
 * <p>
 * Unlike other "fast math" implementations, the values returned by this class are *bit-for-bit identical* with those
 * from {@link Mth}. Validation is performed during runtime to ensure that the table is correct.
 *
 * @author coderbot16   Author of the original (and very clever) implementation in Rust:
 * <a href="https://gitlab.com/coderbot16/i73/-/tree/master/i73-trig/src"></a>
 * @author jellysquid3  Additional optimizations, port to Java
 */
public class CompactSineLUT {

    private static final int[] SINE_TABLE_INT = new int[16384 + 1];
    private static final float SINE_TABLE_MIDPOINT;

    static {
        final float[] SINE_TABLE = Mth.SIN;
        // Copy the sine table, covering to raw int bits
        for (int i = 0; i < SINE_TABLE_INT.length; i++) {
            SINE_TABLE_INT[i] = Float.floatToRawIntBits(SINE_TABLE[i]);
        }

        SINE_TABLE_MIDPOINT = SINE_TABLE[SINE_TABLE.length / 2];

        // Test that the lookup table is correct during runtime
        for (int i = 0; i < SINE_TABLE.length; i++) {
            float expected = SINE_TABLE[i];
            float value = lookup(i);

            if (expected != value) {
                throw new IllegalArgumentException(String.format("LUT error at index %d (expected: %s, found: %s)", i, expected, value));
            }
        }
    }

    // [VanillaCopy] MathHelper#sin(float)
    public static float sin(float f) {
        return lookup((int) (f * 10430.378f) & 0xFFFF);
    }

    // [VanillaCopy] MathHelper#cos(float)
    public static float cos(float f) {
        return lookup((int) (f * 10430.378f + 16384.0f) & 0xFFFF);
    }

    private static float lookup(int index) {
        int neg = (index & 0x8000) << 16;

        //im sorry
        int specialCaseMask = ~((index ^ 32768) | -((index ^ 32768))) >> 31;

        int mask = (index << 17) >> 31;
        int pos = (0x8001 & mask) + (index ^ mask);
        pos &= 0x7fff;

        int normalResult = SINE_TABLE_INT[pos] ^ neg;
        int specialResult = Float.floatToRawIntBits(SINE_TABLE_MIDPOINT);

        // Select between normalResult and specialResult using the mask
        return Float.intBitsToFloat((normalResult & ~specialCaseMask) | (specialResult & specialCaseMask));
    }
}
