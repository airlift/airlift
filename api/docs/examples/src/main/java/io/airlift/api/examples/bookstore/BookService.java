package io.airlift.api.examples.bookstore;

import io.airlift.api.ApiService;

@ApiService(name = "bookService", type = BookServiceType.class, description = "Manage books in the bookstore")
public class BookService
{
}
