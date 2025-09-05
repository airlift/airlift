package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;

@ApiPolyResource(key = "dontReuse", name = "manunited", description = "Glory glory")
public sealed interface PolyResourceWithReusedKey
{
    @SuppressWarnings("unused")
    @ApiResource(name = "ok", description = "ok")
    record ItsOk(@ApiDescription("hi") String ok)
            implements PolyResourceWithReusedKey
    {
    }

    @SuppressWarnings("unused")
    @ApiResource(name = "notOk", description = "ok")
    record ItsNotOk(@ApiDescription("hi") String ok, @ApiDescription("hi") String dontReuse)
            implements PolyResourceWithReusedKey
    {
    }
}
