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
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;

/**
 * Polymorphic item resource demonstrating sealed interface pattern.
 * Items can be either Books or Movies.
 */
@ApiPolyResource(key = "itemType", name = "item", description = "Polymorphic item resource")
public sealed interface Item
{
    ItemId itemId();

    String title();

    /**
     * A book item.
     */
    @ApiResource(name = "book", description = "A book item")
    record Book(
            @ApiDescription("Unique item identifier") @ApiReadOnly ItemId itemId,
            @ApiDescription("Book title") String title,
            @ApiDescription("Book author") String author,
            @ApiDescription("Number of pages") int pages)
            implements Item
    {
    }

    /**
     * A movie item.
     */
    @ApiResource(name = "movie", description = "A movie item")
    record Movie(
            @ApiDescription("Unique item identifier") @ApiReadOnly ItemId itemId,
            @ApiDescription("Movie title") String title,
            @ApiDescription("Movie director") String director,
            @ApiDescription("Duration in minutes") int durationMinutes)
            implements Item
    {
    }
}
