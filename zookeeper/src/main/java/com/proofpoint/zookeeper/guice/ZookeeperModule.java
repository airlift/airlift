package com.proofpoint.zookeeper.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.zookeeper.DefaultZookeeperClientCreator;
import com.proofpoint.zookeeper.ZookeeperClient;
import com.proofpoint.zookeeper.ZookeeperClientConfig;
import com.proofpoint.zookeeper.ZookeeperClientCreator;

public class ZookeeperModule
    extends AbstractModule
{
    @Override
    protected void configure()
    {
        binder().bind(ZookeeperClientCreator.class).to(DefaultZookeeperClientCreator.class).in(Scopes.SINGLETON);
        binder().bind(ZookeeperClient.class).in(Scopes.SINGLETON);
        ConfigurationModule.bindConfig(binder()).to(ZookeeperClientConfig.class);
    }
}
