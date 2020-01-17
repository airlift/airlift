package com.facebook.airlift.http.client.jetty;

import org.eclipse.jetty.client.api.ContentProvider;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class TestChunkedBytesContentProvider
{
    @Test
    public void testGetLength()
    {
        ContentProvider contentProvider = new ChunkedBytesContentProvider(new byte[] {1, 2, 3});
        assertEquals(contentProvider.getLength(), 3);
        contentProvider = new ChunkedBytesContentProvider(new byte[] {});
        assertEquals(contentProvider.getLength(), 0);
    }

    @Test
    public void testIterator()
    {
        ContentProvider contentProvider = new ChunkedBytesContentProvider(new byte[] {}, 2);
        Iterator<ByteBuffer> iterator = contentProvider.iterator();
        assertFalse(iterator.hasNext());

        contentProvider = new ChunkedBytesContentProvider(new byte[] {1}, 2);
        iterator = contentProvider.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), ByteBuffer.wrap(new byte[] {1}));
        assertFalse(iterator.hasNext());

        contentProvider = new ChunkedBytesContentProvider(new byte[] {1, 2}, 2);
        iterator = contentProvider.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), ByteBuffer.wrap(new byte[] {1, 2}));
        assertFalse(iterator.hasNext());

        contentProvider = new ChunkedBytesContentProvider(new byte[] {1, 2, 3}, 2);
        iterator = contentProvider.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), ByteBuffer.wrap(new byte[] {1, 2}));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), ByteBuffer.wrap(new byte[] {3}));
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testIsReproducible()
    {
        byte[] bytes = {1, 2, 3};
        ContentProvider contentProvider = new ChunkedBytesContentProvider(bytes);
        assertTrue(contentProvider.isReproducible());
        Iterator<ByteBuffer> iterator = contentProvider.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), ByteBuffer.wrap(bytes));
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
        iterator = contentProvider.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), ByteBuffer.wrap(bytes));
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }
}
