/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2011 INRIA/University of
 *                 Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s): ActiveEon Team - http://www.activeeon.com
 *
 * ################################################################
 * $$ACTIVEEON_CONTRIBUTOR$$
 */
package functionaltests.selectionscript;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import org.objectweb.proactive.api.PAFuture;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeFactory;
import org.ow2.proactive.resourcemanager.common.NodeState;
import org.ow2.proactive.resourcemanager.common.event.RMEventType;
import org.ow2.proactive.resourcemanager.common.event.RMNodeEvent;
import org.ow2.proactive.resourcemanager.frontend.ResourceManager;
import org.ow2.proactive.scripting.SelectionScript;
import org.ow2.proactive.utils.NodeSet;
import org.junit.Assert;

import functionaltests.RMConsecutive;
import functionaltests.RMTHelper;

import static org.junit.Assert.assertEquals;


/**
 *
 * This class tests RM's mechanism of resource selection with dynamic
 * scripts.
 *
 * - get a node and give back it, then tries to get nodes excluding the first got (test 1)
 *
 * - get nodes with a dummy dynamic selection script that always says "ok", with always same node excluded (test 2)
 *
 * - get nodes with a dummy static selection script that always says "ok", with always same node excluded (test 3)
 *
 * - launch 2 nodes 'special' nodes with a specific java property, that can be selected with a selection script,
 *  and try to get nodes with a selection script that checks this JVM property ,
 *  with putting one of these two 'special nodes in exclusion list, so just one node is provided. ( test 4).
 *
 * - same test as test 4 but with a static script (test 5).
 *
 * - exclude the two special nodes, and get nodes with with the specific java property,
 * no nodes are returned (test 6).
 *
 *
 * @author ProActive team
 *
 */
public class SelectionWithNodesExclusionTest extends RMConsecutive {

    private URL vmPropSelectionScriptpath = this.getClass().getResource("vmPropertySelectionScript.groovy");

    private URL dummySelectionScriptPath = this.getClass().getResource("dummySelectionScript.js");

    private String vmPropKey = "myProperty";
    private String vmPropValue = "myValue";

