package io.airlift.http.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// This code was forked from Apache CXF CacheControlHeaderProviderTest
public class TestCacheControl {
    @Test
    public void testFromSimpleString() {
        CacheControl c = CacheControl.valueOf("public,must-revalidate");
        assertThat(c.isPrivate()).isFalse();
        assertThat(c.isNoStore()).isFalse();
        assertThat(c.isMustRevalidate()).isTrue();
        assertThat(c.isProxyRevalidate()).isFalse();
        assertThat(c.isNoCache()).isFalse();
        assertThat(c.isNoTransform()).isFalse();
        assertThat(c.getNoCacheFields()).isEmpty();
        assertThat(c.getPrivateFields()).isEmpty();
    }

    @Test
    public void testFromComplexString() {
        CacheControl c = CacheControl.valueOf(
                "private=\"foo\",no-cache=\"bar\",no-store,no-transform,must-revalidate,proxy-revalidate,max-age=2,s-maxage=3");
        assertThat(c.isPrivate()).isTrue();
        assertThat(c.isNoStore()).isTrue();
        assertThat(c.isMustRevalidate()).isTrue();
        assertThat(c.isProxyRevalidate()).isTrue();
        assertThat(c.isNoCache()).isTrue();
        assertThat(c.isNoTransform()).isTrue();
        assertThat(c.getNoCacheFields()).hasSize(1);
        assertThat(c.getPrivateFields()).hasSize(1);
        assertThat(c.getPrivateFields().get(0)).isEqualTo("foo");
        assertThat(c.getNoCacheFields().get(0)).isEqualTo("bar");
    }

    @Test
    public void testToString() {
        String expected =
                "private=\"foo\",no-cache=\"bar\",no-store,no-transform,must-revalidate,proxy-revalidate,max-age=2,s-maxage=3";
        String parsed = CacheControl.valueOf(expected).toString();
        assertThat(parsed).isEqualTo(expected);
    }

    @Test
    public void testNoCacheEnabled() {
        CacheControl cc = new CacheControl();
        cc.setNoCache(true);
        assertThat(cc.toString()).isEqualTo("no-cache,no-transform");
    }

    @Test
    public void testNoCacheDisabled() {
        CacheControl cc = new CacheControl();
        cc.setNoCache(false);
        assertThat(cc.toString()).isEqualTo("no-transform");
    }

    @Test
    public void testMultiplePrivateFields() {
        CacheControl cc = new CacheControl();
        cc.setPrivate(true);
        cc.getPrivateFields().add("a");
        cc.getPrivateFields().add("b");
        assertThat(cc.toString()).contains("private=\"a,b\"");
    }

    @Test
    public void testMultipleNoCacheFields() {
        CacheControl cc = new CacheControl();
        cc.setNoCache(true);
        cc.getNoCacheFields().add("c");
        cc.getNoCacheFields().add("d");
        assertThat(cc.toString()).contains("no-cache=\"c,d\"");
    }

    @Test
    public void testReadMultiplePrivateAndNoCacheFields() {
        String s =
                "private=\"foo1,foo2\",no-store,no-transform,must-revalidate,proxy-revalidate,max-age=2,s-maxage=3,no-cache=\"bar1,bar2\",ext=1";
        CacheControl cacheControl = CacheControl.valueOf(s);

        assertThat(cacheControl.isPrivate()).isTrue();
        List<String> privateFields = cacheControl.getPrivateFields();
        assertThat(privateFields.size()).isEqualTo(2);
        assertThat(privateFields.get(0)).isEqualTo("foo1");
        assertThat(privateFields.get(1)).isEqualTo("foo2");
        assertThat(cacheControl.isNoCache()).isTrue();
        List<String> noCacheFields = cacheControl.getNoCacheFields();
        assertThat(noCacheFields).hasSize(2);
        assertThat(noCacheFields.get(0)).isEqualTo("bar1");
        assertThat(noCacheFields.get(1)).isEqualTo("bar2");

        assertThat(cacheControl.isNoStore()).isTrue();
        assertThat(cacheControl.isNoTransform()).isTrue();
        assertThat(cacheControl.isMustRevalidate()).isTrue();
        assertThat(cacheControl.isProxyRevalidate()).isTrue();
        assertThat(cacheControl.getMaxAge()).isEqualTo(2);
        assertThat(cacheControl.getSMaxAge()).isEqualTo(3);

        Map<String, String> cacheExtension = cacheControl.getCacheExtension();
        assertThat(cacheExtension.size()).isEqualTo(1);
        assertThat(cacheExtension.get("ext")).isEqualTo("1");
    }

    @Test
    public void testCacheExtensionToString() {
        CacheControl cc = new CacheControl();
        cc.getCacheExtension().put("ext1", null);
        cc.getCacheExtension().put("ext2", "value2");
        cc.getCacheExtension().put("ext3", "value 3");
        String value = cc.toString();
        assertThat(value).contains("ext1").doesNotContain("ext1=");
        assertThat(value).contains("ext2=value2");
        assertThat(value).contains("ext3=\"value 3\"");
    }
}
