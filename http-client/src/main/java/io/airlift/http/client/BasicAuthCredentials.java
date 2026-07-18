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

import java.util.Base64;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.http.client.HeaderNames.AUTHORIZATION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public final class BasicAuthCredentials
{
    private final String authenticationHeader;

    public BasicAuthCredentials(String username, String password)
    {
        requireNonNull(username, "username is null");
        checkArgument(!username.contains(":"), "Illegal character ':' found in username");
        requireNonNull(password, "password is null");
        // RFC 7617 only defines UTF-8 as a charset for user-pass; the OAuth2 client_secret_basic header uses UTF-8 too
        authenticationHeader = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(UTF_8));
    }

    public void apply(Request.Builder requestBuilder)
    {
        requireNonNull(requestBuilder, "requestBuilder is null")
                .setHeader(AUTHORIZATION, authenticationHeader);
    }

    @Override
    public String toString()
    {
        return "BasicAuthCredentials{REDACTED}";
    }
}
