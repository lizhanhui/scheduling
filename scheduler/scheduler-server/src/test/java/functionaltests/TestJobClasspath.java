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
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.ow2.proactive.scheduler.common.task.executable.Executable;
import org.ow2.proactive.scheduler.common.task.executable.JavaExecutable;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.ow2.proactive.scheduler.common.job.Job;
import org.ow2.proactive.scheduler.common.job.JobEnvironment;
import org.ow2.proactive.scheduler.common.job.JobId;
import org.ow2.proactive.scheduler.common.job.JobInfo;
import org.ow2.proactive.scheduler.common.job.JobResult;
import org.ow2.proactive.scheduler.common.job.JobStatus;
import org.ow2.proactive.scheduler.common.job.TaskFlowJob;
import org.ow2.proactive.scheduler.common.job.factories.JobFactory;
import org.ow2.proactive.scheduler.common.task.ScriptTask;
import org.ow2.proactive.scheduler.common.task.TaskInfo;
import org.ow2.proactive.scheduler.common.task.TaskResult;
import org.ow2.proactive.scheduler.common.task.TaskStatus;
import org.ow2.proactive.scheduler.common.util.JarUtils;
import org.ow2.proactive.scripting.SimpleScript;
import org.ow2.proactive.scripting.TaskScript;


/**
 * This class tests the job classpath feature. It will first start the
 * scheduler, then connect it and submit two different workers. Also test the
 * get[Job/Task]Result(String) methods.
 *
 * (test 1) submit the worker without classpath : must return a
 * classNotFoundException (test 2) submit the worker in classpath a : should
 * return a value (test 3) submit the worker in classpath b : should return a
 * different value even if the scheduler already knows this class name.
 *
 * @author The ProActive Team
 * @date 18 Feb. 09
 * @since ProActive Scheduling 1.0
 */
public class TestJobClasspath extends SchedulerConsecutive {
    final static String DESCRIPTOR = absolutify("/functionaltests/descriptors/Job_Test_CP.xml");

    private static final Integer firstValueToTest = 1;
    private static final Integer SecondValueToTest = 2;

    /**
     * Tests start here.
     *
     * @throws Throwable any exception that can be thrown during the test.
     */
    @org.junit.Test
    public void testJobClasspath() throws Throwable {

        String taskName = "task1";

        String[] classPathes = createClasses();

        {
            SchedulerTHelper.log("Test 0 : Jobclasspath in USERSPACE ...");
            // Push a jar into the userspace

            List<String> uris = SchedulerTHelper.getSchedulerInterface().getUserSpaceURIs();

            // Get the path to the userspace uri
            File userspaceDir = new File(new URI(uris.get(0)));

            // Create a jar in the userspace and refer to it in the jobClasspath
            File jarFile = new File(userspaceDir, "testJobClasspathInUserspace.jar");
            jarFile.deleteOnExit();
            JarUtils.jar(new String[] { classPathes[0] }, jarFile, null, null, null, null);

            //job creation
            Job submittedJob = JobFactory.getFactory().createJob(DESCRIPTOR);
            JobEnvironment env = new JobEnvironment();
            env.setJobClasspath(new String[] { "$USERSPACE/" + jarFile.getName() });
            submittedJob.setEnvironment(env);

            //job submission
            JobId id = SchedulerTHelper.submitJob(submittedJob);

            // Wait for job finishes
            SchedulerTHelper.waitForEventJobFinished(id);

            // Get task result from job result
            JobResult jr = SchedulerTHelper.getJobResult(id);
            Map<String, TaskResult> results = jr.getAllResults();
            Assert.assertEquals(1, results.size());

            // Print the task exception in the assertion messag in case of failure
            TaskResult tr = results.get(taskName);
            String message = "";
            if (tr.hadException()) {
                message = tr.getException().getMessage();
            }
            Assert.assertFalse("The task failure reason: " + message, tr.hadException());
            Assert.assertEquals("The executable class in " + jarFile +
                " is not returning the correct value, the jobclasspath is broken", firstValueToTest,
                    (Integer) tr.value());
        }

        {
            SchedulerTHelper.log("Test 1 : Awaiting faulty task due to missing class in jobclasspath ...");

            JobId id = SchedulerTHelper.submitJob(DESCRIPTOR);

            //this task should be faulty
            TaskInfo tInfo = SchedulerTHelper.waitForEventTaskFinished(id, taskName);
            Assert.assertEquals(TaskStatus.FAULTY, tInfo.getStatus());

            JobInfo jInfo = SchedulerTHelper.waitForEventJobFinished(id);

            Assert.assertEquals(JobStatus.FINISHED, jInfo.getStatus());
            JobResult jr = SchedulerTHelper.getJobResult(id);

            Assert.assertTrue(jr.hadException());
            Assert.assertEquals(1, jr.getAllResults().size());
            Assert.assertNotNull(jr.getResult("task1").getException());
        }

        {
            SchedulerTHelper.log("Test 2 : With classpath 1 ...");
            //job creation
            Job submittedJob = JobFactory.getFactory().createJob(DESCRIPTOR);
            JobEnvironment env = new JobEnvironment();
            env.setJobClasspath(new String[] { classPathes[0] });
            submittedJob.setEnvironment(env);

            //job submission
            JobId id = SchedulerTHelper.testJobSubmission(submittedJob);

            //get result
            JobResult jr = SchedulerTHelper.getJobResult(id);
            Assert.assertFalse(jr.hadException());
            Assert.assertEquals(1, jr.getAllResults().size());
            Assert.assertEquals(firstValueToTest, (Integer) jr.getResult(taskName).value());
        }

        {
            SchedulerTHelper.log("Test 3 : With classpath 2 ...");
            //job creation
            Job submittedJob = JobFactory.getFactory().createJob(DESCRIPTOR);
            JobEnvironment env = new JobEnvironment();
            env.setJobClasspath(new String[] { classPathes[1] });
            submittedJob.setEnvironment(env);

            //job submission
            JobId id = SchedulerTHelper.testJobSubmission(submittedJob);

            //check results
            JobResult jr = SchedulerTHelper.getJobResult(id);
            Assert.assertFalse(jr.hadException());
            Assert.assertEquals(1, jr.getAllResults().size());
            Assert.assertEquals(SecondValueToTest, (Integer) jr.getResult(taskName).value());
        }

        {
            SchedulerTHelper.log("Test 4 : Script task with jobclassapth ...");
            //job creation
            TaskFlowJob job = new TaskFlowJob();

            JobEnvironment env = new JobEnvironment();
            env.setJobClasspath(new String[] { classPathes[0] });
            job.setEnvironment(env);

            ScriptTask scriptTask = new ScriptTask();
            scriptTask.setName(taskName);
            String code = "result=ObjectStreamClass.lookup(test.Worker).getSerialVersionUID()";
            SimpleScript ss = new SimpleScript(code, "groovy");
            scriptTask.setScript(new TaskScript(ss));
            job.addTask(scriptTask);

            //job submission
            JobId id = SchedulerTHelper.testJobSubmission(job);

            //check results
            JobResult jr = SchedulerTHelper.getJobResult(id);
            Assert.assertFalse(jr.hadException());
            Map<String, TaskResult> results = jr.getAllResults();
            Assert.assertEquals(1, results.size());
            Long value = (Long) results.get(taskName).value();
            Assert.assertEquals(firstValueToTest, (Integer) value.intValue());
        }
    }

