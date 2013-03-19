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
import com.proofpoint.configuration.Config;

import javax.validation.constraints.Min;

@Beta
public class NettyIoPoolConfig
{
    private int ioBossThreads = 1;
    private int ioWorkerThreads = Runtime.getRuntime().availableProcessors() * 2;

    @Min(1)
    public int getIoBossThreads()
    {
        return ioBossThreads;
    }

    @Config("http-client.shared-io-boss-threads")
    public NettyIoPoolConfig setIoBossThreads(int ioBossThreads)
    {
        this.ioBossThreads = ioBossThreads;
        return this;
    }

    @Min(2)
    public int getIoWorkerThreads()
    {
        return ioWorkerThreads;
    }

    @Config("http-client.shared-io-worker-threads")
    public NettyIoPoolConfig setIoWorkerThreads(int ioWorkerThreads)
    {
        this.ioWorkerThreads = ioWorkerThreads;
        return this;
    }
}
