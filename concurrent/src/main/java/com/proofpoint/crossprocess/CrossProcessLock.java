package com.proofpoint.crossprocess;

public interface CrossProcessLock
{
    /**
     * Acquires the lock.
     *
     * <p>If the lock is not available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until the
     * lock has been acquired.
     *
     * @throws Exception errors
     */
    void lock() throws Exception;

    /**
     * Acquires the lock only if it is free at the time of invocation.
     *
     * <p>Acquires the lock if it is available and returns immediately
     * with the value {@code true}.
     * If the lock is not available then this method will return
     * immediately with the value {@code false}.
     * @return {@code true} if the lock was acquired and
     *         {@code false} otherwise
     * @throws Exception errors
     */
    boolean tryLock() throws Exception;

    /**
     * Return true if the lock is currently held by someone 
     *
     * @return true/false
     * @throws Exception errors
     */
    boolean isLocked() throws Exception;

    /**
     * Releases the lock.
     *
     * <p><b>Implementation Considerations</b>
     *
     * <p>A {@code Lock} implementation will usually impose
     * restrictions on which thread can release a lock (typically only the
     * holder of the lock can release it) and may throw
     * an (unchecked) exception if the restriction is violated.
     * Any restrictions and the exception
     * type must be documented by that {@code Lock} implementation.
     * @throws Exception errors
     */
    void unlock() throws Exception;
}
