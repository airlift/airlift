package com.proofpoint.zookeeper;

import com.google.common.base.Predicate;
import com.google.inject.Inject;
import com.proofpoint.concurrent.events.EventQueue;
import com.proofpoint.crossprocess.CrossProcessLock;
import org.apache.hadoop.io.retry.RetryProxy;
import org.apache.zookeeper.AsyncCallback;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;

/**
 * A wrapper around ZooKeeper that makes SK more manageable and adds some higher level features
 */
public class ZookeeperClient implements ZookeeperClientHelper
{
    private final AtomicBoolean                     started;
    private final EventQueue<ZookeeperEvent>        eventQueue;
    private final AtomicReference<State>            stateRef;
    private final ZookeeperClientCreator            creator;
    private final CreateMode                        createMode;
    private final boolean                           inBackground;
    private final Object                            key;
    private final boolean                           watched;
    private final int                               dataVersion;
    private final Object                            context;
    private final Map<EventQueue.EventListener<ZookeeperEvent>, EventQueue.EventListener<ZookeeperEvent>>   externalToInternalListenerMap = new ConcurrentHashMap<EventQueue.EventListener<ZookeeperEvent>, EventQueue.EventListener<ZookeeperEvent>>();
    private final AtomicReference<ZookeeperClientErrorHandler> errorHandler = new AtomicReference<ZookeeperClientErrorHandler>(null);

    private volatile ZooKeeper client;

    private enum State
    {
        WAITING_FOR_STARTUP,
        STARTUP_FAILED,
        STARTUP_SUCCEEDED,
        ZOMBIE_MODE
    }

    @Inject
    public ZookeeperClient(ZookeeperClientCreator creator) throws IOException
    {
        this
        (
            new AtomicBoolean(false),
            null,
            new EventQueue<ZookeeperEvent>(0),
            // no wait for events - process immediately
            new AtomicReference<State>(State.WAITING_FOR_STARTUP),
            creator,
            CreateMode.PERSISTENT, 
            false,
            null,
            false,
            -1,
            null,
            null
        );
    }

    /**
     * Change/set the error handler for this client
     *
     * @param errorHandler handler
     */
    @Inject(optional = true)
    public void     setErrorHandler(ZookeeperClientErrorHandler errorHandler)
    {
        this.errorHandler.set(errorHandler);
    }

    private ZookeeperClient
        (
            AtomicBoolean started,
            ZooKeeper client,
            EventQueue<ZookeeperEvent> eventQueue,
            AtomicReference<State> stateRef,
            ZookeeperClientCreator creator,
            CreateMode createMode,
            boolean inBackground,
            Object key,
            boolean watched,
            int dataVersion,
            Object context,
            ZookeeperClientErrorHandler errorHandler
        )
    {
        this.started = started;
        this.client = client;
        this.eventQueue = eventQueue;
        this.stateRef = stateRef;
        this.creator = creator;
        this.createMode = createMode;
        this.inBackground = inBackground;
        this.key = key;
        this.watched = watched;
        this.dataVersion = dataVersion;
        this.context = context;
        this.errorHandler.set(errorHandler);
    }

    @Override
    public ZookeeperClientHelper withCreateMode(CreateMode createMode)
    {
        return new ZookeeperClient(started, client, eventQueue, stateRef, creator, createMode, inBackground, key, watched, dataVersion, context, errorHandler.get());
    }

    @Override
    public ZookeeperClientHelper    inBackground(Object key)
    {
        return new ZookeeperClient(started, client, eventQueue, stateRef, creator, createMode, true, key, watched, dataVersion, context, errorHandler.get());
    }

    @Override
    public ZookeeperClientHelper    watched()
    {
        return new ZookeeperClient(started, client, eventQueue, stateRef, creator, createMode, inBackground, key, true, dataVersion, context, errorHandler.get());
    }

    @Override
    public ZookeeperClientHelper    withContext(Object contextArg)
    {
        return new ZookeeperClient(started, client, eventQueue, stateRef, creator, createMode, inBackground, key, watched, dataVersion, contextArg, errorHandler.get());
    }