    /** Actions to be Perform by this test.
    * The method is called automatically by Junit framework.
    * @throws Exception If the test fails.
    */
    @org.junit.Test
    public void action() throws Exception {
        RMTHelper helper = RMTHelper.getDefaultInstance();
        ResourceManager resourceManager = helper.getResourceManager();
        final int initialNodesNumber = helper.createNodeSource("SelectionWithNodesExclusionTest");

        RMTHelper.log("Test 1");

        NodeSet nodeSetWithNodeToExclude = resourceManager.getAtMostNodes(1, null);

        //wait for node selection
        PAFuture.waitFor(nodeSetWithNodeToExclude);

        assertEquals(1, nodeSetWithNodeToExclude.size());
        assertEquals( initialNodesNumber - 1, resourceManager.getState().getFreeNodesNumber());

        //wait for node busy event
        RMNodeEvent evt = helper.waitForAnyNodeEvent(RMEventType.NODE_STATE_CHANGED);
        assertEquals(NodeState.BUSY, evt.getNodeState());

        resourceManager.releaseNodes(nodeSetWithNodeToExclude);

        //wait for node free event
        evt = helper.waitForAnyNodeEvent(RMEventType.NODE_STATE_CHANGED);
        Assert.assertEquals(NodeState.FREE, evt.getNodeState());

        assertEquals(initialNodesNumber, resourceManager.getState().getFreeNodesNumber());

        //get nodes with the previous node excluded
        NodeSet nodes = resourceManager.getAtMostNodes(initialNodesNumber, new ArrayList<SelectionScript>(),
                nodeSetWithNodeToExclude);

        //wait for node selection
        PAFuture.waitFor(nodes);

        for (int i = 0; i < initialNodesNumber - 1; i++) {
            evt = helper.waitForAnyNodeEvent(RMEventType.NODE_STATE_CHANGED);
            Assert.assertEquals(evt.getNodeState(), NodeState.BUSY);
        }

        // booked all nodes minus the node to exclude
        assertEquals(initialNodesNumber - 1, nodes.size());
        //excluded node stays in free state
        assertEquals(1, resourceManager.getState().getFreeNodesNumber());

        resourceManager.releaseNodes(nodes);

        //wait for nodes freed event
        for (int i = 0; i < initialNodesNumber - 1; i++) {
            evt = helper.waitForAnyNodeEvent(RMEventType.NODE_STATE_CHANGED);
            assertEquals(NodeState.FREE, evt.getNodeState());
        }

        assertEquals(initialNodesNumber, resourceManager.getState().getFreeNodesNumber());

        RMTHelper.log("Test 2");

        //create the dynamic selection script object
        SelectionScript dummyDynamicScript = new SelectionScript(new File(dummySelectionScriptPath.toURI()),
            new String[] {}, true);

        //get nodes with the previous node excluded
        nodes = resourceManager.getAtMostNodes(initialNodesNumber, dummyDynamicScript,
                nodeSetWithNodeToExclude);

        //wait for node selection
        PAFuture.waitFor(nodes);

        //wait for nodes busy event
        for (int i = 0; i < initialNodesNumber - 1; i++) {
            evt = helper.waitForAnyNodeEvent(RMEventType.NODE_STATE_CHANGED);
            assertEquals(NodeState.BUSY, evt.getNodeState());
        }

        // booked all nodes minus the node to exclude
        assertEquals(initialNodesNumber - 1, nodes.size());
        //excluded node stays in free state
        assertEquals(1, resourceManager.getState().getFreeNodesNumber());

        resourceManager.releaseNodes(nodes);

        //wait for nodes freed event
        for (int i = 0; i < initialNodesNumber - 1; i++) {
            evt = helper.waitForAnyNodeEvent(RMEventType.NODE_STATE_CHANGED);
            assertEquals(NodeState.FREE, evt.getNodeState());
        }

        assertEquals(initialNodesNumber, resourceManager.getState().getFreeNodesNumber());

        RMTHelper.log("Test 3");

        //create the static selection script object
        SelectionScript dummyStaticScript = new SelectionScript(new File(dummySelectionScriptPath.toURI()),
            new String[] {}, false);

        //get nodes with the previous node excluded
        nodes = resourceManager.getAtMostNodes(initialNodesNumber, dummyStaticScript,
                nodeSetWithNodeToExclude);

        //wait for node selection
        PAFuture.waitFor(nodes);

        //wait for nodes busy event
        for (int i = 0; i < initialNodesNumber - 1; i++) {
            evt = helper.waitForAnyNodeEvent(RMEventType.NODE_STATE_CHANGED);
            assertEquals(evt.getNodeState(), NodeState.BUSY);
        }

        // booked all nodes minus the node to exclude
        assertEquals(initialNodesNumber - 1, nodes.size());
        //excluded node stays in free state
        assertEquals(1, resourceManager.getState().getFreeNodesNumber());

        resourceManager.releaseNodes(nodes);

        //wait for node free event
        for (int i = 0; i < initialNodesNumber - 1; i++) {
            evt = helper.waitForAnyNodeEvent(RMEventType.NODE_STATE_CHANGED);
            assertEquals(NodeState.FREE, evt.getNodeState());
        }

        assertEquals(initialNodesNumber, resourceManager.getState().getFreeNodesNumber());

        RMTHelper.log("Test 4");

        String node1Name = "node1";
        String node2Name = "node2";

        HashMap<String, String> vmProperties = new HashMap<String, String>();
        vmProperties.put(this.vmPropKey, this.vmPropValue);

        String node1URL = helper.createNode(node1Name, vmProperties).getNode().getNodeInformation().getURL();
        resourceManager.addNode(node1URL);

        String node2URL = helper.createNode(node2Name, vmProperties).getNode().getNodeInformation().getURL();
        resourceManager.addNode(node2URL);

        Thread.sleep(5000);
        helper.waitForNodeEvent(RMEventType.NODE_ADDED, node1URL);
        helper.waitForNodeEvent(RMEventType.NODE_ADDED, node2URL);
        //wait for the nodes to be in free state
        helper.waitForAnyNodeEvent(RMEventType.NODE_STATE_CHANGED);
        helper.waitForAnyNodeEvent(RMEventType.NODE_STATE_CHANGED);

        //wait for nodes added events
        assertEquals(initialNodesNumber + 2, resourceManager.getState().getFreeNodesNumber());

        //create the dynamic selection script object
        SelectionScript checkPropDynamicSScript = new SelectionScript(new File(vmPropSelectionScriptpath
                .toURI()), new String[] { this.vmPropKey, this.vmPropValue }, true);
        Node node1ToExclude = NodeFactory.getNode(node1URL);

        nodeSetWithNodeToExclude = new NodeSet();
        nodeSetWithNodeToExclude.add(node1ToExclude);

        //get nodes with the previous node1 excluded
        nodes = resourceManager.getAtMostNodes(initialNodesNumber, checkPropDynamicSScript,
                nodeSetWithNodeToExclude);

        //wait for node selection
        PAFuture.waitFor(nodes);

        //wait for nodes busy event
        evt = helper.waitForNodeEvent(RMEventType.NODE_STATE_CHANGED, node2URL);
        assertEquals(NodeState.BUSY, evt.getNodeState());

        // booked all nodes minus the node to exclude
        assertEquals(1, nodes.size());
        //excluded node stays in free state
        assertEquals(initialNodesNumber + 1, resourceManager.getState().getFreeNodesNumber());

        //unique node got is node2
        assertEquals(node2URL, nodes.get(0).getNodeInformation().getURL());

        resourceManager.releaseNodes(nodes);
        //wait for node free event
        evt = helper.waitForNodeEvent(RMEventType.NODE_STATE_CHANGED, node2URL);
        assertEquals(NodeState.FREE, evt.getNodeState());

        assertEquals(initialNodesNumber + 2, resourceManager.getState().getFreeNodesNumber());

        RMTHelper.log("Test 5");

        //create the static selection script object
        SelectionScript checkPropStaticSScript = new SelectionScript(new File(vmPropSelectionScriptpath
                .toURI()), new String[] { this.vmPropKey, this.vmPropValue }, false);

        Node node2ToExclude = NodeFactory.getNode(node2URL);

        nodeSetWithNodeToExclude = new NodeSet();
        nodeSetWithNodeToExclude.add(node2ToExclude);

        //get nodes with the previous node1 excluded
        nodes = resourceManager.getAtMostNodes(initialNodesNumber, checkPropStaticSScript,
                nodeSetWithNodeToExclude);

        //wait for node selection
        PAFuture.waitFor(nodes);

        //wait for nodes busy event
        evt = helper.waitForNodeEvent(RMEventType.NODE_STATE_CHANGED, node1URL);
        assertEquals(NodeState.BUSY, evt.getNodeState());

        // booked all nodes minus the node to exclude
        assertEquals(1, nodes.size());
        //excluded node stays in free state
        assertEquals(initialNodesNumber + 1, resourceManager.getState().getFreeNodesNumber());

        //unique node got is node2
        assertEquals(node1URL, nodes.get(0).getNodeInformation().getURL());

        resourceManager.releaseNodes(nodes);

        //wait for node free event
        evt = helper.waitForNodeEvent(RMEventType.NODE_STATE_CHANGED, node1URL);
        assertEquals(NodeState.FREE, evt.getNodeState());

        assertEquals(initialNodesNumber + 2, resourceManager.getState().getFreeNodesNumber());

        RMTHelper.log("Test 6");

        nodeSetWithNodeToExclude.add(node1ToExclude);

        //get nodes with the previous node1 excluded
        nodes = resourceManager.getAtMostNodes(initialNodesNumber, checkPropStaticSScript,
                nodeSetWithNodeToExclude);

        //wait for node selection
        PAFuture.waitFor(nodes);

        assertEquals(initialNodesNumber + 2, resourceManager.getState().getFreeNodesNumber());
    }
}
