/*
 *  Copyright (c) 2012-2013 DataTorrent, Inc.
 *  All Rights Reserved.
 */
package com.datatorrent.stram;

import java.net.InetSocketAddress;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.junit.Assert;

import com.datatorrent.api.Stats.OperatorStats;

import com.datatorrent.stram.api.Checkpoint;
import com.datatorrent.stram.api.StreamingContainerUmbilicalProtocol.ContainerHeartbeat;
import com.datatorrent.stram.api.StreamingContainerUmbilicalProtocol.ContainerHeartbeatResponse;
import com.datatorrent.stram.api.StreamingContainerUmbilicalProtocol.ContainerStats;
import com.datatorrent.stram.api.StreamingContainerUmbilicalProtocol.OperatorHeartbeat;
import com.datatorrent.stram.plan.physical.PTContainer;
import com.datatorrent.stram.plan.physical.PTOperator;

/**
 * Mock container for testing container manager and heartbeat protocol.
 */
public class MockContainer
{
  final StramChildAgent sca;
  final PTContainer container;
  final Map<Integer, MockOperatorStats> stats = Maps.newHashMap();

  public MockContainer(StreamingContainerManager scm, PTContainer c)
  {
    this.sca = assignContainer(scm, c);
    this.container = c;
    Assert.assertEquals(c, sca.container);
    Assert.assertEquals(PTContainer.State.ALLOCATED, container.getState());
    ContainerStats cstats = new ContainerStats(sca.container.getExternalId());
    ContainerHeartbeat hb = new ContainerHeartbeat();
    hb.setContainerStats(cstats);
    sca.dnmgr.processHeartbeat(hb); // activate container
    Assert.assertEquals(PTContainer.State.ACTIVE, container.getState());
  }

  private StramChildAgent assignContainer(StreamingContainerManager scm, PTContainer c) {
    c.setResourceRequestPriority(c.getId());
    String containerId = "container" + c.getId();
    InetSocketAddress bufferServerAddress = InetSocketAddress.createUnresolved(containerId+"Host", 0);
    return scm.assignContainer(new StreamingContainerManager.ContainerResource(c.getId(), containerId, "localhost", 1024, null), bufferServerAddress);
  }

  public void deploy()
  {
    Assert.assertNotNull(sca.container.getExternalId());
    Assert.assertEquals(PTContainer.State.ACTIVE, container.getState());
    //Assert.assertEquals(PTOperator.State.PENDING_DEPLOY, o1p1.getState());

    ContainerStats cstats = new ContainerStats(sca.container.getExternalId());
    ContainerHeartbeat hb = new ContainerHeartbeat();
    hb.setContainerStats(cstats);

    ContainerHeartbeatResponse chr = sca.dnmgr.processHeartbeat(hb); // get deploy request
    Assert.assertNotNull(chr.deployRequest);
    Assert.assertEquals(""+chr.deployRequest, container.getOperators().size(), chr.deployRequest.size());
    Assert.assertEquals(PTContainer.State.ACTIVE, container.getState());

    for (PTOperator oper : container.getOperators()) {
      Assert.assertEquals("state " + oper, PTOperator.State.PENDING_DEPLOY, oper.getState());
    }
  }

  public void sendHeartbeat()
  {
    ContainerStats cstats = new ContainerStats(sca.container.getExternalId());
    ContainerHeartbeat hb = new ContainerHeartbeat();
    hb.setContainerStats(cstats);

    for (Map.Entry<Integer, MockOperatorStats> oe : this.stats.entrySet()) {
      OperatorHeartbeat ohb = new OperatorHeartbeat();
      ohb.setNodeId(oe.getKey());
      ohb.setState(OperatorHeartbeat.DeployState.ACTIVE);
      OperatorStats lstats = new OperatorStats();
      lstats.checkpoint = new Checkpoint(oe.getValue().checkpointWindowId, 0, 0);
      lstats.windowId = oe.getValue().currentWindowId;

      //stats.outputPorts = Lists.newArrayList();
      //PortStats ps = new PortStats(TestGeneratorInputOperator.OUTPUT_PORT);
      //ps.bufferServerBytes = 101;
      //ps.tupleCount = 1;
      //stats.outputPorts.add(ps);

      ohb.windowStats = Lists.newArrayList(lstats);
      cstats.operators.add(ohb);
    }

    ContainerHeartbeatResponse chr = sca.dnmgr.processHeartbeat(hb);
    Assert.assertNull(chr.deployRequest);
  }

  public MockOperatorStats stats(int operatorId) {
    MockOperatorStats os = this.stats.get(operatorId);
    if (os == null) {
      os = new MockOperatorStats(operatorId);
      this.stats.put(operatorId, os);
    }
    return os;
  }

  public class MockOperatorStats
  {
    final int operatorId;
    OperatorHeartbeat.DeployState deployState;
    long currentWindowId;
    long checkpointWindowId;

    private MockOperatorStats(int operatorId)
    {
      this.operatorId = operatorId;
    }

    public MockOperatorStats deployState(OperatorHeartbeat.DeployState s) {
      this.deployState = s;
      return this;
    }

    public MockOperatorStats currentWindowId(long windowId) {
      this.currentWindowId = windowId;
      return this;
    }

    public MockOperatorStats checkpointWindowId(long windowId) {
      this.checkpointWindowId = windowId;
      return this;
    }

  }

}