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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

import static com.google.common.base.MoreObjects.toStringHelper;

public class Announcement
{
    private final String environment;
    private final String nodeId;
    private final String location;
    private final String pool;
    private final Set<ServiceAnnouncement> services;

    public Announcement(String environment, String nodeId, String pool, String location, Set<ServiceAnnouncement> services)
    {
        Preconditions.checkNotNull(environment, "environment is null");
        Preconditions.checkNotNull(nodeId, "nodeId is null");
        Preconditions.checkNotNull(services, "services is null");
        Preconditions.checkNotNull(pool, "pool is null");
        Preconditions.checkArgument(!services.isEmpty(), "services is empty");

        this.environment = environment;
        this.nodeId = nodeId;
        this.location = location;
        this.pool = pool;
        this.services = ImmutableSet.copyOf(services);
    }

    @JsonProperty
    public String getEnvironment()
    {
        return environment;
    }

    @JsonProperty
    public String getNodeId()
    {
        return nodeId;
    }

    @JsonProperty
    public String getLocation()
    {
        return location;
    }

    @JsonProperty
    public String getPool()
    {
        return pool;
    }

    @JsonProperty
    public Set<ServiceAnnouncement> getServices()
    {
        return services;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Announcement that = (Announcement) o;

        if (!nodeId.equals(that.nodeId)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return nodeId.hashCode();
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("environment", environment)
                .add("nodeId", nodeId)
                .add("location", location)
                .add("pool", pool)
                .add("services", services)
                .toString();
    }
}
