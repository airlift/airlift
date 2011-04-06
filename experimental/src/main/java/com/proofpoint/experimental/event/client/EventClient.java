package com.proofpoint.experimental.event.client;

import java.io.IOException;
import java.util.concurrent.Future;

public interface EventClient
{
    <T> Future<Void> post(T... event)
            throws IllegalArgumentException;

    <T> Future<Void> post(Iterable<T> events)
            throws IllegalArgumentException;

    <T> Future<Void> post(EventGenerator<T> eventGenerator)
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
