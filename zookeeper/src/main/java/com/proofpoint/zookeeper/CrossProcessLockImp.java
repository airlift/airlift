package com.proofpoint.zookeeper;

import com.proofpoint.crossprocess.CrossProcessLock;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * Implements a cross-process lock/mutex using Zookeeper
 */
class CrossProcessLockImp implements CrossProcessLock
{
    private final ZooKeeper         zookeeper;
    private final String            basePath;
    private final String            path;
    private final Watcher           watcher;

    private volatile String lockPath;

    private static final String     LOCK_NAME = "lock-";

    /**
     * Allocate a lock for the given path. The mutex locks on other ZKLocks with the same
     * path in this JVM or other JVMs connected to the same Zookeeper cluster
     *
     * @param zookeeper the zookeeper client
     * @param name lock path
     */
    CrossProcessLockImp(ZooKeeper zookeeper, String name)
    {
        this.zookeeper = zookeeper;
        basePath = name;
        this.path = makePath(name);
        watcher = new Watcher()
        {
            @Override
            public void process(WatchedEvent watchedEvent)
            {
                notifyFromWatcher();
            }
        };

        lockPath = null;
    }

    @Override
    public void lockInterruptibly() throws InterruptedException
    {
        try
        {
            internalLock(true, -1, null);
        }
        catch ( InterruptedException e )
        {
            throw e;
        }
        catch ( Exception e )
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Condition newCondition()
    {
        throw new UnsupportedOperationException();
    }

    /**
     * NOTE: this lock is actually interruptible. I can't think of a reason for it not to be.
     */
    @Override
    public synchronized void lock()
    {
        try
        {
            internalLock(true, -1, null);
        }
        catch ( Exception e )
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException
    {
        try
        {
            internalLock(true, time, unit);
        }
        catch ( Exception e )
        {
            throw new RuntimeException(e);
        }
        return isLocked();
    }

    @Override
    public synchronized boolean tryLock()
    {
        try
        {
            internalLock(false, -1, null);
        }
        catch ( InterruptedException e )
        {
            throw new RuntimeException(e);
        }
        catch ( Exception e )
        {
            throw new RuntimeException(e);
        }
        return isLocked();
    }

    @Override
    public synchronized boolean isLocked()
    {
        return lockPath != null;
    }

    @Override
    public synchronized void unlock()
    {
        if ( lockPath == null )
        {
            throw new Error("You do not own the lock: " + basePath);
        }

        try
        {
            zookeeper.delete(lockPath, -1);
            lockPath = null;

            // attempt to delete the parent node so that sequence numbers get reset
            try
            {
                Stat        stat = zookeeper.exists(path, false);
                if ( (stat != null) && (stat.getNumChildren() == 0) )
                {
                    zookeeper.delete(basePath, -1);
                }
            }
            catch ( KeeperException.BadVersionException ignore )
            {
                // ignore - another thread/process got the lock
            }
            catch ( KeeperException.NotEmptyException ignore )
            {
                // ignore - other threads/processes are waiting
            }
        }
        catch ( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);  // TODO - is this correct or should it just return?
        }
        catch ( KeeperException e )
        {
            throw new RuntimeException(e);
        }
    }

    private synchronized void notifyFromWatcher()
    {
        notifyAll();
    }

    private void internalLock(boolean blocking, long time, TimeUnit unit) throws Exception
    {
        if ( isLocked() )
        {
            throw new IllegalStateException("Thread already owns the lock");
        }

        long        startMillis = System.currentTimeMillis();
        Long        millisToWait = (unit != null) ? TimeUnit.MILLISECONDS.convert(time, unit) : null;

        ZookeeperUtils.mkdirs(zookeeper, basePath);
        String      ourPath = zookeeper.create(path, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        try
        {
            while ( lockPath == null )
            {
                List<String>    children = ZookeeperUtils.getSortedChildren(zookeeper, basePath);
                String          sequenceNodeName = ourPath.substring(basePath.length() + 1); // +1 to include the slash
                int             ourIndex = children.indexOf(sequenceNodeName);
                if ( ourIndex < 0 )
                {
                    throw new Exception("Sequential path not found: " + ourPath);
                }

                if ( ourIndex == 0 )
                {
                    // we have the lock
                    lockPath = ourPath;
                }
                else if ( blocking )
                {
                    String  previousSequenceNodeName = children.get(ourIndex - 1);
                    String  previousSequencePath = basePath + "/" + previousSequenceNodeName;
                    synchronized(this)
                    {
                        Stat    stat = zookeeper.exists(previousSequencePath, watcher);
                        if ( stat != null )
                        {
                            if ( millisToWait != null )
                            {
                                millisToWait -= (System.currentTimeMillis() - startMillis);
                                startMillis = System.currentTimeMillis();
                                if ( millisToWait <= 0 )
                                {
                                    break;
                                }

                                wait(millisToWait);
                            }
                            else
                            {
                                wait();
                            }
                        }
                        // else it may have been deleted (i.e. lock released). Try to acquire again
                    }
                }
                else
                {
                    // didn't get it and we're not blocking... delete our lock file 
                    zookeeper.delete(ourPath, -1);
                    break;
                }
            }
        }
        catch ( Exception e )
        {
            zookeeper.delete(ourPath, -1);
            throw e;
        }
    }

    private static String makePath(String path)
    {
        if ( !path.endsWith("/") )
        {
            path += "/";
        }
        return path + LOCK_NAME;
    }
}
