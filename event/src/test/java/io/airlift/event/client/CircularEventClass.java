package io.airlift.event.client;

@SuppressWarnings("UnusedDeclaration")
@EventType("Circular")
public class CircularEventClass
{
    private final CircularPart part = new CircularPart();

    @EventField
    public CircularPart getPart()
    {
        return part;
    }

    @EventType
    public static class CircularPart
    {
        private final CircularPart part;

        public CircularPart()
        {
            this.part = this;
        }

        @EventField
        public CircularPart getPart()
        {
            return part;
        }
    }
}
