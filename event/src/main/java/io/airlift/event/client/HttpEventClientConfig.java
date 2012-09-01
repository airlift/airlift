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
package io.airlift.event.client;

import com.google.common.base.Preconditions;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.units.Duration;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class HttpEventClientConfig
{
    private int maxConnections = -1;
    private Duration connectTimeout = new Duration(50, MILLISECONDS);
    private Duration requestTimeout = new Duration(60, SECONDS);
    private boolean compress = false;
    private int jsonVersion = 2;

    @Deprecated
    public int getMaxConnections()
    {
        return maxConnections;
    }

    @Deprecated
    @Config("collector.max-connections")
    public HttpEventClientConfig setMaxConnections(int maxConnections)
    {
        this.maxConnections = maxConnections;
        return this;
    }

    @Deprecated
    public Duration getConnectTimeout()
    {
        return connectTimeout;
    }

    @Deprecated
    @Config("collector.connect-timeout")
    public HttpEventClientConfig setConnectTimeout(Duration connectTimeout)
    {
        Preconditions.checkNotNull(connectTimeout, "connectionTimeout");
        if (connectTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("Connection timeout must be greater than 0");
        }
        this.connectTimeout = connectTimeout;
        return this;
    }

    @Deprecated
    public Duration getRequestTimeout()
    {
        return requestTimeout;
    }

    @Deprecated
    @Config("collector.request-timeout")
    public HttpEventClientConfig setRequestTimeout(Duration requestTimeout)
    {
        Preconditions.checkNotNull(requestTimeout, "requestTimeout");
        if (requestTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("Request timeout must be greater than 0");
        }
        this.requestTimeout = requestTimeout;
        return this;
    }

    @Deprecated
    public boolean isCompress()
    {
        return compress;
    }

    @Deprecated
    @Config("collector.compress")
    public HttpEventClientConfig setCompress(boolean compress)
    {
        this.compress = compress;
        return this;
    }

    @Deprecated
    @Min(1)
    @Max(2)
    public int getJsonVersion()
    {
        return jsonVersion;
    }

    @Deprecated
    @Config("collector.json-version")
    @ConfigDescription("JSON format version supported by collector")
    public HttpEventClientConfig setJsonVersion(int jsonVersion)
    {
        this.jsonVersion = jsonVersion;
        return this;
    }
}
