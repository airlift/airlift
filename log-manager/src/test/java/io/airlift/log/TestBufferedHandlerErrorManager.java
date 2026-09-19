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
package io.airlift.log;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.ErrorManager.GENERIC_FAILURE;
import static org.assertj.core.api.Assertions.assertThat;

public class TestBufferedHandlerErrorManager
{
    @Test
    public void testReportsOnlyFirstError()
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BufferedHandlerErrorManager errorManager = new BufferedHandlerErrorManager(new PrintStream(output, true, UTF_8));

        errorManager.error("first", null, GENERIC_FAILURE);
        errorManager.error("second", null, GENERIC_FAILURE);

        assertThat(output.toString(UTF_8))
                .isEqualTo("java.util.logging.ErrorManager: 0: first" + System.lineSeparator());
    }
}
