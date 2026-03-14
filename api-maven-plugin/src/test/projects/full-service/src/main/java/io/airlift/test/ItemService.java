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
package io.airlift.test;

import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiCreate;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiList;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiService;
import io.airlift.test.model.Item;
import io.airlift.test.model.ItemId;
import io.airlift.test.model.NewItem;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Item service demonstrating polymorphic resources (Books and Movies).
 */
@ApiService(name = "items", type = TestServiceType.class, description = "Item service with polymorphic types")
public class ItemService
{
    private final Map<String, Item> items = new ConcurrentHashMap<>();

    public ItemService()
    {
        // Pre-populate with some sample items
        String bookId = UUID.randomUUID().toString();
        items.put(bookId, new Item.Book(new ItemId(bookId), "The Great Gatsby", "F. Scott Fitzgerald", 180));

        String movieId = UUID.randomUUID().toString();
        items.put(movieId, new Item.Movie(new ItemId(movieId), "Inception", "Christopher Nolan", 148));
    }

    @ApiList(description = "List all items (books and movies)")
    public List<Item> listItems()
    {
        return ImmutableList.copyOf(items.values());
    }

    @ApiGet(description = "Get an item by ID")
    public Item getItem(@ApiParameter ItemId itemId)
    {
        Item item = items.get(itemId.value());
        if (item == null) {
            throw new RuntimeException("Item not found: " + itemId);
        }
        return item;
    }

    @ApiCreate(description = "Create a new item (book or movie)")
    public Item createItem(NewItem newItem)
    {
        String id = UUID.randomUUID().toString();
        ItemId itemId = new ItemId(id);

        Item item = switch (newItem) {
            case NewItem.NewBook book -> new Item.Book(itemId, book.title(), book.author(), book.pages());
            case NewItem.NewMovie movie -> new Item.Movie(itemId, movie.title(), movie.director(), movie.durationMinutes());
        };

        items.put(id, item);
        return item;
    }
}
