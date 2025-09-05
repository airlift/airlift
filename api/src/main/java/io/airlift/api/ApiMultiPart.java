package io.airlift.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

@SuppressWarnings("unused")
public sealed interface ApiMultiPart
{
    record ApiMultiPartFormWithResource<RESOURCE>(RESOURCE resource, Iterator<ItemInput> itemInputIterator)
            implements ApiMultiPart
    {
        public ApiMultiPartFormWithResource
        {
            requireNonNull(resource, "resource is null");
            requireNonNull(itemInputIterator, "itemInputIterator is null");
        }
    }

    record ApiMultiPartForm<RESOURCE>(Iterator<ItemInput> itemInputIterator)
            implements ApiMultiPart
    {
        public ApiMultiPartForm
        {
            requireNonNull(itemInputIterator, "itemInputIterator is null");
        }
    }

    interface ItemInput
    {
        String contentType();

        Optional<String> header(String name);

        Collection<String> headerNames();

        InputStream inputStream()
                throws IOException;

        String name();

        Optional<String> fileName();
    }
}
