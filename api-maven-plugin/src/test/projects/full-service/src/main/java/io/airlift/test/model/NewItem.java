/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.test.model;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;

/**
 * Polymorphic new item request for creating items.
 */
@ApiPolyResource(key = "itemType", name = "newItem", description = "Request to create a new item")
public sealed interface NewItem
{
    String title();

    /**
     * Request to create a new book.
     */
    @ApiResource(name = "newBook", description = "Request to create a new book")
    record NewBook(
            @ApiDescription("Book title") String title,
            @ApiDescription("Book author") String author,
            @ApiDescription("Number of pages") int pages)
            implements NewItem
    {
    }

    /**
     * Request to create a new movie.
     */
    @ApiResource(name = "newMovie", description = "Request to create a new movie")
    record NewMovie(
            @ApiDescription("Movie title") String title,
            @ApiDescription("Movie director") String director,
            @ApiDescription("Duration in minutes") int durationMinutes)
            implements NewItem
    {
    }
}
