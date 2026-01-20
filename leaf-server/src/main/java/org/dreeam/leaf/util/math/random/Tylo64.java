package org.dreeam.leaf.util.math.random;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class Tylo64 implements BitRandomSource {
    private static final int LROT = 24;
    private static final int RSHIFT = 11;
    private static final int LSHIFT = 3;

    private long a;
    private long b;
    private long w;
    private final long k;
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public Tylo64(long seed, long k) {
        this.a = this.b = this.w = seed;
        this.k = k | 1;
        for (int i = 0; i < 12; i++) {
            this.next();
        }
    }

    public Tylo64(long a, long b, long w, long k) {
        this.a = a;
        this.b = b;
        this.w = w;
        this.k = k | 1;
    }

    private long next() {
        final long b = this.b;
        final long out = this.a ^ (this.w += this.k);
        this.a = (b + (b << LSHIFT)) ^ (b >>> RSHIFT);
        this.b = ((b << LROT) | (b >>> (Long.SIZE - LROT))) + out;
        return out;
    }

    @Override
    public int next(int size) {
        return (int) (this.next() >>> (Long.SIZE - size));
    }

    @Override
    public RandomSource fork() {
        return new Tylo64(this.next(), this.k);
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new Tylo64PositionalFactory(this.next(), this.k);
    }

    @Override
    public void setSeed(long seed) {
        this.a = this.b = this.w = seed;
        for (int i = 0; i < 12; i++) {
            this.next();
        }
        this.gaussianSource.reset();
    }

    @Override
    public int nextInt() {
        return (int) (this.next() >>> 32);
    }

    @Override
    public long nextLong() {
        return this.next();
    }

    public long nextLong(final long bound) {
        final long m = bound - 1L;
        long r = this.next();
        if ((bound & m) == 0L) {
            r &= m;
        } else {
            //noinspection StatementWithEmptyBody
            for (long u = r >>> 1;
                 u + m - (r = u % bound) < 0L;
                 u = this.next() >>> 1)
                ;
        }
        return r;
    }

    @Override
    public int nextInt(final int bound) {
        final int m = bound - 1;
        int r = (int) (this.next() >>> 33);
        if ((bound & m) == 0) {
            r &= m;
        } else {
            //noinspection StatementWithEmptyBody
            for (int u = r >>> 1;
                 u + m - (r = u % bound) < 0;
                 u = (int) (this.next() >>> 33))
                ;
        }
        return r;
    }


    @Override
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }

    private record Tylo64PositionalFactory(long seed, long k) implements PositionalRandomFactory {
        @Override
        public RandomSource fromHashOf(final String string) {
            return new Tylo64((long) string.hashCode() ^ this.seed, this.k);
        }

        @Override
        public RandomSource fromSeed(final long seed) {
            return new Tylo64(seed, this.k);
        }

        @Override
        public RandomSource at(final int x, final int y, final int z) {
            return new Tylo64(Mth.getSeed(x, y, z) ^ this.seed, this.k);
        }

        @Override
        public void parityConfigString(final StringBuilder stringBuilder) {
            stringBuilder.append("SimpleRandomPositionalFactory{").append(this.seed).append(',').append(this.k).append('}');
        }
    }
}
