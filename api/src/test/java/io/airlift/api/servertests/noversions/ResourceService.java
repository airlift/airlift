package io.airlift.api.servertests.noversions;

import io.airlift.api.ApiService;
import io.airlift.api.ApiUpdate;

@ApiService(type = NoVersionType.class, name = "standard", description = "Does standard things")
public class ResourceService
{
    @ApiUpdate(description = "no version update")
    public void update(ResourceWithoutVersion ignore)
    {
    }
}
