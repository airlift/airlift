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

import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;

@FunctionalInterface
public interface RecordedExchangeSanitizer
{
    String REDACTED = "[REDACTED]";

    RecordedExchange sanitize(RecordedExchange exchange);

    default RecordedExchangeSanitizer andThen(RecordedExchangeSanitizer after)
    {
        requireNonNull(after, "after is null");
        return exchange -> after.sanitize(sanitize(exchange));
    }

    static RecordedExchangeSanitizer redactSensitiveHeaders()
    {
        return exchange -> exchange
                .withRequest(exchange.request().withHeaders(redactHeaders(exchange.request().headers())))
                .withResponse(exchange.response().map(response -> response.withHeaders(redactHeaders(response.headers()))));
    }

    private static Map<String, List<String>> redactHeaders(Map<String, List<String>> headers)
    {
        ImmutableMap.Builder<String, List<String>> sanitized = ImmutableMap.builder();
        headers.forEach((name, values) -> sanitized.put(
                name,
                isSensitive(name) ? ImmutableList.copyOf(values.stream().map(_ -> REDACTED).toList()) : values));
        return sanitized.buildOrThrow();
    }

    private static boolean isSensitive(String name)
    {
        String lowerCase = name.toLowerCase(ENGLISH);
        return lowerCase.equals("authorization") ||
                lowerCase.equals("proxy-authorization") ||
                lowerCase.equals("cookie") ||
                lowerCase.equals("set-cookie") ||
                lowerCase.equals("api-key") ||
                lowerCase.equals("x-api-key") ||
                lowerCase.equals("token") ||
                lowerCase.endsWith("-token");
    }
}
