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
        /**
         * Connection succeeded
         */
        SUCCESS,

        /**
         * Connection failed due to an invalid session, try re-connecting with a fresh session
         */
        INVALID_SESSION,

        /**
         * General connection failure
         */
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
     * If {@link #waitForStart(org.apache.zookeeper.ZooKeeper, org.apache.zookeeper.Watcher)} fails with the status
     * {@link ConnectionStatus#INVALID_SESSION}, you can call this method. Once called, you should re-call {@link #create()}.
     * It will initiate a connection with a new session.
     * 
     * @return instance
     * @throws Exception errors
     */
    public ZooKeeper             recreateWithNewSession() throws Exception;

    /**
     * Return the retry policy to use for connection losses
     *
     * @return the policy
     */
    public RetryPolicy          getRetryPolicy();
}
