package io.airlift.jaxrs.testing;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import javax.ws.rs.core.MultivaluedMap;

import java.util.List;
import java.util.Map;

public class GuavaMultivaluedMap<K, V>
        extends ForwardingMap<K, List<V>>
        implements MultivaluedMap<K, V>
{
    private final ListMultimap<K, V> multimap = ArrayListMultimap.create();

    public GuavaMultivaluedMap()
    {
    }

    public GuavaMultivaluedMap(Multimap<K, V> multimap)
    {
        this.multimap.putAll(multimap);
    }

    @Override
    public void putSingle(K key, V value)
    {
        multimap.removeAll(key);
        multimap.put(key, value);
    }

    @Override
    protected Map<K, List<V>> delegate()
    {
        return Multimaps.asMap(multimap);
    }

    @Override
    public void add(K key, V value)
    {
        multimap.put(key, value);
    }

    @SafeVarargs
    @Override
    public final void addAll(K key, V... newValues)
    {
        multimap.putAll(key, ImmutableList.copyOf(newValues));
    }

    @Override
    public void addAll(K key, List<V> valueList)
    {
        multimap.putAll(key, valueList);
    }

    @Override
    public V getFirst(K key)
    {
        return Iterables.getFirst(multimap.get(key), null);
    }

    @Override
    public void addFirst(K key, V value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equalsIgnoreValueOrder(MultivaluedMap<K, V> otherMap)
    {
        throw new UnsupportedOperationException();
    }
}
