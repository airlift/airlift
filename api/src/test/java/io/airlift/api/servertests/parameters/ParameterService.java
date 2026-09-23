package io.airlift.api.servertests.parameters;

import io.airlift.api.ApiFilterList;
import io.airlift.api.ApiList;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@ApiService(type = ServiceType.class, name = "parameters", description = "Receives parameters")
public class ParameterService
{
    static final AtomicReference<List<String>> RECEIVED_COLORS = new AtomicReference<>(List.of());
    static final AtomicReference<List<String>> RECEIVED_SHAPES = new AtomicReference<>(List.of());

    @ApiList(description = "List things")
    public List<Thing> listThings(@ApiParameter ApiFilterList colors, @ApiParameter ApiFilterList shapes)
    {
        RECEIVED_COLORS.set(colors.mapAsString());
        RECEIVED_SHAPES.set(shapes.mapAsString());
        return List.of();
    }
}
