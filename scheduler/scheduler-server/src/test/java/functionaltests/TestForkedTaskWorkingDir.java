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
package functionaltests;

import java.io.File;
import java.io.Serializable;
import java.net.URI;

import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.utils.OperatingSystem;
import org.ow2.proactive.resourcemanager.common.NodeState;
import org.ow2.proactive.resourcemanager.common.event.RMEventType;
import org.ow2.proactive.resourcemanager.common.event.RMNodeEvent;
import org.ow2.proactive.scheduler.common.job.JobId;
import org.ow2.proactive.scheduler.common.job.TaskFlowJob;
import org.ow2.proactive.scheduler.common.job.factories.JobFactory_stax;
import org.ow2.proactive.scheduler.common.task.ForkEnvironment;
import org.ow2.proactive.scheduler.common.task.JavaTask;
import org.ow2.proactive.scheduler.common.task.TaskResult;
import org.ow2.proactive.scheduler.common.task.dataspaces.OutputAccessMode;
import org.ow2.proactive.scheduler.common.task.executable.JavaExecutable;
import org.ow2.tests.FunctionalTest;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import functionaltests.utils.ProActiveLock;

import static org.junit.Assert.*;
import static org.ow2.proactive.utils.FileUtils.createTempDirectory;


public class TestForkedTaskWorkingDir extends FunctionalTest {

    @Test
    public void input_files_are_in_working_dir_for_forked_tasks() throws Throwable {
        scriptTask();
        nativeTask();
        javaTaskTaskRestartedAnotherNode();
    }

    private void scriptTask() throws Exception {
        File input = createTempDirectory("test", ".input_script", null);
        File output = createTempDirectory("test", ".output_script", null);

        FileUtils.touch(new File(input, "inputFile_script.txt"));

        TaskFlowJob job = (TaskFlowJob) JobFactory_stax.getFactory().createJob(
                new File(TestForkedTaskWorkingDir.class.getResource(
                        "/functionaltests/descriptors/Job_forked_script_task_working_dir.xml").toURI())
                        .getAbsolutePath());

        job.setInputSpace(input.toURI().toString());
        job.setOutputSpace(output.toURI().toString());

        SchedulerTHelper.testJobSubmission(job);

        assertTrue(new File(output, "outputFile_script.txt").exists());
    }

    private void nativeTask() throws Exception {
        if (OperatingSystem.getOperatingSystem() == OperatingSystem.unix) {

            File input = createTempDirectory("test", ".input_native", null);
            File output = createTempDirectory("test", ".output_native", null);

            FileUtils.touch(new File(input, "inputFile_native.txt"));

            TaskFlowJob job = (TaskFlowJob) JobFactory_stax.getFactory().createJob(
                    new File(TestForkedTaskWorkingDir.class.getResource(
                            "/functionaltests/descriptors/Job_forked_native_task_working_dir.xml").toURI())
                            .getAbsolutePath());

            job.setInputSpace(input.toURI().toString());
            job.setOutputSpace(output.toURI().toString());

            SchedulerTHelper.testJobSubmission(job);

            assertTrue(new File(output, "outputFile_native.txt").exists());
        }
    }

    /*
     * SCHEDULING-2129 Mapping for a given space URI is already registered
     * 
     * Run a task, kill the node,let it restart on another node and check the the shared scratch
     * space was correctly setup by transferring a file created in working dir from the task
     */
    private void javaTaskTaskRestartedAnotherNode() throws Exception {
        ProActiveLock blockTaskFromTest = PAActiveObject.newActive(ProActiveLock.class, new Object[] {});
        ProActiveLock blockTestBeforeKillingNode = PAActiveObject.newActive(ProActiveLock.class,
                new Object[] {});

        TaskFlowJob job = createFileInLocalSpaceJob(PAActiveObject.getUrl(blockTaskFromTest), PAActiveObject
                .getUrl(blockTestBeforeKillingNode));

        JobId idJ1 = SchedulerTHelper.submitJob(job);

        SchedulerTHelper.log("Wait until task is in the middle of the run");
        final String taskNodeUrl = findNodeRunningTask();
        SchedulerTHelper.waitForEventTaskRunning(idJ1, "task1");
        ProActiveLock.waitUntilUnlocked(blockTestBeforeKillingNode);

        SchedulerTHelper.log("Kill the node running the task");
        RMTHelper.getDefaultInstance().killNode(taskNodeUrl);

        SchedulerTHelper.log("Let the task finish");
        blockTaskFromTest.unlock();

        SchedulerTHelper.log("Waiting for job 1 to finish");
        SchedulerTHelper.waitForEventJobFinished(idJ1);

        String userSpaceUri = URI.create(SchedulerTHelper.getSchedulerInterface().getUserSpaceURIs().get(0))
                .getPath();
        assertTrue("Could not find expected output file", new File(userSpaceUri, "output_file.txt").exists());
    }

    private TaskFlowJob createFileInLocalSpaceJob(String blockTaskFromTestUrl,
            String blockTestBeforeKillingNodeUrl) throws Exception {
        TaskFlowJob job = new TaskFlowJob();

        JavaTask task1 = new JavaTask();
        task1.setForkEnvironment(new ForkEnvironment());
        task1.setName("task1");
        task1.setExecutableClassName(CreateFileInLocalSpaceTask.class.getName());
        task1.addArgument("blockTaskFromTestUrl", blockTaskFromTestUrl);
        task1.addArgument("blockTestBeforeKillingNodeUrl", blockTestBeforeKillingNodeUrl);
        task1.addOutputFiles("output_file.txt", OutputAccessMode.TransferToUserSpace);

        job.addTask(task1);
        return job;
    }

    private String findNodeRunningTask() {
        RMNodeEvent event;
        do {
            event = RMTHelper.getDefaultInstance().waitForAnyNodeEvent(RMEventType.NODE_STATE_CHANGED,
                    30 * 1000);
        } while (!event.getNodeState().equals(NodeState.BUSY));
        return event.getNodeUrl();
    }

    public static class CreateFileInLocalSpaceTask extends JavaExecutable {

        private String blockTaskFromTestUrl;
        private String blockTestBeforeKillingNodeUrl;

        @Override
        public Serializable execute(TaskResult... results) throws Throwable {
            ProActiveLock blockTaskFromTest = PAActiveObject.lookupActive(ProActiveLock.class,
                    blockTaskFromTestUrl);

            ProActiveLock blockTestBeforeKillingNode = PAActiveObject.lookupActive(ProActiveLock.class,
                    blockTestBeforeKillingNodeUrl);

            blockTestBeforeKillingNode.unlock();
            // for the first execution, the node will be killed here
            ProActiveLock.waitUntilUnlocked(blockTaskFromTest);

            return new File("output_file.txt").createNewFile();
        }
    }

}
