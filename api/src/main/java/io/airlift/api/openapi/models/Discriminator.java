package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class Discriminator
{
    private String propertyName;
    private Map<String, String> mapping;

    public Discriminator propertyName(String propertyName)
    {
        this.propertyName = propertyName;
        return this;
    }

    @JsonProperty
    public String getPropertyName()
    {
        return propertyName;
    }

    public void setPropertyName(String propertyName)
    {
        this.propertyName = propertyName;
    }

    public Discriminator mapping(String name, String value)
    {
        if (this.mapping == null) {
            this.mapping = new LinkedHashMap<>();
        }
        this.mapping.put(name, value);
        return this;
    }

    public Discriminator mapping(Map<String, String> mapping)
    {
        this.mapping = mapping;
        return this;
    }

    @JsonProperty
    public Map<String, String> getMapping()
    {
        return mapping;
    }

    public void setMapping(Map<String, String> mapping)
    {
        this.mapping = mapping;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Discriminator)) {
            return false;
        }

        Discriminator that = (Discriminator) o;

        if (propertyName != null ? !propertyName.equals(that.propertyName) : that.propertyName != null) {
            return false;
        }
        return mapping != null ? mapping.equals(that.mapping) : that.mapping == null;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(propertyName, mapping);
    }

    @Override
    public String toString()
    {
        return "Discriminator{" +
                "propertyName='" + propertyName + '\'' +
                ", mapping=" + mapping +
                '}';
    }
}
