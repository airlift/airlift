package com.proofpoint.zookeeper;

import com.proofpoint.log.Logger;

public class DefaultZookeeperClientErrorHandler implements ZookeeperClientErrorHandler
{
    private static final Logger     log = Logger.get(DefaultZookeeperClientErrorHandler.class);

    @Override
    public void connectionLost(ZookeeperClient client)
    {
        log.error("Connection lost to Zookeeper! Exiting.");
        client.setZombie();
        System.exit(-1);
    }
}
