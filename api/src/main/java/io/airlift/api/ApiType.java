package io.airlift.api;

public enum ApiType
{
    GET("GET"),
    LIST("GET"),
    CREATE("POST"),
    DELETE("DELETE"),
    UPDATE("PUT", "PATCH");

    public String httpMethod(boolean useAltMethod)
    {
        return useAltMethod ? altHttpMethod : httpMethod;
    }

    private final String httpMethod;
    private final String altHttpMethod;

    ApiType(String httpMethod)
    {
        this(httpMethod, httpMethod);
    }

    ApiType(String httpMethod, String altHttpMethod)
    {
        this.httpMethod = httpMethod;
        this.altHttpMethod = altHttpMethod;
    }
}
