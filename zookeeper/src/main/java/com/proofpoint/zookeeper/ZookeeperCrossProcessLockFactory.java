package com.proofpoint.zookeeper;

import com.google.inject.Inject;
import com.proofpoint.crossprocess.CrossProcessLock;
import com.proofpoint.crossprocess.CrossProcessLockFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

public class ZookeeperCrossProcessLockFactory implements CrossProcessLockFactory
{
    private final ZookeeperClient client;
    private final String namespacePath;

    @Target(ElementType.PARAMETER)
    @Retention(RUNTIME)
    public @interface NamespacePath
    {
    }

    @Inject
    public ZookeeperCrossProcessLockFactory(ZookeeperClient client, @NamespacePath String namespacePath)
    {
        this.client = client;
        this.namespacePath = namespacePath;
    }

    @Override
    public CrossProcessLock newLock(String name)
            throws Exception
    {
        return client.newLock(client.makePath(namespacePath, name));
    }
}
