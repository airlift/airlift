package io.airlift.api.examples.bookstore;

import io.airlift.api.ApiServiceType;

public class BookServiceType
        implements ApiServiceType
{
    // All endpoints in this service type will have URIs beginning with `<service-type-id>/api/v<service-version-number>/`.

    @Override
    public String id()
    {
        return "bookServiceTypeId";
    }

    @Override
    public int version()
    {
        return 21;
    }

    @Override
    public String title()
    {
        return "Book Service Type";
    }

    @Override
    public String description()
    {
        return "BookServiceType description";
    }
}
