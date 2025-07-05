package io.airlift.mcp;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record Person(String name, int age, Address address, List<Address> alternateAddresses, Optional<String> code)
{
    public record Address(String street, String city, String state, String zip)
    {
        public Address
        {
            requireNonNull(street, "street is null");
            requireNonNull(city, "city is null");
            requireNonNull(state, "state is null");
            requireNonNull(zip, "zip is null");
        }
    }

    public Person
    {
        requireNonNull(name, "name is null");
    }
}
