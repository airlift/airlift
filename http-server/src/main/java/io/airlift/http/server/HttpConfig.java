/*
 * Copyright 2010 Proofpoint, Inc.
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
package io.airlift.http.server;

import io.airlift.configuration.Config;
import io.airlift.configuration.LegacyConfig;
import jakarta.validation.constraints.Min;

public class HttpConfig
{
    private int httpPort = 8080;
    private int acceptQueueSize = 8000;
    private Integer httpAcceptorThreads;
    private Integer httpSelectorThreads;

    public int getHttpPort()
    {
        return httpPort;
    }

    @Config("http-server.http.port")
    public HttpConfig setHttpPort(int httpPort)
    {
        this.httpPort = httpPort;
        return this;
    }

    @Min(1)
    public int getAcceptQueueSize()
    {
        return acceptQueueSize;
    }

    @Config("http-server.http.accept-queue-size")
    @LegacyConfig("http-server.accept-queue-size")
    public HttpConfig setAcceptQueueSize(int acceptQueueSize)
    {
        this.acceptQueueSize = acceptQueueSize;
        return this;
    }

    @Min(1)
    public Integer getHttpAcceptorThreads()
    {
        return httpAcceptorThreads;
    }

    @Config("http-server.http.acceptor-threads")
    public HttpConfig setHttpAcceptorThreads(Integer httpAcceptorThreads)
    {
        this.httpAcceptorThreads = httpAcceptorThreads;
        return this;
    }

    @Min(1)
    public Integer getHttpSelectorThreads()
    {
        return httpSelectorThreads;
    }

    @Config("http-server.http.selector-threads")
    public HttpConfig setHttpSelectorThreads(Integer httpSelectorThreads)
    {
        this.httpSelectorThreads = httpSelectorThreads;
        return this;
    }
}
