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
 *  Contributor(s):
 *
 * ################################################################
 * $$PROACTIVE_INITIAL_DEV$$
 */
package org.ow2.proactive.scheduler.common.task.dataspaces;

import org.objectweb.proactive.annotation.PublicAPI;


/**
 * OutputAccessMode provide a way to define how output files should be managed
 * after the execution.
 *
 * @author The ProActive Team
 * @since ProActive Scheduling 1.1
 */
@PublicAPI
public enum OutputAccessMode {
    /** Transfer files from LOCAL space to OUTPUT space */
    TransferToOutputSpace("transferToOutputSpace"),
    /** LOCAL to GLOBAL */
    TransferToGlobalSpace("transferToGlobalSpace"),
    /** LOCAL to GLOBAL */
    TransferToUserSpace("transferToUserSpace"),
    /** Do nothing */
    none("none");

    String title;

    private OutputAccessMode(String s) {
        title = s;
    }

    public static OutputAccessMode getAccessMode(String accessMode) {
        if (TransferToOutputSpace.title.equalsIgnoreCase(accessMode)) {
            return TransferToOutputSpace;
        } else if (TransferToGlobalSpace.title.equalsIgnoreCase(accessMode)) {
            return TransferToGlobalSpace;
        } else if (TransferToUserSpace.title.equalsIgnoreCase(accessMode)) {
            return TransferToUserSpace;
        } else if (none.title.equalsIgnoreCase(accessMode)) {
            return none;
        } else {
            throw new IllegalArgumentException("Unknow Output access mode : " + accessMode);
        }
    }

    public String toString() {
        return this.title;
    }
}
