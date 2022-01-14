package de.dakror.modding;

import java.lang.ref.WeakReference;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class VeryWeakMap<K,V> implements Map<K, V> {
    private final Map<K, WeakReference<V>> weakMap;

    public VeryWeakMap() {
        this(new WeakHashMap<K, WeakReference<V>>());
    }

    public VeryWeakMap(Map<? extends K, ? extends V> m) {
        this(new WeakHashMap<>(toWeakValues(m)));
    }

    public VeryWeakMap(VeryWeakMap<K, V> m) {
        this(new WeakHashMap<>(m.weakMap));
    }

    public VeryWeakMap(int initialCapacity) {
        this(new WeakHashMap<K, WeakReference<V>>(initialCapacity));
    }

    public VeryWeakMap(int initialCapacity, float loadFactor) {
        this(new WeakHashMap<K, WeakReference<V>>(initialCapacity, loadFactor));
    }

    private VeryWeakMap(WeakHashMap<K, WeakReference<V>> weakMap) {
        this.weakMap = weakMap;
    }
    
    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, WeakReference<V>> toWeakValues(Map<? extends K, ? extends V> m) {
        return Map.ofEntries(m.entrySet()
                            .stream()
                            .<Map.Entry<K, WeakReference<V>>>map(VeryWeakMap::toWeakEntry)
                            .toArray(Map.Entry[]::new));
    }

    private static <K, V> Map.Entry<K, WeakReference<V>> toWeakEntry(Map.Entry<? extends K, ? extends V> e) {
        if (e.getValue() instanceof WeakReference) {
            @SuppressWarnings("unchecked")
            var cast = (Map.Entry<K, WeakReference<V>>) e;
            return cast;
        } else {
            return Map.entry(e.getKey(), new WeakReference<V>(e.getValue()));
        }
    }

    @Override
    public int size() {
        return weakMap.size();
    }

    @Override
    public boolean isEmpty() {
        return weakMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return weakMap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return weakMap.containsValue(new WeakReference<>(value));
    }

    @Override
    public V get(Object key) {
        return deref(weakMap.get(key), key);
    }

    @Override
    public V put(K key, V value) {
        return deref(weakMap.put(key, new WeakReference<>(value)));
    }

    @Override
    public V remove(Object key) {
        return deref(weakMap.remove(key));
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        weakMap.putAll(toWeakValues(m));
    }

    @Override
    public void clear() {
        weakMap.clear();
    }

    @Override
    public Set<K> keySet() {
        return weakMap.keySet();
    }

    @Override
    public Collection<V> values() {
        return new Values();
    }

    @Override
    public EntrySet entrySet() {
        return new EntrySet();
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return deref(weakMap.compute(key, (k,vref) -> {
            V v0 = deref(vref);
            V v1 = remappingFunction.apply(key, v0);
            return v1 == null ? null : new WeakReference<>(v1);
        }));
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        return compute(key, (k, v) -> v == null ? mappingFunction.apply(k) : v);
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return compute(key, (k, v) -> v == null ? null : remappingFunction.apply(k, v));
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        return compute(key, (k, v) -> v == null ? value : remappingFunction.apply(v, value));
    }

    private V deref(WeakReference<V> ref) {
        return ref == null ? null : ref.get();
    }

    private V deref(WeakReference<V> ref, Object key) {
        V v = deref(ref);
        if (v == null) {
            weakMap.remove(key);
        }
        return v;
    }

    private final class Entry implements Map.Entry<K, V> {
        private final Map.Entry<K, WeakReference<V>> entry;

        public Entry(java.util.Map.Entry<K, WeakReference<V>> entry) {
            this.entry = entry;
        }

        public Map.Entry<K, V> materialize() {
            K key = getKey();
            V value = getValue();
            if (key != null && value != null) {
                return Map.entry(key, value);
            }
            return null;
        }

        @Override
        public K getKey() {
            return entry.getKey();
        }

        @Override
        public V getValue() {
            return entry.getValue().get();
        }

        @Override
        public V setValue(V value) {
            var oldEntry = entry.setValue(new WeakReference<>(value));
            return oldEntry == null ? null : oldEntry.get();
        }
    }

    private final class Values extends AbstractCollection<V> {
        private final Collection<WeakReference<V>> weakValues = weakMap.values();

        @Override
        public int size() {
            return weakValues.size();
        }

        @Override
        public boolean isEmpty() {
            return weakValues.isEmpty();
        }

        @Override
        public Iterator<V> iterator() {
            return new Iterator<>() {
                private final EntryIterator it = entrySet().iterator();

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public V next() {
                    return it.next().getValue();
                }

                @Override
                public void remove() {
                    it.remove();
                }
            };
        }

        @Override
        public void clear() {
            weakMap.clear();
        }
    }

    private final class EntryIterator implements Iterator<Map.Entry<K, V>> {
        private final Iterator<Map.Entry<K, WeakReference<V>>> it = weakMap.entrySet().iterator();
        private Entry next = null;
        private K lastKey = null;
        private Map.Entry<K, V> getNext() {
            Map.Entry<K, V> theNext = null;
            while ((next == null || (theNext = next.materialize()) == null) && it.hasNext()) {
                next = new Entry(it.next());
            }
            return theNext;
        }
        @Override
        public boolean hasNext() {
            getNext();
            return it.hasNext();
        }

        @Override
        public Map.Entry<K, V> next() {
            var e = getNext();
            if (e == null) {
                throw new NoSuchElementException();
            }
            lastKey = e.getKey();
            return e;
        }

        @Override
        public void remove() {
            VeryWeakMap.this.remove(lastKey);
            lastKey = null;
        }
    }

    private class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        private final Set<Map.Entry<K, WeakReference<V>>> weakSet = weakMap.entrySet();

        @Override
        public int size() {
            return weakSet.size();
        }

        @Override
        public boolean isEmpty() {
            return weakSet.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry)) return false;
            var e = (Map.Entry<?,?>)o;
            var ev = e.getValue();
            var v = get(e.getKey());
            return ev != null && Objects.equals(ev, v);
        }

        @Override
        public EntryIterator iterator() {
            return new EntryIterator();
        }

        @Override
        public boolean remove(Object o) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public void clear() {
            // TODO Auto-generated method stub
            
        }

    }
}
