package com.proofpoint.zookeeper;

public interface ChildDataListener
{
    void added(String child, byte[] data) throws Exception;
    void updated(String child, byte[] data) throws Exception;
    void removed(String child) throws Exception;
}
