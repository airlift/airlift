package com.proofpoint.event.client;

import com.google.common.util.concurrent.CheckedFuture;

import java.io.IOException;
import java.util.concurrent.Future;

public interface EventClient
{
    <T> CheckedFuture<Void, ? extends RuntimeException> post(T... event)
            throws IllegalArgumentException;

    <T> CheckedFuture<Void, ? extends RuntimeException> post(Iterable<T> events)
            throws IllegalArgumentException;

    <T> CheckedFuture<Void, ? extends RuntimeException> post(EventGenerator<T> eventGenerator)
            throws IllegalArgumentException;

    public interface EventGenerator<T>
    {
        void generate(EventPoster<T> eventPoster)
                throws IOException;
    }

    public interface EventPoster<T>
    {
        void post(T event)
                throws IOException;
    }
}
