package com.proofpoint.zookeeper;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;

import java.util.List;

public interface ZookeeperClientHelper
{
    /**
     * Analog for {@link org.apache.zookeeper.ZooKeeper#getChildren(String, boolean)}
     *
     * @param path the path
     * @return list of children
     * @throws Exception ZooKeeper errors or timeouts, etc.
     */
    public List<String> getChildren(String path) throws Exception;

    /**
     * Analog for {@link org.apache.zookeeper.ZooKeeper#create(String, byte[], java.util.List, org.apache.zookeeper.CreateMode)}
     *
     * @param path the path
     * @param data data to set for the path
     * @return for sequential files, returns the actual name created
     * @throws Exception ZooKeeper errors or timeouts, etc.
     */
    public String create(String path, byte data[]) throws Exception;

    /**
     * Analog for {@link org.apache.zookeeper.ZooKeeper#exists(String, boolean)}
     *
     * @param path the path
     * @return stat or null
     * @throws Exception ZooKeeper errors or timeouts, etc.
     */
    public Stat exists(String path) throws Exception;

    /**
     * Analog for {@link org.apache.zookeeper.ZooKeeper#sync(String, org.apache.zookeeper.AsyncCallback.VoidCallback, Object)}
     *
     * @param path the path
     * @throws Exception ZooKeeper errors or timeouts, etc.
     */
    public void sync(String path) throws Exception;

    /**
     * Analog for {@link org.apache.zookeeper.ZooKeeper#setData(String, byte[], int)}
     *
     * @param path the path
     * @param data data to set
     * @throws Exception ZooKeeper errors or timeouts, etc.
     */
    public void setData(String path, byte data[]) throws Exception;

    /**
     * Analog for {@link org.apache.zookeeper.ZooKeeper#delete(String, int)}
     *
     * @param path the path
     * @throws Exception ZooKeeper errors or timeouts, etc.
     */
    public void delete(String path) throws Exception;

    /**
     * Analog for {@link org.apache.zookeeper.ZooKeeper#getData(String, boolean, org.apache.zookeeper.data.Stat)}
     *
     * @param path the path
     * @throws Exception ZooKeeper errors or timeouts, etc.
     * @return the data
     */
    public byte[] getData(String path) throws Exception;

    /**
     * Builder-style method that returns a view of the client with the given create mode. The
     * create mode applies to {@link #create(String, byte[])}
     *
     * @param createMode new create mode
     * @return new instance with the given mode set
     */
    ZookeeperClientHelper withCreateMode(CreateMode createMode);

    /**
     * Builder-style method that returns a view of the client that causes methods that take a callback
     * to use an internal callback that posts messages to the client's event queue
     *
     * @return new instance with background mode set
     * @param key key to identify the event when it comes through the queue
     */
    ZookeeperClientHelper    inBackground(Object key);

    /**
     * Builder-style method that returns a view of the client that passes <code>true</code> for the various
     * Zookeeper methods have a boolean "watch" argument. Changes to the path will generate watch events.
     *
     * @return new instance with background mode set
     */
    ZookeeperClientHelper    watched();

    /**
     * Builder-style method that returns a view of the client that passes a context object
     * to backgrounded methods that allow for context objects. This context will be placed
     * in the event
     *
     * @param contextArg the context to set
     * @return new instance with background mode set
     */
    ZookeeperClientHelper   withContext(Object contextArg);

    /**
     * Builder-style method that returns a view of the client with the given data version set. The default data version is
     * -1. The instance returned will change the data version used in {@link #setData(String, byte[])} and {@link #delete(String)}.
     *
     * @param version new data version
     * @return new instance with the data version set
     */
    ZookeeperClientHelper    dataVersion(int version);
}
