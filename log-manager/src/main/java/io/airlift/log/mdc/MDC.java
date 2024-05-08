
package io.airlift.log.mdc;

import java.util.HashMap;
import java.util.Map;

public class MDC
{
    static final ThreadLocal<Map<String, String>> contextMap = ThreadLocal.withInitial(HashMap::new);

    private MDC()
    {
    }

    public static void put(String key, String val)
    {
        contextMap.get().put(key, val);
    }

    public static String get(String key)
    {
        return contextMap.get().get(key);
    }

    public static void remove(String key)
    {
        contextMap.get().remove(key);
    }

    public static void clear()
    {
        contextMap.get().clear();
    }

    public static Map<String, String> getCopyOfContextMap()
    {
        return new HashMap<String, String>(contextMap.get());
    }
}
