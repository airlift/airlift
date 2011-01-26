package com.proofpoint.crossprocess;

public interface CrossProcessLockFactory
{
    /**
     * Allocate a new lock with the given name.
     *
     * @param name lock name
     * @return the lock
     * @throws Exception errors
     */
    public CrossProcessLock newLock(String name) throws Exception;
}
