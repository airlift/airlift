package com.proofpoint.collections;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A consistent hash implementation that uses the quantizing method
 */
public class ConsistentHash<T>
{
    private final List<T> nodes = Lists.newArrayList();
    private final HashFunction hashFunction;

    private long factor = 0;

    /**
     * The default hashing function. Uses 50% of an MD5's bits
     */
    private static final HashFunction md5Hash = new HashFunction()
    {
        @Override
        public long hash(Object s)
        {
            try {
                MessageDigest digest = MessageDigest.getInstance("MD5");
                String stringValue = String.valueOf(s);
                byte[] bytes = digest.digest(stringValue.getBytes());
                return getLong(bytes, 0);
            }
            catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        @SuppressWarnings({"PointlessBitwiseExpression", "PointlessArithmeticExpression"})
        // copied from JDK's Bits.java
        private long getLong(byte[] b, int off)
        {
            return ((b[off + 7] & 0xFFL) << 0) +
                    ((b[off + 6] & 0xFFL) << 8) +
                    ((b[off + 5] & 0xFFL) << 16) +
                    ((b[off + 4] & 0xFFL) << 24) +
                    ((b[off + 3] & 0xFFL) << 32) +
                    ((b[off + 2] & 0xFFL) << 40) +
                    ((b[off + 1] & 0xFFL) << 48) +
                    (((long) b[off + 0]) << 56);
        }
    };

    /**
     * Create a new consistent hasher with an MD5 hash function
     *
     * @param nodes initial nodes (can be empty)
     * @return new hasher
     */
    public static <T> ConsistentHash<T> newConsistentHash(Collection<T> nodes)
    {
        return new ConsistentHash<T>(md5Hash, nodes);
    }

    /**
     * Create a new consistent hasher with an MD5 hash function
     *
     * @return new hasher
     */
    public static <T> ConsistentHash<T> newConsistentHash()
    {
        return new ConsistentHash<T>(md5Hash, null);
    }

    /**
     * @param hashFunction the hash function to use
     * @param nodes initial set of nodes (can be empty or null)
     */
    public ConsistentHash(HashFunction hashFunction, Collection<T> nodes)
    {
        this.hashFunction = hashFunction;

        if (nodes != null) {
            for (T node : nodes) {
                addNode(node);
            }
        }
    }

    /**
     * Adda new node
     *
     * @param node the node to add
     */
    public void addNode(T node)
    {
        nodes.add(node);
        resetFactor();
    }

    /**
     * Remove the given node
     *
     * @param node node to remove
     */
    public void removeNode(T node)
    {
        nodes.remove(node);
        resetFactor();
    }

    /**
     * Return which node to use for the given key
     *
     * @param key key
     * @return node for the key (or null if there are no nodes)
     */
    public T getNodeForKey(Object key)
    {
        return (nodes.size() > 0) ? nodes.get(keyToIndex(key)) : null;
    }

    /**
     * Returns a set of nodes for the given key. The first node is the
     * node that would be returned by {@link #getNodeForKey(Object)}. The remaining
     * nodes are the nodes that follow in the circle
     *
     * @param key key
     * @param qty number of nodes to get
     * @return list of nodes (size will be less than qty if qty is larger than the number of nodes)
     */
    public Collection<T> getNodesForKey(Object key, int qty)
    {
        if (nodes.size() == 0) {
            return Lists.newArrayList();
        }

        qty = Math.max(1, Math.min(qty, nodes.size()));

        List<T> list = Lists.newArrayList();
        int index = keyToIndex(key);
        int startingIndex = index;
        while (qty-- > 0) {
            list.add(nodes.get(index));
            ++index;
            if (index >= nodes.size()) {
                index = 0;
            }

            if ((index == startingIndex) && (qty > 0)) {
                throw new IllegalStateException();
            }
        }

        return list;
    }

    /**
     * Return the set of nodes in the hash circle
     *
     * @return nodes
     */
    public Set<T> getNodes()
    {
        return Sets.newHashSet(nodes);
    }

    private void resetFactor()
    {
        factor = (nodes.size() > 0) ? (Long.MAX_VALUE / nodes.size()) : 0;
    }

    private int keyToIndex(Object key)
    {
        long hashOfKey = hashFunction.hash(key);
        return (int) Math.abs(hashOfKey / factor);
    }
}
