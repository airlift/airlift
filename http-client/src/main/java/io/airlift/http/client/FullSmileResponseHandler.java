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

import com.google.common.collect.ListMultimap;
import com.google.common.net.MediaType;
import io.airlift.http.client.FullSmileResponseHandler.SmileResponse;
import io.airlift.json.Codec;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static java.util.Objects.requireNonNull;

public class FullSmileResponseHandler<T>
        extends ResponseHandler<SmileResponse<T>, RuntimeException>
{
    private static final MediaType MEDIA_TYPE_SMILE = MediaType.create("application", "x-jackson-smile");

    public static <T> FullSmileResponseHandler<T> createFullSmileResponseHandler(Codec<T> smileCodec)
    {
        return new FullSmileResponseHandler<>(smileCodec);
    }

    private FullSmileResponseHandler(Codec<T> smileCodec)
    {
        super(smileCodec);
    }

    @Override
    public SmileResponse<T> handleException(Request request, Exception exception)
    {
        throw propagate(request, exception);
    }

    @Override
    public SmileResponse<T> handle(Request request, Response response)
    {
        byte[] bytes = readResponseBytes(response);
        String contentType = response.getHeader(CONTENT_TYPE);
        if ((contentType == null) || !MediaType.parse(contentType).is(MEDIA_TYPE_SMILE)) {
            return new SmileResponse<>(response.getStatusCode(), response.getStatusMessage(), response.getHeaders(), bytes);
        }
        return new SmileResponse(response.getStatusCode(), response.getStatusMessage(), response.getHeaders(), codec.get(), bytes);
    }

    public static class SmileResponse<T>
            extends ValueResponse<T>
    {
        private final byte[] smileBytes;

        public SmileResponse(int statusCode, String statusMessage, ListMultimap<HeaderName, String> headers, byte[] responseBytes)
        {
            super(statusCode, statusMessage, headers, responseBytes);
            this.smileBytes = null;
        }

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        public SmileResponse(int statusCode, String statusMessage, ListMultimap<HeaderName, String> headers, Codec<T> smileCodec, byte[] smileBytes)
        {
            super(statusCode, statusMessage, headers, smileCodec, smileBytes);
            this.smileBytes = requireNonNull(smileBytes, "smileBytes is null");
        }

        @Override
        protected String toResponseString(byte[] responseBytes)
        {
            return null;
        }

        public byte[] getSmileBytes()
        {
            return (smileBytes == null) ? null : smileBytes.clone();
        }
    }
}
