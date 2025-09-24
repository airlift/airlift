package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class SecurityScheme
{
    public enum Type
    {
        APIKEY("apiKey"),
        HTTP("http"),
        OAUTH2("oauth2"),
        OPENIDCONNECT("openIdConnect"),
        MUTUALTLS("mutualTLS");

        private final String value;

        Type(String value)
        {
            this.value = value;
        }

        @JsonValue
        @Override
        public String toString()
        {
            return value;
        }
    }

    public enum In
    {
        COOKIE("cookie"),

        HEADER("header"),

        QUERY("query");

        private final String value;

        In(String value)
        {
            this.value = value;
        }

        @JsonValue
        @Override
        public String toString()
        {
            return value;
        }
    }

    private Type type;
    private String description;
    private String name;
    private String $ref;
    private In in;
    private String scheme;
    private String bearerFormat;
    private OAuthFlows flows;
    private String openIdConnectUrl;

    @JsonProperty
    public Type getType()
    {
        return type;
    }

    public void setType(Type type)
    {
        this.type = type;
    }

    public SecurityScheme type(Type type)
    {
        this.type = type;
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

    public SecurityScheme description(String description)
    {
        this.description = description;
        return this;
    }

    @JsonProperty
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public SecurityScheme name(String name)
    {
        this.name = name;
        return this;
    }

    @JsonProperty
    public In getIn()
    {
        return in;
    }

    public void setIn(In in)
    {
        this.in = in;
    }

    public SecurityScheme in(In in)
    {
        this.in = in;
        return this;
    }

    @JsonProperty
    public String getScheme()
    {
        return scheme;
    }

    public void setScheme(String scheme)
    {
        this.scheme = scheme;
    }

    public SecurityScheme scheme(String scheme)
    {
        this.scheme = scheme;
        return this;
    }

    @JsonProperty
    public String getBearerFormat()
    {
        return bearerFormat;
    }

    public void setBearerFormat(String bearerFormat)
    {
        this.bearerFormat = bearerFormat;
    }

    public SecurityScheme bearerFormat(String bearerFormat)
    {
        this.bearerFormat = bearerFormat;
        return this;
    }

    @JsonProperty
    public OAuthFlows getFlows()
    {
        return flows;
    }

    public void setFlows(OAuthFlows flows)
    {
        this.flows = flows;
    }

    public SecurityScheme flows(OAuthFlows flows)
    {
        this.flows = flows;
        return this;
    }

    @JsonProperty
    public String getOpenIdConnectUrl()
    {
        return openIdConnectUrl;
    }

    public void setOpenIdConnectUrl(String openIdConnectUrl)
    {
        this.openIdConnectUrl = openIdConnectUrl;
    }

    public SecurityScheme openIdConnectUrl(String openIdConnectUrl)
    {
        this.openIdConnectUrl = openIdConnectUrl;
        return this;
    }

    @JsonProperty
    public String get$ref()
    {
        return $ref;
    }

    public void set$ref(String $ref)
    {
        if ($ref != null && ($ref.indexOf('.') == -1 && $ref.indexOf('/') == -1)) {
            $ref = "#/components/securitySchemes/" + $ref;
        }
        this.$ref = $ref;
    }

    public SecurityScheme $ref(String $ref)
    {
        set$ref($ref);
        return this;
    }
}
