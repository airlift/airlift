/*
 * Copyright 2010 Proofpoint, Inc.
 *
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

import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.airlift.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static io.airlift.http.client.HttpStatus.MULTI_STATUS;
import static io.airlift.http.client.HttpStatus.NO_CONTENT;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StatusResponseHandler.createSuccessfulStatusResponseHandler;
import static io.airlift.http.client.testing.TestingResponse.mockResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStatusResponseHandler
{
    @Test
    void testValidatesStatusAndCapturesFailureBody()
    {
        StatusResponseHandler handler = createStatusResponseHandler(NO_CONTENT.code());

        assertThat(handler.handle(null, mockResponse(NO_CONTENT, JSON_UTF_8, "")).getStatusCode())
                .isEqualTo(NO_CONTENT.code());

        assertThatThrownBy(() -> handler.handle(null, mockResponse(INTERNAL_SERVER_ERROR, JSON_UTF_8, "{\"error\":true}")))
                .isInstanceOf(UnexpectedResponseException.class)
                .satisfies(exception -> assertThat(((UnexpectedResponseException) exception).getResponseBody())
                        .isEqualTo("{\"error\":true}"));
    }

    @Test
    void testCompatibilityHandlerAcceptsAnyStatus()
    {
        assertThat(createStatusResponseHandler()
                .handle(null, mockResponse(INTERNAL_SERVER_ERROR, JSON_UTF_8, "{\"error\":true}"))
                .getStatusCode())
                .isEqualTo(INTERNAL_SERVER_ERROR.code());
    }

    @Test
    void testSuccessfulStatusHandlerAcceptsEntireTwoHundredRange()
    {
        assertThat(createSuccessfulStatusResponseHandler()
                .handle(null, mockResponse(MULTI_STATUS, JSON_UTF_8, ""))
                .getStatusCode())
                .isEqualTo(MULTI_STATUS.code());

        assertThatThrownBy(() -> createSuccessfulStatusResponseHandler()
                .handle(null, mockResponse(INTERNAL_SERVER_ERROR, JSON_UTF_8, "")))
                .isInstanceOf(UnexpectedResponseException.class);
    }
}
