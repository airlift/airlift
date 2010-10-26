package com.proofpoint.concurrent.events;

/**
 * An adapter for events that has default behavior
 */
public class EventAdapter implements EventQueue.Event<EventAdapter>
{
    @Override
    public boolean canBeMergedWith(EventAdapter event)
    {
        return false;
    }

    @Override
    public void processEvent()
    {
    }
}
