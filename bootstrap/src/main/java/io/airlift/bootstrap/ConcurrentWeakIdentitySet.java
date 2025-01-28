package io.airlift.bootstrap;

import com.google.common.collect.Sets;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Set;

import static java.lang.System.identityHashCode;

class ConcurrentWeakIdentitySet
{
    private final Set<Wrapper> set = Sets.newConcurrentHashSet();
    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    // copied/modified from WeakHashMap implementation
    private static class Wrapper
            extends WeakReference<Object>
    {
        private final int id;

        private Wrapper(Object o, ReferenceQueue<Object> queue)
        {
            super(o, queue);

            // Asserting that "id" refers to an object's address in memory and that no
            // two objects can have the same identityHashCode.
            // This may not be true for all VM implementations.
            id = identityHashCode(o);
        }

        @Override
        public boolean equals(Object o)
        {
            return (this == o) || ((o instanceof Wrapper wrapper) && (wrapper.id == id));
        }

        @Override
        public int hashCode()
        {
            return id;
        }
    }

    int size()
    {
        removeStaleEntries();

        return set.size();
    }

    boolean add(Object o)
    {
        removeStaleEntries();

        return set.add(new Wrapper(o, queue));
    }

    private void removeStaleEntries()
    {
        // copied/modified from WeakHashMap implementation
        for (Object o; (o = queue.poll()) != null; ) {
            set.remove((Wrapper) o);
        }
    }
}
