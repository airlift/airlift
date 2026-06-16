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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.net.MediaType;
import io.airlift.http.client.testing.TestingResponse;
import io.airlift.yaml.YamlCodec;
import org.junit.jupiter.api.Test;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.airlift.http.client.HttpStatus.OK;
import static io.airlift.http.client.YamlResponseHandler.createYamlResponseHandler;
import static io.airlift.http.client.testing.TestingResponse.mockResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestYamlResponseHandler
{
    private static final MediaType APPLICATION_YAML = MediaType.create("application", "yaml").withCharset(UTF_8);

    private final YamlCodec<User> codec = YamlCodec.yamlCodec(User.class);
    private final YamlResponseHandler<User> handler = createYamlResponseHandler(codec);

    @Test
    public void testValidYaml()
    {
        User user = new User("Joe", 25);
        User response = handler.handle(null, mockResponse(OK, APPLICATION_YAML, codec.toYaml(user)));

        assertThat(response.getName()).isEqualTo(user.getName());
        assertThat(response.getAge()).isEqualTo(user.getAge());
    }

    @Test
    public void testValidYamlWithXYamlContentType()
    {
        MediaType xYaml = MediaType.create("application", "x-yaml").withCharset(UTF_8);
        User user = new User("Joe", 25);
        User response = handler.handle(null, mockResponse(OK, xYaml, codec.toYaml(user)));

        assertThat(response.getName()).isEqualTo(user.getName());
    }

    @Test
    public void testInvalidYaml()
    {
        String yaml = "age: not_a_number\n";
        assertThatThrownBy(() -> handler.handle(null, mockResponse(OK, APPLICATION_YAML, yaml)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to create");
    }

    @Test
    public void testNonYamlResponse()
    {
        assertThatThrownBy(() -> handler.handle(null, mockResponse(OK, PLAIN_TEXT_UTF_8, "hello")))
                .isInstanceOf(UnexpectedResponseException.class)
                .hasMessageContaining("Expected YAML response from server but got text/plain");
    }

    @Test
    public void testMissingContentType()
    {
        assertThatThrownBy(() -> handler.handle(null, new TestingResponse(OK, ImmutableListMultimap.of(), "hello".getBytes(UTF_8))))
                .isInstanceOf(UnexpectedResponseException.class)
                .hasMessageContaining("Expected YAML response from server but got null");
    }

    public static class User
    {
        private final String name;
        private final int age;

        @JsonCreator
        public User(@JsonProperty("name") String name, @JsonProperty("age") int age)
        {
            this.name = name;
            this.age = age;
        }

        @JsonProperty
        public String getName()
        {
            return name;
        }

        @JsonProperty
        public int getAge()
        {
            return age;
        }
    }
}
