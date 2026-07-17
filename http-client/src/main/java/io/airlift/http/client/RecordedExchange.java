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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public record RecordedExchange(
        int version,
        long sequence,
        RecordedRequest request,
        Optional<RecordedResponse> response,
        Optional<RecordedFailure> failure)
{
    public static final int CURRENT_VERSION = 1;

    public RecordedExchange
    {
        checkArgument(version == CURRENT_VERSION, "unsupported recorded exchange version: %s", version);
        checkArgument(sequence > 0, "sequence must be positive");
        requireNonNull(request, "request is null");
        requireNonNull(response, "response is null");
        requireNonNull(failure, "failure is null");
        checkArgument(response.isPresent() || failure.isPresent(), "response and failure are both empty");
    }

    public RecordedExchange withRequest(RecordedRequest request)
    {
        return new RecordedExchange(version, sequence, request, response, failure);
    }

    public RecordedExchange withResponse(Optional<RecordedResponse> response)
    {
        return new RecordedExchange(version, sequence, request, response, failure);
    }

    public RecordedExchange withFailure(Optional<RecordedFailure> failure)
    {
        return new RecordedExchange(version, sequence, request, response, failure);
    }

    public record RecordedRequest(String method, String uri, Map<String, List<String>> headers, CapturedBody body)
    {
        public RecordedRequest
        {
            requireNonNull(method, "method is null");
            requireNonNull(uri, "uri is null");
            headers = copyHeaders(headers);
            requireNonNull(body, "body is null");
        }

        public RecordedRequest withHeaders(Map<String, List<String>> headers)
        {
            return new RecordedRequest(method, uri, headers, body);
        }

        public RecordedRequest withUri(String uri)
        {
            return new RecordedRequest(method, uri, headers, body);
        }

        public RecordedRequest withBody(CapturedBody body)
        {
            return new RecordedRequest(method, uri, headers, body);
        }
    }

    public record RecordedResponse(int statusCode, Map<String, List<String>> headers, CapturedBody body)
    {
        public RecordedResponse
        {
            checkArgument(statusCode >= 100 && statusCode <= 999, "invalid status code: %s", statusCode);
            headers = copyHeaders(headers);
            requireNonNull(body, "body is null");
        }

        public RecordedResponse withHeaders(Map<String, List<String>> headers)
        {
            return new RecordedResponse(statusCode, headers, body);
        }

        public RecordedResponse withBody(CapturedBody body)
        {
            return new RecordedResponse(statusCode, headers, body);
        }
    }

    public record RecordedFailure(String type, String message)
    {
        public RecordedFailure
        {
            requireNonNull(type, "type is null");
            requireNonNull(message, "message is null");
        }
    }

    public record CapturedBody(byte[] bytes, CaptureState state, boolean truncated)
    {
        public CapturedBody
        {
            bytes = requireNonNull(bytes, "bytes is null").clone();
            requireNonNull(state, "state is null");
            checkArgument(state != CaptureState.UNSUPPORTED || (bytes.length == 0 && !truncated), "unsupported body cannot contain captured bytes");
        }

        @Override
        public byte[] bytes()
        {
            return bytes.clone();
        }
    }

    public enum CaptureState
    {
        COMPLETE,
        INCOMPLETE,
        UNSUPPORTED,
    }

    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> headers)
    {
        requireNonNull(headers, "headers is null");
        ImmutableMap.Builder<String, List<String>> copy = ImmutableMap.builder();
        headers.forEach((name, values) -> copy.put(requireNonNull(name, "header name is null"), ImmutableList.copyOf(values)));
        return copy.buildOrThrow();
    }
}
