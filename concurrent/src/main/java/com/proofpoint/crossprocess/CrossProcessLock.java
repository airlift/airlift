package com.proofpoint.crossprocess;

import java.util.concurrent.locks.Lock;

public interface CrossProcessLock extends Lock
{
    /**
     * Return true if the lock is currently held by someone 
     *
     * @return true/false
     * @throws Exception errors
     */
    boolean isLocked() throws Exception;
}
