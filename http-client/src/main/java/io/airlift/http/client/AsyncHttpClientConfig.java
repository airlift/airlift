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
package io.airlift.http.client;

import com.google.common.annotations.Beta;
import io.airlift.configuration.Config;
import io.airlift.units.DataSize;
import io.airlift.units.DataSize.Unit;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Beta
public class AsyncHttpClientConfig
{
    private int workerThreads = Runtime.getRuntime().availableProcessors() * 4;;
    private DataSize maxContentLength = new DataSize(16, Unit.MEGABYTE);

    @Min(1)
    public int getWorkerThreads()
    {
        return workerThreads;
    }

    @Config("http-client.threads")
    public AsyncHttpClientConfig setWorkerThreads(int workerThreads)
    {
        this.workerThreads = workerThreads;
        return this;
    }

    @NotNull
    public DataSize getMaxContentLength()
    {
        return maxContentLength;
    }

    @Config("http-client.max-content-length")
    public AsyncHttpClientConfig setMaxContentLength(DataSize maxContentLength)
    {
        this.maxContentLength = maxContentLength;
        return this;
    }
}
