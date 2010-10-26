package com.proofpoint.zookeeper;

import com.proofpoint.log.Logger;
import org.apache.hadoop.io.retry.RetryPolicy;
import org.apache.zookeeper.KeeperException;

class BackgroundRetryHandler
{
    private static final Logger         log = Logger.get(BackgroundRetryHandler.class);

    private final ZookeeperClient   client;
    private final RetryPolicy       policy;
    private final Call              proc;

    private int                 retries = 0;

    interface Call
    {
        public void call(BackgroundRetryHandler retryHandler);
    }

    static void       makeAndStart(ZookeeperClient client, RetryPolicy policy, Call proc) throws Exception
    {
        new BackgroundRetryHandler(client, policy, proc).start();
    }

    BackgroundRetryHandler(ZookeeperClient client, RetryPolicy policy, Call proc)
    {
        this.client = client;
        this.policy = policy;
        this.proc = proc;
    }

    void        start() throws Exception
    {
        proc.call(this);
    }

    boolean     handled(int rc)
    {
        KeeperException.Code code = KeeperException.Code.get(rc);
        if ( (code == KeeperException.Code.CONNECTIONLOSS) || (code == KeeperException.Code.OPERATIONTIMEOUT) )
        {
            try
            {
                //noinspection ThrowableResultOfMethodCallIgnored
                if ( policy.shouldRetry(KeeperException.create(code), retries++) )
                {
                    proc.call(this);
                }
                else
                {
                    log.info("Connection lost on retries for call %s", proc.getClass().getName());
                    client.errorConnectionLost();
                }
            }
            catch ( Exception e )
            {
                log.error(e, "for call %s", proc.getClass().getName());
            }
            return true;
        }
        return false;
    }
}
