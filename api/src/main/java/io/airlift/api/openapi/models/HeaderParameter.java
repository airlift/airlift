package io.airlift.api.openapi.models;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class HeaderParameter
        extends Parameter
{
    public HeaderParameter()
    {
        setIn("header");
    }
}
