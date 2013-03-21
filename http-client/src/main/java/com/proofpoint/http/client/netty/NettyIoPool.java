/*
 * Copyright 2013 Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.http.client.netty;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.jboss.netty.channel.socket.nio.NioClientBossPool;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.ThreadNameDeterminer;

import javax.annotation.PreDestroy;

import java.io.Closeable;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.Executors.newCachedThreadPool;

@Beta
public class NettyIoPool
        implements Closeable
{
    private final HashedWheelTimer hashedWheelTimer;
    private final ExecutorService bossExecutor;
    private final ExecutorService workerExecutor;
    private final NioWorkerPool workerPool;
    private final NioClientBossPool bossPool;
    private final String name;

    public NettyIoPool(String name)
    {
        this(name, new NettyIoPoolConfig());
    }

    public NettyIoPool(String name, NettyIoPoolConfig config)
    {
        this.name = checkNotNull(name, "name is null");
        checkNotNull(config, "config is null");

        String prefix = "netty-client-" + name + "-io-";

        this.hashedWheelTimer = new HashedWheelTimer(new ThreadFactoryBuilder().setNameFormat(prefix + "timer-%s").setDaemon(true).build());

        // Give netty infinite thread "sources" for worker and boss.
        // Netty will name the threads and will size the pool appropriately
        this.bossExecutor = newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat(prefix + "boss-%s").setDaemon(true).build());
        this.workerExecutor = newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat(prefix + "worker-%s").setDaemon(true).build());

        this.bossPool = new NioClientBossPool(bossExecutor, config.getIoBossThreads(), hashedWheelTimer, ThreadNameDeterminer.CURRENT);
        this.workerPool = new NioWorkerPool(workerExecutor, config.getIoWorkerThreads(), ThreadNameDeterminer.CURRENT);
    }

    public NioClientBossPool getBossPool()
    {
        return bossPool;
    }

    public NioWorkerPool getWorkerPool()
    {
        return workerPool;
    }

    @PreDestroy
    @Override
    public void close()
    {
        try {
            bossPool.shutdown();
        }
        catch (Exception e) {
            // ignored
        }

        try {
            workerPool.shutdown();
        }
        catch (Exception e) {
            // ignored
        }

        try {
            hashedWheelTimer.stop();
        }
        catch (Exception e) {
            // ignored
        }

        try {
            bossExecutor.shutdownNow();
        }
        catch (Exception e) {
            // ignored
        }

        try {
            workerExecutor.shutdownNow();
        }
        catch (Exception e) {
            // ignored
        }
    }

    @Override
    public String toString()
    {
        return "Netty IO Pool for " + name;
    }
}
