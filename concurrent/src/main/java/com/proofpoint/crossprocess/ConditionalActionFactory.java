package com.proofpoint.crossprocess;

/**
 * Factory for making conditional actions
 */
public interface ConditionalActionFactory
{
    /**
     * @param conditionalActionPath ZPath used by the conditional action to store values
     * @param actionKey a unique value that represents this action
     * @param driver bridge to cleanup activities
     * @return the new conditional action
     * @throws Exception errors
     */
    public ConditionalAction    newConditionalAction(String conditionalActionPath, String actionKey, ConditionalActionDriver driver) throws Exception;

    /**
     * Cleanups happen automatically, However, you can force a cleanup check by calling this method
     *
     * @param conditionalActionPath ZPath used by the conditional action to store values
     * @param driver bridge to cleanup activities
     * @throws Exception errors
     */
    public void     forceCleanup(String conditionalActionPath, ConditionalActionDriver driver) throws Exception;
}
