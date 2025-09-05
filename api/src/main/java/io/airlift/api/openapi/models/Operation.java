package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class Operation
{
    private List<String> tags;
    private String summary;
    private String description;
    private String operationId;
    private List<Parameter> parameters;
    private RequestBody requestBody;
    private ApiResponses responses;
    private Boolean deprecated;
    private List<SecurityRequirement> security;
    @JsonUnwrapped public MapWrapper extensions;

    // https://claude.ai/chat/e32e2c16-53d8-4c86-a6b1-8654d5da1149
    public static class MapWrapper
    {
        private final Map<String, Object> properties = new HashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getProperties()
        {
            return properties;
        }

        @JsonAnySetter
        public void add(String key, Object value)
        {
            properties.put(key, value);
        }
    }

    @JsonProperty
    public List<String> getTags()
    {
        return tags;
    }

    public void setTags(List<String> tags)
    {
        this.tags = tags;
    }

    public Operation tags(List<String> tags)
    {
        this.tags = tags;
        return this;
    }

    public Operation addTagsItem(String tagsItem)
    {
        if (this.tags == null) {
            this.tags = new ArrayList<>();
        }
        this.tags.add(tagsItem);
        return this;
    }

    @JsonProperty
    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public Operation summary(String summary)
    {
        this.summary = summary;
        return this;
    }

    @JsonProperty
    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public Operation description(String description)
    {
        this.description = description;
        return this;
    }

    @JsonProperty
    public String getOperationId()
    {
        return operationId;
    }

    public void setOperationId(String operationId)
    {
        this.operationId = operationId;
    }

    public Operation operationId(String operationId)
    {
        this.operationId = operationId;
        return this;
    }

    @JsonProperty
    public List<Parameter> getParameters()
    {
        return parameters;
    }

    public void setParameters(List<Parameter> parameters)
    {
        this.parameters = parameters;
    }

    public Operation parameters(List<Parameter> parameters)
    {
        this.parameters = parameters;
        return this;
    }

    public Operation addParametersItem(Parameter parametersItem)
    {
        if (this.parameters == null) {
            this.parameters = new ArrayList<>();
        }
        this.parameters.add(parametersItem);
        return this;
    }

    @JsonProperty
    public RequestBody getRequestBody()
    {
        return requestBody;
    }

    public void setRequestBody(RequestBody requestBody)
    {
        this.requestBody = requestBody;
    }

    public Operation requestBody(RequestBody requestBody)
    {
        this.requestBody = requestBody;
        return this;
    }

    @JsonProperty
    public ApiResponses getResponses()
    {
        return responses;
    }

    public void setResponses(ApiResponses responses)
    {
        this.responses = responses;
    }

    public Operation responses(ApiResponses responses)
    {
        this.responses = responses;
        return this;
    }

    @JsonProperty
    public Boolean getDeprecated()
    {
        return deprecated;
    }

    public void setDeprecated(Boolean deprecated)
    {
        this.deprecated = deprecated;
    }

    public Operation deprecated(Boolean deprecated)
    {
        this.deprecated = deprecated;
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

    public Operation security(List<SecurityRequirement> security)
    {
        this.security = security;
        return this;
    }

    public Operation addSecurityItem(SecurityRequirement securityItem)
    {
        if (this.security == null) {
            this.security = new ArrayList<>();
        }
        this.security.add(securityItem);
        return this;
    }

    public Map<String, Object> getExtensions()
    {
        return extensions.properties;
    }

    public void addExtension(String name, Object value)
    {
        if (name == null || !name.startsWith("x-")) {
            return;
        }
        if (this.extensions == null) {
            this.extensions = new MapWrapper();
        }
        this.extensions.properties.put(name, value);
    }
}
