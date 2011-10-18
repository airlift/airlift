package com.proofpoint.log;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestLoggingMBean
{
    private final LoggingMBean logging = new LoggingMBean();
    private String rootLevel;

    @BeforeMethod
    public void setRootLevel()
    {
        rootLevel = logging.getRootLevel();
        logging.setRootLevel("INFO");
    }

    @AfterMethod
    public void restoreRootLevel()
    {
        logging.setRootLevel(rootLevel);
    }

    @Test
    public void testGetAndSetRoot()
    {
        assertEquals(logging.getRootLevel(), "INFO");

        logging.setRootLevel("WARN");
        assertEquals(logging.getRootLevel(), "WARN");

        logging.setRootLevel("INFO");
        assertEquals(logging.getRootLevel(), "INFO");
    }

    @Test
    public void testGetAndSetNonExisting()
    {
        assertEquals(logging.getRootLevel(), "INFO");

        String name = "this.logger.does.not.exist.yet.Bogus";
        assertFalse(logging.getAllLevels().containsKey(name));
        assertEquals(logging.getLevel(name), "INFO");
        logging.setLevel(name, "WARN");
        assertEquals(logging.getLevel(name), "WARN");
        assertTrue(logging.getAllLevels().containsKey(name));

        assertEquals(logging.getRootLevel(), "INFO");
    }

    @Test
    public void testSetInvalidLevel()
    {
        assertEquals(logging.getRootLevel(), "INFO");
        logging.setRootLevel("FOO");
        assertEquals(logging.getRootLevel(), "DEBUG");
    }
}
