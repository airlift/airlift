package io.airlift.api;

public enum ApiOpenApiTrait
{
    /**
     * By default, Airlift use `allOf` when creating polymorphic schema discriminators.
     * Many OpenAPI tools have known bugs with this. Use this trait to have Airlift use `oneOf` instead.
     * Note that this will cause the generated OpenAPI to not support highly recursive polymorphic schemas,
     * so it should only be used when necessary to work around tooling bugs.
     */
    USE_ONE_OF_DISCRIMINATORS,
}
