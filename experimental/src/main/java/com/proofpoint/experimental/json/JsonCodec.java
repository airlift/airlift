/*
 * Copyright 2010 Proofpoint, Inc.
 *
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
