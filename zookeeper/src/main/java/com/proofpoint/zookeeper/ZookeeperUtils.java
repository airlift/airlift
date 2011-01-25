package com.proofpoint.zookeeper;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.common.PathUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Various utilities to use with Zookeeper
 */
public class ZookeeperUtils
{
    /**
     * Return the children of the given path sorted by sequence number
     *
     * @param zookeeper the client
     * @param path the path
     * @return sorted list of children
     * @throws InterruptedException thread interruption
     * @throws org.apache.zookeeper.KeeperException zookeeper errors
     */
    static List<String> getSortedChildren(ZooKeeper zookeeper, String path)
            throws InterruptedException, KeeperException
    {
        List<String> children = zookeeper.getChildren(path, false);
        List<String> sortedList = new ArrayList<String>(children);
        Collections.sort(sortedList);
        return sortedList;
    }

    /**
     * Make sure all the nodes in the path are created. NOTE: Unlike File.mkdirs(), Zookeeper doesn't distinguish
     * between directories and files. So, every node in the path is created. The data for each node is an empty blob
     *
     * @param zookeeper the client
     * @param path path to ensure
     * @throws InterruptedException thread interruption
     * @throws org.apache.zookeeper.KeeperException Zookeeper errors
     */
    public static void mkdirs(ZooKeeper zookeeper, String path)
            throws InterruptedException, KeeperException
    {
        PathUtils.validatePath(path);

        int pos = 1; // skip first slash, root is guaranteed to exist
        do {
            pos = path.indexOf('/', pos + 1);

            if (pos == -1) {
                pos = path.length();
            }

            String subPath = path.substring(0, pos);
            if (zookeeper.exists(subPath, false) == null) {
                try {
                    zookeeper.create(subPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                }
                catch (KeeperException.NodeExistsException e) {
                    // ignore... someone else has created it since we checked
                }
            }

        }
        while (pos < path.length());
    }

    /**
     * Given a parent path and a child node, create a combined full path
     *
     * @param parent the parent
     * @param child the child
     * @return full path
     */
    static String makePath(String parent, String child)
    {
        if (child.length() == 0) {
            throw new UnsupportedOperationException();
        }

        StringBuilder path = new StringBuilder();

        if (!parent.startsWith("/")) {
            path.append("/");
        }
        path.append(parent);
        if (!parent.endsWith("/")) {
            path.append("/");
        }

        if (child.startsWith("/")) {
            path.append(child.substring(1));
        }
        else {
            path.append(child);
        }

        return path.toString();
    }

    private ZookeeperUtils()
    {
    }
}
