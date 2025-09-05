package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class OAuthFlow
{
    private String authorizationUrl;
    private String tokenUrl;
    private String refreshUrl;
    private Scopes scopes;

    @JsonProperty
    public String getAuthorizationUrl()
    {
        return authorizationUrl;
    }

    public void setAuthorizationUrl(String authorizationUrl)
    {
        this.authorizationUrl = authorizationUrl;
    }

    public OAuthFlow authorizationUrl(String authorizationUrl)
    {
        this.authorizationUrl = authorizationUrl;
        return this;
    }

    @JsonProperty
    public String getTokenUrl()
    {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl)
    {
        this.tokenUrl = tokenUrl;
    }

    public OAuthFlow tokenUrl(String tokenUrl)
    {
        this.tokenUrl = tokenUrl;
        return this;
    }

    @JsonProperty
    public String getRefreshUrl()
    {
        return refreshUrl;
    }

    public void setRefreshUrl(String refreshUrl)
    {
        this.refreshUrl = refreshUrl;
    }

    public OAuthFlow refreshUrl(String refreshUrl)
    {
        this.refreshUrl = refreshUrl;
        return this;
    }

    @JsonProperty
    public Scopes getScopes()
    {
        return scopes;
    }

    public void setScopes(Scopes scopes)
    {
        this.scopes = scopes;
    }

    public OAuthFlow scopes(Scopes scopes)
    {
        this.scopes = scopes;
        return this;
    }
}
