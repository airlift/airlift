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

import static java.lang.String.format;

// TODO move to discovery client?
public class ServiceUnavailableException
    extends RuntimeException
{
    private final String service;
    private final String pool;

    public ServiceUnavailableException(String type, String pool)
    {
        super(format("Service type=[%s], pool=[%s] is not available", type, pool));
        this.service = type;
        this.pool = pool;
    }

    public String getType()
    {
        return service;
    }

    public String getPool()
    {
        return pool;
    }
}