    /**
     * Returns absolute path of a relative path to this class location
     *
     * @param relative the path relative to this class
     * @return the absolute path
     */
    public static String absolutify(String relative) {
        return FileUtils.toFile(TestJobClasspath.class.getResource(relative)).getAbsolutePath();
    }

    /**
     * Create 2 classes with different return values in 2 different classPathes
     * and return the 2 created classPathes.
     *
     * @return the 2 created classPathes where to find the classes.
     * @throws Exception If the classes cannot be created
     */
    private String[] createClasses() throws Exception {
        String[] classPathes = new String[2];
        String className = "test.Worker";

        ClassPool pool = ClassPool.getDefault();
        //create new classes
        CtClass cc1 = pool.makeClass(className);
        CtClass cc2 = pool.makeClass(className);
        CtClass serializableClass = pool.get("java.io.Serializable");

        //get super-type and super-super-type
        CtClass upper = pool.get(JavaExecutable.class.getName());
        CtClass upupper = pool.get(Executable.class.getName());

        //get Executable 'execute' method
        CtMethod absExec = upupper.getMethod("execute",
                "([Lorg/ow2/proactive/scheduler/common/task/TaskResult;)Ljava/io/Serializable;");

        //set superclass of new classes
        cc1.setSuperclass(upper);
        cc1.addInterface(serializableClass);
        cc2.setSuperclass(upper);
        cc2.addInterface(serializableClass);

        //get a directory in the temp directory
        File tmpdir = new File(System.getProperty("java.io.tmpdir") + File.separator + "SchedTestJob_CP");

        //add uid to first class
        CtField uidField1 = new CtField(CtClass.longType, "serialVersionUID", cc1);
        uidField1.setModifiers(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL);
        cc1.addField(uidField1, CtField.Initializer.constant((long) firstValueToTest));

        //create method for first class
        CtMethod exec1 = CtNewMethod.make(serializableClass, absExec.getName(), absExec.getParameterTypes(),
                absExec.getExceptionTypes(), "return new java.lang.Integer(" + firstValueToTest + ");", cc1);
        cc1.addMethod(exec1);

        //create first classPath
        File f1 = new File(tmpdir.getAbsolutePath() + File.separator + firstValueToTest);
        f1.mkdirs();
        classPathes[0] = f1.getAbsolutePath();
        cc1.writeFile(classPathes[0]);

        //add uid
        CtField uidField2 = new CtField(CtClass.longType, "serialVersionUID", cc2);
        uidField2.setModifiers(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL);
        cc2.addField(uidField2, CtField.Initializer.constant((long) SecondValueToTest));

        //create method for second class
        CtMethod exec2 = CtNewMethod.make(serializableClass, absExec.getName(), absExec.getParameterTypes(),
                absExec.getExceptionTypes(), "return new java.lang.Integer(" + SecondValueToTest + ");", cc2);
        cc2.addMethod(exec2);

        //create second classPath
        File f2 = new File(tmpdir.getAbsolutePath() + File.separator + SecondValueToTest);
        f2.mkdirs();
        classPathes[1] = f2.getAbsolutePath();
        cc2.writeFile(classPathes[1]);

        return classPathes;
    }
}