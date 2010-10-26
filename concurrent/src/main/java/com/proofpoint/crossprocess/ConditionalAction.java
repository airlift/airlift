package com.proofpoint.crossprocess;

/**
 * Abstracts a conditional action. Sample use case: you need to write
 * 2 files and if there is a failure you want to clean up the first file sometime in the future.
 */
public interface ConditionalAction
{
    /**
     * Call this method before you begin the action. If the ZookeeperConditionalActionDriver
     * returns true for {@link ConditionalActionDriver#shouldCleanUp()} an
     * attempt will be made to cleanup old failed actions
     *
     * @throws Exception Zookeeper errors
     */
    void     prepare() throws Exception;

    /**
     * Call once the action has been successfully completed
     *
     * @throws Exception Zookeeper errors
     */
    void     commit() throws Exception;

    /**
     * Normally, cleanups occur automatically. Call this method
     * to force a clean up check
     *
     * @throws Exception errors
     */
    void    cleanup() throws Exception;
}
