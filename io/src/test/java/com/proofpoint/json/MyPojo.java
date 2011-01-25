package com.proofpoint.json;

public class MyPojo
{
    private final String str;
    private final int i;
    private final long l;
    private final double d;

    public MyPojo(String str, int i, long l, double d)
    {
        this.str = str;
        this.i = i;
        this.l = l;
        this.d = d;
    }

    public String getStr()
    {
        return str;
    }

    public int getI()
    {
        return i;
    }

    public long getL()
    {
        return l;
    }

    public double getD()
    {
        return d;
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

        MyPojo myPojo = (MyPojo) o;

        if (Double.compare(myPojo.d, d) != 0) {
            return false;
        }
        if (i != myPojo.i) {
            return false;
        }
        if (l != myPojo.l) {
            return false;
        }
        //noinspection RedundantIfStatement
        if (str != null ? !str.equals(myPojo.str) : myPojo.str != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result;
        long temp;
        result = str != null ? str.hashCode() : 0;
        result = 31 * result + i;
        result = 31 * result + (int) (l ^ (l >>> 32));
        temp = d != +0.0d ? Double.doubleToLongBits(d) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
