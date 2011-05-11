package com.proofpoint.discovery.client;

import com.google.common.base.Preconditions;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.Set;

public class Announcement
{
    private final String environment;
    private final String nodeId;
    private final String location;
    private final Set<ServiceAnnouncement> services;

    public Announcement(String environment, String nodeId, String location, Set<ServiceAnnouncement> services)
    {
        Preconditions.checkNotNull(environment, "environment is null");
        Preconditions.checkNotNull(nodeId, "nodeId is null");
        Preconditions.checkNotNull(services, "services is null");
        Preconditions.checkArgument(!services.isEmpty(), "services is empty");

        this.environment = environment;
        this.nodeId = nodeId;
        this.location = location;
        this.services = services;
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
        final StringBuilder sb = new StringBuilder();
        sb.append("Announcement");
        sb.append("{nodeId=").append(nodeId);
        sb.append(", location='").append(location).append('\'');
        sb.append(", services=").append(services);
        sb.append('}');
        return sb.toString();
    }
}