    @Override
    public ZookeeperClientHelper    dataVersion(int version)
    {
        return new ZookeeperClient(started, client, eventQueue, stateRef, creator, createMode, inBackground, key, watched, version, context, errorHandler.get());
    }

    @PreDestroy
    public void closeForShutdown()
    {
        try
        {
            client.close();
        }
        catch ( InterruptedException e )
        {
            Thread.currentThread().interrupt();
        }
    }

    @PostConstruct
    public void start() throws Exception
    {
        if ( started.compareAndSet(false, true) )
        {
            client = creator.create();
        }
    }

    /**
     * Make this client a NOP zombie
     */
    public void setZombie()
    {
        stateRef.set(State.ZOMBIE_MODE);
    }

    /**
     * Add an event listener that listens for events starting with the given path and/or use the given key. i.e. if the
     * event's path starts with <code>basePath</code> or the event's key is equal to <code>backgroundKey</code> then the
     * event will be sent to the listener
     *
     * @param listener listener
     * @param basePath base path - can be null
     * @param backgroundKey background key (see {@link #inBackground(Object)}) - can be null 
     */
    public void     addListener(final EventQueue.EventListener<ZookeeperEvent> listener, final String basePath, final Object backgroundKey)
    {
        addListener(listener, new Predicate<ZookeeperEvent>()
        {
            @Override
            public boolean apply(ZookeeperEvent event)
            {
                boolean     applies = true;
                do
                {
                    if ( (basePath != null) && (event.getPath() != null) )
                    {
                        if ( !event.getPath().startsWith(basePath) )
                        {
                            applies = false;
                            break;
                        }
                    }

                    if ( (backgroundKey != null) && (event.getKey() != null) )
                    {
                        if ( !event.getKey().equals(backgroundKey) )
                        {
                            applies = false;
                            break;
                        }
                    }
                } while ( false );

                return applies;
            }
        });
    }

    /**
     * Add an event queue listener
     *
     * @param listener the listener
     * @param predicate functor that decides which events to pass through to the listener
     */
    public void     addListener(final EventQueue.EventListener<ZookeeperEvent> listener, final Predicate<ZookeeperEvent> predicate)
    {
        EventQueue.EventListener<ZookeeperEvent>    internalListener = new EventQueue.EventListener<com.proofpoint.zookeeper.ZookeeperEvent>()
        {
            @Override
            public void eventProcessed(ZookeeperEvent event) throws Exception
            {
                if ( predicate.apply(event) )
                {
                    listener.eventProcessed(event);
                }
            }
        };
        externalToInternalListenerMap.put(listener, internalListener);
        eventQueue.addListener(internalListener);
    }

    /**
     * Remove an event queue listener
     *
     * @param listener the listener to remove
     */
    public void     removeListener(EventQueue.EventListener<ZookeeperEvent> listener)
    {
        EventQueue.EventListener<ZookeeperEvent>    internalListener = externalToInternalListenerMap.remove(listener);
        if ( internalListener != null )
        {
            eventQueue.removeListener(internalListener);
        }
    }

    @Override
    public List<String> getChildren(final String path) throws Exception
    {
        if ( !waitForStart() )
        {
            return new ArrayList<String>();
        }

        if ( inBackground )
        {
            client.getChildren
            (
                path,
                watched,
                new AsyncCallback.Children2Callback()
                {
                    @Override
                    public void processResult(int rc, String path, Object ctx, List<String> children, Stat stat)
                    {
                        eventQueue.postEvent(new ZookeeperEvent(ZookeeperEvent.Type.GET_CHILDREN, rc, path, ctx, null, stat, null, children, key));
                    }
                },
                context
            );
            return null;
        }

        return withRetry(new Callable<List<String>>()
        {
            @Override
            public List<String> call() throws Exception
            {
                return client.getChildren(path, watched);
            }
        });
    }

