package io.airlift.sample;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import io.airlift.event.client.EventField;
import io.airlift.event.client.EventType;

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
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PersonEvent that = (PersonEvent) o;

        if (operation != that.operation) {
            return false;
        }
        if (!person.equals(that.person)) {
            return false;
        }
        if (!personId.equals(that.personId)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = operation.hashCode();
        result = 31 * result + personId.hashCode();
        result = 31 * result + person.hashCode();
        return result;
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
