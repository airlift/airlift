package io.airlift.configuration;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.airlift.configuration.ConfigurationLoader.loadPropertiesFrom;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;

public class TestTomlConfigurationLoader
{
    @Test
    public void testToml()
    {
        Map<String, String> properties = loadToml("""
                # This is a TOML document
                
                title = "TOML Example"
                
                [owner]
                name = "Tom Preston-Werner"
                dob = 1979-05-27T07:32:00-08:00
                
                [database]
                enabled = true
                ports = [ 8000, 8001, 8002 ]
                data = [ ["delta", "phi"], [3.14] ]
                temp_targets = { cpu = 79.5, case = 72.0 }
                
                [servers]
                
                [servers.alpha]
                ip = "10.0.0.1"
                role = "frontend"
                
                [servers.beta]
                ip = "10.0.0.2"
                role = "backend"
                secret = "${ENV:SECRET_VALUE}"
                """);

        assertEquals(properties.get("title"), "TOML Example");
        assertEquals(properties.get("owner.name"), "Tom Preston-Werner");
        assertEquals(properties.get("database.enabled"), "true");
        assertEquals(properties.get("database.data"), "delta,phi,3.14");
        assertEquals(properties.get("database.ports"), "8000,8001,8002");
        assertEquals(properties.get("database.temp_targets.cpu"), "79.5");
        assertEquals(properties.get("servers"), null);
        assertEquals(properties.get("servers.alpha.ip"), "10.0.0.1");
        assertEquals(properties.get("servers.beta.secret"), "${ENV:SECRET_VALUE}");
    }

    public Map<String, String> loadToml(String toml)
    {
        try {
            Path tempFile = Files.createTempFile("toml", ".toml");
            tempFile.toFile().deleteOnExit();
            Files.write(tempFile, toml.getBytes(UTF_8));
            return loadPropertiesFrom(tempFile.toString());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
