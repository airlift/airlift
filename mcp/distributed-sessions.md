[◀︎ Airlift](../README.md) • [◀︎ MCP](README.md)

# MCP server support

## Distributed sessions

To support distributed sessions, you need to implement the `McpSessionController` interface. Your implementation 
should be backed by a database or another persistent storage mechanism to ensure session data is retained across server restarts and
is accessible from multiple server instances.

Bind your `McpSessionController` implementation in a Guice module.

## McpSessionController methods

### newSession

```
String newSession();
```

Allocate a new session. Return a globally unique session ID that can be used to reference the session. The session should have
a timeout and whatever other limits are appropriate for your application.

### currentSessionIds

```
Set<String> currentSessionIds();
```

Return a set of all currently active session IDs.

### deleteSession

```
boolean deleteSession(String sessionId);
```

Delete the session associated with the provided session ID. Return true if the session was successfully deleted,
or false if the session ID does not exist.

### isValidSession

```
boolean isValidSession(String sessionId);
```

Check if the provided session ID corresponds to a valid and active session. Return true if the session is valid,
or false otherwise.

### addEvent

```
boolean addEvent(String sessionId, String eventData);
```

Enqueue an event for the session identified by the provided session ID. The event must be assigned a monotonically increasing
event ID that is unique for the session. Return true if the event was successfully added, or false if the session ID does not exist.

See the section below for [details on session events](#events).

### pollEvents

```
List<Event> pollEvents(String sessionId, long lastEventId, Duration timeout);
```

Retrieve a list of events for the session identified by the provided session ID that have occurred after the specified last event ID.
`lastEventId` will be `-1` to indicate that the client wants all events from the beginning of the session. Return an empty list if there are no new events or if the session ID does not exist.

**IMPORTANT:** this method should block until some events are available or until the timeout is reached or the thread is interrupted. Ideally, if events are added 
via [addEvent()](#addevent) _on any server_ while a poll is waiting, the poll should return immediately with the new events rather than waiting for the timeout to be reached.
If the timeout is reached or the thread is interrupted, return an empty list.

### upsertValue

```
<T> boolean upsertValue(String sessionId, McpValueKey<T> key, T value);
```

Insert or update a value associated with the provided key for the session identified by the provided session ID. Return true if the value was successfully upserted,
or false if the session ID does not exist

See the section below for [details on values](#values).

## deleteValue

```
<T> boolean deleteValue(String sessionId, McpValueKey<T> key);
```

Delete the value associated with the provided key for the session identified by the provided session ID. Return true if the value was successfully deleted,
or false if the session ID does not exist.

See the section below for [details on values](#values).

## currentValue

```
<T> Optional<T> currentValue(String sessionId, McpValueKey<T> key);
```

Return the current value associated with the provided key for the session identified by the provided session ID. Return 
`empty()` if the value does not exist or if the session ID does not exist.

See the section below for [details on values](#values).

## Events

Session events are the mechanism used to manage logging, list changes, etc. Your session controller implementation must support storing and retrieving
events associated with a session. Events must be assigned a monotonically increasing event ID that is unique for the session. When an event is returned
via the [pollEvents](#pollevents) method, your session controller should retain the event for a reasonable period time so that sessions can be resumed
from a known event ID.

## Values

Your session controller implementation must support storing and retrieving arbitrary values associated with a session. 
These values are identified by `McpValueKey<T>` instances, which define the name and type of the value being stored. All values 
are guaranteed to be serializable as JSON. Airlift uses this mechanism to store client info, roots, subscriptions, etc.
