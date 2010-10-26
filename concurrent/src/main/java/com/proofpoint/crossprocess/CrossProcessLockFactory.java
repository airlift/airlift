package com.proofpoint.crossprocess;

public interface CrossProcessLockFactory
{
    /**
     * Allocate a new lock at the given path.
     *
     * @param path lock path - the meaning of the path is implementation dependent
     * @return the lock
     * @throws Exception errors
     */
    public CrossProcessLock newLock(String path) throws Exception;

    /**
     * Create a new path based on parent child
     *
     * @param parent the parent path
     * @param child the child name
     * @return new path
     */
    public String makePath(String parent, String child);
}
