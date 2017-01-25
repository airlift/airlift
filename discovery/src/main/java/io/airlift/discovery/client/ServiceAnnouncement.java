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
import com.google.common.collect.ImmutableMap;
import io.airlift.node.NodeInfo;

import java.util.Map;
import java.util.UUID;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.airlift.discovery.client.ServiceDescriptor.serviceDescriptor;
import static java.util.Objects.requireNonNull;

public class ServiceAnnouncement
{
    private final UUID id = UUID.randomUUID();
    private final String type;
    private final Map<String, String> properties;

    private ServiceAnnouncement(String type, Map<String, String> properties)
    {
        requireNonNull(type, "type is null");
        requireNonNull(properties, "properties is null");

        this.type = type;
        this.properties = ImmutableMap.copyOf(properties);
    }

    @JsonProperty
    public UUID getId()
    {
        return id;
    }

    @JsonProperty
    public String getType()
    {
        return type;
    }

    @JsonProperty
    public Map<String, String> getProperties()
    {
        return properties;
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

        ServiceAnnouncement that = (ServiceAnnouncement) o;

        if (!id.equals(that.id)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return id.hashCode();
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("id", id)
                .add("type", type)
                .add("properties", properties)
                .toString();
    }

    public ServiceDescriptor toServiceDescriptor(NodeInfo nodeInfo)
    {
        return serviceDescriptor(type)
                .setId(id)
                .setNodeInfo(nodeInfo)
                .setLocation(nodeInfo.getLocation())
                .setState(ServiceState.RUNNING)
                .addProperties(properties)
                .build();
    }

    public static ServiceAnnouncementBuilder serviceAnnouncement(String type)
    {
        return new ServiceAnnouncementBuilder(type);
    }

    public static class ServiceAnnouncementBuilder
    {
        private final String type;
        private final ImmutableMap.Builder<String, String> properties = ImmutableMap.builder();

        private ServiceAnnouncementBuilder(String type)
        {
            this.type = type;
        }

        public ServiceAnnouncementBuilder addProperty(String key, String value)
        {
            requireNonNull(key, "key is null");
            requireNonNull(value, "value is null");
            properties.put(key, value);
            return this;
        }

        public ServiceAnnouncementBuilder addProperties(Map<String, String> properties)
        {
            requireNonNull(properties, "properties is null");
            this.properties.putAll(properties);
            return this;
        }

        public ServiceAnnouncement build()
        {
            return new ServiceAnnouncement(type, properties.build());
        }
    }
}

