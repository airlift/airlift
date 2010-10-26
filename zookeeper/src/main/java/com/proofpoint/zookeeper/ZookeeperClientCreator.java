package com.proofpoint.zookeeper;

import org.apache.hadoop.io.retry.RetryPolicy;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.ZooKeeper;

import java.util.List;

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
     * @return whether connection was successful or not
     * @throws InterruptedException connection was interrupted
     */
    public ConnectionStatus       waitForStart() throws InterruptedException;

    /**
     * Return any events that came through the watcher during start up. The list of
     * pending events should be cleared by this method. i.e. a second call to this method would return
     * an empty list if no new events had been posted.
     *
     * @return list of events (can be empty)
     */
    public List<WatchedEvent>   getPendingEvents();

    /**
     * Return the retry policy to use for connection losses
     *
     * @return the policy
     */
    public RetryPolicy          getRetryPolicy();
}
