package com.proofpoint.experimental.discovery.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.Map;
import java.util.UUID;

public class ServiceAnnouncement
{
    private final UUID id = UUID.randomUUID();
    private final String type;
    private final String pool;
    private final Map<String, String> properties;

    private ServiceAnnouncement(
            String type,
            String pool,
            Map<String, String> properties)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(pool, "pool is null");
        Preconditions.checkNotNull(properties, "properties is null");

        this.type = type;
        this.pool = pool;
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
    public String getPool()
    {
        return pool;
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
        final StringBuilder sb = new StringBuilder();
        sb.append("ServiceDescriptor");
        sb.append("{id=").append(id);
        sb.append(", type='").append(type).append('\'');
        sb.append(", pool='").append(pool).append('\'');
        sb.append(", properties=").append(properties);
        sb.append('}');
        return sb.toString();
    }

    public static ServiceAnnouncementBuilder serviceAnnouncement(String type)
    {
        return new ServiceAnnouncementBuilder(type);
    }

    public static class ServiceAnnouncementBuilder
    {
        private final String type;
        private String pool = ServiceSelectorConfig.DEFAULT_POOL;

        private ImmutableMap.Builder<String, String> properties = ImmutableMap.builder();

        private ServiceAnnouncementBuilder(String type)
        {
            this.type = type;
        }

        public ServiceAnnouncementBuilder setPool(String pool)
        {
            Preconditions.checkNotNull(pool, "pool is null");
            this.pool = pool;
            return this;
        }

        public ServiceAnnouncementBuilder addProperty(String key, String value)
        {
            Preconditions.checkNotNull(key, "key is null");
            Preconditions.checkNotNull(value, "value is null");
            properties.put(key, value);
            return this;
        }

        public ServiceAnnouncementBuilder addProperties(Map<String, String> properties)
        {
            Preconditions.checkNotNull(properties, "properties is null");
            this.properties.putAll(properties);
            return this;
        }

        public ServiceAnnouncement build()
        {
            return new ServiceAnnouncement(type, pool, properties.build());
        }
    }
}

