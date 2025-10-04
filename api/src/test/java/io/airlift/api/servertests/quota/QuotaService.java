package io.airlift.api.servertests.quota;

import com.google.inject.Inject;
import io.airlift.api.ApiCreate;
import io.airlift.api.ApiCustom;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiQuotaController;
import io.airlift.api.ApiService;
import io.airlift.api.ApiTrait;
import io.airlift.api.ApiType;
import io.airlift.api.ServiceType;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Request;

import static io.airlift.api.responses.ApiException.badRequest;
import static java.util.Objects.requireNonNull;

@ApiService(type = ServiceType.class, name = "quota", description = "Does quota things")
public class QuotaService
{
    private final ApiQuotaController quotaController;

    @Inject
    public QuotaService(ApiQuotaController quotaController)
    {
        this.quotaController = requireNonNull(quotaController, "quotaController is null");
    }

    @ApiCustom(type = ApiType.CREATE, verb = "shouldThrow", description = "goodBeta", traits = ApiTrait.BETA)
    public void shouldWarnButNotThrow()
    {
    }

    @ApiCreate(description = "void", quotas = "dummy")
    public void addVoid()
    {
    }

    @ApiCreate(description = "good")
    public void addCorrectly(@Context Request request, @ApiParameter QuotaId ignore, QuotaResource ignore2)
    {
        quotaController.recordQuotaUsage(request, QuotaKeys.DUMMY.name());
    }

    @ApiCustom(type = ApiType.CREATE, verb = "goodBeta", description = "goodBeta", traits = ApiTrait.BETA)
    public void addCorrectlyBeta(@ApiParameter QuotaId ignore, QuotaResource ignore2)
    {
        // no usage recorded but this is beta
    }

    @ApiCustom(type = ApiType.CREATE, verb = "throws", description = "throws")
    public void addCorrectlyButThrows(@ApiParameter QuotaId ignore, QuotaResource ignore2)
    {
        throw badRequest("boo hoo");
    }

    @ApiCustom(type = ApiType.CREATE, verb = "badly", description = "bad")
    public void addIncorrectly(@ApiParameter QuotaId ignore, QuotaResource ignore2)
    {
    }

    @ApiCustom(type = ApiType.CREATE, verb = "unspecified", description = "bad")
    public void addUnspecifiedQuota(@Context Request request, @ApiParameter QuotaId ignore, QuotaResource ignore2)
    {
        quotaController.recordQuotaUsage(request, "this isn't specified");
    }
}
