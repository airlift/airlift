package com.proofpoint.zookeeper;

public interface ChildDataListener
{
    void added(String child, byte[] data);
    void updated(String child, byte[] data);
    void removed(String child);
}
