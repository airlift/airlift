package com.proofpoint.zookeeper;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Attempts to provide a stable view of a Zookeeper path. Watches for changes in the path and
 * updates state to match.
 */
public class NodeView
{
    private final ChildDataWatcher childDataWatcher;
    private final Map<String, byte[]> viewData = Maps.newTreeMap();
    private final Object lock = new Object();

    private long version = 0;

    /**
     * @param client ZK
     * @param path path to watch
     */
    public NodeView(ZookeeperClient client, String path)
    {
        childDataWatcher = new ChildDataWatcher(client, path, new Listener(), Executors.newSingleThreadExecutor());
    }

    @PostConstruct
    public void start() throws Exception
    {
        childDataWatcher.start();
    }

    @PreDestroy
    public void stop() throws Exception
    {
        childDataWatcher.stop();
    }

    /**
     * If a node has been added locally, this must be called as ZK won't notify of local changes
     *
     * @param child the child path
     * @param data data
     */
    public void nodeAdded(String child, byte[] data)
    {
        synchronized(lock)
        {
            viewData.put(child, data);
            ++version;
            lock.notifyAll();
        }
    }

    /**
     * If a node has been updated locally, this must be called as ZK won't notify of local changes
     *
     * @param child the child path
     * @param data data
     */
    public void nodeUpdated(String child, byte[] data)
    {
        nodeAdded(child, data);
    }

    /**
     * If a node has been removed locally, this must be called as ZK won't notify of local changes
     *
     * @param child the child path
     */
    public void nodeRemoved(String child)
    {
        synchronized(lock)
        {
            viewData.remove(child);
            ++version;
            lock.notifyAll();
        }
    }

    /**
     * If you want to synchronize with the view's sync object
     *
     * @return sync object
     */
    public Object getLock()
    {
        return lock;
    }

    /**
     * Wait for a change in state
     *
     * @param fromVersion the version that you are starting from
     * @throws InterruptedException if interrupted
     */
    public void waitForUpdate(long fromVersion) throws InterruptedException
    {
        synchronized(lock)
        {
            while ( fromVersion == version )
            {
                lock.wait();
            }
        }
    }

    /**
     * A stable view
     */
    public interface View
    {
        /**
         * This views version. Version number increment each time there is an update
         *
         * @return version
         */
        public long version();

        /**
         * The data - path -> bytes
         *
         * @return data
         */
        public List<Map.Entry<String, byte[]>> entries();
    }

    /**
     * Return a stable view of the path being watched. The view returned is a snapshot of the current state.
     * Future changes in the path will not change the view returned.
     *
     * @return view
     */
    public View getView()
    {
        final List<Map.Entry<String, byte[]>>     localView;
        final long                                localVersion;
        synchronized(lock)
        {
            localVersion = version;
            Iterable<Map.Entry<String, byte[]>> immutableEntries = Iterables.transform(viewData.entrySet(), new Function<Map.Entry<String, byte[]>, Map.Entry<String, byte[]>>()
            {
                @Override
                public Map.Entry<String, byte[]> apply(Map.Entry<String, byte[]> from)
                {
                    return Maps.immutableEntry(from.getKey(), from.getValue());
                }
            });
            localView = Lists.newArrayList(immutableEntries);
        }
        
        return new View()
        {
            @Override
            public long version()
            {
                return localVersion;
            }

            @Override
            public List<Map.Entry<String, byte[]>> entries()
            {
                return localView;
            }
        };
    }

    private class Listener implements ChildDataListener
    {
        @Override
        public void added(String child, byte[] data) throws Exception
        {
            nodeAdded(child, data);
        }

        @Override
        public void updated(String child, byte[] data) throws Exception
        {
            nodeUpdated(child, data);
        }

        @Override
        public void removed(String child) throws Exception
        {
            nodeRemoved(child);
        }
    }
}
