package io.airlift.api.examples.bookstore;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import static java.util.Objects.requireNonNull;

@ApiResource(name = "bookData", description = "Book information")
public record BookData(
        @ApiDescription("Title of the book") String title,
        @ApiDescription("Author of the book") String author,
        @ApiDescription("ISBN number") String isbn,
        @ApiDescription("Year published") int year,
        @ApiDescription("Price in USD") double price)
{
    public BookData
    {
        requireNonNull(title, "title is null");
        requireNonNull(author, "author is null");
        requireNonNull(isbn, "isbn is null");

        if (year < 0) {
            throw new IllegalArgumentException("year must be positive");
        }
        if (price < 0) {
            throw new IllegalArgumentException("price must be positive");
        }
    }
}
