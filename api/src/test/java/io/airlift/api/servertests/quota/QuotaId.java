package io.airlift.api.servertests.quota;

import io.airlift.api.ApiId;

public class QuotaId
        extends ApiId<QuotaResource, InternalQuotaId>
{
    public QuotaId()
    {
        super(new InternalQuotaId("dummy"));
    }

    public QuotaId(String id)
    {
        super(new InternalQuotaId(id));
    }
}
