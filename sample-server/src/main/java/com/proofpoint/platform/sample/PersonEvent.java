package com.proofpoint.platform.sample;

import com.google.auto.value.AutoValue;
import com.proofpoint.event.client.EventField;
import com.proofpoint.event.client.EventType;

@EventType("Person")
@AutoValue
public abstract class PersonEvent
{
    public static PersonEvent personAdded(String personId, Person person)
    {
        return new AutoValue_PersonEvent(Operation.ADDED, personId, person);
    }

    public static PersonEvent personUpdated(String personId, Person person)
    {
        return new AutoValue_PersonEvent(Operation.UPDATED, personId, person);
    }

    public static PersonEvent personRemoved(String personId, Person person)
    {
        return new AutoValue_PersonEvent(Operation.REMOVED, personId, person);
    }

    public enum Operation{ ADDED, UPDATED, REMOVED }

    protected abstract Operation getInternalOperation();

    @EventField
    public String getOperation()
    {
        return getInternalOperation().toString();
    }

    @EventField
    public abstract String getPersonId();

    protected abstract Person getPerson();

    @EventField
    public String getEmail()
    {
        return getPerson().getEmail();
    }

    @EventField
    public String getName()
    {
        return getPerson().getName();
    }
}
