package io.airlift.json;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class Car implements Vehicle {
    private final String name;

    @JsonCreator
    public Car(String name) {
        this.name = requireNonNull(name, "Name is null");
    }

    @JsonProperty
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        Car o = (Car) obj;
        return Objects.equals(this.name, o.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
