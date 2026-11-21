package io.airlift.api.examples.bookstore;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiService;
import io.airlift.api.ApiTrait;
import io.airlift.api.ApiUpdate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.airlift.api.responses.ApiException.badRequest;
import static io.airlift.api.responses.ApiException.notFound;

@ApiService(name = "bookService", type = BookServiceType.class, description = "Manage books in the bookstore")
public class BookService
{
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<String, Book> books = new ConcurrentHashMap<>();

    @ApiCreate(description = "Create a new book", traits = {ApiTrait.BETA}) // TODO: Remove BETA trait and explain quotas.
    public Book createBook(BookData bookData)
    {
        if (bookData == null) {
            throw badRequest("Must provide BookData payload");
        }
        String id = String.valueOf(nextId.getAndIncrement()); // BookData includes ISBN, but ISBN is neither universal nor actually unique.
        Book book = new Book(new Book.Id(id), new ApiResourceVersion(), bookData);
        books.put(id, book);
        return book;
    }

    @ApiGet(description = "Get a book by its ID")
    public Book getBook(@ApiParameter Book.Id id)
    {
        Book book = books.get(id.toString());
        if (book == null) {
            throw notFound("Book with ID %s not found".formatted(id));
        }
        return book;
    }

    @ApiUpdate(description = "Overwrite book data")
    public Book updateBook(@ApiParameter Book.Id id, Book newValue)
    {
        if (newValue == null) {
            throw badRequest("Must provide Book payload");
        }
        if (!id.equals(newValue.bookId())) {
            throw badRequest("Book ID in URL (%s) does not match Book ID in payload (%s)".formatted(id, newValue.bookId()));
        }
        synchronized (books) { // Make syncToken test-and-set atomic.
            Book oldValue = books.get(id.toString());
            if (oldValue == null) {
                throw notFound("Book with ID %s not found".formatted(id));
            }
            if (!newValue.syncToken().equals(oldValue.syncToken())) {
                throw badRequest("syncToken mismatch: expected %s but got %s".formatted(
                        oldValue.syncToken().syncToken(),
                        newValue.syncToken().syncToken()));
            }
            // Bump the syncToken version on update:
            newValue = new Book(
                    newValue.bookId(),
                    new ApiResourceVersion(newValue.syncToken().version() + 1),
                    newValue.data());
            books.put(id.toString(), newValue);
        }
        return newValue;
    }
}
