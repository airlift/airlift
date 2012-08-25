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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import io.airlift.node.NodeInfo;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.Map;
import java.util.UUID;

public class ServiceDescriptor
{
    private final UUID id;
    private final String nodeId;
    private final String type;
    private final String pool;
    private final String location;
    private final ServiceState state;
    private final Map<String, String> properties;

    @JsonCreator
    public ServiceDescriptor(
            @JsonProperty("id") UUID id,
            @JsonProperty("nodeId") String nodeId,
            @JsonProperty("type") String type,
            @JsonProperty("pool") String pool,
            @JsonProperty("location") String location,
            @JsonProperty("state") ServiceState state,
            @JsonProperty("properties") Map<String, String> properties)
    {
        Preconditions.checkNotNull(properties, "properties is null");

        this.id = id;
        this.nodeId = nodeId;
        this.type = type;
        this.pool = pool;
        this.location = location;
        this.state = state;
        this.properties = ImmutableMap.copyOf(properties);
    }

    @JsonProperty
    public UUID getId()
    {
        return id;
    }

    @JsonProperty
    public String getNodeId()
    {
        return nodeId;
    }

    @JsonProperty
    public String getType()
    {
        return type;
    }

    @JsonProperty
    public String getPool()
    {
        return pool;
    }

    @JsonProperty
    public String getLocation()
    {
        return location;
    }

    @JsonProperty
    public ServiceState getState()
    {
        return state;
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

        ServiceDescriptor that = (ServiceDescriptor) o;

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
        final StringBuilder sb = new StringBuilder();
        sb.append("ServiceDescriptor");
        sb.append("{id=").append(id);
        sb.append(", nodeId=").append(nodeId);
        sb.append(", type='").append(type).append('\'');
        sb.append(", pool='").append(pool).append('\'');
        sb.append(", location='").append(location).append('\'');
        sb.append(", state='").append(state).append('\'');
        sb.append(", properties=").append(properties);
        sb.append('}');
        return sb.toString();
    }

    public static ServiceDescriptorBuilder serviceDescriptor(String type)
    {
        Preconditions.checkNotNull(type, "type is null");
        return new ServiceDescriptorBuilder(type);
    }

    public static class ServiceDescriptorBuilder
    {
        private UUID id = UUID.randomUUID();
        private String nodeId;
        private final String type;
        private String pool = ServiceSelectorConfig.DEFAULT_POOL;
        private String location;
        private ServiceState state;

        private final ImmutableMap.Builder<String, String> properties = ImmutableMap.builder();

        private ServiceDescriptorBuilder(String type)
        {
            this.type = type;
        }

        public ServiceDescriptorBuilder setId(UUID id)
        {
            Preconditions.checkNotNull(id, "id is null");
            this.id = id;
            return this;
        }

        public ServiceDescriptorBuilder setNodeInfo(NodeInfo nodeInfo)
        {
            Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
            this.nodeId = nodeInfo.getNodeId();
            this.pool = nodeInfo.getPool();
            return this;
        }

        public ServiceDescriptorBuilder setNodeId(String nodeId)
        {
            Preconditions.checkNotNull(nodeId, "nodeId is null");
            this.nodeId = nodeId;
            return this;
        }


        public ServiceDescriptorBuilder setPool(String pool)
        {
            Preconditions.checkNotNull(pool, "pool is null");
            this.pool = pool;
            return this;
        }

        public ServiceDescriptorBuilder setLocation(String location)
        {
            Preconditions.checkNotNull(location, "location is null");
            this.location = location;
            return this;
        }

        public ServiceDescriptorBuilder setState(ServiceState state)
        {
            Preconditions.checkNotNull(state, "state is null");
            this.state = state;
            return this;
        }

        public ServiceDescriptorBuilder addProperty(String key, String value)
        {
            Preconditions.checkNotNull(key, "key is null");
            Preconditions.checkNotNull(value, "value is null");
            properties.put(key, value);
            return this;
        }

        public ServiceDescriptorBuilder addProperties(Map<String, String> properties)
        {
            Preconditions.checkNotNull(properties, "properties is null");
            this.properties.putAll(properties);
            return this;
        }

        public ServiceDescriptor build()
        {
            return new ServiceDescriptor(id, nodeId, type, pool, location, state, properties.build());
        }
    }
}

