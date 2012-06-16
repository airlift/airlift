package io.airlift.event.client;

@SuppressWarnings("UnusedDeclaration")
@EventType("ChainedCircular")
public class ChainedCircularEventClass
{
    private final ChainedPart part;

    public ChainedCircularEventClass(ChainedPart part)
    {
        this.part = part;
    }

    @EventField
    public ChainedPart getPart()
    {
        return part;
    }

    @EventType
    public static class ChainedPart
    {
        private final String name;
        private ChainedPart part;

        public ChainedPart(String name)
        {
            this.name = name;
        }

        public void setPart(ChainedPart part)
        {
            this.part = part;
        }

        @EventField
        public String getName()
        {
            return name;
        }

        @EventField
        public ChainedPart getPart()
        {
            return part;
        }

        @Override
        public String toString()
        {
            return String.format("ChainedPart{%s}", name);
        }
    }
}
