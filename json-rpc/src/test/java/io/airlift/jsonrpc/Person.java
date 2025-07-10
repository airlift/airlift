package io.airlift.jsonrpc;

import static java.util.Objects.requireNonNull;

public record Person(String name, int age)
{
    public Person
    {
        requireNonNull(name, "name is null");
    }
}
