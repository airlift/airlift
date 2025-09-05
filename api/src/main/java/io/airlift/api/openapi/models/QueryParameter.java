package io.airlift.api.openapi.models;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class QueryParameter
        extends Parameter
{
    public QueryParameter()
    {
        setIn("query");
    }
}
