package de.dakror.modding;

import java.lang.invoke.MethodHandleProxies;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HashSetMap<K,V> extends HashMap<K, V> {
    private final Function<? super V, ? extends K> keyFunction;
    private final Class<V> keyfParamType;
    private final static Map<Function<?, ?>, Class<?>> keyfParamTypes = new HashMap<>();

    public HashSetMap(Function<? super V, ? extends K> keyFunction) {
        this.keyFunction = keyFunction;
        this.keyfParamType = findVClass(keyFunction);
    }

    public HashSetMap(int initialCapacity, Function<? super V, ? extends K> keyFunction) {
        super(initialCapacity);
        this.keyFunction = keyFunction;
        this.keyfParamType = findVClass(keyFunction);
    }

    public HashSetMap(Set<? extends V> s, Function<? super V, ? extends K> keyFunction) {
        super(s.stream().collect(Collectors.toMap(keyFunction, Function.identity())));
        this.keyFunction = keyFunction;
        this.keyfParamType = findVClass(keyFunction);
    }

    public HashSetMap(int initialCapacity, float loadFactor, Function<? super V, ? extends K> keyFunction) {
        super(initialCapacity, loadFactor);
        this.keyFunction = keyFunction;
        this.keyfParamType = findVClass(keyFunction);
    }

    private static <V> Class<V> findVClass(Function<? super V, ?> keyFunction) {
        var vclass = keyfParamTypes.computeIfAbsent(keyFunction, function -> {
            if (MethodHandleProxies.isWrapperInstance(function)) {
                var mh = MethodHandleProxies.wrapperInstanceTarget(function);
                return mh.type().parameterType(0);
            }
            return Object.class;
        });
        @SuppressWarnings("unchecked")
        var vClass = (Class<V>)vclass;
        return vClass;
    }

    public boolean add(V element) {
        var orig = put(keyOf(element), element);
        return !Objects.equals(element, orig);
    }
    
    public V removeElement(Object element) {
        V orig = remove(keyOfCast(element));
        if (!element.equals(orig)) {
            add(orig);
            return null;
        }
        return orig;
    }

    public boolean contains(Object element) {
        try {
            return element.equals(get(keyOfCast(element)));
        } catch (NullPointerException|ClassCastException e) {
            return false;
        }
    }

    public V find(V value) {
        return get(keyOf(value));
    }

    public Set<V> asSet() {
        return new SetAdapter();
    }

    public K keyOf(V value) {
        return keyFunction.apply(value);
    }
    public K keyOfCast(Object o) {
        return keyOf(keyfParamType.cast(o));
    }

    private class SetAdapter extends AbstractSet<V> {
        @Override
        public int size() {
            return HashSetMap.this.size();
        }

        @Override
        public boolean contains(Object o) {
            return HashSetMap.this.contains(o);
        }

        @Override
        public Iterator<V> iterator() {
            return values().iterator();
        }

        @Override
        public Object[] toArray() {
            return values().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return values().toArray(a);
        }

        @Override
        public boolean add(V e) {
            return HashSetMap.this.add(e);
        }

        @Override
        public boolean remove(Object o) {
            return removeElement(o) != null;
        }

        @Override
        public void clear() {
            HashSetMap.this.clear();
        }

    }
}
