package io.airlift.bootstrap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestConcurrentWeakIdentitySet
{
    @Test
    public void testClearedReferences()
    {
        int iterations = 100000;

        ConcurrentWeakIdentitySet weakIdentitySet = new ConcurrentWeakIdentitySet();
        for (int i = 0; i < iterations; ++i) {
            weakIdentitySet.add(new byte[1_000_000]);
        }

        // probably doesn't do anything but can't hurt
        System.gc();

        assertThat(weakIdentitySet.size()).isLessThan(iterations);
    }

    @Test
    public void testIdentity()
    {
        record Tester(String id) {}

        ConcurrentWeakIdentitySet weakIdentitySet = new ConcurrentWeakIdentitySet();

        assertThat(weakIdentitySet.add("test")).isTrue();
        assertThat(weakIdentitySet.add("test")).isFalse();

        Tester one = new Tester("one");
        assertThat(weakIdentitySet.add(one)).isTrue();
        assertThat(weakIdentitySet.add(one)).isFalse();
        assertThat(weakIdentitySet.add(new Tester("one"))).isTrue();
    }
}
