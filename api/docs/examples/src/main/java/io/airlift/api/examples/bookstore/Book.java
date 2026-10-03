package io.airlift.api.examples.bookstore;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiStringId;
import io.airlift.api.ApiUnwrapped;

import static java.util.Objects.requireNonNull;

/**
 * Complete Book resource including system metadata.
 */
@ApiResource(name = "book", description = "Book resource with metadata")
public record Book(
        @ApiDescription("Unique book identifier") @ApiReadOnly Id bookId,
        ApiResourceVersion syncToken,
        @ApiUnwrapped BookData data)
{
    public Book
    {
        requireNonNull(bookId, "id is null");
        requireNonNull(syncToken, "syncToken is null");
        requireNonNull(data, "data is null");
    }

    public static class Id
            extends ApiStringId<Book>
    {
        // The JsonCreator annotation prevents [Guice/ErrorInjectingConstructor]:
        // ValidatorException: [Could not deserialize Resource io.airlift.api.examples.bookstore.Book.
        // Error: Cannot construct instance of `io.airlift.api.examples.bookstore.Book$Id` (although at least one Creator exists):
        // no String-argument constructor/factory method to deserialize from String value ('.')
        @JsonCreator
        public Id(String id)
        {
            super(id);
        }

        // Prevents [Guice/ErrorInjectingConstructor]:
        // ValidatorException: [Could not instantiate record io.airlift.api.examples.bookstore.Book.
        // Message: [Could not create default value for ApiAbstractId io.airlift.api.examples.bookstore.Book$Id]]
        public Id()
        {
            super(".");
        }

    }
}
