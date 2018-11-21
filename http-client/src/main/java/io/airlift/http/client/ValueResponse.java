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
package io.airlift.http.client;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import io.airlift.json.Codec;

import javax.annotation.Nullable;

import java.util.List;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public abstract class ValueResponse<T>
{
    private final int statusCode;
    private final String statusMessage;
    private final ListMultimap<HeaderName, String> headers;
    private final boolean hasValue;
    private final T value;
    private final IllegalArgumentException exception;
    final byte[] responseBytes;

    public ValueResponse(int statusCode, String statusMessage, ListMultimap<HeaderName, String> headers, byte[] responseBytes)
    {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.headers = ImmutableListMultimap.copyOf(headers);

        this.hasValue = false;
        this.responseBytes = requireNonNull(responseBytes, "responseBytes is null");
        this.value = null;
        this.exception = null;
    }

    @SuppressWarnings("ThrowableInstanceNeverThrown")
    public ValueResponse(int statusCode, String statusMessage, ListMultimap<HeaderName, String> headers, Codec<T> codec, byte[] responseBytes)
    {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.headers = ImmutableListMultimap.copyOf(headers);

        this.responseBytes = requireNonNull(responseBytes, "responseBytes is null");

        T value = null;
        IllegalArgumentException exception = null;
        try {
            value = codec.fromBytes(responseBytes);
        }
        catch (IllegalArgumentException e) {
            exception = new IllegalArgumentException(format("Unable to create %s from response:\n[%s]", codec.getType(), toResponseString(responseBytes)), e);
        }
        this.hasValue = (exception == null);
        this.value = value;
        this.exception = exception;
    }

    protected abstract String toResponseString(byte[] responseBytes);

    public int getStatusCode()
    {
        return statusCode;
    }

    public String getStatusMessage()
    {
        return statusMessage;
    }

    @Nullable
    public String getHeader(String name)
    {
        List<String> values = getHeaders().get(HeaderName.of(name));
        return values.isEmpty() ? null : values.get(0);
    }

    public List<String> getHeaders(String name)
    {
        return headers.get(HeaderName.of(name));
    }

    public ListMultimap<HeaderName, String> getHeaders()
    {
        return headers;
    }

    public boolean hasValue()
    {
        return hasValue;
    }

    public T getValue()
    {
        if (!hasValue) {
            throw new IllegalStateException("Response does not contain a valid value", exception);
        }
        return value;
    }

    public int getResponseSize()
    {
        return responseBytes.length;
    }

    public byte[] getResponseBytes()
    {
        return responseBytes.clone();
    }

    public IllegalArgumentException getException()
    {
        return exception;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("statusCode", statusCode)
                .add("statusMessage", statusMessage)
                .add("headers", headers)
                .add("hasValue", hasValue)
                .add("value", value)
                .toString();
    }
}
