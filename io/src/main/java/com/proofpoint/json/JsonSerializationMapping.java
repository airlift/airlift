package com.proofpoint.json;

/**
 * DAO that maps a serializable class to a separate serializer
 */
public class JsonSerializationMapping<T, S extends JsonSerializerHelper<T>>
{
    private final Class<T> type;
    private final S serializer;

    /**
     * Helper. NOTE that the value return is not generically type so that it can be easily added to a collection
     * of mappings
     *
     * @param type serializable class
     * @param serializer serializer instance
     * @return mapping
     */
    public static <T, S extends JsonSerializerHelper<T>> JsonSerializationMapping make(Class<T> type, S serializer)
    {
        return new JsonSerializationMapping<T, S>(type, serializer);
    }

    /**
     * @param type serializable class
     * @param serializer serializer instance
     */
    public JsonSerializationMapping(Class<T> type, S serializer)
    {
        this.type = type;
        this.serializer = serializer;
    }

    public Class<T> getType()
    {
        return type;
    }

    public S getSerializer()
    {
        return serializer;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        JsonSerializationMapping that = (JsonSerializationMapping) o;

        //noinspection RedundantIfStatement
        if (!type.equals(that.type)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return type.hashCode();
    }
}
