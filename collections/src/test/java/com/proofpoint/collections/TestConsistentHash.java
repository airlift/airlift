package com.proofpoint.collections;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.math.stat.descriptive.AggregateSummaryStatistics;
import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class TestConsistentHash
{
    private static final double          TOLERATED_DEVIATION = .50; // TODO find out why this number can't be lower

    @Test
    public void     testBasic()
    {
        ConsistentHash<String> hasher = ConsistentHash.newConsistentHash(null);

        List<String> nodes = Lists.newArrayList();
        for ( int i = 0; i < 10; ++i )
        {
            String node = UUID.randomUUID().toString();
            nodes.add(node);
            hasher.addNode(node);
        }

        List<String>                values = Lists.newArrayList();
        for ( int i = 0; i < 1000; ++i )
        {
            values.add(UUID.randomUUID().toString());
        }

        Map<String, String> valueToNode = Maps.newHashMap();
        for ( String v : values )
        {
            valueToNode.put(v, hasher.getNodeForKey(v));
        }

        validateConsistency(values, valueToNode, hasher);

        Random random = new Random();
        for ( int i = 0; i < 2; ++i )
        {
            nodes.remove(random.nextInt(nodes.size()));
            validateConsistency(values, valueToNode, hasher);
        }

        for ( int i = 0; i < 10; ++i )
        {
            nodes.add(UUID.randomUUID().toString());
            validateConsistency(values, valueToNode, hasher);
        }

        for ( int i = 0; i < (nodes.size() / 4); ++i )
        {
            nodes.remove(random.nextInt(nodes.size()));
            validateConsistency(values, valueToNode, hasher);
        }
    }

    @Test
    public void     testExtremes()
    {
        ConsistentHash<String> hasher = ConsistentHash.newConsistentHash();

        Assert.assertNull(hasher.getNodeForKey("asfalksfj asfkl afs"));

        final String nodeId = "foo";
        hasher.addNode(nodeId);
        Assert.assertEquals(hasher.getNodesForKey("359023q r", 100).size(), 1);

        for ( int i = 0; i < 1000; ++i )
        {
            Assert.assertEquals(hasher.getNodeForKey(UUID.randomUUID().toString()), nodeId);
        }

        hasher.removeNode(nodeId);

        Assert.assertNull(hasher.getNodeForKey("2359-23508"));
        Assert.assertEquals(hasher.getNodesForKey("2359-23508", 2).size(), 0);
    }

    @Test
    public void     testDistributionManyNodesFewKeys()
    {
        testDistribution(100, 3);
    }

    @Test
    public void     testDistributionFewNodesManyKeys()
    {
        testDistribution(3, 1000);
    }

    @Test
    public void     testDistributionFew()
    {
        testDistribution(3, 10);
    }

    @Test
    public void     testDistributionMany()
    {
        testDistribution(1000, 10000);
    }

    private void testDistribution(int nodeQty, int keyQty)
    {
        ConsistentHash<String> hasher = ConsistentHash.newConsistentHash(null);
        List<String> nodes = Lists.newArrayList();
        Map<String, Integer> nodeCounts = Maps.newHashMap();
        for ( int i = 0; i < nodeQty; ++i )
        {
            String node = UUID.randomUUID().toString();
            nodes.add(node);
            hasher.addNode(node);
            nodeCounts.put(node, 0);
        }

        for ( int i = 0; i < keyQty; ++i )
        {
            String      node = hasher.getNodeForKey(UUID.randomUUID().toString());
            nodeCounts.put(node, nodeCounts.get(node) + 1);
        }

        AggregateSummaryStatistics  statistics = new AggregateSummaryStatistics();
        SummaryStatistics           summaryStatistics = statistics.createContributingStatistics();
        for ( int count : nodeCounts.values() )
        {
            summaryStatistics.addValue((double)count / (double)keyQty);
        }
        Assert.assertTrue(statistics.getStandardDeviation() <= TOLERATED_DEVIATION);
    }

    private void validateConsistency(List<String> values, Map<String, String> valueToNode, ConsistentHash<String> hasher)
    {
        for ( String v : values )
        {
            String node = hasher.getNodeForKey(v);
            Assert.assertEquals(node, valueToNode.get(v));
        }
    }
}
