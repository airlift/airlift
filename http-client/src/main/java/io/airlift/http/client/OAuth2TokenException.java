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
import jakarta.annotation.Nullable;

import java.net.URI;
import java.util.Optional;

public final class OAuth2TokenException
        extends UnexpectedResponseException
{
    private final Optional<String> error;
    private final Optional<String> errorDescription;
    private final Optional<URI> errorUri;

    private OAuth2TokenException(Builder builder)
    {
        super(builder.response);
        this.error = Optional.ofNullable(builder.error);
        this.errorDescription = Optional.ofNullable(builder.errorDescription);
        this.errorUri = Optional.ofNullable(builder.errorUri);
    }

    static Builder builder(String message, int statusCode, Request request)
    {
        return new Builder(message, statusCode, request);
    }

    public Optional<String> getError()
    {
        return error;
    }

    public Optional<String> getErrorDescription()
    {
        return errorDescription;
    }

    public Optional<URI> getErrorUri()
    {
        return errorUri;
    }

    static final class Builder
    {
        private final UnexpectedResponseException.Builder response;
        private String error;
        private String errorDescription;
        private URI errorUri;

        private Builder(String message, int statusCode, Request request)
        {
            this.response = UnexpectedResponseException.builder(message, statusCode)
                    .setRequest(request);
        }

        public Builder setHeaders(ListMultimap<HeaderName, String> headers)
        {
            response.setHeaders(headers);
            return this;
        }

        public Builder setResponseBody(byte[] responseBody, boolean truncated)
        {
            response.setResponseBody(responseBody, truncated);
            return this;
        }

        public Builder setCause(@Nullable Throwable cause)
        {
            response.setCause(cause);
            return this;
        }

        public Builder setError(@Nullable String error)
        {
            this.error = error;
            return this;
        }

        public Builder setErrorDescription(@Nullable String errorDescription)
        {
            this.errorDescription = errorDescription;
            return this;
        }

        public Builder setErrorUri(@Nullable URI errorUri)
        {
            this.errorUri = errorUri;
            return this;
        }

        public OAuth2TokenException build()
        {
            return new OAuth2TokenException(this);
        }
    }
}
