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
package io.airlift.security.jwks;

import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Response;
import io.airlift.http.client.testing.TestingHttpClient;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import java.net.URI;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.airlift.http.client.testing.TestingResponse.mockResponse;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestJwksService
{
    private static final String EMPTY_KEYS = "{ \"keys\": [] }";
    private static final String TEST_JWKS_RESPONSE = """
            {
              "keys": [
                {
                  "e": "AQAB",
                  "n": "mvj-0waJ2owQlFWrlC06goLs9PcNehIzCF0QrkdsYZJXOsipcHCFlXBsgQIdTdLvlCzNI07jSYA-zggycYi96lfDX-FYv_CqC8dRLf9TBOPvUgCyFMCFNUTC69hsrEYMR_J79Wj0MIOffiVr6eX-AaCG3KhBMZMh15KCdn3uVrl9coQivy7bk2Uw-aUJ_b26C0gWYj1DnpO4UEEKBk1X-lpeUMh0B_XorqWeq0NYK2pN6CoEIh0UrzYKlGfdnMU1pJJCsNxMiha-Vw3qqxez6oytOV_AswlWvQc7TkSX6cHfqepNskQb7pGxpgQpy9sA34oIxB_S-O7VS7_h0Qh4vQ",
                  "alg": "RS256",
                  "use": "sig",
                  "kty": "RSA",
                  "kid": "test-rsa"
                },
                {
                  "kty": "EC",
                  "use": "sig",
                  "crv": "P-256",
                  "kid": "test-ec",
                  "x": "W9pnAHwUz81LldKjL3BzxO1iHe1Pc0fO6rHkrybVy6Y",
                  "y": "XKSNmn_xajgOvWuAiJnWx5I46IwPVJJYPaEpsX3NPZg",
                  "alg": "ES256"
                },
                {
                  "alg": "RS256",
                  "kty": "RSA",
                  "use": "sig",
                  "x5c": [
                       "MIIC+DCCAeCgAwIBAgIJBIGjYW6hFpn2MA0GCSqGSIb3DQEBBQUAMCMxITAfBgNVBAMTGGN1c3RvbWVyLWRlbW9zLmF1dGgwLmNvbTAeFw0xNjExMjIyMjIyMDVaFw0zMDA4MDEyMjIyMDVaMCMxITAfBgNVBAMTGGN1c3RvbWVyLWRlbW9zLmF1dGgwLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMnjZc5bm/eGIHq09N9HKHahM7Y31P0ul+A2wwP4lSpIwFrWHzxw88/7Dwk9QMc+orGXX95R6av4GF+Es/nG3uK45ooMVMa/hYCh0Mtx3gnSuoTavQEkLzCvSwTqVwzZ+5noukWVqJuMKNwjL77GNcPLY7Xy2/skMCT5bR8UoWaufooQvYq6SyPcRAU4BtdquZRiBT4U5f+4pwNTxSvey7ki50yc1tG49Per/0zA4O6Tlpv8x7Red6m1bCNHt7+Z5nSl3RX/QYyAEUX1a28VcYmR41Osy+o2OUCXYdUAphDaHo4/8rbKTJhlu8jEcc1KoMXAKjgaVZtG/v5ltx6AXY0CAwEAAaMvMC0wDAYDVR0TBAUwAwEB/zAdBgNVHQ4EFgQUQxFG602h1cG+pnyvJoy9pGJJoCswDQYJKoZIhvcNAQEFBQADggEBAGvtCbzGNBUJPLICth3mLsX0Z4z8T8iu4tyoiuAshP/Ry/ZBnFnXmhD8vwgMZ2lTgUWwlrvlgN+fAtYKnwFO2G3BOCFw96Nm8So9sjTda9CCZ3dhoH57F/hVMBB0K6xhklAc0b5ZxUpCIN92v/w+xZoz1XQBHe8ZbRHaP1HpRM4M7DJk2G5cgUCyu3UBvYS41sHvzrxQ3z7vIePRA4WF4bEkfX12gvny0RsPkrbVMXX1Rj9t6V7QXrbPYBAO+43JvDGYawxYVvLhz+BJ45x50GFQmHszfY3BR9TPK8xmMmQwtIvLu1PMttNCs7niCYkSiUv2sc2mlq1i3IashGkkgmo="
                  ],
                  "n": "yeNlzlub94YgerT030codqEztjfU_S6X4DbDA_iVKkjAWtYfPHDzz_sPCT1Axz6isZdf3lHpq_gYX4Sz-cbe4rjmigxUxr-FgKHQy3HeCdK6hNq9ASQvMK9LBOpXDNn7mei6RZWom4wo3CMvvsY1w8tjtfLb-yQwJPltHxShZq5-ihC9irpLI9xEBTgG12q5lGIFPhTl_7inA1PFK97LuSLnTJzW0bj096v_TMDg7pOWm_zHtF53qbVsI0e3v5nmdKXdFf9BjIARRfVrbxVxiZHjU6zL6jY5QJdh1QCmENoejj_ytspMmGW7yMRxzUqgxcAqOBpVm0b-_mW3HoBdjQ",
                  "e": "AQAB",
                  "kid": "test-certificate-chain",
                  "x5t": "NjVBRjY5MDlCMUIwNzU4RTA2QzZFMDQ4QzQ2MDAyQjVDNjk1RTM2Qg"
                }
              ]
            }""";

    @Test
    public void testSuccess()
    {
        HttpClient httpClient = new TestingHttpClient(request -> mockResponse(HttpStatus.OK, JSON_UTF_8, TEST_JWKS_RESPONSE));
        JwksService service = new JwksService(URI.create("http://example.com"), httpClient, new Duration(1, DAYS));
        assertTestKeys(service);
    }

    @Test
    public void testReload()
    {
        AtomicReference<Response> response = new AtomicReference<>(mockResponse(HttpStatus.OK, JSON_UTF_8, EMPTY_KEYS));
        JwksService service = new JwksService(URI.create("http://example.com"), new TestingHttpClient(request -> response.get()), new Duration(1, DAYS));
        assertEmptyKeys(service);

        response.set(mockResponse(HttpStatus.OK, JSON_UTF_8, EMPTY_KEYS));
        service.refreshKeys();
        assertEmptyKeys(service);

        response.set(mockResponse(HttpStatus.OK, JSON_UTF_8, TEST_JWKS_RESPONSE));
        service.refreshKeys();
        assertTestKeys(service);

        response.set(mockResponse(HttpStatus.OK, JSON_UTF_8, TEST_JWKS_RESPONSE));
        service.refreshKeys();
        assertTestKeys(service);

        response.set(mockResponse(HttpStatus.OK, JSON_UTF_8, EMPTY_KEYS));
        service.refreshKeys();
        assertEmptyKeys(service);
    }

    @Test
    public void testTimedReload()
            throws InterruptedException
    {
        AtomicReference<Supplier<Response>> response = new AtomicReference<>(() -> mockResponse(HttpStatus.OK, JSON_UTF_8, EMPTY_KEYS));
        JwksService service = new JwksService(URI.create("http://example.com"), new TestingHttpClient(request -> response.get().get()), new Duration(1, MILLISECONDS));
        assertEmptyKeys(service);

        try {
            // start service
            response.set(() -> mockResponse(HttpStatus.OK, JSON_UTF_8, EMPTY_KEYS));
            service.start();
            while (!service.getKeys().isEmpty()) {
                //noinspection BusyWait
                Thread.sleep(1000);
            }

            response.set(() -> mockResponse(HttpStatus.OK, JSON_UTF_8, TEST_JWKS_RESPONSE));
            while (service.getKeys().isEmpty()) {
                //noinspection BusyWait
                Thread.sleep(1000);
            }
            assertTestKeys(service);
        }
        finally {
            service.stop();
        }
    }

    @Test
    public void testRequestFailure()
    {
        AtomicReference<Response> response = new AtomicReference<>(mockResponse(HttpStatus.OK, JSON_UTF_8, TEST_JWKS_RESPONSE));
        JwksService service = new JwksService(
                URI.create("http://example.com"),
                new TestingHttpClient(request -> {
                    Response value = response.get();
                    if (value == null) {
                        throw new IllegalArgumentException("test");
                    }
                    return value;
                }),
                new Duration(1, DAYS));
        assertTestKeys(service);

        // request failure
        response.set(null);
        assertThatThrownBy(service::refreshKeys)
                .hasMessage("Error reading JWKS keys from http://example.com")
                .isInstanceOf(RuntimeException.class);
        assertTestKeys(service);

        // valid update
        response.set(mockResponse(HttpStatus.OK, JSON_UTF_8, EMPTY_KEYS));
        service.refreshKeys();
        assertEmptyKeys(service);
    }

    @Test
    public void testBadResponse()
    {
        AtomicReference<Response> response = new AtomicReference<>(mockResponse(HttpStatus.OK, JSON_UTF_8, TEST_JWKS_RESPONSE));
        JwksService service = new JwksService(URI.create("http://example.com"), new TestingHttpClient(request -> response.get()), new Duration(1, DAYS));
        assertTestKeys(service);

        // bad response code document
        response.set(mockResponse(HttpStatus.CREATED, JSON_UTF_8, ""));
        assertThatThrownBy(service::refreshKeys)
                .hasMessage("Unexpected response code 201 from JWKS service at http://example.com")
                .isInstanceOf(RuntimeException.class);
        assertTestKeys(service);

        // empty document
        response.set(mockResponse(HttpStatus.OK, JSON_UTF_8, ""));
        assertThatThrownBy(service::refreshKeys)
                .hasMessage("Unable to decode JWKS response from http://example.com")
                .isInstanceOf(RuntimeException.class);
        assertTestKeys(service);

        // valid update
        response.set(mockResponse(HttpStatus.OK, JSON_UTF_8, EMPTY_KEYS));
        service.refreshKeys();
        assertEmptyKeys(service);
    }

    private static void assertEmptyKeys(JwksService service)
    {
        assertEquals(service.getKeys().size(), 0);
    }

    private static void assertTestKeys(JwksService service)
    {
        Map<String, PublicKey> keys = service.getKeys();
        assertEquals(keys.size(), 3);
        assertTrue(keys.containsKey("test-rsa"));
        assertTrue(keys.containsKey("test-ec"));
        assertTrue(keys.containsKey("test-certificate-chain"));
    }
}
