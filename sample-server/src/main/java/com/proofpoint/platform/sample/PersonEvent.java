package com.proofpoint.platform.sample;

import com.proofpoint.event.client.EventField;
import com.proofpoint.event.client.EventType;

import java.util.Objects;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

@EventType("Person")
public class PersonEvent
{
    public static PersonEvent personAdded(String personId, Person person)
    {
        return new PersonEvent(Operation.ADDED, personId, person);
    }

    public static PersonEvent personUpdated(String personId, Person person)
    {
        return new PersonEvent(Operation.UPDATED, personId, person);
    }

    public static PersonEvent personRemoved(String personId, Person person)
    {
        return new PersonEvent(Operation.REMOVED, personId, person);
    }

    public enum Operation{ ADDED, UPDATED, REMOVED }

    private final Operation operation;
    private final String personId;
    private final Person person;

    private PersonEvent(Operation operation, String personId, Person person)
    {
        checkNotNull(operation, "operation is null");
        checkNotNull(personId, "id is null");
        checkNotNull(person, "person is null");

        this.operation = operation;
        this.personId = personId;
        this.person = person;
    }

    @EventField
    public String getOperation()
    {
        return operation.toString();
    }

    @EventField
    public String getPersonId()
    {
        return personId;
    }

    @EventField
    public String getEmail()
    {
        return person.getEmail();
    }

    @EventField
    public String getName()
    {
        return person.getName();
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(operation, personId, person);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final PersonEvent other = (PersonEvent) obj;
        return Objects.equals(this.operation, other.operation) && Objects.equals(this.personId, other.personId) && Objects.equals(this.person, other.person);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("operation", operation)
                .add("personId", personId)
                .add("person", person)
                .toString();
    }
}
