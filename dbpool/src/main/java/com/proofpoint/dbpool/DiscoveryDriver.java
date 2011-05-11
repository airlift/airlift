package com.proofpoint.dbpool;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationLoader;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.discovery.client.CachingServiceSelector;
import com.proofpoint.discovery.client.DiscoveryClient;
import com.proofpoint.discovery.client.DiscoveryModule;
import com.proofpoint.discovery.client.ServiceSelectorConfig;
import com.proofpoint.json.JsonModule;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeModule;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeoutException;

public class DiscoveryDriver implements Driver
{
    private static final String URL_PREFIX = "jdbc:discovery:";
    private static final Object lock = new Object();
    private static final DiscoveryDriver DISCOVERY_DRIVER = new DiscoveryDriver();
    private static final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(10,
            new ThreadFactoryBuilder().setNameFormat("DatabaseDiscovery-%s").setDaemon(true).build());

    private static DiscoveryClient discoveryClient;
    private static boolean registered;

    static {
        try {
            register();
        }
        catch (SQLException e) {
            Logger.get(DiscoveryDriver.class).error(e, "Unable to register %s with the DriverManager", DiscoveryDriver.class.getName());
        }
    }

    private static final Map<DataSourceKey, DataSource> dataSources = new MapMaker().makeComputingMap(
            new Function<DataSourceKey, DataSource>()
            {
                public DataSource apply(DataSourceKey dataSourceKey)
                {
                    ConnectionProperties connectionProperties = new ConnectionProperties(dataSourceKey.url, dataSourceKey.properties);


                    CachingServiceSelector serviceSelector = new CachingServiceSelector(
                            connectionProperties.type,
                            new ServiceSelectorConfig().setPool(connectionProperties.pool),
                            getDiscoveryClient(),
                            executorService);
                    try {
                        serviceSelector.start();
                    }
                    catch (TimeoutException ignored) {
                    }
                    MySqlDataSourceConfig mySqlDataSourceConfig = new ConfigurationFactory(connectionProperties.properties).build(MySqlDataSourceConfig.class);
                    return new MySqlDataSource(serviceSelector, mySqlDataSourceConfig);
                }
            }
    );

    public static void setDiscoveryClient(DiscoveryClient discoveryClient)
    {
        Preconditions.checkNotNull(discoveryClient, "discoveryClient is null");
        synchronized (lock) {
            DiscoveryDriver.discoveryClient = discoveryClient;
            dataSources.clear();
        }
    }

    @Override
    public Connection connect(String url, Properties info)
            throws SQLException
    {
        if (!acceptsURL(url)) {
            return null;
        }
        if (info == null) {
            info = new Properties();
        }
        try {
            DataSource dataSource = dataSources.get(new DataSourceKey(url, info));
            return dataSource.getConnection();
        }
        catch (Exception e) {
            SQLException sqlException = Iterables.getFirst(Iterables.filter(Throwables.getCausalChain(e), SQLException.class), null);
            if (sqlException != null) {
                throw sqlException;
            }
            throw new SQLException(e);
        }
    }

