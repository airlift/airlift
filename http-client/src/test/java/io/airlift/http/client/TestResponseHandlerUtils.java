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
import com.google.common.net.MediaType;
import io.airlift.http.client.testing.TestingResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.ResponseHandlerUtils.isJsonUtf8Content;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class TestResponseHandlerUtils
{
    @Test
    public void testNoContentType()
    {
        Response response = new TestingResponse(HttpStatus.OK, ImmutableListMultimap.of(), body());
        assertThat(isJsonUtf8Content(response)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "application/json",
            "application/JSON",
            "APPLICATION/JSON",
            "application/json; charset=utf-8",
            "application/json;charset=utf-8",
            "application/json; charset=UTF-8",
            "application/json;charset=UTF-8",
            "application/json; charset=\"utf-8\"",
            "application/json; charset=iso-8859-1",
            "application/json; charset=us-ascii",
            "application/json; foo=bar",
            "application/json; charset=utf-8; foo=bar",
            "application/jsonx",
            "application/json-patch+json",
            "text/json",
            "text/plain",
            "application/xml",
            "application/json ",
            " application/json",
            "application/json ; charset=utf-8",
            "application/json;charset=utf-8 ",
            "*/*",
            "not a media type",
            "",
    })
    public void testMatchesReferenceImplementation(String contentType)
    {
        // The optimized implementation must produce exactly the same observable outcome (return
        // value or thrown exception) as the reference MediaType-parse-only implementation.
        Outcome optimized = capture(() -> isJsonUtf8Content(response(contentType)));
        Outcome reference = capture(() -> reference(contentType));
        assertThat(optimized)
                .as("content type: <%s>", contentType)
                .isEqualTo(reference);
    }

    // The original, MediaType-parse-only implementation, used as the source of truth.
    private static boolean reference(String contentType)
    {
        MediaType type = MediaType.parse(contentType);
        return type.type().equals("application")
                && type.subtype().equals("json")
                && type.charset().toJavaUtil().map(UTF_8::equals).orElse(true);
    }

    private static Response response(String contentType)
    {
        return new TestingResponse(HttpStatus.OK, ImmutableListMultimap.of(CONTENT_TYPE, contentType), body());
    }

    private static Outcome capture(BooleanSupplier supplier)
    {
        try {
            return new Outcome(supplier.getAsBoolean(), null);
        }
        catch (RuntimeException e) {
            return new Outcome(false, e.getClass());
        }
    }

    private record Outcome(boolean value, Class<?> thrown) {}

    private interface BooleanSupplier
    {
        boolean getAsBoolean();
    }

    private static byte[] body()
    {
        return "{}".getBytes(UTF_8);
    }
}
