package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class OpenAPI
{
    private String openapi = "3.0.1";
    private Info info;
    private List<Server> servers;
    private List<SecurityRequirement> security;
    private List<Tag> tags;
    private List<TagGroup> tagGroups;
    private Paths paths;
    private Components components;

    @JsonProperty
    public String getOpenapi()
    {
        return openapi;
    }

    public void setOpenapi(String openapi)
    {
        this.openapi = openapi;
    }

    public OpenAPI openapi(String openapi)
    {
        this.openapi = openapi;
        return this;
    }

    @JsonProperty
    public Components getComponents()
    {
        return components;
    }

    @JsonProperty
    public Info getInfo()
    {
        return info;
    }

    public void setInfo(Info info)
    {
        this.info = info;
    }

    public OpenAPI info(Info info)
    {
        this.info = info;
        return this;
    }

    @JsonProperty
    public List<Server> getServers()
    {
        return servers;
    }

    public OpenAPI schema(String name, Schema schema)
    {
        if (components == null) {
            this.components = new Components();
        }
        components.addSchemas(name, schema);
        return this;
    }

    public OpenAPI schemaRequirement(String name, SecurityScheme securityScheme)
    {
        if (components == null) {
            this.components = new Components();
        }
        components.addSecuritySchemes(name, securityScheme);
        return this;
    }

    public void setServers(List<Server> servers)
    {
        this.servers = servers;
    }

    public OpenAPI servers(List<Server> servers)
    {
        this.servers = servers;
        return this;
    }

    public OpenAPI addServersItem(Server serversItem)
    {
        if (this.servers == null) {
            this.servers = new ArrayList<>();
        }
        this.servers.add(serversItem);
        return this;
    }

    @JsonProperty
    public List<SecurityRequirement> getSecurity()
    {
        return security;
    }

    public void setSecurity(List<SecurityRequirement> security)
    {
        this.security = security;
    }

    public OpenAPI security(List<SecurityRequirement> security)
    {
        this.security = security;
        return this;
    }

    public OpenAPI addSecurityItem(SecurityRequirement securityItem)
    {
        if (this.security == null) {
            this.security = new ArrayList<>();
        }
        this.security.add(securityItem);
        return this;
    }

    @JsonProperty("x-tagGroups")
    public List<TagGroup> getTagGroups()
    {
        return tagGroups;
    }

    public void setTagGroups(List<TagGroup> tagGroups)
    {
        this.tagGroups = tagGroups;
    }

    public OpenAPI tagGroups(List<TagGroup> tagGroups)
    {
        this.tagGroups = tagGroups;
        return this;
    }

    @JsonProperty
    public List<Tag> getTags()
    {
        return tags;
    }

    public void setTags(List<Tag> tags)
    {
        this.tags = tags;
    }

    public OpenAPI tags(List<Tag> tags)
    {
        this.tags = tags;
        return this;
    }

    public OpenAPI addTagsItem(Tag tagsItem)
    {
        if (this.tags == null) {
            this.tags = new ArrayList<>();
        }
        this.tags.add(tagsItem);
        return this;
    }

    @JsonProperty
    public Paths getPaths()
    {
        return paths;
    }

    public void setPaths(Paths paths)
    {
        this.paths = paths;
    }

    public OpenAPI paths(Paths paths)
    {
        this.paths = paths;
        return this;
    }

    public OpenAPI path(String name, PathItem path)
    {
        if (this.paths == null) {
            this.paths = new Paths();
        }

        this.paths.addPathItem(name, path);
        return this;
    }
}
