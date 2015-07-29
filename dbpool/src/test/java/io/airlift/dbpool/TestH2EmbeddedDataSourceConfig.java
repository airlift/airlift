package io.airlift.dbpool;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import javax.validation.constraints.NotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.airlift.dbpool.H2EmbeddedDataSourceConfig.AllowLiterals;
import static io.airlift.dbpool.H2EmbeddedDataSourceConfig.Cipher;
import static io.airlift.dbpool.H2EmbeddedDataSourceConfig.CompressLob;
import static io.airlift.testing.ValidationAssertions.assertFailsValidation;

public class TestH2EmbeddedDataSourceConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(H2EmbeddedDataSourceConfig.class)
                .setAllowLiterals(AllowLiterals.ALL)
                .setCacheSize(16384)
                .setCipher(Cipher.NONE)
                .setCompressLob(CompressLob.LZF)
                .setFilename(null)
                .setFilePassword(null)
                .setInitScript(null)
                .setMaxLengthInplaceLob(1024)
                .setMaxMemoryRows(10000)
                .setMvccEnabled(true)
                .setMaxConnections(10)
                .setMaxConnectionWait(new Duration(500, TimeUnit.MILLISECONDS)));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("db.allow-literals", "NONE")
                .put("db.cache-size", "4096")
                .put("db.cipher", "AES")
                .put("db.compress-lob", "NO")
                .put("db.filename", "TestData")
                .put("db.file-password", "test123")
                .put("db.init-script", "init.sql")
                .put("db.inplace.lob.length.max", "8192")
                .put("db.rows.memory.max", "5000")
                .put("db.mvcc.enabled", "FALSE")
                .put("db.connections.max", "12")
                .put("db.connections.wait", "42s")
                .build();

        H2EmbeddedDataSourceConfig expected = new H2EmbeddedDataSourceConfig()
                .setAllowLiterals(AllowLiterals.NONE)
                .setCacheSize(4096)
                .setCipher(Cipher.AES)
                .setCompressLob(CompressLob.NO)
                .setFilename("TestData")
                .setFilePassword("test123")
                .setInitScript("init.sql")
                .setMaxLengthInplaceLob(8192)
                .setMaxMemoryRows(5000)
                .setMvccEnabled(false)
                .setMaxConnections(12)
                .setMaxConnectionWait(new Duration(42, TimeUnit.SECONDS));

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testValidations()
    {
        assertFailsValidation(new H2EmbeddedDataSourceConfig(), "filename", "may not be null", NotNull.class);
    }
}
