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
package io.airlift.discovery.client;

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.units.Duration;

import javax.validation.constraints.NotNull;

import java.net.URI;
import java.util.concurrent.TimeUnit;

public class ServiceInventoryConfig
{
    private URI serviceInventoryUri;
    private Duration updateInterval = new Duration(10, TimeUnit.SECONDS);

    public URI getServiceInventoryUri()
    {
        return serviceInventoryUri;
    }

    @Config("service-inventory.uri")
    @ConfigDescription("Service inventory base URI")
    public ServiceInventoryConfig setServiceInventoryUri(URI uri)
    {
        this.serviceInventoryUri = uri;
        return this;
    }

    @NotNull
    public Duration getUpdateInterval()
    {
        return updateInterval;
    }

    @Config("service-inventory.update-interval")
    @ConfigDescription("Service inventory update interval")
    public ServiceInventoryConfig setUpdateInterval(Duration updateInterval)
    {
        this.updateInterval = updateInterval;
        return this;
    }
}
