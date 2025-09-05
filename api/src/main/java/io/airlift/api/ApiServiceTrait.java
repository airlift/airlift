package io.airlift.api;

public enum ApiServiceTrait
{
    USES_VERSIONED_RESOURCES,
    REQUIRES_RESOURCE_IDS,
    QUOTAS_REQUIRED,
    ENUMS_AS_STRINGS,
    DESCRIPTIONS_REQUIRED,
}
