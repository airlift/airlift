package io.airlift.api.examples.bookstore;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ApiTrait;

@ApiService(name = "bookService", type = BookServiceType.class, description = "Manage books in the bookstore")
public class BookService
{
    @ApiCreate(description = "Create a new book", traits = {ApiTrait.BETA}) // TODO: Remove BETA trait and explain quotas.
    public void createBook() {}
}
