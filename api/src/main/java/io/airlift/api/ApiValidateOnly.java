package io.airlift.api;

public record ApiValidateOnly(boolean requested)
{
    public static final String VALIDATE_ONLY_PARAMETER_NAME = "validateOnly";
}
