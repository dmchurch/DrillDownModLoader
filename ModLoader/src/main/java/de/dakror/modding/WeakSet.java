package de.dakror.modding;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

public class WeakSet<E> implements Set<E> {
    private final WeakHashMap<E, Void> weakMap;
    private final Set<E> weakKeys;

    public WeakSet() {
        weakMap = new WeakHashMap<>();
        weakKeys = weakMap.keySet();
    }

    public WeakSet(Collection<? extends E> c) {
        weakMap = new WeakHashMap<>(c.size());
        for (var e: c) {
            weakMap.put(Objects.requireNonNull(e), null);
        }
        weakKeys = weakMap.keySet();
    }

    public WeakSet(int initialCapacity) {
        weakMap = new WeakHashMap<>(initialCapacity);
        weakKeys = weakMap.keySet();
    }

    public WeakSet(int initialCapacity, float loadFactor) {
        weakMap = new WeakHashMap<>(initialCapacity, loadFactor);
        weakKeys = weakMap.keySet();
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
    public boolean contains(Object o) {
        return weakMap.containsKey(o);
    }

    @Override
    public Iterator<E> iterator() {
        return weakKeys.iterator();
    }

    @Override
    public Object[] toArray() {
        return weakKeys.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return weakKeys.toArray(a);
    }

    @Override
    public boolean add(E e) {
        Objects.requireNonNull(e);
        if (weakMap.containsKey(e)) return false;
        weakMap.put(e, null);
        return true;
    }

    @Override
    public boolean remove(Object o) {
        return weakKeys.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return weakKeys.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean changed = false;
        for (var e: c) {
            changed = add(e) || changed;
        }
        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return weakKeys.retainAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return weakKeys.removeAll(c);
    }

    @Override
    public void clear() {
        weakMap.clear();
    }
}
