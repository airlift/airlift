package com.proofpoint.zookeeper;

import org.apache.hadoop.io.retry.RetryPolicy;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

/**
 * Responsible for allocating the raw ZooKeeper instance used by {@link com.proofpoint.zookeeper.ZookeeperClient}
 */
public interface ZookeeperClientCreator
{
    /**
     * Create and return a new ZooKeeper instance
     *
     * @return instance
     * @throws Exception errors
     */
    public ZooKeeper    create() throws Exception;

    public enum ConnectionStatus
    {
        SUCCESS,
        FAILED
    }

    /**
     * Must block until the client has successfully/unsuccessfully connected.
     *
     * @param client the client that was returned from {@link #create()}
     * @param watcher the watcher to substitute once connection succeeds
     * @return whether connection was successful or not
     * @throws InterruptedException connection was interrupted
     */
    public ConnectionStatus       waitForStart(ZooKeeper client, Watcher watcher) throws InterruptedException;

    /**
     * Return the retry policy to use for connection losses
     *
     * @return the policy
     */
    public RetryPolicy          getRetryPolicy();
}
