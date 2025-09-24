package io.airlift.api.servertests.patch;

import io.airlift.api.ApiPatch;
import io.airlift.api.ApiService;
import io.airlift.api.ApiUpdate;
import io.airlift.api.ServiceType;

import java.util.Map;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

@ApiService(type = ServiceType.class, name = "standard", description = "Does standard things")
public class PackageService
{
    @ApiUpdate(description = "Update things")
    public Fields updateThings(ApiPatch<Package> apiPatch)
    {
        Map<String, String> fields = apiPatch.fields().entrySet().stream().collect(toImmutableMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue().apply(String.class))));
        return new Fields(fields);
    }
}
