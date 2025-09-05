package io.airlift.api.servertests.unwrapped;

import com.google.inject.Inject;
import io.airlift.api.ApiCreate;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiQuotaController;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Request;

import java.time.Instant;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

@ApiService(type = ServiceType.class, name = "unwrapped", description = "Unwrapped test")
public class UnwrappedService
{
    private final ApiQuotaController quotaController;

    @Inject
    public UnwrappedService(ApiQuotaController quotaController)
    {
        this.quotaController = requireNonNull(quotaController, "quotaController is null");
    }

    @SuppressWarnings("unused")
    @ApiCreate(description = "create", quotas = "UNWRAPPED")
    public void updateUnwrapped(@Context Request request, TopLevel topLevel)
    {
        quotaController.recordQuotaUsage(request, "UNWRAPPED");
    }

    @ApiGet(description = "hey")
    public TopLevelResult getUnwrapped(@ApiParameter TopLevelId topLevelId)
    {
        return new TopLevelResult(new ApiResourceVersion(1), topLevelId, "server", 1, new ChildLevel(Instant.now(), 1.23, Optional.empty(), new ChildChildLevel(true)));
    }
}
