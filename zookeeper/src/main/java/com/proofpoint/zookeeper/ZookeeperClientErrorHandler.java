package com.proofpoint.zookeeper;

public interface ZookeeperClientErrorHandler
{
    public void     connectionLost(ZookeeperClient client);
}
