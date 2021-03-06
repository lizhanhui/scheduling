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
 *  Initial developer(s):               The ActiveEon Team
 *                        http://www.activeeon.com/
 *  Contributor(s):
 *
 * ################################################################
 * $$ACTIVEEON_INITIAL_DEV$$
 */
package functionaltests;

import org.apache.log4j.Level;
import org.junit.Assert;
import org.objectweb.proactive.ActiveObjectCreationException;
import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.core.ProActiveTimeoutException;
import org.objectweb.proactive.core.config.CentralPAPropertyRepository;
import org.objectweb.proactive.core.node.NodeException;
import org.objectweb.proactive.core.util.ProActiveInet;
import org.objectweb.proactive.utils.OperatingSystem;

import org.ow2.proactive.authentication.crypto.CredData;
import org.ow2.proactive.authentication.crypto.Credentials;
import org.ow2.proactive.process_tree_killer.ProcessTree;
import org.ow2.proactive.resourcemanager.core.properties.PAResourceManagerProperties;
import org.ow2.proactive.resourcemanager.frontend.ResourceManager;
import org.ow2.proactive.rm.util.process.EnvironmentCookieBasedChildProcessKiller;
import org.ow2.proactive.scheduler.common.Scheduler;
import org.ow2.proactive.scheduler.common.SchedulerAuthenticationInterface;
import org.ow2.proactive.scheduler.common.SchedulerConnection;
import org.ow2.proactive.scheduler.common.SchedulerConstants;
import org.ow2.proactive.scheduler.common.SchedulerEvent;
import org.ow2.proactive.scheduler.common.SchedulerState;
import org.ow2.proactive.scheduler.common.exception.SchedulerException;
import org.ow2.proactive.scheduler.common.exception.UnknownJobException;
import org.ow2.proactive.scheduler.common.job.Job;
import org.ow2.proactive.scheduler.common.job.JobId;
import org.ow2.proactive.scheduler.common.job.JobInfo;
import org.ow2.proactive.scheduler.common.job.JobResult;
import org.ow2.proactive.scheduler.common.job.JobState;
import org.ow2.proactive.scheduler.common.job.JobStatus;
import org.ow2.proactive.scheduler.common.job.TaskFlowJob;
import org.ow2.proactive.scheduler.common.job.factories.JobFactory;
import org.ow2.proactive.scheduler.common.task.ForkEnvironment;
import org.ow2.proactive.scheduler.common.task.JavaTask;
import org.ow2.proactive.scheduler.common.task.NativeTask;
import org.ow2.proactive.scheduler.common.task.Task;
import org.ow2.proactive.scheduler.common.task.TaskInfo;
import org.ow2.proactive.scheduler.common.task.TaskResult;
import org.ow2.proactive.scheduler.common.task.TaskStatus;
import org.ow2.proactive.scheduler.core.properties.PASchedulerProperties;
import org.ow2.proactive.utils.FileUtils;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import functionaltests.common.CommonTUtils;
import functionaltests.common.InputStreamReaderThread;
import functionaltests.monitor.MonitorEventReceiver;
import functionaltests.monitor.SchedulerMonitorsHandler;


/**
 *
 * Static helpers that provide main operations for Scheduler functional test.
 *
 * - helpers for launching a scheduler and a RM in a forked JVM, and deploys 5 local ProActive nodes :
 * Scheduler can be stated with default configuration file, configuration is also
 * defined in $PA_SCHEDULER/config/PAschedulerProperties.ini. If database exists in
 * $PA_SCHEDULER/SCHEDULER_DB/, it recovers it and keeps its state.
 *
 * Scheduler can be started with specific configuration file for tests, in that case :
 * 		- database database is recovered without jobs, in $PA_SCHEDULER/SCHEDULER_DB/
 * 		- removejobdelay property is set to 1.
 * 		- numberofexecutiononfailure property is set to 2
 * 		- initialwaitingtime is set to 10
 *
 * scheduler can also be started with other specific scheduler property file, and GCM deployment file,
 *
 * - helpers to acquire user and administrator interfaces
 * - helper for job submission
 * - helpers for events waiting. Creates if needed an event receiver that receives
 * all Scheduler events, store them until waitForEvent**()methods check theses event.
 *
 * For waitForEvent**() methods, it acts as Producer-consumer mechanism ;
 * a Scheduler produce events that are memorized,
 * and waiting methods waitForEvent**() are consumers of these events.
 * It means that an event asked to be waited for by a call to waitForEvent**() methods, is removed
 * after its occurrence. On the contrary, an event is kept till a waitForEvent**() for this event
 * has been called.
 *
 * waitForTerminatedJob() method dosen't act as other waitForEvent**() Methods.
 * This method deduce a job finished from current Scheduler's job states and received event.
 * This method can also be used for testing for job submission with killing and restarting
 * Scheduler.
 *
 * WARNING, you cannot get Scheduler user interface and Administrator interface twice ;
 * //TODO solve this, one connection per body allowed
 *
 *
 * @author ProActive team
 * @since ProActive Scheduling 1.0
 *
 */
public class SchedulerTHelper {

    private static EnvironmentCookieBasedChildProcessKiller childProcessKiller = new EnvironmentCookieBasedChildProcessKiller(
        "TEST");

    protected static URL functionalTestRMProperties = SchedulerTHelper.class
            .getResource("config/functionalTRMProperties.ini");

    protected static URL functionalTestSchedulerProperties = SchedulerTHelper.class
            .getResource("config/functionalTSchedulerProperties.ini");

    public static final int RMI_PORT = 1199;
    public static String schedulerUrl = "rmi://" + ProActiveInet.getInstance().getHostname() + ":" +
        RMI_PORT + "/" + SchedulerConstants.SCHEDULER_DEFAULT_NAME;

    private static Process schedulerProcess;

    protected static SchedulerAuthenticationInterface schedulerAuth;

    protected static Scheduler adminSchedInterface;

    protected static SchedulerMonitorsHandler monitorsHandler;

    protected static MonitorEventReceiver eventReceiver;

    public static String admin_username = "demo";
    public static String admin_password = "demo";

    public static String user_username = "user";
    public static String user_password = "pwd";

    /**
     * Start the scheduler using a forked JVM and
     * deploys, with its associated Resource manager, 5 local ProActive nodes.
     *
     * @throws Exception if an error occurs.
     */
    public static void startScheduler() throws Exception {
        startScheduler(true, null);
    }

