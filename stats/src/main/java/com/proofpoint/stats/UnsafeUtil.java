package com.proofpoint.stats;

import com.google.common.base.Throwables;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;

class UnsafeUtil
{
    private static final Unsafe unsafe;
    private static final int ALIGNMENT = 8; // assume alignment to 8-byte boundary
    private static final int BASE_SIZE;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);

            BASE_SIZE = align((int) unsafe.objectFieldOffset(Dummy.class.getDeclaredField("dummy")), ALIGNMENT);
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static int sizeOf(Class<?> clazz)
    {
        // find the offset of the last field
        long maxOffset = -1;
        Field lastField = null;
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    long offset = unsafe.objectFieldOffset(field);
                    if (offset > maxOffset) {
                        lastField = field;
                        maxOffset = offset;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }

        // compute the size of a value of type type of the last field
        if (lastField != null) {
            Class<?> fieldType = lastField.getType();
            Class<?> arrayType;
            if (fieldType == boolean.class) {
                arrayType = boolean[].class;
            }
            else if (fieldType == byte.class) {
                arrayType = byte[].class;
            }
            else if (fieldType == short.class) {
                arrayType = short[].class;
            }
            else if (fieldType == int.class) {
                arrayType = int[].class;
            }
            else if (fieldType == long.class) {
                arrayType = long[].class;
            }
            else if (fieldType == float.class) {
                arrayType = float[].class;
            }
            else if (fieldType == double.class) {
                arrayType = double[].class;
            }
            else {
                arrayType = Object[].class;
            }

            return align((int) (maxOffset + unsafe.arrayIndexScale(arrayType)), ALIGNMENT);
        }

        // object has no fields, so return the size of an empty object
        return BASE_SIZE;
    }

    private static int align(int size, int alignment)
    {
        return ((size / alignment) + 1) * alignment;
    }

    // used to determine the size of an empty object
    private static class Dummy
    {
        byte dummy;
    }
}
