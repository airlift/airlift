package io.airlift.http.client;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;


// This code was forked from Apache CXF CacheControlHeaderProviderTest
public class TestCacheControl
{

    @Test
    public void testFromSimpleString()
    {
        CacheControl c = CacheControl.valueOf("public,must-revalidate");
        assertFalse(c.isPrivate());
        assertFalse(c.isNoStore());
        assertTrue(c.isMustRevalidate());
        assertFalse(c.isProxyRevalidate());
        assertFalse(c.isNoCache());
        assertFalse(c.isNoTransform());
        assertTrue(c.getNoCacheFields().size() == 0);
        assertTrue(c.getPrivateFields().size() == 0);
    }

    @Test
    public void testFromComplexString()
    {
        CacheControl c = CacheControl.valueOf("private=\"foo\",no-cache=\"bar\",no-store,no-transform,must-revalidate,proxy-revalidate,max-age=2,s-maxage=3");
        assertTrue(c.isPrivate());
        assertTrue(c.isNoStore());
        assertTrue(c.isMustRevalidate());
        assertTrue(c.isProxyRevalidate());
        assertTrue(c.isNoCache());
        assertTrue(c.isNoTransform());
        assertTrue(c.getNoCacheFields().size() == 1);
        assertTrue(c.getPrivateFields().size() == 1);
        assertEquals(c.getPrivateFields().get(0), "foo");
        assertEquals(c.getNoCacheFields().get(0), "bar");

    }

    @Test
    public void testToString()
    {
        String expected = "private=\"foo\",no-cache=\"bar\",no-store,no-transform,must-revalidate,proxy-revalidate,max-age=2,s-maxage=3";
        String parsed = CacheControl.valueOf(expected).toString();
        assertEquals(parsed, expected);
    }

    @Test
    public void testNoCacheEnabled()
    {
        CacheControl cc = new CacheControl();
        cc.setNoCache(true);
        assertEquals(cc.toString(), "no-cache,no-transform");
    }

    @Test
    public void testNoCacheDisabled()
    {
        CacheControl cc = new CacheControl();
        cc.setNoCache(false);
        assertEquals(cc.toString(), "no-transform");
    }

    @Test
    public void testMultiplePrivateFields()
    {
        CacheControl cc = new CacheControl();
        cc.setPrivate(true);
        cc.getPrivateFields().add("a");
        cc.getPrivateFields().add("b");
        assertTrue(cc.toString().contains("private=\"a,b\""));
    }

    @Test
    public void testMultipleNoCacheFields()
    {
        CacheControl cc = new CacheControl();
        cc.setNoCache(true);
        cc.getNoCacheFields().add("c");
        cc.getNoCacheFields().add("d");
        assertTrue(cc.toString().contains("no-cache=\"c,d\""));
    }

    @Test
    public void testReadMultiplePrivateAndNoCacheFields()
    {
        String s = "private=\"foo1,foo2\",no-store,no-transform,must-revalidate,proxy-revalidate,max-age=2,s-maxage=3,no-cache=\"bar1,bar2\",ext=1";
        CacheControl cacheControl = CacheControl.valueOf(s);

        assertTrue(cacheControl.isPrivate());
        List<String> privateFields = cacheControl.getPrivateFields();
        assertEquals(privateFields.size(), 2);
        assertEquals(privateFields.get(0), "foo1");
        assertEquals(privateFields.get(1), "foo2");
        assertTrue(cacheControl.isNoCache());
        List<String> noCacheFields = cacheControl.getNoCacheFields();
        assertEquals(2, noCacheFields.size());
        assertEquals(noCacheFields.get(0), "bar1");
        assertEquals(noCacheFields.get(1), "bar2");

        assertTrue(cacheControl.isNoStore());
        assertTrue(cacheControl.isNoTransform());
        assertTrue(cacheControl.isMustRevalidate());
        assertTrue(cacheControl.isProxyRevalidate());
        assertEquals(cacheControl.getMaxAge(), 2);
        assertEquals(cacheControl.getSMaxAge(), 3);

        Map<String, String> cacheExtension = cacheControl.getCacheExtension();
        assertEquals(cacheExtension.size(), 1);
        assertEquals(cacheExtension.get("ext"), "1");
    }

    @Test
    public void testCacheExtensionToString()
    {
        CacheControl cc = new CacheControl();
        cc.getCacheExtension().put("ext1", null);
        cc.getCacheExtension().put("ext2", "value2");
        cc.getCacheExtension().put("ext3", "value 3");
        String value = cc.toString();
        assertTrue(value.contains("ext1") && !value.contains("ext1="));
        assertTrue(value.contains("ext2=value2"));
        assertTrue(value.contains("ext3=\"value 3\""));
    }
}
