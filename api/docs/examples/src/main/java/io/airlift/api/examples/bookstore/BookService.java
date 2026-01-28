package io.airlift.api.examples.bookstore;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ApiTrait;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.airlift.api.responses.ApiException.badRequest;

@ApiService(name = "bookService", type = BookServiceType.class, description = "Manage books in the bookstore")
public class BookService
{
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<String, BookData> books = new ConcurrentHashMap<>();

    @ApiCreate(description = "Create a new book", traits = {ApiTrait.BETA}) // TODO: Remove BETA trait and explain quotas.
    public void createBook(BookData bookData)
    {
        if (bookData == null) {
            throw badRequest("Must provide BookData payload");
        }
        String id = String.valueOf(nextId.getAndIncrement()); // BookData includes ISBN, but ISBN is neither universal nor actually unique.
        books.put(id, bookData);
    }
}
