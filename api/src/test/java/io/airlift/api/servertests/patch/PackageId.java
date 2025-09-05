package io.airlift.api.servertests.patch;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiId;
import io.airlift.api.TestId;

public class PackageId
        extends ApiId<Package, TestId>
{
    public PackageId()
    {
        this("package");
    }

    public PackageId(TestId internalId)
    {
        super(internalId);
    }

    @JsonCreator
    public PackageId(String id)
    {
        super(id);
    }
}
