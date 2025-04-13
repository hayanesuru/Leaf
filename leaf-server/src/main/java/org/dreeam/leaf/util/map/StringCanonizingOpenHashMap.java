package org.dreeam.leaf.util.map;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.Map;
import java.util.function.Function;

public class StringCanonizingOpenHashMap<T> extends Object2ObjectOpenHashMap<String, T> {

    private static final Interner<String> KEY_INTERNER = Interners.newWeakInterner();
    private final float loadFactor;

    private static String intern(String key) {
        if (key == null) return null;
        String jvmInterned = key.intern();
        if (jvmInterned == key) {
            return key;
        }

        return KEY_INTERNER.intern(key);
    }

    public StringCanonizingOpenHashMap() {
        super();
        this.loadFactor = 0.8f;
    }

    public StringCanonizingOpenHashMap(int expectedSize) {
        super(expectedSize);
        this.loadFactor = 0.8f;
    }

    public StringCanonizingOpenHashMap(int expectedSize, float loadFactor) {
        super(expectedSize, loadFactor);
        this.loadFactor = loadFactor;
    }

    @Override
    public T put(String key, T value) {
        return super.put(intern(key), value);
    }

    @Override
    public void putAll(Map<? extends String, ? extends T> m) {
        if (m.isEmpty()) return;

        // Fast path for maps that already have interned keys
        if (m instanceof StringCanonizingOpenHashMap) {
            super.putAll(m);
            return;
        }
        // Process each entry directly rather than creating a temporary map
        for (Map.Entry<? extends String, ? extends T> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    private void putWithoutInterning(String key, T value) {
        super.put(key, value);
    }

    public static <T> StringCanonizingOpenHashMap<T> deepCopy(StringCanonizingOpenHashMap<T> incomingMap, Function<T, T> deepCopier) {
        int size = incomingMap.size();
        if (size == 0) {
            return new StringCanonizingOpenHashMap<>(0, incomingMap.loadFactor);
        }
        // Pre-allocate
        StringCanonizingOpenHashMap<T> newMap = new StringCanonizingOpenHashMap<>(size, incomingMap.loadFactor);
        ObjectIterator<Entry<String, T>> iterator = incomingMap.object2ObjectEntrySet().fastIterator();

        while (iterator.hasNext()) {
            Entry<String, T> entry = iterator.next();
            // Keys are already interned, so we can add them directly
            newMap.putWithoutInterning(entry.getKey(), deepCopier.apply(entry.getValue()));
        }

        return newMap;
    }
}
