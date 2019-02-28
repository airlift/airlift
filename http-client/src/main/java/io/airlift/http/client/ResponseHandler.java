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
import io.airlift.json.Codec;

import java.io.IOException;
import java.util.Optional;

import static com.google.common.io.ByteStreams.toByteArray;
import static java.util.Objects.requireNonNull;

@Beta
public abstract class ResponseHandler<T, E extends Exception>
{
    public abstract T handle(Request request, Response response)
            throws E;

    public abstract T handleException(Request request, Exception exception)
            throws E;

    final Optional<Codec<?>> codec;

    public ResponseHandler()
    {
        this.codec = Optional.empty();
    }

    public ResponseHandler(Codec<?> codec)
    {
        requireNonNull(codec, "codec is null");
        this.codec = Optional.of(codec);
    }

    static byte[] readResponseBytes(Response response)
    {
        try {
            return toByteArray(response.getInputStream());
        }
        catch (IOException e) {
            throw new RuntimeException("Error reading response from server", e);
        }
    }
}
