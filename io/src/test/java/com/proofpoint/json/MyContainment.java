package com.proofpoint.json;

public class MyContainment
{
    private final MyPojo p;
    private final String s;

    public MyContainment(MyPojo p, String s)
    {
        this.p = p;
        this.s = s;
    }

    public MyPojo getP()
    {
        return p;
    }

    public String getS()
    {
        return s;
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

        MyContainment that = (MyContainment) o;

        if (p != null ? !p.equals(that.p) : that.p != null) {
            return false;
        }
        //noinspection RedundantIfStatement
        if (s != null ? !s.equals(that.s) : that.s != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = p != null ? p.hashCode() : 0;
        result = 31 * result + (s != null ? s.hashCode() : 0);
        return result;
    }
}
