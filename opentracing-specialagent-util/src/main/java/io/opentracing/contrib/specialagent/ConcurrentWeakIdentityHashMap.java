package io.opentracing.contrib.specialagent;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConcurrentWeakIdentityHashMap<K,V>extends AbstractMap<K,V> implements ConcurrentMap<K,V> {
  private final ConcurrentMap<Key<K>,V> map;
  private final ReferenceQueue<K> queue = new ReferenceQueue<>();
  private transient Set<Map.Entry<K,V>> es;

  public ConcurrentWeakIdentityHashMap(final int initialCapacity) {
    this.map = new ConcurrentHashMap<>(initialCapacity);
  }

  public ConcurrentWeakIdentityHashMap() {
    this.map = new ConcurrentHashMap<>();
  }

  @Override
  public V get(final Object key) {
    purgeKeys();
    return map.get(new Key<>(key, null));
  }

  @Override
  public V put(final K key, final V value) {
    purgeKeys();
    return map.put(new Key<>(key, queue), value);
  }

  @Override
  public int size() {
    purgeKeys();
    return map.size();
  }

  private void purgeKeys() {
    for (Reference<? extends K> reference; (reference = queue.poll()) != null; map.remove(reference));
  }

  @Override
  public Set<Map.Entry<K,V>> entrySet() {
    final Set<Map.Entry<K,V>> entrySet = this.es;
    return entrySet == null ? es = new EntrySet() : entrySet;
  }

  @Override
  public V putIfAbsent(final K key, final V value) {
    purgeKeys();
    return map.putIfAbsent(new Key<>(key, queue), value);
  }

  @Override
  public V remove(final Object key) {
    return map.remove(new Key<>(key, null));
  }

  @Override
  public boolean remove(final Object key, final Object value) {
    purgeKeys();
    return map.remove(new Key<>(key, null), value);
  }

  @Override
  public boolean replace(final K key, final V oldValue, final V newValue) {
    purgeKeys();
    return map.replace(new Key<>(key, null), oldValue, newValue);
  }

  @Override
  public V replace(final K key, final V value) {
    purgeKeys();
    return map.replace(new Key<>(key, null), value);
  }

  @Override
  public boolean containsKey(final Object key) {
    purgeKeys();
    return map.containsKey(new Key<>(key, null));
  }

  @Override
  public void clear() {
    while (queue.poll() != null);
    map.clear();
  }

  @Override
  public boolean containsValue(final Object value) {
    purgeKeys();
    return map.containsValue(value);
  }

  private static class Key<T>extends WeakReference<T> {
    private final int hash;

    Key(final T t, final ReferenceQueue<T> queue) {
      super(t, queue);
      hash = System.identityHashCode(Objects.requireNonNull(t));
    }

    @Override
    public boolean equals(final Object obj) {
      return this == obj || obj instanceof Key && ((Key<?>)obj).get() == get();
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }

  private class Iter implements Iterator<Map.Entry<K,V>> {
    private final Iterator<Map.Entry<Key<K>,V>> iterator;
    private Map.Entry<K,V> next;

    Iter(final Iterator<Map.Entry<Key<K>,V>> iterator) {
      this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
      if (next != null)
        return true;

      while (iterator.hasNext()) {
        final Map.Entry<Key<K>,V> entry = iterator.next();
        final K key = entry.getKey().get();
        if (key == null)
          iterator.remove();

        next = new Entry(key, entry.getValue());
        return true;
      }
      return false;
    }

    @Override
    public Map.Entry<K,V> next() {
      if (!hasNext())
        throw new NoSuchElementException();

      final Map.Entry<K,V> temp = next;
      next = null;
      return temp;
    }

    @Override
    public void remove() {
      iterator.remove();
      next = null;
    }
  }

  private class EntrySet extends AbstractSet<Map.Entry<K,V>> {
    @Override
    public Iterator<Map.Entry<K,V>> iterator() {
      return new Iter(map.entrySet().iterator());
    }

    @Override
    public int size() {
      return ConcurrentWeakIdentityHashMap.this.size();
    }

    @Override
    public void clear() {
      ConcurrentWeakIdentityHashMap.this.clear();
    }

    @Override
    public boolean contains(final Object o) {
      if (!(o instanceof Map.Entry))
        return false;

      final Map.Entry<?,?> e = (Map.Entry<?,?>)o;
      return ConcurrentWeakIdentityHashMap.this.get(e.getKey()) == e.getValue();
    }

    @Override
    public boolean remove(final Object o) {
      if (!(o instanceof Map.Entry))
        return false;

      final Map.Entry<?,?> e = (Map.Entry<?,?>)o;
      return ConcurrentWeakIdentityHashMap.this.remove(e.getKey(), e.getValue());
    }
  }

  private class Entry extends AbstractMap.SimpleEntry<K,V> {
    private static final long serialVersionUID = -3665682102204878760L;

    Entry(final K key, final V value) {
      super(key, value);
    }

    @Override
    public V setValue(final V value) {
      ConcurrentWeakIdentityHashMap.this.put(getKey(), value);
      return super.setValue(value);
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj == this)
        return true;

      if (!(obj instanceof Map.Entry))
        return false;

      final Map.Entry<?,?> that = (Map.Entry<?,?>)obj;
      return getKey() == that.getKey() && getValue() == that.getValue();
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(getKey()) ^ System.identityHashCode(getValue());
    }
  }
}