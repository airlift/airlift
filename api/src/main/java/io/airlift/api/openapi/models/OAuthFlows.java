package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class OAuthFlows
{
    private OAuthFlow implicit;
    private OAuthFlow password;
    private OAuthFlow clientCredentials;
    private OAuthFlow authorizationCode;

    @JsonProperty
    public OAuthFlow getImplicit()
    {
        return implicit;
    }

    public void setImplicit(OAuthFlow implicit)
    {
        this.implicit = implicit;
    }

    public OAuthFlows implicit(OAuthFlow implicit)
    {
        this.implicit = implicit;
        return this;
    }

    @JsonProperty
    public OAuthFlow getPassword()
    {
        return password;
    }

    public void setPassword(OAuthFlow password)
    {
        this.password = password;
    }

    public OAuthFlows password(OAuthFlow password)
    {
        this.password = password;
        return this;
    }

    @JsonProperty
    public OAuthFlow getClientCredentials()
    {
        return clientCredentials;
    }

    public void setClientCredentials(OAuthFlow clientCredentials)
    {
        this.clientCredentials = clientCredentials;
    }

    public OAuthFlows clientCredentials(OAuthFlow clientCredentials)
    {
        this.clientCredentials = clientCredentials;
        return this;
    }

    @JsonProperty
    public OAuthFlow getAuthorizationCode()
    {
        return authorizationCode;
    }

    public void setAuthorizationCode(OAuthFlow authorizationCode)
    {
        this.authorizationCode = authorizationCode;
    }

    public OAuthFlows authorizationCode(OAuthFlow authorizationCode)
    {
        this.authorizationCode = authorizationCode;
        return this;
    }
}
