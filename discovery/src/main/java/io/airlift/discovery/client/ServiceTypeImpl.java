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
package io.airlift.discovery.client;

import java.lang.annotation.Annotation;

import static java.util.Objects.requireNonNull;

class ServiceTypeImpl
        implements ServiceType
{
    private final String value;

    public ServiceTypeImpl(String value)
    {
        requireNonNull(value, "value is null");
        this.value = value;
    }

    public String value()
    {
        return value;
    }

    public String toString()
    {
        return String.format("@%s(value=%s)", ServiceType.class.getName(), value);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceType)) {
            return false;
        }

        ServiceType that = (ServiceType) o;

        if (!value.equals(that.value())) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        // see Annotation.hashCode()
        int result = 0;
        result += ((127 * "value".hashCode()) ^ value.hashCode());
        return result;
    }

    public Class<? extends Annotation> annotationType()
    {
        return ServiceType.class;
    }
}
