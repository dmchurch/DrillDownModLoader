package de.dakror.modding;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.function.Function;

public class CacheSet<E> extends AbstractSet<E> {
    private final CacheMap<E, E> cacheMap;

    public CacheSet() {
        cacheMap = new CacheMap<>(Function.identity());
    }

    public CacheSet(int initialCapacity) {
        cacheMap = new CacheMap<>(Function.identity(), initialCapacity);
    }

    public CacheSet(int initialCapacity, float loadFactor) {
        cacheMap = new CacheMap<>(Function.identity(), initialCapacity, loadFactor);
    }

    public E cacheGet(E key) {
        return cacheMap.cacheGet(key);
    }

    @Override
    public Iterator<E> iterator() {
        return cacheMap.keySet().iterator();
    }

    @Override
    public int size() {
        return cacheMap.size();
    }

    @Override
    public boolean contains(Object o) {
        return cacheMap.containsKey(o);
    }

    @Override
    public Object[] toArray() {
        return cacheMap.keySet().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return cacheMap.keySet().toArray(a);
    }

    @Override
    public boolean add(E e) {
        return cacheMap.put(e, e) == null;
    }

    @Override
    public boolean remove(Object o) {
        return cacheMap.remove(o) != null;
    }

    @Override
    public void clear() {
        cacheMap.clear();
    }

}