    @Override
    public boolean acceptsURL(String url)
            throws SQLException
    {
        return url != null && url.startsWith(URL_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
            throws SQLException
    {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion()
    {
        return 0;
    }

    @Override
    public int getMinorVersion()
    {
        return 41;
    }

    @Override
    public boolean jdbcCompliant()
    {
        // sure why not
        return true;
    }

    public static void register()
            throws SQLException
    {
        synchronized (lock) {
            if (registered) {
                return;
            }

            DriverManager.registerDriver(DISCOVERY_DRIVER);
            registered = true;
        }
    }

    public static void deregister()
            throws SQLException
    {
        synchronized (lock) {
            if (!registered) {
                return;
            }

            DriverManager.deregisterDriver(DISCOVERY_DRIVER);
            registered = false;
        }
    }

    private static DiscoveryClient getDiscoveryClient()
    {
        synchronized (lock) {
            if (discoveryClient == null) {
                ConfigurationLoader loader = new ConfigurationLoader();

                Map<String, String> configFileProperties = Collections.emptyMap();
                String configFile = System.getProperty("config");
                if (configFile != null) {
                    try {
                        configFileProperties = loader.loadPropertiesFrom(configFile);
                    }
                    catch (IOException e) {
                        throw new RuntimeException("Unable to load configuration file " + configFile);
                    }
                }

                SortedMap<String, String> properties = Maps.newTreeMap();
                properties.putAll(configFileProperties);
                properties.putAll(loader.getSystemProperties());
                properties = ImmutableSortedMap.copyOf(properties);

                ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);

                Injector injector = Guice.createInjector(
                        new NodeModule(),
                        new DiscoveryModule(),
                        new JsonModule(),
                        new ConfigurationModule(configurationFactory)
                );

                discoveryClient = injector.getInstance(DiscoveryClient.class);
            }
            return discoveryClient;
        }
    }

    private static class DataSourceKey
    {
        private final String url;
        private final Map<String, String> properties;

        private DataSourceKey(String url, Properties properties)
        {
            Preconditions.checkNotNull(url, "url is null");
            Preconditions.checkNotNull(properties, "properties is null");

            this.url = url;
            if (properties != null) {
                this.properties = ImmutableMap.<String,String>copyOf((Map) properties);
            }
            else {
                this.properties = ImmutableMap.of();
            }
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DataSourceKey that = (DataSourceKey) o;

            if (!properties.equals(that.properties)) {
                return false;
            }
            if (!url.equals(that.url)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = url.hashCode();
            result = 31 * result + properties.hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("DataSourceKey");
            sb.append("{url='").append(url).append('\'');
            sb.append(", info=").append(properties);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class ConnectionProperties
    {
        private final String pool;
        private final String type;
        private final Map<String, String> properties;

        private ConnectionProperties(String url, Map<String,String> properties)
        {
            Preconditions.checkNotNull(url, "url is null");
            Preconditions.checkNotNull(properties, "properties is null");
            try {
                URI uri = new URI(url.substring("jdbc:" .length()));
                List<String> poolType = ImmutableList.copyOf(Splitter.on('.').split(uri.getAuthority()));
                if (poolType.size() > 2) {
                    throw new IllegalArgumentException(uri.getAuthority());
                }

                if (poolType.size() == 2) {
                    pool = poolType.get(0);
                    type = poolType.get(1);
                }
                else {
                    pool = ServiceSelectorConfig.DEFAULT_POOL;
                    type = poolType.get(0);
                }

                Builder<String, String> builder = ImmutableMap.builder();
                builder.putAll(properties);

                String queryString = uri.getRawQuery();
                if (queryString != null) {
                    for (String entry : Splitter.on('&').split(queryString)) {
                        List<String> pair = ImmutableList.copyOf(Splitter.on('=').limit(2).split(entry));
                        String key = URLDecoder.decode(pair.get(0), Charsets.UTF_8.displayName());
                        String value;
                        if (pair.size() > 1) {
                            value = URLDecoder.decode(pair.get(1), Charsets.UTF_8.displayName());
                        }
                        else {
                            value = "";
                        }
                        // explicit properties take precedence over url properties
                        if (!properties.containsKey(key)) {
                            builder.put(key, value);
                        }
                    }
                }

                this.properties = builder.build();
            }
            catch (Exception e) {
                throw new RuntimeException(new SQLException(String.format("Invalid connection url '%s'", url)));
            }
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ConnectionProperties that = (ConnectionProperties) o;

            if (!pool.equals(that.pool)) {
                return false;
            }
            if (!properties.equals(that.properties)) {
                return false;
            }
            if (!type.equals(that.type)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = pool.hashCode();
            result = 31 * result + type.hashCode();
            result = 31 * result + properties.hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("ConnectionProperties");
            sb.append("{pool='").append(pool).append('\'');
            sb.append(", type='").append(type).append('\'');
            sb.append(", properties=").append(properties);
            sb.append('}');
            return sb.toString();
        }
    }
}
