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
import java.net.URL;

import org.objectweb.proactive.utils.OperatingSystem;
import org.ow2.proactive.scheduler.common.job.JobId;
import org.ow2.proactive.scheduler.common.job.JobInfo;
import org.ow2.proactive.scheduler.common.job.JobResult;
import org.ow2.proactive.scheduler.common.job.JobState;
import org.ow2.proactive.scheduler.common.job.JobStatus;
import org.ow2.proactive.scheduler.common.job.TaskFlowJob;
import org.ow2.proactive.scheduler.common.job.factories.JobFactory_stax;
import org.ow2.proactive.scheduler.common.task.NativeTask;
import org.ow2.proactive.scheduler.common.task.TaskInfo;
import org.ow2.proactive.scheduler.common.task.TaskStatus;
import org.junit.Assert;


/**
 * Test whether attribute reservation of several nodes for a native task
 * book correctly number a needed node (attribute coresNumber in nativeExecutable in a job SML descriptor)
 * Test whther PAS_NODEFILE and PAS_CORE_NB environment variables are correctly set
 *
 * @author The ProActive Team
 */
public class TestMultipleHostsRequest extends SchedulerConsecutive {

    private static URL jobDescriptor = TestMultipleHostsRequest.class
            .getResource("/functionaltests/descriptors/Job_native_4_hosts.xml");

    private static String executablePathPropertyName = "EXEC_PATH";

    private static URL executablePath = TestMultipleHostsRequest.class
            .getResource("/functionaltests/executables/test_multiple_hosts_request.sh");

    private static URL executablePathWindows = TestMultipleHostsRequest.class
            .getResource("/functionaltests/executables/test_multiple_hosts_request.bat");

    /**
     * Tests start here.
     *
     * @throws Throwable any exception that can be thrown during the test.
     */
    @org.junit.Test
    public void run() throws Throwable {

        String task1Name = "task1";

        switch (OperatingSystem.getOperatingSystem()) {
            case windows:
                //set system Property for executable path
                System.setProperty(executablePathPropertyName, new File(executablePathWindows.toURI())
                        .getAbsolutePath());
                break;
            case unix:
                SchedulerTHelper.setExecutable(new File(executablePath.toURI()).getAbsolutePath());
                //set system Property for executable path
                System.setProperty(executablePathPropertyName, new File(executablePath.toURI())
                        .getAbsolutePath());
                break;
            default:
                throw new IllegalStateException("Unsupported operating system");
        }

        //set system Property for executable path
        //System.setProperty(executablePathPropertyName, new File(executablePath.toURI()).getAbsolutePath());

        //test submission and event reception
        TaskFlowJob job = (TaskFlowJob) JobFactory_stax.getFactory().createJob(
                new File(jobDescriptor.toURI()).getAbsolutePath());

        //must add /bin/sh at beginning of task1 command line on Linux OS in runAsMe mode
        //and add property NODESNUMBER and NODESFILE at the end
        if (OperatingSystem.getOperatingSystem().equals(OperatingSystem.unix) &&
            System.getProperty("proactive.test.runAsMe") != null) {
            String[] commandLine = ((NativeTask) job.getTask(task1Name)).getCommandLine();
            String[] newCommandLine = new String[commandLine.length + 3];
            newCommandLine[0] = "/bin/sh";
            for (int i = 0; i < commandLine.length; i++) {
                newCommandLine[i + 1] = commandLine[i];
            }
            newCommandLine[newCommandLine.length - 2] = "$NODESNUMBER";
            newCommandLine[newCommandLine.length - 1] = "$NODESFILE";
            ((NativeTask) job.getTask(task1Name)).setCommandLine(newCommandLine);
        }

        JobId id = SchedulerTHelper.submitJob(job);
        RMTHelper rmHelper = RMTHelper.getDefaultInstance();
        rmHelper.createNodeSource("extra", 3);

        SchedulerTHelper.log("Job submitted, id " + id.toString());

        SchedulerTHelper.log("Waiting for jobSubmitted Event");
        JobState receivedState = SchedulerTHelper.waitForEventJobSubmitted(id);

        Assert.assertEquals(receivedState.getId(), id);

        SchedulerTHelper.log("Waiting for job running");
        JobInfo jInfo = SchedulerTHelper.waitForEventJobRunning(id);
        Assert.assertEquals(jInfo.getJobId(), id);
        Assert.assertEquals(JobStatus.RUNNING, jInfo.getStatus());

        SchedulerTHelper.waitForEventTaskRunning(id, task1Name);
        TaskInfo tInfo = SchedulerTHelper.waitForEventTaskFinished(id, task1Name);

        SchedulerTHelper.log(SchedulerTHelper.getSchedulerInterface().getTaskResult(id, "task1").getOutput()
                .getAllLogs(false));

        Assert.assertEquals(TaskStatus.FINISHED, tInfo.getStatus());

        SchedulerTHelper.waitForEventJobFinished(id);
        JobResult res = SchedulerTHelper.getJobResult(id);

        //check that there is one exception in results
        Assert.assertTrue(res.getExceptionResults().size() == 0);

        //remove job
        SchedulerTHelper.removeJob(id);
        SchedulerTHelper.waitForEventJobRemoved(id);
    }
}
