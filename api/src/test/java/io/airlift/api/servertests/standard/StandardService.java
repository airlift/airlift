package io.airlift.api.servertests.standard;

import com.google.inject.Inject;
import io.airlift.api.ApiCreate;
import io.airlift.api.ApiCustom;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiQuotaController;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiService;
import io.airlift.api.ApiType;
import io.airlift.api.ApiUpdate;
import io.airlift.api.ServiceType;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Request;

import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unused")
@ApiService(type = ServiceType.class, name = "standard", description = "Does standard things")
public class StandardService
{
    private final ApiQuotaController quotaController;

    @Inject
    public StandardService(ApiQuotaController quotaController)
    {
        this.quotaController = requireNonNull(quotaController, "quotaController is null");
    }

    @ApiGet(description = "Get things")
    public Thing getThings(@ApiParameter ThingId thingId)
    {
        return new Thing(new ApiResourceVersion(1), thingId, "dummy", 10, Optional.of("code"));
    }

    @ApiUpdate(description = "Update things")
    public void updateThings(@ApiParameter ThingId thingId, Thing thing)
    {
    }

    @ApiCreate(description = "Create things", quotas = "dummy")
    public void createThings(@Context Request request)
    {
        quotaController.recordQuotaUsage(request, "dummy");
    }

    @ApiCustom(verb = "repeat", type = ApiType.GET, description = "Repeat things")
    public Thing repeat(@ApiParameter ThingId thingId)
    {
        return new Thing(new ApiResourceVersion(1), thingId, "dummy", 10, Optional.of("code"));
    }

    @ApiCustom(type = ApiType.CREATE, verb = "void", description = "baggage", quotas = "SOMETHING")
    public void createWithVoid(@Context Request request, NewThing thing)
    {
        quotaController.recordQuotaUsage(request, "SOMETHING");
    }

    @ApiGet(description = "baggage")
    public Thing getWithBaggage(@ApiParameter(allowedValues = {"extraSoft", "veryHard"}) BaggageId baggageId, @ApiParameter ThingId thingId)
    {
        assertThat(baggageId.toInternal()).isIn(BaggageType.EXTRA_SOFT, BaggageType.VERY_HARD);

        return new Thing(new ApiResourceVersion(1), thingId, baggageId.toInternal().name(), 10, Optional.of("code"));
    }

    @ApiCustom(type = ApiType.GET, verb = "ultra", description = "baggage")
    public Thing getWithUltraBaggage(@ApiParameter(allowedValues = "ultraLuxurious") BaggageId baggageId, @ApiParameter ThingId thingId)
    {
        assertThat(baggageId.toInternal()).isEqualTo(BaggageType.ULTRA_LUXURIOUS);

        return new Thing(new ApiResourceVersion(1), thingId, baggageId.toInternal().name(), 10, Optional.of("code"));
    }

    @ApiGet(description = "hey")
    public Name getName(@ApiParameter NameId nameId)
    {
        return new Name(new ApiResourceVersion(1), nameId);
    }

    @ApiCustom(type = ApiType.GET, verb = "name", description = "test")
    public Thing getFromLookup(@ApiParameter LookupId lookupId)
    {
        return new Thing(new ApiResourceVersion(1), new ThingId(), lookupId.toInternal().id(), 0, Optional.empty());
    }

    @ApiCreate(description = "dummy")
    public PolyResourceResult createPoly(@Context Request request, PolyResource polyResource)
    {
        quotaController.recordQuotaUsage(request, "POLY");

        return new PolyResourceResult(new ApiResourceVersion(1), new PolyResourceId(polyResource.poly().getClass().getSimpleName()));
    }
}
