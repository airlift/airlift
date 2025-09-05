package io.airlift.api.servertests.filters;

import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiList;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiRequestFilter;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiResponseFilter;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

import java.util.List;

@ApiService(type = ServiceType.class, name = "standard", description = "Does standard things")
public class FilterService
{
    @ApiGet(description = "Get things")
    @ApiRequestFilter(TestFilter.class)
    public Thing getThing(@ApiParameter ThingId ignore)
    {
        return new Thing(new ApiResourceVersion(1), new ThingId("sss"), "hey");
    }

    @ApiList(description = "Get things")
    @ApiResponseFilter(TestFilter.class)
    public List<Thing> getAllThings()
    {
        return ImmutableList.of();
    }
}
