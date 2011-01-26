package com.proofpoint.concurrent.events;

import com.proofpoint.log.Logger;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple queue for posting events. Allows for a tick based buffer between posting
 * and event processing. All events are processed in a single thread.
 */
public class EventQueue<T extends EventQueue.Event<T>>
{
    /**
     * Base type for events
     */
    public interface Event<E>
    {
        /**
         * Return true if the given event can take the place of you - i.e. the two
         * events can be combined into one.
         *
         * @param event event to compare with
         * @return true if events can be combined, false otherwise
         */
        public boolean      canBeMergedWith(E event);

        /**
         * process the event
         */
        public void         processEvent();
    }

    /**
     * Listener for event processing
     */
    public interface EventListener<U>
    {
        /**
         * Called after the event has been processed. NOTE: called from the same single thread
         * that processes events
         *
         * @param event the event that was processed
         * @throws Exception errors
         */
        public void     eventProcessed(U event) throws Exception;
    }
    
    private static final Logger          log = Logger.get(EventQueue.class);

    private final QuantizedExecutor      executor;
    private final List<T>                events = new ArrayList<T>();
    private final Set<EventListener<T>>  listeners = new CopyOnWriteArraySet<EventListener<T>>();

    private final AtomicInteger          pauseCount = new AtomicInteger();

    /**
     * @param intervalTimeInMs ticks to buffer before events are processed. i.e., when an event
     * is posted, this many ticks may elapse before the event is processed (however, it could happen
     * sooner if previous events have been posted)
     */
    public EventQueue(long intervalTimeInMs)
    {
        executor = new QuantizedExecutor(intervalTimeInMs, new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    processEvents();
                }
                catch ( Exception e )
                {
                    log.error(e, "From EventQueue");
                    throw new Error(e);
                }
            }
        });
    }

    /**
     * Shutdown the event processor
     */
    public void     shutdown()
    {
        executor.shutdown();
    }

    /**
     * Add an event listener
     *
     * @param listener the listener
     */
    public void     addListener(EventListener<T> listener)
    {
        listeners.add(listener);
    }

    /**
     * Remove an event listener
     *
     * @param listener the listener
     */
    public void     removeListener(EventListener<T> listener)
    {
        listeners.remove(listener);
    }

    /**
     * Returns the current pause count
     *
     * @return count
     */
    public int      getPauseCount()
    {
        return pauseCount.get();
    }

    /**
     * Return the number of events currently in the queue
     *
     * @return event count
     */
    public int      size()
    {
        return events.size();
    }

    /**
     * Pauses event processing. New events will be queued, but not processed
     * until {@link #resumeQueue()} is called. NOTE: calls to pauseQueue() can be
     * nested but each call must be balanaced by a call to {@link #resumeQueue()}
     */
    public void     pauseQueue()
    {
        log.debug("paused - PauseCount(%d)", pauseCount.get());
        pauseCount.incrementAndGet();
    }

    /**
     * Resume event processing when all previous calls to {@link #pauseQueue()} have been balanced
     */
    public void     resumeQueue()
    {
        log.debug("resumed - PauseCount(%d)", pauseCount.get());
        int localPaushCount = pauseCount.decrementAndGet();
        if ( localPaushCount < 0 )
        {
            throw new IllegalStateException();
        }
        else if ( localPaushCount == 0 )
        {
            executor.makeRunnable();
        }
    }

    /**
     * Forces immediate queue processing (however, it is
     * still done in the queue's internal thread)
     */
    public void     forceQueue()
    {
        log.debug("forced");
        executor.runNowIf();
    }

    /**
     * Post one or more events to the queue
     *
     * @param eventsToPost the events
     */
    public void     postEvent(T... eventsToPost)
    {
        List<T>     eventList = Arrays.asList(eventsToPost);
        log.debug("Posting: %s - PauseCount(%d)", eventList, pauseCount.get());
        
        synchronized(this)
        {
            events.addAll(eventList);
        }
        executor.makeRunnable();
    }

    private void processEvents() throws Exception
    {
        if ( pauseCount.get() > 0 )
        {
            return;
        }

        List<T>        localEvents;
        synchronized(this)
        {
            localEvents = new ArrayList<T>(events);
            events.clear();
        }

        final Map<T, T> visitedSet = Maps.newHashMap();
        List<T>         filteredLocalEvents = Lists.newArrayList(Iterators.filter(localEvents.iterator(), new Predicate<T>()
        {
            @Override
            public boolean apply(T event)
            {
                boolean     keepIt = true;
                if ( visitedSet.containsKey(event) )
                {
                    T   masterEvent = visitedSet.get(event);
                    // JZ - IDEA is wrong on the generic usage here. I've double checked it by compiling on the command line using javac
                    //noinspection unchecked
                    if ( event.canBeMergedWith(masterEvent) )
                    {
                        keepIt = false;
                    }
                }
                else
                {
                    visitedSet.put(event, event);
                }
                return keepIt;
            }
        }));

        for ( T event : filteredLocalEvents )
        {
            event.processEvent();
            for ( EventListener<T> listener : listeners )
            {
                listener.eventProcessed(event);
            }
        }
    }
}
