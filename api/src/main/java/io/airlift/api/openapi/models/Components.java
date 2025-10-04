package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class Components
{
    private Map<String, SecurityScheme> securitySchemes;
    private Map<String, Schema> schemas;

    @JsonProperty
    public Map<String, SecurityScheme> getSecuritySchemes()
    {
        return securitySchemes;
    }

    public void setSecuritySchemes(Map<String, SecurityScheme> securitySchemes)
    {
        this.securitySchemes = securitySchemes;
    }

    public Components securitySchemes(Map<String, SecurityScheme> securitySchemes)
    {
        this.securitySchemes = securitySchemes;
        return this;
    }

    public Components addSecuritySchemes(String key, SecurityScheme securitySchemesItem)
    {
        if (this.securitySchemes == null) {
            this.securitySchemes = new LinkedHashMap<>();
        }
        this.securitySchemes.put(key, securitySchemesItem);
        return this;
    }

    @JsonProperty
    public Map<String, Schema> getSchemas()
    {
        return schemas;
    }

    public void setSchemas(Map<String, Schema> schemas)
    {
        this.schemas = schemas;
    }

    public Components schemas(Map<String, Schema> schemas)
    {
        this.schemas = schemas;
        return this;
    }

    public Components addSchemas(String key, Schema schemasItem)
    {
        if (this.schemas == null) {
            this.schemas = new LinkedHashMap<>();
        }
        this.schemas.put(key, schemasItem);
        return this;
    }
}
