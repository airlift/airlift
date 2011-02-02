package com.proofpoint.experimental.json;

public interface JsonCodec<T>
{
    /**
     * Coverts the specified json string into an instance of type T
     * @return Parsed response; never null
     * @throws IllegalArgumentException if the json string can not be converted to the type T
     */
    T fromJson(String json)
            throws IllegalArgumentException;

    /**
     * Converts the specified instance to json.
     * @param instance the instance to convert to json
     * @return Parsed response; never null
     * @throws IllegalArgumentException if the specified instance can not be converted to json
     */
    String toJson(T instance)
            throws IllegalArgumentException;
}
