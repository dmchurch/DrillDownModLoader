package de.dakror.modding;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class DefaultingHashMap<K,V> extends HashMap<K,V> {
    public static final Object FREEZE = new Object();
    public static final Object UNFREEZE = new Object();
    protected Function<? super K, ? extends V> mappingFunction;
    protected Supplier<? extends V> supplierFunction = null;
    protected boolean frozen = false;

    public static <K,V> DefaultingHashMap<K,V> using(Supplier<? extends V> supplierFunction) {
        return new DefaultingHashMap<>(supplierFunction);
    }

    public DefaultingHashMap(Function<? super K, ? extends V> mappingFunction) {
        this.mappingFunction = mappingFunction;
    }

    protected DefaultingHashMap(Supplier<? extends V> supplierFunction) {
        this.supplierFunction = supplierFunction;
        mappingFunction = this::supplierMap;
    }

    public DefaultingHashMap(Map<? extends K, ? extends V> m, Function<? super K, ? extends V> mappingFunction) {
        super(m);
        this.mappingFunction = mappingFunction;
    }

    public DefaultingHashMap(int initialCapacity, Function<? super K, ? extends V> mappingFunction) {
        super(initialCapacity);
        this.mappingFunction = mappingFunction;
    }

    public DefaultingHashMap(int initialCapacity, float loadFactor, Function<? super K, ? extends V> mappingFunction) {
        super(initialCapacity, loadFactor);
        this.mappingFunction = mappingFunction;
    }

    protected V supplierMap(K key) {
        return supplierFunction.get();
    }

    @Override
    public V get(Object key) {
        if (key == UNFREEZE) {
            frozen = false;
            return super.get(key);
        } else if (key == FREEZE || frozen) {
            frozen = true;
            return null;
        }

        @SuppressWarnings("unchecked") K typedKey = (K) key;
        return super.computeIfAbsent(typedKey, mappingFunction);
    }

    @Override
    public V put(K key, V value) {
        if (frozen) throw new UnsupportedOperationException("attempting to modify frozen DefaultingHashMap");
        return super.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        if (frozen) throw new UnsupportedOperationException("attempting to modify frozen DefaultingHashMap");
        super.putAll(m);
    }

    @Override
    public V putIfAbsent(K key, V value) {
        if (frozen) throw new UnsupportedOperationException("attempting to modify frozen DefaultingHashMap");
        return super.putIfAbsent(key, value);
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (frozen) throw new UnsupportedOperationException("attempting to modify frozen DefaultingHashMap");
        return super.compute(key, remappingFunction);
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (frozen) throw new UnsupportedOperationException("attempting to modify frozen DefaultingHashMap");
        return super.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (frozen) throw new UnsupportedOperationException("attempting to modify frozen DefaultingHashMap");
        return super.computeIfPresent(key, remappingFunction);
    }
}
