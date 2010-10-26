package com.proofpoint.crossprocess;

import org.joda.time.DateTime;

/**
 * Bridge between {@link com.proofpoint.crossprocess.ConditionalAction} and cleanup for the action
 */
public interface ConditionalActionDriver
{
    /**
     * Return true if the {@link com.proofpoint.crossprocess.ConditionalAction} should try a cleanup
     *
     * @return true/false
     */
    boolean shouldCleanUp();

    /**
     * Called during cleanup for any actions that have not been committed. This
     * method should do what's necessary to clean up the action if the action is indeed
     * failed. Return true to tell the conditional action to remove the key.
     *
     * @param actionKey the key that is potentially failed
     * @param createTime the time that the key was initially prepared
     * @return true to consider this key cleaned, false to continue to monitor the key
     * @throws Exception errors
     */
    boolean hasBeenCleaned(String actionKey, DateTime createTime) throws Exception;
}
