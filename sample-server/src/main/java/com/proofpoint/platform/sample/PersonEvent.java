package com.proofpoint.platform.sample;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.proofpoint.event.client.EventField;
import com.proofpoint.event.client.EventType;

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
        Preconditions.checkNotNull(operation, "operation is null");
        Preconditions.checkNotNull(personId, "id is null");
        Preconditions.checkNotNull(person, "person is null");

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
        return Objects.hashCode(operation, personId, person);
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
        return Objects.equal(this.operation, other.operation) && Objects.equal(this.personId, other.personId) && Objects.equal(this.person, other.person);
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("operation", operation)
                .add("personId", personId)
                .add("person", person)
                .toString();
    }
}
