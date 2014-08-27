/*
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
package io.airlift.units;

import javax.validation.Payload;

import java.lang.annotation.Annotation;

import static com.google.common.base.Preconditions.checkNotNull;

@SuppressWarnings("ClassExplicitlyAnnotation")
class MockMinDataSize
        implements MinDataSize
{
    private final DataSize dataSize;

    public MockMinDataSize(DataSize dataSize)
    {
        this.dataSize = checkNotNull(dataSize, "dataSize is null");
    }

    @Override
    public String value()
    {
        return dataSize.toString();
    }

    @Override
    public String message()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<?>[] groups()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<? extends Payload>[] payload()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<? extends Annotation> annotationType()
    {
        return MinDataSize.class;
    }
}
