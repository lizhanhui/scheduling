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
package org.ow2.proactive.scheduler.task.forked;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.objectweb.proactive.api.PAActiveObject;
import org.ow2.proactive.scheduler.common.exception.ExecutableCreationException;
import org.ow2.proactive.scheduler.common.task.ForkEnvironment;
import org.ow2.proactive.scheduler.common.task.executable.internal.JavaExecutableInitializerImpl;
import org.ow2.proactive.scheduler.common.task.executable.Executable;
import org.ow2.proactive.scheduler.common.task.util.ByteArrayWrapper;
import org.ow2.proactive.scheduler.task.java.JavaExecutableContainer;
import org.ow2.proactive.scheduler.task.TaskLauncher;


/**
 * This class is a container for forked Java executable. The actual executable is instantiated on the worker node
 * in a dedicated classloader, which can download classes from the associated classServer.<br>
 * In this case an other JVM is started from the current one, and the task itself is deployed on the new JVM.<br>
 * As a consequence, we keep control on the forked JVM, can kill the process or give a new brand environment to the user
 *
 * @author The ProActive Team
 * @since ProActive Scheduling 1.1
 */
public class ForkedJavaExecutableContainer extends JavaExecutableContainer {

    public static final Logger logger = Logger.getLogger(ForkedJavaExecutableContainer.class);

    /** Environment of a new dedicated JVM */
    protected ForkEnvironment forkEnvironment = null;

    /**
     * Create a new container for JavaExecutable
     * @param userExecutableClassName the classname of the user defined executable
     * @param args the arguments for Executable.init() method.
     */
    public ForkedJavaExecutableContainer(String userExecutableClassName, Map<String, byte[]> args) {
        super(userExecutableClassName, args);
    }

    /**
     * Copy constructor
     * 
     * @param original object to copy
     * @throws ExecutableCreationException forkenv script copy failed
     */
    public ForkedJavaExecutableContainer(ForkedJavaExecutableContainer original)
            throws ExecutableCreationException {
        super(original);
        this.forkEnvironment = new ForkEnvironment(original.forkEnvironment);
    }

    /**
     * @see org.ow2.proactive.scheduler.task.ExecutableContainer#getExecutable()
     */
    @Override
    public Executable getExecutable() throws ExecutableCreationException {
        return new JavaForkerExecutable((TaskLauncher) PAActiveObject.getStubOnThis());
    }

    /**
     * @see org.ow2.proactive.scheduler.task.ExecutableContainer#createExecutableInitializer()
     */
    @Override
    public ForkedJavaExecutableInitializer createExecutableInitializer() {
        JavaExecutableInitializerImpl jei = super.createExecutableInitializer();
        ForkedJavaExecutableInitializer fjei = new ForkedJavaExecutableInitializer(jei);
        fjei.setForkEnvironment(forkEnvironment);
        Map<String, byte[]> tmp = new HashMap<String, byte[]>();
        for (Entry<String, ByteArrayWrapper> e : this.serializedArguments.entrySet()) {
            tmp.put(e.getKey(), e.getValue().getByteArray());
        }
        JavaExecutableContainer newjec = new JavaExecutableContainer(this.userExecutableClassName, tmp);
        newjec.setCredentials(this.getCredentials());
        fjei.setJavaExecutableContainer(newjec);
        return fjei;
    }

    /**
     * Set the forkEnvironment value to the given forkEnvironment value
     *
     * @param forkEnvironment the forkEnvironment to set
     */
    public void setForkEnvironment(ForkEnvironment forkEnvironment) {
        this.forkEnvironment = forkEnvironment;
    }

    /**
     * Get the forkEnvironment
     *
     * @return the forkEnvironment
     */
    public ForkEnvironment getForkEnvironment() {
        return forkEnvironment;
    }

}
