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
package com.proofpoint.http.client;

import com.google.common.annotations.Beta;
import com.proofpoint.configuration.Config;

import javax.validation.constraints.Min;

@Beta
public class AsyncHttpClientConfig
{
    private int workerThreads = 16;

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
}
