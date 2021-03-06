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
package functionaltests.nodesource;

import java.io.File;

import org.objectweb.proactive.core.config.CentralPAPropertyRepository;
import org.ow2.proactive.resourcemanager.common.event.RMEventType;
import org.ow2.proactive.resourcemanager.core.properties.PAResourceManagerProperties;
import org.ow2.proactive.resourcemanager.nodesource.infrastructure.LocalInfrastructure;
import org.ow2.proactive.resourcemanager.nodesource.policy.StaticPolicy;
import org.ow2.proactive.utils.FileToBytesConverter;
import org.ow2.tests.FunctionalTest;
import functionaltests.RMTHelper;
import functionaltests.common.CommonTUtils;
import org.junit.Assert;


public class TestNodeSourceAfterRestart extends FunctionalTest {

    private RMTHelper helper = RMTHelper.getDefaultInstance();

    protected void createDefaultNodeSource(String sourceName) throws Exception {
        // creating node source
        byte[] creds = FileToBytesConverter.convertFileToByteArray(new File(PAResourceManagerProperties
                .getAbsolutePath(PAResourceManagerProperties.RM_CREDS.getValueAsString())));
        helper.getResourceManager().createNodeSource(
                sourceName,
                LocalInfrastructure.class.getName(),
                new Object[] { creds, 1, RMTHelper.defaultNodesTimeout,
                        CentralPAPropertyRepository.PA_RMI_PORT.getCmdLine() + RMTHelper.PA_RMI_PORT },
                //first parameter is empty rm url
                StaticPolicy.class.getName(), new Object[] { "ME", "ALL" });
        helper.waitForNodeSourceCreation(sourceName, 1);
    }

    private void startRMPreservingDB() throws Exception {
        String rmconf = new File(RMTHelper.class.getResource("/functionaltests/config/rm-with-db.ini")
                .toURI()).getAbsolutePath();
        RMTHelper.getDefaultInstance().startRM(rmconf, RMTHelper.PA_RMI_PORT);
        RMTHelper.getDefaultInstance().getResourceManager();
    }

    protected void removeNodeSource(String sourceName) throws Exception {
        // removing node source
        helper.getResourceManager().removeNodeSource(sourceName, true);

        //wait for the event of the node source removal
        helper.waitForNodeSourceEvent(RMEventType.NODESOURCE_REMOVED, sourceName);
    }

    @org.junit.Test
    public void action() throws Exception {
        String source1 = "Node_source_1";

        RMTHelper.log("Test 1 - creation of the node source with nodes");
        createDefaultNodeSource(source1);

        helper.killRM();
        CommonTUtils.cleanupActiveObjectRegistry("local-" + source1 + "-0");

        RMTHelper.log("Test 1 - starting the resource manager");
        startRMPreservingDB();

        while (helper.getResourceManager().getState().getFreeNodesNumber() != 1) {
            Thread.sleep(500);
        }

        RMTHelper.log("Test 1 - passed");

        RMTHelper.log("Test 2 - removing node source");
        removeNodeSource(source1);

        helper.killRM();
        CommonTUtils.cleanupActiveObjectRegistry("local-" + source1 + "-0");

        RMTHelper.log("Test 2 - starting the resource manager");
        startRMPreservingDB();

        Thread.sleep(10000);
        Assert.assertEquals(0, helper.getResourceManager().getState().getFreeNodesNumber());

        RMTHelper.log("Test 2 - passed");
    }
}