    /**
     * Start the scheduler using a forked JVM and
     * deploys, with its associated Resource manager, 5 local ProActive nodes.
     *
     * @param configuration the Scheduler configuration file to use (default is functionalTSchedulerProperties.ini)
     * 			null to use the default one.
     * @throws Exception if an error occurs.
     */
    public static void startScheduler(String configuration) throws Exception {
        startScheduler(true, configuration);
    }

    /**
     * Start the scheduler using a forked JVM and
     * deploys, with its associated empty Resource manager.
     *
     * @throws Exception if an error occurs.
     */
    public static void startSchedulerWithEmptyResourceManager() throws Exception {
        startScheduler(false, null);
    }

    /**
     * Same as startSchedulerWithEmptyResourceManager but allows to specify a rm property file path
     * @param rmPropertyFilePath the file holding rm properties.
     * @throws Exception if an error occurs.
     */
    public static void startSchedulerWithEmptyResourceManager(String rmPropertyFilePath) throws Exception {
        startScheduler(false, null, rmPropertyFilePath, null);
    }

    /**
     * Starts Scheduler with scheduler properties file,
     * @param localnodes true if the RM has to start some nodes
     * @param schedPropertiesFilePath the Scheduler configuration file to use (default is functionalTSchedulerProperties.ini)
     * 			null to use the default one.
     * @throws Exception
     */
    public static void startScheduler(boolean localnodes, String schedPropertiesFilePath) throws Exception {
        startScheduler(localnodes, schedPropertiesFilePath, null, null);
    }

    /**
     * Same as startScheduler but allows to specify a file holding rm properties
     *
     * @throws Exception
     */
    public static void startScheduler(boolean localnodes, String schedPropertiesFilePath,
            String rmPropertiesFilePath, String rmUrl) throws Exception {
        if (schedPropertiesFilePath == null) {
            schedPropertiesFilePath = new File(functionalTestSchedulerProperties.toURI()).getAbsolutePath();
        }
        if (rmPropertiesFilePath == null) {
            rmPropertiesFilePath = new File(functionalTestRMProperties.toURI()).getAbsolutePath();
        }
        cleanTMP();

        List<String> commandLine = new ArrayList<String>();
        commandLine.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        commandLine.add("-Djava.security.manager");
        //commandLine.add("-agentlib:jdwp=transport=dt_socket,server=y,address=9009,suspend=y");
        String proactiveHome = CentralPAPropertyRepository.PA_HOME.getValue();
        if (!CentralPAPropertyRepository.PA_HOME.isSet()) {
            proactiveHome = PAResourceManagerProperties.RM_HOME.getValueAsString();
            CentralPAPropertyRepository.PA_HOME.setValue(PAResourceManagerProperties.RM_HOME
                    .getValueAsString());
        }

        commandLine.add(CentralPAPropertyRepository.PA_HOME.getCmdLine() + proactiveHome);

        commandLine.add(CentralPAPropertyRepository.PA_RMI_PORT.getCmdLine() + RMI_PORT);

        String securityPolicy = CentralPAPropertyRepository.JAVA_SECURITY_POLICY.getValue();
        if (!CentralPAPropertyRepository.JAVA_SECURITY_POLICY.isSet()) {
            securityPolicy = PASchedulerProperties.SCHEDULER_HOME.getValueAsString() +
                "/config/security.java.policy-server";
        }
        commandLine.add(CentralPAPropertyRepository.JAVA_SECURITY_POLICY.getCmdLine() + securityPolicy);

        String log4jConfiguration = CentralPAPropertyRepository.LOG4J.getValue();
        if (!CentralPAPropertyRepository.LOG4J.isSet()) {
            log4jConfiguration = SchedulerTHelper.class.getResource("/log4j-junit").toString();
        }
        commandLine.add(CentralPAPropertyRepository.LOG4J.getCmdLine() + log4jConfiguration);

        commandLine.add(PASchedulerProperties.SCHEDULER_HOME.getCmdLine() +
            PASchedulerProperties.SCHEDULER_HOME.getValueAsString());
        commandLine.add(PAResourceManagerProperties.RM_HOME.getCmdLine() +
            PAResourceManagerProperties.RM_HOME.getValueAsString());
        if (System.getProperty("pas.launcher.forkas.method") != null) {
            commandLine.add("-Dpas.launcher.forkas.method=" +
                System.getProperty("pas.launcher.forkas.method"));
        }
        if (System.getProperty("proactive.test.runAsMe") != null) {
            commandLine.add("-Dproactive.test.runAsMe=true");
        }
        //commandLine.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8765");

        commandLine.add("-cp");
        commandLine.add(testClasspath());
        commandLine.add(CentralPAPropertyRepository.PA_TEST.getCmdLine() + "true");
        commandLine.add(SchedulerTStarter.class.getName());
        commandLine.add(String.valueOf(localnodes));
        commandLine.add(schedPropertiesFilePath);
        commandLine.add(rmPropertiesFilePath);
        if (rmUrl != null) {
            commandLine.add(rmUrl);
        }

        System.out.println("Starting Scheduler process: " + commandLine);

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put(childProcessKiller.getCookieName(), childProcessKiller.getCookieValue());
        schedulerProcess = processBuilder.start();

        InputStreamReaderThread outputReader = new InputStreamReaderThread(schedulerProcess.getInputStream(),
            "[Scheduler VM output]: ");
        outputReader.start();

        System.out.println("Waiting for the Scheduler using URL: " + schedulerUrl);
        schedulerAuth = SchedulerConnection.waitAndJoin(schedulerUrl);
        System.out.println("The Scheduler is up and running");

        if (localnodes) {
            // Waiting while all the nodes will be registered in the RM.
            // Without waiting test can finish earlier than nodes are added.
            // It leads to test execution hang up on windows due to running processes.

            RMTHelper rmHelper = RMTHelper.getDefaultInstance();
            ResourceManager rm = rmHelper.getResourceManager();
            while (rm.getState().getTotalAliveNodesNumber() < SchedulerTStarter.RM_NODE_NUMBER) {
                System.out.println("Waiting for nodes deployment");
                Thread.sleep(1000);
            }
            System.out.println("Nodes are deployed");
        }
    }

