[◀︎ Airlift](../README.md) • [◀︎ Getting Started](getting_started.md) • [◀︎ Next Steps](next_steps.md)

## Add Logging

Airlift includes a simple logging API based on the JDK logging package.

### Step 1 - Add Needed Dependencies

We need a few additional dependencies. Add the following to the dependencies section of your
`pom.xml` file:

```xml 
        <dependency>
            <groupId>io.airlift</groupId>
            <artifactId>log</artifactId>
        </dependency>

        <dependency>
            <groupId>io.airlift</groupId>
            <artifactId>log-manager</artifactId>
        </dependency>
```

### Step 2 - Start Logging

Example logging:

```java
import io.airlift.log.Logger;

public class MyClass
{
    private static final Logger LOG = Logger.get(MyClass.class);
    
    public void fooBar(String argument)
    {
        LOG.info("Formatted output %s", argument);
    }
}
```
