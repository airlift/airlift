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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestBearerTokenProvider
{
    @Test
    void testFixedToken()
    {
        BearerTokenProvider provider = BearerTokenProvider.fixedToken("token");

        assertThat(provider.getToken()).isEqualTo("token");
        assertThat(provider.refreshToken("token")).isFalse();
    }

    @Test
    void testFixedTokenRejectsNull()
    {
        assertThatThrownBy(() -> BearerTokenProvider.fixedToken(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("token is null");
    }
}
