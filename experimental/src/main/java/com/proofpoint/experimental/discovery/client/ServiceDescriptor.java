package com.proofpoint.experimental.discovery.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.node.NodeInfo;
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
    private final Map<String, String> properties;

    @JsonCreator
    public ServiceDescriptor(
            @JsonProperty("id") UUID id,
            @JsonProperty("nodeId") String nodeId,
            @JsonProperty("type") String type,
            @JsonProperty("pool") String pool,
            @JsonProperty("location") String location,
            @JsonProperty("properties") Map<String, String> properties)
    {
        Preconditions.checkNotNull(id, "id is null");
        Preconditions.checkNotNull(nodeId, "nodeId is null");
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(pool, "pool is null");
        Preconditions.checkNotNull(properties, "properties is null");

        this.id = id;
        this.nodeId = nodeId;
        this.type = type;
        this.pool = pool;
        this.location = location;
        this.properties = ImmutableMap.copyOf(properties);
    }

    public UUID getId()
    {
        return id;
    }

    public String getNodeId()
    {
        return nodeId;
    }

    public String getType()
    {
        return type;
    }

    public String getPool()
    {
        return pool;
    }

    public String getLocation()
    {
        return location;
    }

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
        private String type;
        private String pool = ServiceSelectorConfig.DEFAULT_POOL;
        private String location;

        private ImmutableMap.Builder<String, String> properties = ImmutableMap.builder();

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
            return new ServiceDescriptor(id, nodeId, type, pool, location, properties.build());
        }
    }
}

