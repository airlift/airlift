package com.proofpoint.configuration;

import com.google.common.collect.ImmutableSortedSet;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.proofpoint.formatting.ColumnPrinter;
import com.proofpoint.guice.GuiceInjectorIterator;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Utility to output all the Configuration properties used in an Injection instance.
 * The property names used, the default values for each and the runtime values for each
 * are displayed (sorted by property name).
 * <br>
 * <br>
 *
 * To use, call: <code>injector.getInstance(ConfigInspector.class).print()</code><br>
 * This outputs via a logger. You can optionally pass a stream to capture the output.
 */
public class ConfigurationInspector
{
    private static final String     PROPERTY_NAME_COLUMN = "PROPERTY";
    private static final String     DEFAULT_VALUE_COLUMN = "DEFAULT";
    private static final String     CURRENT_VALUE_COLUMN = "RUNTIME";
    private static final String     DESCRIPTION_COLUMN = "DESCRIPTION";

    private final Set<ConfigRecord> configs;

    private static class ConfigRecord implements Comparable<ConfigRecord>
    {
        final String        propertyName;
        final String        defaultValue;
        final String currentValue;
        final String        description;

        @Override
        public int compareTo(ConfigRecord rhs)
        {
            return propertyName.compareTo(rhs.propertyName);
        }

        @Override
        public boolean equals(Object o)
        {
            if ( this == o )
            {
                return true;
            }
            if ( o == null || getClass() != o.getClass() )
            {
                return false;
            }

            ConfigRecord that = (ConfigRecord)o;

            //noinspection RedundantIfStatement
            if ( !propertyName.equals(that.propertyName) )
            {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            return propertyName.hashCode();
        }

        private ConfigRecord(String propertyName, String defaultValue, String currentValue, String description)
        {
            this.propertyName = propertyName;
            this.defaultValue = defaultValue;
            this.currentValue = currentValue;
            this.description = description;
        }
    }

    @Inject
    public ConfigurationInspector(Injector injector, ConfigurationFactory factory) throws InvocationTargetException, IllegalAccessException
    {
        ImmutableSortedSet.Builder<ConfigRecord>    builder = ImmutableSortedSet.naturalOrder();
        GuiceInjectorIterator                       injectorIterator = new GuiceInjectorIterator(injector);
        for ( Class<?> clazz : injectorIterator )
        {
            addConfig(factory, clazz, builder);
        }

        configs = builder.build();
    }

    /**
     * Print the config details to the given stream
     *
     * @param out stream
     */
    public void     print(PrintWriter out)
    {
        ColumnPrinter columnPrinter = makePrinter();
        columnPrinter.print(out);
        out.flush();
    }

    private ColumnPrinter makePrinter()
    {
        ColumnPrinter       columnPrinter = new ColumnPrinter();

        columnPrinter.addColumn(PROPERTY_NAME_COLUMN);
        columnPrinter.addColumn(DEFAULT_VALUE_COLUMN);
        columnPrinter.addColumn(CURRENT_VALUE_COLUMN);
        columnPrinter.addColumn(DESCRIPTION_COLUMN);

        for ( ConfigRecord record : configs )
        {
            columnPrinter.addValue(PROPERTY_NAME_COLUMN, record.propertyName);
            columnPrinter.addValue(DEFAULT_VALUE_COLUMN, record.defaultValue);
            columnPrinter.addValue(CURRENT_VALUE_COLUMN, record.currentValue);
            columnPrinter.addValue(DESCRIPTION_COLUMN, record.description);
        }
        return columnPrinter;
    }

    private void addConfig(ConfigurationFactory factory, Class clazz, ImmutableSortedSet.Builder<ConfigRecord> builder) throws InvocationTargetException, IllegalAccessException
    {
        Object      instance = null;
        for ( Method method : clazz.getMethods() )
        {
            Config configAnnotation = method.getAnnotation(Config.class);
            if ( configAnnotation != null )
            {
                if ( instance == null )
                {
                    //noinspection unchecked
                    instance = factory.build(clazz);
                }

                ConfigDescription   descriptionAnnotation = method.getAnnotation(ConfigDescription.class);
                Default             defaultAnnotation = method.getAnnotation(Default.class);

                String      defaultValue = (defaultAnnotation != null) ? ("\"" + defaultAnnotation.value() + "\"") : "-- none --";
                String      currentValue = (method.getParameterTypes().length == 0) ? ("\"" + String.valueOf(method.invoke(instance)) + "\"") : "-- n/a --";
                String      description = (descriptionAnnotation != null) ? descriptionAnnotation.value() : "";

                builder.add(new ConfigRecord(configAnnotation.value(), defaultValue, currentValue, description));
            }
        }
    }
}
