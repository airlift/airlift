package io.airlift.api.servertests.headers;

import io.airlift.api.ApiGet;
import io.airlift.api.ApiHeader;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@ApiService(type = ServiceType.class, name = "headers", description = "Receives headers")
public class HeaderService
{
    static final AtomicReference<Optional<String>> RECEIVED_IDEMPOTENCY_KEY = new AtomicReference<>(Optional.empty());
    static final AtomicReference<Optional<String>> RECEIVED_REQUEST_ID = new AtomicReference<>(Optional.empty());

    @ApiGet(description = "Get a thing")
    public Thing getThing(@ApiParameter ThingId thingId, @ApiParameter(name = "Idempotency-Key") ApiHeader idempotencyKey, @ApiParameter ApiHeader requestId)
    {
        RECEIVED_IDEMPOTENCY_KEY.set(idempotencyKey.value());
        RECEIVED_REQUEST_ID.set(requestId.value());
        return new Thing(new ApiResourceVersion("1"), thingId, "thing");
    }
}
