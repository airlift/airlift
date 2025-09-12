package io.airlift.api.servertests.standard;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiEnumId;

public class BaggageId
        extends ApiEnumId<Baggage, BaggageType>
{
    public BaggageId()
    {
        super(BaggageType.EXTRA_SOFT);
    }

    @JsonCreator
    public BaggageId(String id)
    {
        super(id);
    }

    @Override
    public BaggageType toInternal()
    {
        return super.toInternal();
    }
}
