package com.proofpoint.experimental.discovery.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
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
        Preconditions.checkNotNull(location, "location is null");
        Preconditions.checkNotNull(properties, "properties is null");

        this.id = id;
        this.nodeId = nodeId;
        this.type = type;
        this.pool = pool;
        this.location = location;
        this.properties = ImmutableMap.copyOf(properties);
    }

    @JsonProperty
    public UUID getId()
    {
        return id;
    }

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

    public String getLocation()
    {
        return location;
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
        sb.append(", properties=").append(properties);
        sb.append('}');
        return sb.toString();
    }
}

