package com.proofpoint.zookeeper;

import com.google.inject.Inject;
import com.proofpoint.crossprocess.CrossProcessLock;
import com.proofpoint.crossprocess.CrossProcessLockFactory;

public class ZookeeperCrossProcessLockFactory implements CrossProcessLockFactory
{
    private final ZookeeperClient client;

    @Inject
    public ZookeeperCrossProcessLockFactory(ZookeeperClient client)
    {
        this.client = client;
    }

    @Override
    public CrossProcessLock newLock(String path) throws Exception
    {
        return client.newLock(path);
    }

    @Override
    public String makePath(String parent, String child)
    {
        return client.makePath(parent, child);
    }
}
