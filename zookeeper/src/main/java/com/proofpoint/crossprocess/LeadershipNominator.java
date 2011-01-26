package com.proofpoint.crossprocess;

public interface LeadershipNominator
{
    /**
     * Set the notifier to be called when leadership is obtained
     *
     * @param n notifier
     */
    void     setNotifier(LeadershipNominatorNotifier n);

    /**
     * Returns the currently set notifier
     *
     * @return notifier
     */
    LeadershipNominatorNotifier getNotifier();

    /**
     * Start the nominator
     */
    void        start();

    /**
     * Shutdown the group. Interrupt the current leader.
     */
    void        stop();

    /**
     * Returns true if this instance currently has leadership
     *
     * @return true/false
     */
    boolean     hasLeadership();
}
