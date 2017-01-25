/*
 * Copyright 2012 Proofpoint, Inc.
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
package io.airlift.jaxrs;

import static java.util.Objects.requireNonNull;

/**
 * Wraps JsonProcessingExceptions to provide more information about parsing errors.
 */
public class JsonMapperParsingException
        extends ParsingException
{
    private Class<?> type;

    public JsonMapperParsingException(Class<?> type, Throwable cause)
    {
        super("Invalid json for Java type " + requireNonNull(type, "type is null").getName(), cause);
        this.type = type;
    }

    /**
     * Returns the type of object that failed Json parsing.
     */
    public Class<?> getType()
    {
        return type;
    }
}
