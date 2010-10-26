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
     * @param path lock path
     */
    CrossProcessLockImp(ZooKeeper zookeeper, String path)
    {
        this.zookeeper = zookeeper;
        basePath = path;
        this.path = makePath(path);
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
    public synchronized void lock() throws Exception
    {
        internalLock(true);
    }

    @Override
    public synchronized boolean tryLock() throws Exception
    {
        internalLock(false);
        return lockPath != null;
    }

    @Override
    public synchronized boolean isLocked()
    {
        return lockPath != null;
    }

    @Override
    public synchronized void unlock() throws Exception
    {
        if ( lockPath == null )
        {
            throw new Error("You do not own the lock: " + basePath);
        }

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

    private synchronized void notifyFromWatcher()
    {
        notifyAll();
    }

    private void internalLock(boolean blocking) throws Exception
    {
        if ( lockPath != null )
        {
            // already has the lock
            return;
        }

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
                            wait();
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
