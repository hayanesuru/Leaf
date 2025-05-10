package org.dreeam.leaf.util;

import java.util.List;
import java.util.Arrays; // For Arrays.copyOf
import net.minecraft.world.entity.LivingEntity;

public class fastBitRadixSort {

    private static final int SMALL_ARRAY_THRESHOLD = 2;
    private LivingEntity[] entityBuffer = new LivingEntity[0];
    private long[] bitsBuffer = new long[0];

    /**
     * Sorts a list of LivingEntity objects based on their squared distance
     * to a reference entity using a fast radix sort algorithm.
     **/
    public <T_REF extends LivingEntity> LivingEntity[] sort(
        List<LivingEntity> entities,
        T_REF referenceEntity
    ) {
        int size = entities.size();
        if (size <= 1) {
            return entities.toArray(new LivingEntity[0]);
        }

        if (this.entityBuffer.length < size) {
            this.entityBuffer = new LivingEntity[size];
            this.bitsBuffer = new long[size];
        }
        for (int i = 0; i < size; i++) {
            LivingEntity e = entities.get(i);
            this.entityBuffer[i] = e;
            this.bitsBuffer[i] = Double.doubleToRawLongBits(
                referenceEntity.distanceToSqr(e)
            );
        }

        // start from bit 62 (most significant for positive doubles, ignoring sign bit)
        fastRadixSort(this.entityBuffer, this.bitsBuffer, 0, size - 1, 62);
        return Arrays.copyOf(this.entityBuffer, size);
    }

    private void fastRadixSort(
        LivingEntity[] ents,
        long[] bits,
        int low,
        int high,
        int bit
    ) {
        if (bit < 0 || low >= high) {
            return; // Base case: no bits left or subarray is trivial
        }

        // For small subarrays, insertion sort is generally faster
        if (high - low <= SMALL_ARRAY_THRESHOLD) {
            insertionSort(ents, bits, low, high);
            return;
        }

        int i = low;
        int j = high;
        final long mask = 1L << bit;

        while (i <= j) {
            while (i <= j && (bits[i] & mask) == 0) {
                i++;
            }
            while (i <= j && (bits[j] & mask) != 0) {
                j--;
            }
            if (i < j) {
                swap(ents, bits, i++, j--);
            }
        }

        if (low < j) {
            fastRadixSort(ents, bits, low, j, bit - 1);
        }
        if (i < high) {
            fastRadixSort(ents, bits, i, high, bit - 1);
        }
    }

    private void insertionSort(
        LivingEntity[] ents,
        long[] bits,
        int low,
        int high
    ) {
        for (int i = low + 1; i <= high; i++) {
            int j = i;
            LivingEntity currentEntity = ents[j];
            long currentBits = bits[j];

            while (j > low && bits[j - 1] > currentBits) {
                ents[j] = ents[j - 1];
                bits[j] = bits[j - 1];
                j--;
            }

            ents[j] = currentEntity;
            bits[j] = currentBits;
        }
    }

    private void swap(LivingEntity[] ents, long[] bits, int a, int b) {
        LivingEntity tempEntity = ents[a];
        ents[a] = ents[b];
        ents[b] = tempEntity;

        long tempBits = bits[a];
        bits[a] = bits[b];
        bits[b] = tempBits;
    }
}
