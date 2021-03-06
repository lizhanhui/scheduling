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
package functionaltests.authentication;

import org.ow2.proactive.resourcemanager.RMFactory;
import org.ow2.proactive.resourcemanager.frontend.RMConnection;
import org.ow2.tests.FunctionalTest;
import functionaltests.RMTHelper;

import static org.junit.Assert.assertTrue;


/**
 *
 * test timeouts on RM's connection helpers
 *
 * @author ProActive team
 *
 */
public class ConnectionTest2 extends FunctionalTest {

    /**
     * test function
     *
     * @throws Exception
     */
    @org.junit.Test
    public void action() throws Exception {

        RMTHelper.log("Test 1");
        RMTHelper.log("Connecting to non existing resource manager with join");
        try {
            RMConnection.join(RMTHelper.getLocalUrl());
            RMTHelper.log("Failed: exception should be thrown");
            assertTrue(false);
        } catch (Exception e) {
            RMTHelper.log("Passed");
        }

        RMTHelper.log("Test 2");
        RMTHelper.log("Connecting to non existing resource manager with waitAndJoin and timeout");
        try {
            RMConnection.waitAndJoin(RMTHelper.getLocalUrl(), 1000);
            RMTHelper.log("Failed: exception should be thrown");
            assertTrue(false);
        } catch (Exception e) {
            RMTHelper.log("Passed");
        }

        RMTHelper.log("Test 3");
        RMTHelper.log("Connecting to initializing resource manager with waitAndJoin and timeout");
        try {

            Thread t = new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1000);
                        RMTHelper.log("Running resource manager");
                        RMTHelper.getDefaultInstance().getRMAuth();
                    } catch (Exception e) {
                        assertTrue(false);
                        RMTHelper.log("Failed: unexpected error " + e.getMessage());
                    }
                }
            };
            t.start();

            RMConnection.waitAndJoin(RMTHelper.getLocalUrl(), 60000);
            RMTHelper.log("Passed");
        } catch (Exception e) {
            RMTHelper.log("Failed: unexpected error " + e.getMessage());
            assertTrue(false);
        }
    }

}
