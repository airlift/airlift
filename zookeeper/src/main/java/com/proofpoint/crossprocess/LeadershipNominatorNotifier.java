package com.proofpoint.crossprocess;

/**
 * Callback interface
 */
public interface LeadershipNominatorNotifier
{
    /**
     * Called when you've gained leadership. You will have leadership until
     * you relinquish it by returning from this method. You should respond to the
     * thread getting interrupted as well
     *
     * @throws InterruptedException if you are interrupted
     */
    void    takeLeadership() throws InterruptedException;

    /**
     * Called after leadership is lost
     */
    void    lostLeadership();
}
