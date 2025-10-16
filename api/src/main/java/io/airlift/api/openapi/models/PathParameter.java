package io.airlift.api.openapi.models;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class PathParameter
        extends Parameter
{
    public PathParameter()
    {
        setIn("path");
        setRequired(true);
    }
}
