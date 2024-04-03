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
import com.google.common.collect.ImmutableSet;

import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public record Announcement(
        @JsonProperty
        String environment,
        @JsonProperty
        String nodeId,
        @JsonProperty
        String pool,
        @JsonProperty
        String location,
        @JsonProperty
        Set<ServiceAnnouncement> services)
{
    public Announcement
    {
        requireNonNull(environment, "environment is null");
        requireNonNull(nodeId, "nodeId is null");
        requireNonNull(services, "services is null");
        requireNonNull(pool, "pool is null");
        checkArgument(!services.isEmpty(), "services is empty");
        services = ImmutableSet.copyOf(services);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Announcement that)) {
            return false;
        }

        return Objects.equals(nodeId, that.nodeId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(nodeId);
    }
}