    public static String testClasspath() {
        String home = PASchedulerProperties.SCHEDULER_HOME.getValueAsString();
        String classpathToLibFolderWithWildcard = home + File.separator + "dist" + File.separator + "lib" +
            File.separator + "*";
        if (OperatingSystem.getOperatingSystem().equals(OperatingSystem.windows)) {
            // required by windows otherwise wildcard is expanded
            classpathToLibFolderWithWildcard = "\"" + classpathToLibFolderWithWildcard + "\"";
        }
        return classpathToLibFolderWithWildcard;
    }

    /* convenience method to clean TMP from dataspace when executing test */
    private static void cleanTMP() {
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        for (File f : tmp.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith("PA_JVM");
            }
        })) {
            FileUtils.removeDir(f);
        }
    }

    /**
     * Kill the forked Scheduler if exists.
     */
    public static void killScheduler() throws Exception {
        if (schedulerProcess != null) {
            schedulerProcess.destroy();
            schedulerProcess.waitFor();
            schedulerProcess = null;

            // sometimes RM_NODE object isn't removed from the RMI registry after JVM with RM is killed (SCHEDULING-1498)
            CommonTUtils.cleanupRMActiveObjectRegistry();
            for (int nodeNumber = 0; nodeNumber < SchedulerTStarter.RM_NODE_NUMBER; nodeNumber++) {
                CommonTUtils.cleanupActiveObjectRegistry(SchedulerTStarter.RM_NODE_NAME + "-" + nodeNumber); // clean nodes
            }
            CommonTUtils.cleanupActiveObjectRegistry(SchedulerConstants.SCHEDULER_DEFAULT_NAME);
        }
        schedulerAuth = null;
        adminSchedInterface = null;
        RMTHelper.getDefaultInstance().reset();
    }

    /**
     * Kill the forked Scheduler and all nodes.
     */
    public static void killSchedulerAndNodes() throws Exception {
        org.apache.log4j.Logger.getLogger(ProcessTree.class).setLevel(Level.DEBUG);
        childProcessKiller.killChildProcesses();
        killScheduler();
    }

    /**
     * Restart the scheduler using a forked JVM and all children Nodes.
     * User or administrator interface is not reconnected automatically.
     *
     * @param configuration the Scheduler configuration file to use (default is functionalTSchedulerProperties.ini)
     * 			null to use the default one.
     * @throws Exception
     */
    public static void killSchedulerAndNodesAndRestart(String configuration) throws Exception {
        killSchedulerAndNodes();
        startScheduler(configuration);
    }

    /**
     * Log a String on console.
     */
    public static void log(String s) {
        System.out.println("------------------------------ " + s);
    }

    public static void log(Exception e) {
        e.printStackTrace();
    }

    /**
     * Return Scheduler authentication interface. Start Scheduler with test
     * configuration file, if scheduler is not yet started.
     * @throws Exception
     */
    public static SchedulerAuthenticationInterface getSchedulerAuth() throws Exception {
        if (schedulerAuth == null) {
            try {
                // trying to connect to the existing Scheduler
                schedulerAuth = SchedulerConnection.join(schedulerUrl);
            } catch (Exception e) {
                // creating a new Scheduler
                startScheduler(null);
            }
        }
        return schedulerAuth;
    }

    /**
     * Starts the scheduler or connected to existing one if in consecutive mode
     *
     * @throws Exception
     */
    public static void init() throws Exception {
        getSchedulerAuth();
    }

    /**
     * Return Scheduler's interface. Start Scheduler if needed,
     * connect as administrator if needed (if not yet connected as user).
     *
     * WARNING : if there was a previous connection as User, this connection is shut down.
     * And so some event can be missed by event receiver, between disconnection and reconnection
     * (only one connection to Scheduler per body is possible).
     *
     * @return scheduler interface
     * @throws Exception if an error occurs.
     */
    public static Scheduler getSchedulerInterface() throws Exception {
        return getSchedulerInterface(UserType.USER);
    }

    /**
     * Return Scheduler's interface. Start Scheduler if needed,
     * connect as administrator if needed (if not yet connected as user).
     *
     * WARNING : if there was a previous connection as User, this connection is shut down.
     * And so some event can be missed by event receiver, between disconnection and reconnection
     * (only one connection to Scheduler per body is possible).
     *
     * @param user Type of user
     * @return scheduler interface
     * @throws Exception if an error occurs.
     */
    public static Scheduler getSchedulerInterface(UserType user) throws Exception {
        if (adminSchedInterface == null) {
            if (System.getProperty("proactive.test.runAsMe") != null) {
                connect(user);
            } else {
                connect();
            }
        }
        return adminSchedInterface;
    }

    /**
     * Creates a job from an XML job descriptor, submit it, and return immediately.
     * connect as user if needed (if not yet connected as user).
     * @return JobId the job's identifier corresponding to submission.
     * @throws Exception if an error occurs at job creation/submission.
     */
    public static JobId submitJob(String jobDescPath) throws Exception {
        return submitJob(jobDescPath, UserType.USER);
    }

    /**
     * Creates a job from an XML job descriptor, submit it, and return immediately.
     * connect as user if needed (if not yet connected as user).
     * @param user Type of user
     * @return JobId the job's identifier corresponding to submission.
     * @throws Exception if an error occurs at job creation/submission.
     */
    public static JobId submitJob(String jobDescPath, UserType user) throws Exception {
        Job jobToSubmit = JobFactory.getFactory().createJob(jobDescPath);
        return submitJob(jobToSubmit, user);
    }

    /**
     * Creates a job from an XML job descriptor, submit it, and return immediately.
     * connect as user if needed (if not yet connected as user).
     * @param mode true if forked mode
     * @return JobId the job's identifier corresponding to submission.
     * @throws Exception if an error occurs at job creation/submission.
     */
    public static JobId submitJob(String jobDescPath, ExecutionMode mode) throws Exception {
        return submitJob(jobDescPath, mode, UserType.USER);
    }

    /**
     * Creates a job from an XML job descriptor, submit it, and return immediately.
     * connect as user if needed (if not yet connected as user).
     * @param mode true if forked mode
     * @param user Type of user
     * @return JobId the job's identifier corresponding to submission.
     * @throws Exception if an error occurs at job creation/submission.
     */
    public static JobId submitJob(String jobDescPath, ExecutionMode mode, UserType user) throws Exception {
        Job jobToSubmit = JobFactory.getFactory().createJob(jobDescPath);
        return submitJob(jobToSubmit, mode, user);
    }

    /**
     * Submit a job, and return immediately.
     * Connect as user if needed (if not yet connected as user).
     * @param jobToSubmit job object to schedule.
     * @return JobId the job's identifier corresponding to submission.
     * @throws Exception if an error occurs at job submission.
     */
    public static JobId submitJob(Job jobToSubmit) throws Exception {
        return submitJob(jobToSubmit, UserType.USER);
    }

    /**
     * Submit a job, and return immediately.
     * Connect as user if needed (if not yet connected as user).
     * @param jobToSubmit job object to schedule.
     * @param user Type of user
     * @return JobId the job's identifier corresponding to submission.
     * @throws Exception if an error occurs at job submission.
     */
    public static JobId submitJob(Job jobToSubmit, UserType user) throws Exception {
        ExecutionMode mode = checkModeSet();
        return submitJob(jobToSubmit, mode, user);
    }

    /**
     * Submit a job, and return immediately.
     * Connect as user if needed (if not yet connected as user).
     * @param jobToSubmit job object to schedule.
     * @param mode true if the mode is forked, false if normal mode
     * @return JobId the job's identifier corresponding to submission.
     * @throws Exception if an error occurs at job submission.
     */
    public static JobId submitJob(Job jobToSubmit, ExecutionMode mode) throws Exception {
        return submitJob(jobToSubmit, mode, UserType.USER);
    }

    /**
     * Submit a job, and return immediately.
     * Connect as user if needed (if not yet connected as user).
     * @param jobToSubmit job object to schedule.
     * @param mode true if the mode is forked, false if normal mode
     * @param user Type of user
     * @return JobId the job's identifier corresponding to submission.
     * @throws Exception if an error occurs at job submission.
     */
    public static JobId submitJob(Job jobToSubmit, ExecutionMode mode, UserType user) throws Exception {
        Scheduler userInt = getSchedulerInterface(user);
        if (mode == ExecutionMode.fork) {
            setForked(jobToSubmit);
        } else if (mode == ExecutionMode.runAsMe) {
            setRunAsMe(jobToSubmit);
        }
        return userInt.submit(jobToSubmit);
    }

    /**
     * Kills a job
     * @param jobId
     * @return success or failure at killing the job
     * @throws Exception
     */
    public static boolean killJob(String jobId) throws Exception {
        Scheduler userInt = getSchedulerInterface();
        return userInt.killJob(jobId);
    }

    /**
     * Remove a job from Scheduler database.
     * connect as user if needed (if not yet connected as user).
     * @param id of the job to remove from database.
     * @throws Exception if an error occurs at job removal.
     */
    public static void removeJob(JobId id) throws Exception {
        Scheduler userInt = getSchedulerInterface();
        userInt.removeJob(id);
    }

    public static void testJobSubmissionAndVerifyAllResults(String jobDescPath) throws Throwable {
        Job testJob = JobFactory.getFactory().createJob(jobDescPath);
        testJobSubmissionAndVerifyAllResults(testJob, jobDescPath);
    }

    public static void testJobSubmissionAndVerifyAllResults(Job testJob, String jobDesc) throws Throwable {
        JobId id = testJobSubmission(testJob, UserType.USER);
        // check result are not null
        JobResult res = SchedulerTHelper.getJobResult(id);
        Assert.assertFalse("Had Exception : " + jobDesc, SchedulerTHelper.getJobResult(id).hadException());

        for (Map.Entry<String, TaskResult> entry : res.getAllResults().entrySet()) {

            Assert.assertFalse("Had Exception (" + jobDesc + ") : " + entry.getKey(), entry.getValue()
                    .hadException());

            Assert.assertNotNull("Result not null (" + jobDesc + ") : " + entry.getKey(), entry.getValue()
                    .value());
        }

        SchedulerTHelper.removeJob(id);
        SchedulerTHelper.waitForEventJobRemoved(id);
    }

    /**
     * Creates and submit a job from an XML job descriptor, and check
     * event related to this job submission :
     * 1/ job submitted event
     * 2/ job passing from pending to running (with state set to running).
     * 3/ every task passing from pending to running (with state set to running).
     * 4/ every task passing from running to finished (with state set to finished).
     * 5/ and finally job passing from running to finished (with state set to finished).
     * Then returns.
     *
     * This is the simplest events sequence of a job submission. If you need to test
     * specific event or task states (failure, rescheduling etc, you must not use this
     * helper and check events sequence with waitForEvent**() functions.
     *
     * @param jobDescPath path to an XML job descriptor to submit
     * @return JobId, the job's identifier.
     * @throws Exception if an error occurs at job creation/submission, or during
     * verification of events sequence.
     */
    public static JobId testJobSubmission(String jobDescPath) throws Exception {
        return testJobSubmission(jobDescPath, UserType.USER);
    }

    /**
     * Creates and submit a job from an XML job descriptor, and check
     * event related to this job submission :
     * 1/ job submitted event
     * 2/ job passing from pending to running (with state set to running).
     * 3/ every task passing from pending to running (with state set to running).
     * 4/ every task passing from running to finished (with state set to finished).
     * 5/ and finally job passing from running to finished (with state set to finished).
     * Then returns.
     *
     * This is the simplest events sequence of a job submission. If you need to test
     * specific event or task states (failure, rescheduling etc, you must not use this
     * helper and check events sequence with waitForEvent**() functions.
     *
     * @param jobDescPath path to an XML job descriptor to submit
     * @param user Type of user
     * @return JobId, the job's identifier.
     * @throws Exception if an error occurs at job creation/submission, or during
     * verification of events sequence.
     */
    public static JobId testJobSubmission(String jobDescPath, UserType user) throws Exception {
        Job jobToTest = JobFactory.getFactory().createJob(jobDescPath);
        return testJobSubmission(jobToTest, user);
    }

    /**
     * Creates and submit a job from an XML job descriptor, and check
     * event related to this job submission :
     * 1/ job submitted event
     * 2/ job passing from pending to running (with state set to running).
     * 3/ every task passing from pending to running (with state set to running).
     * 4/ every task passing from running to finished (with state set to finished).
     * 5/ and finally job passing from running to finished (with state set to finished).
     * Then returns.
     *
     * This is the simplest events sequence of a job submission. If you need to test
     * specific event or task states (failure, rescheduling etc, you must not use this
     * helper and check events sequence with waitForEvent**() functions.
     *
     * @param jobDescPath path to an XML job descriptor to submit
     * @param mode true if forked mode
     * @return JobId, the job's identifier.
     * @throws Exception if an error occurs at job creation/submission, or during
     * verification of events sequence.
     */
    public static JobId testJobSubmission(String jobDescPath, ExecutionMode mode) throws Exception {
        return testJobSubmission(jobDescPath, mode, UserType.USER);
    }

    /**
     * Creates and submit a job from an XML job descriptor, and check
     * event related to this job submission :
     * 1/ job submitted event
     * 2/ job passing from pending to running (with state set to running).
     * 3/ every task passing from pending to running (with state set to running).
     * 4/ every task passing from running to finished (with state set to finished).
     * 5/ and finally job passing from running to finished (with state set to finished).
     * Then returns.
     *
     * This is the simplest events sequence of a job submission. If you need to test
     * specific event or task states (failure, rescheduling etc, you must not use this
     * helper and check events sequence with waitForEvent**() functions.
     *
     * @param jobDescPath path to an XML job descriptor to submit
     * @param mode true if forked mode
     * @param user Type of user
     * @return JobId, the job's identifier.
     * @throws Exception if an error occurs at job creation/submission, or during
     * verification of events sequence.
     */
    public static JobId testJobSubmission(String jobDescPath, ExecutionMode mode, UserType user)
            throws Exception {
        Job jobToTest = JobFactory.getFactory().createJob(jobDescPath);
        return testJobSubmission(jobToTest, mode, user);
    }

    /**
     * Creates and submit a job from an XML job descriptor, and check, with assertions,
     * event related to this job submission :
     * 1/ job submitted event
     * 2/ job passing from pending to running (with state set to running).
     * 3/ every task passing from pending to running (with state set to running).
     * 4/ every task finish without error ; passing from running to finished (with state set to finished).
     * 5/ and finally job passing from running to finished (with state set to finished).
     * Then returns.
     *
     * This is the simplest events sequence of a job submission. If you need to test
     * specific events or task states (failures, rescheduling etc, you must not use this
     * helper and check events sequence with waitForEvent**() functions.
     *
     * @param jobToSubmit job object to schedule.
     * @return JobId, the job's identifier.
     * @throws Exception if an error occurs at job submission, or during
     * verification of events sequence.
     */
    public static JobId testJobSubmission(Job jobToSubmit) throws Exception {
        return testJobSubmission(jobToSubmit, UserType.USER);
    }

    /**
     * Creates and submit a job from an XML job descriptor, and check, with assertions,
     * event related to this job submission :
     * 1/ job submitted event
     * 2/ job passing from pending to running (with state set to running).
     * 3/ every task passing from pending to running (with state set to running).
     * 4/ every task finish without error ; passing from running to finished (with state set to finished).
     * 5/ and finally job passing from running to finished (with state set to finished).
     * Then returns.
     *
     * This is the simplest events sequence of a job submission. If you need to test
     * specific events or task states (failures, rescheduling etc, you must not use this
     * helper and check events sequence with waitForEvent**() functions.
     *
     * @param jobToSubmit job object to schedule.
     * @param user Type of user
     * @return JobId, the job's identifier.
     * @throws Exception if an error occurs at job submission, or during
     * verification of events sequence.
     */
    public static JobId testJobSubmission(Job jobToSubmit, UserType user) throws Exception {
        ExecutionMode mode = checkModeSet();
        return testJobSubmission(jobToSubmit, mode, user);
    }

    /**
     * Creates and submit a job from an XML job descriptor, and check, with assertions,
     * event related to this job submission :
     * 1/ job submitted event
     * 2/ job passing from pending to running (with state set to running).
     * 3/ every task passing from pending to running (with state set to running).
     * 4/ every task finish without error ; passing from running to finished (with state set to finished).
     * 5/ and finally job passing from running to finished (with state set to finished).
     * Then returns.
     *
     * This is the simplest events sequence of a job submission. If you need to test
     * specific events or task states (failures, rescheduling etc, you must not use this
     * helper and check events sequence with waitForEvent**() functions.
     *
     * @param jobToSubmit job object to schedule.
     * @param mode true if the mode is forked, false if normal mode
     * @return JobId, the job's identifier.
     * @throws Exception if an error occurs at job submission, or during
     * verification of events sequence.
     */
    public static JobId testJobSubmission(Job jobToSubmit, ExecutionMode mode) throws Exception {
        return testJobSubmission(jobToSubmit, mode, UserType.USER);
    }

    /**
     * Creates and submit a job from an XML job descriptor, and check, with assertions,
     * event related to this job submission :
     * 1/ job submitted event
     * 2/ job passing from pending to running (with state set to running).
     * 3/ every task passing from pending to running (with state set to running).
     * 4/ every task finish without error ; passing from running to finished (with state set to finished).
     * 5/ and finally job passing from running to finished (with state set to finished).
     * Then returns.
     *
     * This is the simplest events sequence of a job submission. If you need to test
     * specific events or task states (failures, rescheduling etc, you must not use this
     * helper and check events sequence with waitForEvent**() functions.
     *
     * @param jobToSubmit job object to schedule.
     * @param mode true if the mode is forked, false if normal mode
     * @return JobId, the job's identifier.
     * @throws Exception if an error occurs at job submission, or during
     * verification of events sequence.
     */
    public static JobId testJobSubmission(Job jobToSubmit, ExecutionMode mode, UserType user)
            throws Exception {
        Scheduler userInt = getSchedulerInterface(user);

        if (mode == ExecutionMode.fork) {
            setForked(jobToSubmit);
        } else if (mode == ExecutionMode.runAsMe) {
            setRunAsMe(jobToSubmit);
        }

        JobId id = userInt.submit(jobToSubmit);

        log("Job submitted, id " + id.toString());

        log("Waiting for jobSubmitted");
        JobState receivedstate = SchedulerTHelper.waitForEventJobSubmitted(id);
        Assert.assertEquals(id, receivedstate.getId());

        log("Waiting for job running");
        JobInfo jInfo = SchedulerTHelper.waitForEventJobRunning(id);

        Assert.assertEquals(jInfo.getJobId(), id);
        Assert.assertEquals("Job " + jInfo.getJobId(), JobStatus.RUNNING, jInfo.getStatus());

        if (jobToSubmit instanceof TaskFlowJob) {

            for (Task t : ((TaskFlowJob) jobToSubmit).getTasks()) {
                log("Waiting for task running : " + t.getName());
                TaskInfo ti = waitForEventTaskRunning(id, t.getName());
                Assert.assertEquals(t.getName(), ti.getTaskId().getReadableName());
                Assert.assertEquals("Task " + t.getName(), TaskStatus.RUNNING, ti.getStatus());
            }

            for (Task t : ((TaskFlowJob) jobToSubmit).getTasks()) {
                log("Waiting for task finished : " + t.getName());
                TaskInfo ti = waitForEventTaskFinished(id, t.getName());
                Assert.assertEquals(t.getName(), ti.getTaskId().getReadableName());
                if (ti.getStatus() == TaskStatus.FAULTY) {
                    TaskResult tres = userInt.getTaskResult(jInfo.getJobId(), t.getName());
                    Assert.assertNotNull("Task result of " + t.getName(), tres);
                    if (tres.getOutput() != null) {
                        System.err.println("Output of failing task (" + t.getName() + ") :");
                        System.err.println(tres.getOutput().getAllLogs(true));
                    }
                    if (tres.hadException()) {
                        System.err.println("Exception occurred in task (" + t.getName() + ") :");
                        tres.getException().printStackTrace(System.err);
                    }

                }
                Assert.assertEquals("Task " + t.getName(), TaskStatus.FINISHED, ti.getStatus());
            }

        }

        log("Waiting for job finished");
        jInfo = SchedulerTHelper.waitForEventJobFinished(id);
        Assert.assertEquals("Job " + jInfo.getJobId(), JobStatus.FINISHED, jInfo.getStatus());

        log("Job finished");
        return id;
    }

    /**
     * Get job result form a job Id.
     * Connect as user if needed (if not yet connected as user).
     * @param id job identifier, representing job result.
     * @return JobResult storing results.
     * @throws Exception if an exception occurs in result retrieval
     */
    public static JobResult getJobResult(JobId id) throws Exception {
        return getSchedulerInterface().getJobResult(id);
    }

    public static TaskResult getTaskResult(JobId jobId, String taskName) throws Exception {
        return getSchedulerInterface().getTaskResult(jobId, taskName);
    }

    //---------------------------------------------------------------//
    // events waiting methods
    //---------------------------------------------------------------//

    /**
     * Wait for a job submission event for a specific job id.
     * If event has been already thrown by scheduler, returns immediately
     * with job object associated to event, otherwise wait for event reception.
     *
     * @param id  job identifier, for which submission event is waited for.
     * @return JobState object corresponding to job submitted event.
     */
    public static JobState waitForEventJobSubmitted(JobId id) {
        try {
            return waitForEventJobSubmitted(id, 0);
        } catch (ProActiveTimeoutException e) {
            //unreachable block, 0 means infinite, no timeout, no timeoutExcpetion
            //log sthing ?
            return null;
        }
    }

    /**
     * Wait for a job submission event for a specific job id.
     * If event has been already thrown by scheduler, returns immediately
     * with job object associated to event, otherwise wait for event reception.
     * @param id job identifier, for which submission event is waited for.
     * @param timeout max waiting time in milliseconds.
     * @return Jobstate object corresponding to job submitted event.
     * @throws ProActiveTimeoutException if timeout is reached.
     */
    public static JobState waitForEventJobSubmitted(JobId id, long timeout) throws ProActiveTimeoutException {
        return getMonitorsHandler().waitForEventJobSubmitted(id, timeout);
    }

    /**
     * Wait for a specific job passing from pending state to running state.
     * If corresponding event has been already thrown by scheduler, returns immediately
     * with jobInfo object associated to event, otherwise wait for reception
     * of the corresponding event.
     *
     * @param id job identifier, for which event is waited for.
     * @return JobInfo event's associated object.
     */
    public static JobInfo waitForEventJobRunning(JobId id) {
        try {
            return waitForEventJobRunning(id, 0);
        } catch (ProActiveTimeoutException e) {
            //unreachable block, 0 means infinite, no timeout
            //log sthing ?
            return null;
        }
    }

    /**
     * Wait for a job passing from pending to running state.
     * If corresponding event has been already thrown by scheduler, returns immediately
     * with jobInfo object associated to event, otherwise wait for reception
     * of the corresponding event.
     * @param id job identifier, for which event is waited for.
     * @param timeout max waiting time in milliseconds.
     * @return JobInfo event's associated object.
     * @throws ProActiveTimeoutException if timeout is reached.
     */
    public static JobInfo waitForEventJobRunning(JobId id, long timeout) throws ProActiveTimeoutException {
        return getMonitorsHandler().waitForEventJob(SchedulerEvent.JOB_PENDING_TO_RUNNING, id, timeout);
    }

    /**
     * Wait for a job passing from running to finished state.
     * If corresponding event has been already thrown by scheduler, returns immediately
     * with jobInfo object associated to event, otherwise wait for reception
     * of the corresponding event.
     * If job is already finished, return immediately.
     * @param id job identifier, for which event is waited for.
     * @return JobInfo event's associated object.
     */
    public static JobInfo waitForEventJobFinished(JobId id) throws Exception {
        try {
            return waitForJobEvent(id, 0, JobStatus.FINISHED, SchedulerEvent.JOB_RUNNING_TO_FINISHED);
        } catch (ProActiveTimeoutException e) {
            //unreachable block, 0 means infinite, no timeout
            //log sthing ?
            return null;
        }
    }

    /**
     * Wait for a job passing from running to finished state.
     * If corresponding event has been already thrown by scheduler, returns immediately
     * with JobInfo object associated to event, otherwise wait for event reception.
     *	This method corresponds to the running to finished transition
     *
     * @param id  job identifier, for which event is waited for.
     * @param timeout  max waiting time in milliseconds.
     * @return JobInfo event's associated object.
     * @throws ProActiveTimeoutException if timeout is reached.
     */
    public static JobInfo waitForEventJobFinished(JobId id, long timeout) throws Exception {
        return waitForJobEvent(id, timeout, JobStatus.FINISHED, SchedulerEvent.JOB_RUNNING_TO_FINISHED);
    }

    private static JobInfo waitForJobEvent(JobId id, long timeout, JobStatus jobStatusAfterEvent,
            SchedulerEvent jobEvent) throws Exception {
        JobState jobState = null;
        try {
            jobState = getSchedulerInterface().getJobState(id);
        } catch (UnknownJobException ignored) {
        }
        if (jobState != null && jobState.getStatus().equals(jobStatusAfterEvent)) {
            System.err.println("Job is already finished - do not wat for the 'job finished' event");
            return jobState.getJobInfo();
        } else {
            try {
                System.err.println("Waiting for the job finished event");
                return getMonitorsHandler().waitForEventJob(jobEvent, id, timeout);
            } catch (ProActiveTimeoutException e) {
                //unreachable block, 0 means infinite, no timeout
                //log something ?
                return null;
            }
        }
    }

    public static JobInfo waitForEventPendingJobFinished(JobId id, long timeout)
            throws ProActiveTimeoutException {
        return getMonitorsHandler().waitForEventJob(SchedulerEvent.JOB_PENDING_TO_FINISHED, id, timeout);
    }

    /**
     * Wait for a job removed from Scheduler's database.
     * If corresponding event has been already thrown by scheduler, returns immediately
     * with JobInfo object associated to event, otherwise wait for event reception.
     *
     * @param id job identifier, for which event is waited for.
     * @return JobInfo event's associated object.
     */
    public static JobInfo waitForEventJobRemoved(JobId id) {
        try {
            return waitForEventJobRemoved(id, 0);
        } catch (ProActiveTimeoutException e) {
            //unreachable block, 0 means infinite, no timeout
            //log sthing ?
            return null;
        }
    }

    /**
     * Wait for a event job removed from Scheduler's database.
     * If corresponding event has been already thrown by scheduler, returns immediately
     * with JobInfo object associated to event, otherwise wait for reception
     * of the corresponding event.
     *
     * @param id job identifier, for which event is waited for.
     * @param timeout max waiting time in milliseconds.
     * @return JobInfo event's associated object.
     * @throws ProActiveTimeoutException if timeout is reached.
     */
    public static JobInfo waitForEventJobRemoved(JobId id, long timeout) throws ProActiveTimeoutException {
        return getMonitorsHandler().waitForEventJob(SchedulerEvent.JOB_REMOVE_FINISHED, id, timeout);
    }

    /**
     * Wait for a task passing from pending to running.
     * If corresponding event has been already thrown by scheduler, returns immediately
     * with TaskInfo object associated to event, otherwise wait for reception
     * of the corresponding event.
     *
     * @param jobId job identifier, for which task belongs.
     * @param taskName for which event is waited for.
     * @return TaskInfo event's associated object.
     */
    public static TaskInfo waitForEventTaskRunning(JobId jobId, String taskName) {
        try {
            return waitForEventTaskRunning(jobId, taskName, 0);
        } catch (ProActiveTimeoutException e) {
            //unreachable block, 0 means infinite, no timeout
            //log sthing ?
            return null;
        }
    }

    /**
     * Wait for a task passing from pending to running.
     * If corresponding event has been already thrown by scheduler, returns immediately
     * with TaskInfo object associated to event, otherwise wait for reception
     * of the corresponding event.
     *
     * @param jobId job identifier, for which task belongs.
     * @param taskName for which event is waited for.
     * @param timeout max waiting time in milliseconds.
     * @return TaskInfo event's associated object.
     * @throws ProActiveTimeoutException if timeout is reached.
     */
    public static TaskInfo waitForEventTaskRunning(JobId jobId, String taskName, long timeout)
            throws ProActiveTimeoutException {
        return getMonitorsHandler().waitForEventTask(SchedulerEvent.TASK_PENDING_TO_RUNNING, jobId, taskName,
                timeout);
    }

    /**
     * Wait for a task failed that waits for restart.
     * If corresponding event has been already thrown by scheduler, returns immediately
     * with TaskInfo object associated to event, otherwise wait for reception
     * of the corresponding event.
     *
     * @param jobId job identifier, for which task belongs.
     * @param taskName for which event is waited for.
     * @return TaskInfo event's associated object.
     */
    public static TaskInfo waitForEventTaskWaitingForRestart(JobId jobId, String taskName) {
        try {
            return waitForEventTaskWaitingForRestart(jobId, taskName, 0);
        } catch (ProActiveTimeoutException e) {
            //unreachable block, 0 means infinite, no timeout
            //log sthing ?
            return null;
        }
    }

    /**
     * Wait for a task failed that waits for restart.
     * If corresponding event has been already thrown by scheduler, returns immediately
     * with TaskInfo object associated to event, otherwise wait for reception
     * of the corresponding event.
     *
     * @param jobId job identifier, for which task belongs.
     * @param taskName for which event is waited for.
     * @param timeout max waiting time in milliseconds.
     * @return TaskInfo event's associated object.
     * @throws ProActiveTimeoutException if timeout is reached.
     */
    public static TaskInfo waitForEventTaskWaitingForRestart(JobId jobId, String taskName, long timeout)
            throws ProActiveTimeoutException {
        return getMonitorsHandler().waitForEventTask(SchedulerEvent.TASK_WAITING_FOR_RESTART, jobId,
                taskName, timeout);
    }

    /**
     * Wait for a task passing from running to finished.
     * If corresponding event has been already thrown by scheduler, returns immediately
     * with TaskInfo object associated to event, otherwise wait for reception
     * of the corresponding event.
     *
     * @param jobId  job identifier, for which task belongs.
     * @param taskName for which event is waited for.
     * @return TaskEvent, associated event's object.
     */
    public static TaskInfo waitForEventTaskFinished(JobId jobId, String taskName) {
        try {
            return waitForEventTaskFinished(jobId, taskName, 0);
        } catch (ProActiveTimeoutException e) {
            //unreachable block, 0 means infinite, no timeout
            //log sthing ?
            return null;
        }
    }

    /**
     * Wait for a task passing from running to finished.
     * If corresponding event has been already thrown by scheduler, returns immediately
     * with TaskInfo object associated to event, otherwise wait for reception
     * of the corresponding event.
     *
     * @param jobId job identifier, for which task belongs.
     * @param taskName for which event is waited for.
     * @param timeout max waiting time in milliseconds.
     * @return TaskInfo, associated event's object.
     * @throws ProActiveTimeoutException if timeout is reached.
     */
    public static TaskInfo waitForEventTaskFinished(JobId jobId, String taskName, long timeout)
            throws ProActiveTimeoutException {
        return getMonitorsHandler().waitForEventTask(SchedulerEvent.TASK_RUNNING_TO_FINISHED, jobId,
                taskName, timeout);
    }

    /**
     * Wait for an event regarding Scheduler state : started, resumed, stopped...
     * If a corresponding event has been already thrown by scheduler, returns immediately,
     * otherwise wait for reception of the corresponding event.
     * @param event awaited event.
     */
    public static void waitForEventSchedulerState(SchedulerEvent event) {
        try {
            waitForEventSchedulerState(event, 0);
        } catch (ProActiveTimeoutException e) {
            //unreachable block, 0 means infinite, no timeout
            //log sthing ?
        }
    }

    /**
     * Wait for an event regarding Scheduler state : started, resumed, stopped...
     * If a corresponding event has been already thrown by scheduler, returns immediately,
     * otherwise wait for reception of the corresponding event.
     * @param event awaited event.
     * @param timeout in milliseconds
     * @throws ProActiveTimeoutException if timeout is reached
     */
    public static void waitForEventSchedulerState(SchedulerEvent event, long timeout)
            throws ProActiveTimeoutException {
        getMonitorsHandler().waitForEventSchedulerState(event, timeout);
    }

    //---------------------------------------------------------------//
    // Job finished waiting methods
    //---------------------------------------------------------------//

    /**
     * Wait for a finished job. If Job is already finished, methods return.
     * This method doesn't wait strictly 'job finished event', it looks
     * first if the job is already finished, if yes, returns immediately.
     * Otherwise method performs a wait for job finished event.
     *
     * @param id JobId representing the job awaited to be finished.
     */
    public static void waitForFinishedJob(JobId id) {
        try {
            waitForFinishedJob(id, 0);
        } catch (ProActiveTimeoutException e) {
            //unreachable block, 0 means infinite, no timeout
            //log sthing ?
        }
    }

    /**
     * Wait for a finished job. If Job is already finished, methods return.
     * This method doesn't wait strictly 'job finished event', it looks
     * first if the job is already finished, if yes, returns immediately.
     * Otherwise method performs a wait for job finished event.
     *
     * @param id JobId representing the job awaited to be finished.
     * @param timeout in milliseconds
     * @throws ProActiveTimeoutException if timeout is reached
     */
    public static void waitForFinishedJob(JobId id, long timeout) throws ProActiveTimeoutException {
        monitorsHandler.waitForFinishedJob(id, timeout);
    }

    //-------------------------------------------------------------//
    //private methods
    //-------------------------------------------------------------//

    private static void initEventReceiver(Scheduler schedInt) throws NodeException, SchedulerException,
            ActiveObjectCreationException {

        SchedulerMonitorsHandler mHandler = getMonitorsHandler();
        if (eventReceiver == null) {
            /** create event receiver then turnActive to avoid deepCopy of MonitorsHandler object
             * 	(shared instance between event receiver and static helpers).
            */
            MonitorEventReceiver passiveEventReceiver = new MonitorEventReceiver(mHandler);
            eventReceiver = PAActiveObject.turnActive(passiveEventReceiver);

        }
        SchedulerState state = schedInt.addEventListener(eventReceiver, true, true);
        mHandler.init(state);
    }

    /**
     * Init connection as user
     * @throws Exception
     */
    private static void connect() throws Exception {
        SchedulerAuthenticationInterface authInt = getSchedulerAuth();
        Credentials cred = Credentials.createCredentials(new CredData(admin_username, admin_password),
                authInt.getPublicKey());
        adminSchedInterface = authInt.login(cred);
        initEventReceiver(adminSchedInterface);
    }

    /**
     * Init connection as user
     * @throws Exception
     */
    private static void connect(UserType user) throws Exception {
        if ((System.getProperty("proactive.test.login." + user) == null) ||
            (System.getProperty("proactive.test.password." + user) == null)) {
            throw new IllegalStateException(
                "Property proactive.test.login or proactive.test.password are not correctly set");
        }
        String login = System.getProperty("proactive.test.login." + user);
        String pwd = System.getProperty("proactive.test.password." + user);
        SchedulerAuthenticationInterface authInt = getSchedulerAuth();
        Credentials cred = Credentials.createCredentials(new CredData(login, pwd), authInt.getPublicKey());
        adminSchedInterface = authInt.login(cred);
        initEventReceiver(adminSchedInterface);
    }

    private static SchedulerMonitorsHandler getMonitorsHandler() {
        if (monitorsHandler == null) {
            monitorsHandler = new SchedulerMonitorsHandler();
        }
        return monitorsHandler;
    }

    public static void setExecutable(String filesList) throws IOException {
        Runtime.getRuntime().exec("chmod u+x " + filesList);
    }

    public static void setForked(Job job) {
        if (TaskFlowJob.class.isAssignableFrom(job.getClass())) {
            for (Task task : ((TaskFlowJob) job).getTasks()) {
                if (JavaTask.class.isAssignableFrom(task.getClass())) {
                    if (!((JavaTask) task).isFork()) {
                        ForkEnvironment forkedEnv = new ForkEnvironment();
                        forkedEnv.addJVMArgument("-Dproactive.test=true");
                        ((JavaTask) task).setForkEnvironment(forkedEnv);
                    }
                }
            }
        }
    }

    public static void setRunAsMe(Job job) {
        if (TaskFlowJob.class.isAssignableFrom(job.getClass())) {
            for (Task task : ((TaskFlowJob) job).getTasks()) {
                if (JavaTask.class.isAssignableFrom(task.getClass())) {
                    if (!task.isRunAsMe()) {
                        task.setRunAsMe(true);
                    }
                } else if (NativeTask.class.isAssignableFrom(task.getClass())) {
                    if (!task.isRunAsMe()) {
                        task.setRunAsMe(true);
                    }
                }
            }
        }
    }

    private static ExecutionMode checkModeSet() {
        if (System.getProperty("proactive.test.runAsMe") != null) {
            return ExecutionMode.runAsMe;
        } else if (System.getProperty("proactive.test.fork") != null) {
            return ExecutionMode.fork;
        } else {
            return ExecutionMode.normal;
        }
    }

}
