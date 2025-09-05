package io.airlift.api.validation;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiId;
import io.airlift.api.TestId;

public class FoodId
        extends ApiId<Food, TestId>
{
    public FoodId()
    {
        this("dummy");
    }

    public FoodId(TestId internalId)
    {
        super(internalId);
    }

    @JsonCreator
    public FoodId(String id)
    {
        super(id);
    }
}
