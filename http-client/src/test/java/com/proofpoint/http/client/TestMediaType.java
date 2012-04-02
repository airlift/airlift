package com.proofpoint.http.client;

import org.testng.Assert;
import org.testng.annotations.Test;

import static com.proofpoint.http.client.MediaType.HTML_UTF_8;
import static com.proofpoint.http.client.MediaType.JSON_UTF_8;

public class TestMediaType
{
    @Test
    public void test()
    {
        Assert.assertTrue(MediaType.parse("application/json; charset=UTF-8").is(JSON_UTF_8));
        Assert.assertTrue(MediaType.parse("application/json;").is(JSON_UTF_8.withoutParameters()));
        Assert.assertTrue(MediaType.parse("application/json").is(JSON_UTF_8.withoutParameters()));

        Assert.assertTrue(MediaType.parse("text/html;charset=UTF-8").is(HTML_UTF_8));
        Assert.assertTrue(MediaType.parse("text/html;").is(HTML_UTF_8.withoutParameters()));
        Assert.assertTrue(MediaType.parse("text/html").is(HTML_UTF_8.withoutParameters()));
    }
}
