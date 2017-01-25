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
package io.airlift.http.client;

import com.google.common.annotations.Beta;
import com.google.common.collect.ListMultimap;

import javax.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Beta
public interface Response
{
    int getStatusCode();

    String getStatusMessage();

    @Nullable
    default String getHeader(String name)
    {
        List<String> values = getHeaders(name);
        return values.isEmpty() ? null : values.get(0);
    }

    default List<String> getHeaders(String name)
    {
        return getHeaders().get(HeaderName.of(name));
    }

    ListMultimap<HeaderName, String> getHeaders();

    long getBytesRead();

    InputStream getInputStream()
            throws IOException;
}
