package com.proofpoint.testing;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.proofpoint.testing.EquivalenceTester.ElementCheckFailure;

import java.util.List;

public class EquivalenceAssertionError extends AssertionError
{
    private final List<ElementCheckFailure> failures;

    public EquivalenceAssertionError(Iterable<ElementCheckFailure> failures)
    {
        super("Equivalence failed:\n      " + Joiner.on("\n      ").join(failures));
        this.failures = ImmutableList.copyOf(failures);
    }

    public List<ElementCheckFailure> getFailures()
    {
        return failures;
    }
}
