package com.proofpoint.zookeeper;

import com.proofpoint.concurrent.events.EventQueue;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wrapper for ZooKeeper events
 */
public class ZookeeperEvent implements EventQueue.Event<ZookeeperEvent>
{
    private final Type type;
    private final Object                context;
    private final KeeperException.Code  resultCode;
    private final byte[]                data;
    private final Stat                  stat;
    private final String                path;
    private final String                name;
    private final List<String>          children;
    private final Object                key;

    public enum Type
    {
        WATCHED_NONE,
        WATCHED_NODE_CREATED,
        WATCHED_NODE_DELETED,
        WATCHED_NODE_DATA_CHANGED,
        WATCHED_NODE_CHILDREN_CHANGED,

        /**
         * Event was due to {@link com.proofpoint.zookeeper.ZookeeperClient#sync(String)} being called
         */
        SYNC,

        /**
         * Event was due to {@link com.proofpoint.zookeeper.ZookeeperClient#exists(String)} being called
         */
        EXISTS,

        /**
         * Event was due to {@link com.proofpoint.zookeeper.ZookeeperClient#getData(String)} being called
         */
        GET_DATA,

        /**
         * Event was due to {@link com.proofpoint.zookeeper.ZookeeperClient#setData(String, byte[])} being called
         */
        SET_DATA,

        /**
         * Event was due to {@link com.proofpoint.zookeeper.ZookeeperClient#getChildren(String)} being called
         */
        GET_CHILDREN,

        /**
         * Event was due to {@link com.proofpoint.zookeeper.ZookeeperClient#create(String, byte[])} being called
         */
        CREATE,

        /**
         * Event was due to {@link com.proofpoint.zookeeper.ZookeeperClient#delete(String)} being called
         */
        DELETE
    }

    ZookeeperEvent(Type type, int rc, String path, Object context, byte[] data, Stat stat, String name, List<String> children, Object key)
    {
        this.type = type;
        this.key = key;
        resultCode = KeeperException.Code.get(rc);
        this.children = (children != null) ? new ArrayList<String>(Collections.unmodifiableList(children)) : null;
        this.path = path;
        this.context = context;
        this.data = data;
        this.stat = stat;
        this.name = name;
    }

    /**
     * For a few types of events, the info in WatchedEvent isn't enough.
     *
     * @return the sub type
     */
    public Type getType()
    {
        return type;
    }

    /**
     * Returns the children list if any
     *
     * @return children or null
     */
    public List<String> getChildren()
    {
        return children;
    }

    /**
     * Returns the context that was set
     *
     * @return context or null
     */
    public Object getContext()
    {
        return context;
    }

    /**
     * Returns the background key that was set
     *
     * @return background key or null
     */
    public Object getKey()
    {
        return key;
    }

    /**
     * Returns the callback result code
     *
     * @return result code
     */
    public KeeperException.Code getResultCode()
    {
        return resultCode;
    }

    /**
     * Returns the data from the callback if any
     *
     * @return data or null
     */
    public byte[] getData()
    {
        return data;
    }

    /**
     * Returns the stat from the callback if any
     *
     * @return stat or null
     */
    public Stat getStat()
    {
        return stat;
    }

    /**
     * Returns the path from the callback if any
     *
     * @return path or null
     */
    public String getPath()
    {
        return path;
    }

    /**
     * Returns the name from the callback if any
     *
     * @return name or null
     */
    public String getName()
    {
        return name;
    }

    @Override
    public boolean canBeMergedWith(ZookeeperEvent event)
    {
        return false;
    }

    @Override
    public void processEvent()
    {
    }
}

