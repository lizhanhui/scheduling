/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2010 INRIA/University of
 * 				Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2
 * or a different license than the GPL.
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s):
 *
 * ################################################################
 * $$PROACTIVE_INITIAL_DEV$$
 */
package org.ow2.proactive.resourcemanager.authentication;

import java.io.Serializable;

import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.core.UniqueID;
import org.objectweb.proactive.core.body.UniversalBody;
import org.objectweb.proactive.core.body.ft.internalmsg.Heartbeat;
import org.objectweb.proactive.core.body.proxy.BodyProxy;
import org.objectweb.proactive.core.mop.Proxy;
import org.objectweb.proactive.core.mop.StubObject;


/**
 * This class represents a client of the resource manager(RM).
 * Basically it is a combination of an authenticated user and its location, so that the same user
 * could connect and use the resource manager simultaneously from different locations.
 *
 * The class is used to track and associate all activities inside the RM to particular user.
 *
 * It also provides capabilities to detect if the client is still alive.
 *
 * NOTE: The pinger functionality has some drawbacks and limitations. For instance it cannot be used
 * after serialization/deserialization of the class. It relies on ProActive internals and
 * probably will be replaced in the future.
 *
 */
public class Client implements Serializable {

    private static final Heartbeat hb = new Heartbeat();
    /** client's name */
    private String name;

    /** Unique id of the client */
    private UniqueID id;
    /** Body of the sender of request */
    private transient UniversalBody body;

    public Client() {
    }

    /**
     * Constructs the client object from given client name.
     * @param clientName the name of the client authenticated in the resource manager
     */
    public Client(String name) {
        this.name = name;
        this.id = PAActiveObject.getContext().getCurrentRequest().getSourceBodyID();
        this.body = PAActiveObject.getContext().getCurrentRequest().getSender();
    }

    /**
     * Gets the name of the client
     * @return the name of the client
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the id of the client
     * @return the id of the client
     */
    public UniqueID getID() {
        return id;
    }

    /**
     * Redefined equals method based on client id
     */
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        Client client = (Client) o;
        return name.equals(client.getName());
    }

    public String toString() {
        return "\"" + name + "\"";
    }

    /**
     * Checks if the client is alive by sending the message to it.
     * There is a blocking network call inside so it should be used carefully.
     *
     * Throws an exception if the client body is not available which is
     * always the case after serialization.
     *
     * @return true if the client is alive, false otherwise
     */
    public boolean isAlive() {
        if (body == null) {
            throw new RuntimeException("Cannot detect if the client " + this + " is alive");
        }
        try {
            body.receiveFTMessage(hb);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Extract the body id from an active object.
     * TODO find more straightforward way to do that
     *
     * @param service a target active object
     * @return an active object body id
     */
    public static UniqueID getId(Object service) {

        if (service instanceof StubObject && ((StubObject) service).getProxy() != null) {
            Proxy proxy = ((StubObject) service).getProxy();

            if (proxy instanceof BodyProxy) {
                return ((BodyProxy) proxy).getBodyID();
            }
        }

        return null;
    }
}