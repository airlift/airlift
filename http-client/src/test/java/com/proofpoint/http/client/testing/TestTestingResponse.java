package com.proofpoint.http.client.testing;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import com.proofpoint.http.client.HttpStatus;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.testing.TestingResponse.Builder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.io.ByteStreams.toByteArray;
import static com.proofpoint.http.client.testing.TestingResponse.mockResponse;
import static org.testng.Assert.assertEquals;

public class TestTestingResponse
{
    @Test
    public void testStatus()
    {
        assertResponse(mockResponse(HttpStatus.IM_A_TEAPOT), HttpStatus.IM_A_TEAPOT, ImmutableListMultimap.of(), new byte[0]);
    }

    @Test
    public void testStringContent()
    {
        assertResponse(mockResponse(HttpStatus.ENHANCE_YOUR_CALM, MediaType.TEXT_JAVASCRIPT_UTF_8, "neé"),
                HttpStatus.ENHANCE_YOUR_CALM,
                ImmutableListMultimap.of("Content-Type", "text/javascript; charset=utf-8"),
                new byte[] { 'n', 'e', -61, -87}
        );
    }

    @Test
    public void testBuilderDefault()
    {
        assertResponse(mockResponse().build(), HttpStatus.NO_CONTENT, ImmutableListMultimap.of(), new byte[0]);
    }

    @Test
    public void testBuilderStatus()
    {
        assertResponse(mockResponse().status(HttpStatus.IM_A_TEAPOT).build(), HttpStatus.IM_A_TEAPOT, ImmutableListMultimap.of(), new byte[0]);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testBuilderStatusTwiceThrowsException()
    {
        mockResponse().status(HttpStatus.IM_A_TEAPOT).status(HttpStatus.IM_A_TEAPOT);
    }

    @Test
    public void testBuilderHeader()
    {
        assertResponse(
                mockResponse()
                        .header("Foo", "one")
                        .header("Bar", "two")
                        .header("Foo", "three")
                        .build(),
                HttpStatus.NO_CONTENT,
                ImmutableListMultimap.of(
                        "Foo", "one",
                        "Bar", "two",
                        "Foo", "three"
                ),
                new byte[0]
        );
    }

    @Test
    public void testBuilderHeaders()
    {
        assertResponse(
                mockResponse()
                        .headers(ImmutableListMultimap.of(
                                "Foo", "one",
                                "Bar", "two",
                                "Foo", "three"
                        ))
                        .build(),
                HttpStatus.NO_CONTENT,
                ImmutableListMultimap.of(
                        "Foo", "one",
                        "Bar", "two",
                        "Foo", "three"
                ),
                new byte[0]
        );
    }

    @Test
    public void testBuilderByteBody()
    {
        byte[] input = { 0, 1, 5 };
        byte[] expected = { 0, 1, 5 };
        TestingResponse response = mockResponse().body(input).build();
        input[0] = 9;
        assertResponse(response,
                HttpStatus.OK, ImmutableListMultimap.<String, String>of(), expected);

        assertResponse(mockResponse().body(new byte[0]).build(),
                HttpStatus.OK, ImmutableListMultimap.<String, String>of(), new byte[0]);
    }

    @Test
    public void testBuilderInputStreamBody()
    {
        TestingResponse response = mockResponse().body(new ByteArrayInputStream(new byte[] {0, 1, 5})).build();
        assertResponse(response,
                HttpStatus.OK, ImmutableListMultimap.<String, String>of(), new byte[] { 0, 1, 5 });

        assertResponse(mockResponse().body(new ByteArrayInputStream(new byte[0])).build(),
                HttpStatus.OK, ImmutableListMultimap.<String, String>of(), new byte[0]);
    }

    @Test(dataProvider = "bodyMethodPossibleCombinations", expectedExceptions = IllegalStateException.class)
    public void testBuilderMultipleBodiesThrowsException(Function<Builder, Builder> firstBody, Function<Builder, Builder> secondBody)
    {
        Builder builder = firstBody.apply(mockResponse());
        secondBody.apply(builder);
    }

    @DataProvider(name = "bodyMethodPossibleCombinations")
    public Object[][] bodyMethodPossibleCombinations()
    {
        Set<Function<Builder, Builder>> bodyMethods = ImmutableSet.of(
                builder -> builder.body(new byte[0]),
                builder -> builder.body(new ByteArrayInputStream(new byte[0]))
        );

        List<Object[]> returnValues = new ArrayList<>();
        for (Function<Builder, Builder> firstMethod : bodyMethods) {
            for (Function<Builder, Builder> secondMethod : bodyMethods) {
                returnValues.add(new Object[] {firstMethod, secondMethod});
            }
        }

        return returnValues.toArray(new Object[][]{});
    }

    private static void assertResponse(Response response, HttpStatus status, ImmutableListMultimap<String, String> headers, byte[] body)
    {
        assertEquals(response.getStatusCode(), status.code());
        assertEquals(response.getHeaders(), headers);
        try {
            assertEquals(toByteArray(response.getInputStream()), body);
        }
        catch (IOException e) {
            propagate(e);
        }
    }
}
