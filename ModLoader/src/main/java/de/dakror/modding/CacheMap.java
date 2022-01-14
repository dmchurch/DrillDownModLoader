package de.dakror.modding;

import java.util.function.Function;

public class CacheMap<K, V> extends VeryWeakMap<K, V> {
    private final Function<K, V> mapFunction;

    public CacheMap(Function<K, V> mapFunction) {
        this.mapFunction = mapFunction;
    }

    public CacheMap(Function<K, V> mapFunction, int initialCapacity) {
        super(initialCapacity);
        this.mapFunction = mapFunction;
    }

    public CacheMap(Function<K, V> mapFunction, int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        this.mapFunction = mapFunction;
    }
    
    public V cacheGet(K key) {
        return super.computeIfAbsent(key, mapFunction);
    }
}
