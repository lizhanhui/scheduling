/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2002 INRIA/University of Nice-Sophia Antipolis
 * Contact: proactive-support@inria.fr
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://www.inria.fr/oasis/ProActive/contacts.html
 *  Contributor(s):
 *
 * ################################################################
 */
package org.objectweb.proactive.core.descriptor.services;

import org.apache.log4j.Logger;

import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.descriptor.data.VirtualMachine;
import org.objectweb.proactive.core.descriptor.data.VirtualNode;
import org.objectweb.proactive.core.descriptor.data.VirtualNodeImpl;
import org.objectweb.proactive.core.event.RuntimeRegistrationEvent;
import org.objectweb.proactive.core.event.RuntimeRegistrationEventListener;
import org.objectweb.proactive.core.runtime.ProActiveRuntime;
import org.objectweb.proactive.core.runtime.ProActiveRuntimeImpl;
import org.objectweb.proactive.core.util.UrlBuilder;


/**
 * @author  ProActive Team
 * @version 1.0,  2004/09/20
 * @since   ProActive 2.0.1
 */
public class ServiceThread extends Thread {
    private VirtualNode vn;
    private UniversalService service;
    private VirtualMachine vm;
    private ProActiveRuntime localRuntime;
    int nodeCount = 0;
    long timeout = 0;
    long P2Ptimeout;
    int nodeRequested;
    public static Logger loggerDeployment = Logger.getLogger("DEPLOYMENT");

    public ServiceThread(VirtualNode vn, VirtualMachine vm) {
        this.vn = vn;
        this.service = vm.getService();
        this.vm = vm;
        this.localRuntime = ProActiveRuntimeImpl.getProActiveRuntime();
    }

    public void run() {
        ProActiveRuntime[] part = null;

        try {
            part = service.startService();
            nodeCount = nodeCount + part.length;
            notifyVirtualNode(part);
            if (service.getServiceName().equals("P2PLookup")) {
                this.P2Ptimeout = ((P2PLookupService) service).getTimeout();
                this.nodeRequested = ((P2PLookupService) service).getNodeNumber();
                // if the timeout of the service is longer than the vn's one
                // then adjust the vn's timeout.
                long vnTimeout = vn.getTimeout();

                if (vnTimeout < P2Ptimeout) {
                    ((VirtualNodeImpl) vn).setTimeout(P2Ptimeout, false);
                }
                while (!timeoutExpired() && askForNodes()) {
                    Thread.sleep(((P2PLookupService) service).getLookupFrequence());
                    part = service.startService();
                    nodeCount = nodeCount + part.length;
                    notifyVirtualNode(part);
                }
            }
        } catch (ProActiveException e) {
            loggerDeployment.error(
                "An exception occured while starting the service " +
                service.getServiceName() + " for the VirtualNode " +
                vn.getName() + " \n" + e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void notifyVirtualNode(ProActiveRuntime[] part)
        throws ProActiveException {
        for (int i = 0; i < part.length; i++) {
            String url = part[i].getURL();
            String protocol = UrlBuilder.getProtocol(url);
            RuntimeRegistrationEvent event = new RuntimeRegistrationEvent(localRuntime,
                    RuntimeRegistrationEvent.RUNTIME_ACQUIRED, part[i],
                    vn.getName(), protocol, vm.getName());
            ((RuntimeRegistrationEventListener) vn).runtimeRegistered(event);
        }
    }

    /**
     * Method used for the timout of the P2PService
     * Returns true if the timeout has expired
     * @return true if the timeout has expired
     */
    private boolean timeoutExpired() {
        if (P2Ptimeout == -1) {
            // timeout = -1 means infinite timeout
            return false;
        } else {
            if (timeout == 0) {
                this.timeout = System.currentTimeMillis() +
                    ((P2PLookupService) service).getTimeout();
            }
            long currentDate = System.currentTimeMillis();
            return currentDate > timeout;
        }
    }

    /**
     * Method used to know if we must ask other nodes
     * @return true if there are still nodes expected
     */
    private boolean askForNodes() {
        if (nodeRequested == ((P2PLookupService) service).getMAX()) {
            // nodeRequested = -1 means try to get the max number of nodes
            return true;
        } else {
            return nodeCount < nodeRequested;
        }
    }
}