    @Override
    public DataAndStat getDataAndStat(final String path) throws Exception
    {
        if ( !waitForStart() )
        {
            return null;
        }

        if ( inBackground )
        {
            client.getData
            (
                path,
                watched,
                new AsyncCallback.DataCallback()
                {
                    @Override
                    public void processResult(int rc, String path, Object ctx, byte[] data, Stat stat)
                    {
                        eventQueue.postEvent(new ZookeeperEvent(ZookeeperEvent.Type.GET_DATA, rc, path, ctx, data, stat, null, null, key));
                    }
                },
                context
            );
            return null;
        }

        return withRetry(new Callable<DataAndStat>()
        {
            @Override
            public DataAndStat call() throws Exception
            {
                final Stat stat = new Stat();
                final byte[] data = client.getData(path, watched, stat);
                return new DataAndStat()
                {
                    @Override
                    public Stat getStat()
                    {
                        return stat;
                    }

                    @Override
                    public byte[] getData()
                    {
                        return data;
                    }
                };
            }
        });
    }

    @Override
    public byte[] getData(final String path) throws Exception
    {
        DataAndStat dataAndStat = getDataAndStat(path);
        return (dataAndStat != null) ? dataAndStat.getData() : null;
    }

    @Override
    public String create(final String path, final byte data[]) throws Exception
    {
        if ( !waitForStart() )
        {
            return null;
        }

        if ( inBackground )
        {
            BackgroundRetryHandler.Call     backgroundCall = new BackgroundRetryHandler.Call()
            {
                @Override
                public void call(final BackgroundRetryHandler retryHandler)
                {
                    client.create
                    (
                        path,
                        data,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        createMode,
                        new AsyncCallback.StringCallback()
                        {
                            @Override
                            public void processResult(int rc, String path, Object ctx, String name)
                            {
                                if ( !retryHandler.handled(rc) )
                                {
                                    eventQueue.postEvent(new ZookeeperEvent(ZookeeperEvent.Type.CREATE, rc, path, ctx, null, null, name, null, key));
                                }
                            }
                        },
                        context
                    );
                }
            };
            BackgroundRetryHandler.makeAndStart(this, creator.getRetryPolicy(), backgroundCall);

            return null;
        }

        return withRetry(new Callable<String>()
        {
            @Override
            public String call() throws Exception
            {
                return client.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, createMode);
            }
        });
    }

    @Override
    public Stat exists(final String path) throws Exception
    {
        if ( !waitForStart() )
        {
            return null;
        }

        if ( inBackground )
        {
            BackgroundRetryHandler.Call     backgroundCall = new BackgroundRetryHandler.Call()
            {
                @Override
                public void call(final BackgroundRetryHandler retryHandler)
                {
                    client.exists
                    (
                        path,
                        watched,
                        new AsyncCallback.StatCallback()
                        {
                            @Override
                            public void processResult(int rc, String path, Object ctx, Stat stat)
                            {
                                if ( !retryHandler.handled(rc) )
                                {
                                    eventQueue.postEvent(new ZookeeperEvent(ZookeeperEvent.Type.EXISTS, rc, path, ctx, null, stat, null, null, key));
                                }
                            }
                        },
                        context
                    );
                }
            };
            BackgroundRetryHandler.makeAndStart(this, creator.getRetryPolicy(), backgroundCall);
            return null;
        }

        return withRetry(new Callable<Stat>()
        {
            @Override
            public Stat call() throws Exception
            {
                return client.exists(path, watched);
            }
        });
    }

    @Override
    public void sync(final String path) throws Exception
    {
        if ( !waitForStart() )
        {
            return;
        }

        if ( !inBackground )
        {
            throw new Exception("sync() must be called inBackground()");
        }

        BackgroundRetryHandler.Call     backgroundCall = new BackgroundRetryHandler.Call()
        {
            @Override
            public void call(final BackgroundRetryHandler retryHandler)
            {
                client.sync
                (
                    path,
                    new AsyncCallback.VoidCallback()
                    {
                        @Override
                        public void processResult(int rc, String path, Object ctx)
                        {
                            if ( !retryHandler.handled(rc) )
                            {
                                eventQueue.postEvent(new ZookeeperEvent(ZookeeperEvent.Type.SYNC, rc, path, ctx, null, null, null, null, key));
                            }
                        }
                    },
                    context
                );
            }
        };
        BackgroundRetryHandler.makeAndStart(this, creator.getRetryPolicy(), backgroundCall);
    }

    @Override
    public Stat setData(final String path, final byte data[]) throws Exception
    {
        if ( !waitForStart() )
        {
            return null;
        }

        if ( inBackground )
        {
            BackgroundRetryHandler.Call     backgroundCall = new BackgroundRetryHandler.Call()
            {
                @Override
                public void call(final BackgroundRetryHandler retryHandler)
                {
                    client.setData
                    (
                        path,
                        data,
                        dataVersion,
                        new AsyncCallback.StatCallback()
                        {
                            @Override
                            public void processResult(int rc, String path, Object ctx, Stat stat)
                            {
                                if ( !retryHandler.handled(rc) )
                                {
                                    eventQueue.postEvent(new ZookeeperEvent(ZookeeperEvent.Type.SET_DATA, rc, path, ctx, null, stat, null, null, key));
                                }
                            }
                        },
                        context
                    );
                }
            };
            BackgroundRetryHandler.makeAndStart(this, creator.getRetryPolicy(), backgroundCall);
            return null;
        }

        return withRetry(new Callable<Stat>()
        {
            @Override
            public Stat call() throws Exception
            {
                return client.setData(path, data, dataVersion);
            }
        });
    }

    @Override
    public void delete(final String path) throws Exception
    {
        if ( !waitForStart() )
        {
            return;
        }

        if ( inBackground )
        {
            BackgroundRetryHandler.Call     backgroundCall = new BackgroundRetryHandler.Call()
            {
                @Override
                public void call(final BackgroundRetryHandler retryHandler)
                {
                    client.delete
                    (
                        path,
                        dataVersion,
                        new AsyncCallback.VoidCallback()
                        {
                            @Override
                            public void processResult(int rc, String path, Object ctx)
                            {
                                if ( !retryHandler.handled(rc) )
                                {
                                    eventQueue.postEvent(new ZookeeperEvent(ZookeeperEvent.Type.DELETE, rc, path, ctx, null, null, null, null, key));
                                }
                            }
                        },
                        context
                    );
                }
            };
            BackgroundRetryHandler.makeAndStart(this, creator.getRetryPolicy(), backgroundCall);
        }
        else
        {
            withRetry(new Callable<Object>()
            {
                @Override
                public Object call() throws Exception
                {
                    client.delete(path, dataVersion);
                    return null;
                }
            });
        }
    }

    /**
     * Return the children of the given path sorted by sequence number
     *
     * @param path the path
     * @return sorted list of children
     * @throws Exception errors
     */
    public List<String> getSortedChildren(String path) throws Exception
    {
        if ( !waitForStart() )
        {
            return new ArrayList<String>();
        }

        return ZookeeperUtils.getSortedChildren(client, path);
    }

    /**
     * Make sure all the nodes in the path are created. NOTE: Unlike File.mkdirs(), Zookeeper doesn't distinguish
     * between directories and files. So, every node in the path is created. The data for each node is an empty blob
     *
     * @param path      path to ensure
     * @throws Exception errors
     */
    public void mkdirs(String path) throws Exception
    {
        if ( !waitForStart() )
        {
            return;
        }

        ZookeeperUtils.mkdirs(client, path);
    }

    /**
     * Given a parent path and a child node, create a combined full path
     *
     * @param parent the parent
     * @param child the child
     * @return full path
     */
    public String    makePath(String parent, String child)
    {
        return ZookeeperUtils.makePath(parent, child);
    }
    
    /**
     * Given a parent path and a child node, create a combined full path
     *
     * @param parent the parent
     * @param child the child
     * @return full path
     */
    public static String    staticMakePath(String parent, String child)
    {
        return ZookeeperUtils.makePath(parent, child);
    }

    CrossProcessLock newLock(final String path) throws Exception
    {
        return new CrossProcessLock()
        {
            @Override
            public void lock()
            {
                getLock().lock();
            }

            @Override
            public boolean isLocked() throws Exception
            {
                return getLock().isLocked();
            }

            @Override
            public boolean tryLock()
            {
                return getLock().tryLock();
            }

            @Override
            public void unlock()
            {
                getLock().unlock();
            }

            @Override
            public void lockInterruptibly() throws InterruptedException
            {
                getLock().lockInterruptibly();
            }

            @Override
            public boolean tryLock(long time, TimeUnit unit) throws InterruptedException
            {
                return getLock().tryLock(time, unit);
            }

            @Override
            public Condition newCondition()
            {
                return getLock().newCondition();
            }

            private synchronized CrossProcessLock getLock()
            {
                try
                {
                    waitForStart();
                    if ( lock == null )
                    {
                        lock = new CrossProcessLockImp(client, path);
                    }
                    return lock;
                }
                catch ( Exception e )
                {
                    throw new RuntimeException(e);
                }
            }

            private CrossProcessLock lock = null;
        };
    }

    void errorConnectionLost()
    {
        ZookeeperClientErrorHandler localErrorHandler = errorHandler.get();
        if ( localErrorHandler != null )
        {
            localErrorHandler.connectionLost(this);
        }
    }

    private ZookeeperEvent.Type getTypeFromWatched(WatchedEvent event)
    {
        switch ( event.getType() )
        {
            case None:
            {
                return ZookeeperEvent.Type.WATCHED_NONE;
            }

            case NodeCreated:
            {
                return ZookeeperEvent.Type.WATCHED_NODE_CREATED;
            }

            case NodeDeleted:
            {
                return ZookeeperEvent.Type.WATCHED_NODE_DELETED;
            }

            case NodeDataChanged:
            {
                return ZookeeperEvent.Type.WATCHED_NODE_DATA_CHANGED;
            }

            case NodeChildrenChanged:
            {
                return ZookeeperEvent.Type.WATCHED_NODE_CHILDREN_CHANGED;
            }
        }
        return ZookeeperEvent.Type.WATCHED_NONE;
    }

    private boolean waitForStart() throws Exception
    {
        if ( !started.get() ) {
            throw new IllegalStateException("start() must be called before other APIs are available");
        }

        boolean     result;
        switch ( stateRef.get() )
        {
            default:
            case WAITING_FOR_STARTUP:
            {
                result = internalWaitForStart();
                break;
            }

            case STARTUP_FAILED:
            case ZOMBIE_MODE:
            {
                result = false;
                break;
            }

            case STARTUP_SUCCEEDED:
            {
                result = true;
                break;
            }
        }

        return result;
    }

    private boolean internalWaitForStart()
    {
        try
        {
            Watcher watcher = new Watcher()
            {
                @Override
                public void process(WatchedEvent event)
                {
                    if ( (event.getState() == Event.KeeperState.Disconnected) || (event.getState() == Event.KeeperState.Expired) )
                    {
                        errorConnectionLost();
                    }
                    else
                    {
                        eventQueue.postEvent(new ZookeeperEvent(getTypeFromWatched(event), 0, event.getPath(), null, null, null, null, null, null));
                    }
                }
            };
            if ( creator.waitForStart(client, watcher) == ZookeeperClientCreator.ConnectionStatus.SUCCESS )
            {
                stateRef.compareAndSet(State.WAITING_FOR_STARTUP, State.STARTUP_SUCCEEDED);
            }
            else
            {
                stateRef.set(State.STARTUP_FAILED);
                errorConnectionLost();
            }
        }
        catch ( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            stateRef.set(State.STARTUP_FAILED);
        }

        return stateRef.get() == State.STARTUP_SUCCEEDED;
    }

    private<T> T withRetry(Callable<T> proc) throws Exception
    {
        //noinspection unchecked
        Callable<T> proxy = (Callable<T>)RetryProxy.create(Callable.class, proc, creator.getRetryPolicy());
        return proxy.call();
    }

}
