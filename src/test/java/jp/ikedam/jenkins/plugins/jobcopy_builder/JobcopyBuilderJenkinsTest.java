/*
 * The MIT License
 *
 * Copyright (c) 2012-2013 IKEDA Yasuyuki
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jp.ikedam.jenkins.plugins.jobcopy_builder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
//import org.jvnet.hudson.test.HudsonTestCase;
import org.xml.sax.SAXException;

import com.cloudbees.hudson.plugins.folder.Folder;

import hudson.EnvVars;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.Descriptor.FormException;
import hudson.plugins.promoted_builds.JobPropertyImpl;
import hudson.plugins.promoted_builds.PromotionProcess;
import hudson.plugins.promoted_builds.conditions.DownstreamPassCondition;
import hudson.plugins.promoted_builds.conditions.ManualCondition;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

/**
 * Tests for JobcopyBuilder, corresponded to Jenkins.
 *
 */
public class JobcopyBuilderJenkinsTest
{
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private JobcopyBuilder.DescriptorImpl getDescriptor()
    {
        return (JobcopyBuilder.DescriptorImpl) (new JobcopyBuilder(
                null,
                null,
                false,
                null,
                null)).getDescriptor();
    }

    private MatrixProject createMatrixProject() throws IOException
    {
        final String prjName = "test" + j.jenkins.getItems().size();
        return createMatrixProject(prjName);
    }

    private MatrixProject createMatrixProject(final String name)
            throws IOException
    {
        return j.jenkins.createProject(MatrixProject.class, name);
    }

    @Test
    public void testDescriptorDoFillFromJobNameItems() throws IOException
    {
        final JobcopyBuilder.DescriptorImpl descriptor = getDescriptor();

        // Job will be added after new job created.
        final ComboBoxModel beforeList = descriptor
                .doFillFromJobNameItems(null);

        final FreeStyleProject project = j.createFreeStyleProject(
                "testDescriptorDoFillFromJobNameItems1");
        final String newJobname = project.getName();

        final ComboBoxModel afterList = descriptor.doFillFromJobNameItems(null);

        assertEquals(
                "new job created",
                beforeList.size() + 1,
                afterList.size());
        assertTrue("new job created", afterList.contains(newJobname));
    }

    @Test
    public void testDescriptorDoFillFromJobNameItemsWithFolder()
            throws IOException
    {
        final JobcopyBuilder.DescriptorImpl descriptor = getDescriptor();

        // job1
        // folder1/job2
        // folder1/folder2/job3
        final FreeStyleProject job1 = j.createFreeStyleProject("job1");
        final Folder folder1 = j.getInstance().createProject(
                Folder.class,
                "folder1");
        final FreeStyleProject job2 = folder1
                .createProject(FreeStyleProject.class, "job2");
        final Folder folder2 = folder1.createProject(Folder.class, "folder2");
        final FreeStyleProject job3 = folder2
                .createProject(FreeStyleProject.class, "job3");

        assertEquals(
                Arrays.asList(
                        "folder1",
                        "folder1/folder2",
                        "folder1/folder2/job3",
                        "folder1/job2",
                        "job1"),
                descriptor.doFillFromJobNameItems(job1));
        assertEquals(
                Arrays.asList(
                        "../folder1",
                        "../job1",
                        "folder2",
                        "folder2/job3",
                        "job2"),
                descriptor.doFillFromJobNameItems(job2));
        assertEquals(
                Arrays.asList(
                        "../../folder1",
                        "../../job1",
                        "../folder2",
                        "../job2",
                        "job3"),
                descriptor.doFillFromJobNameItems(job3));
    }

    @Test
    public void testDescriptorDoCheckFromJobName() throws IOException
    {
        final JobcopyBuilder.DescriptorImpl descriptor = getDescriptor();
        final FreeStyleProject project = j
                .createFreeStyleProject("testDescriptorDoCheckFromJobName1");
        final String existJobname = project.getName();

        // exist job
        {
            assertEquals(
                    "exist job",
                    FormValidation.Kind.OK,
                    descriptor.doCheckFromJobName(null, existJobname).kind);
        }

        // exist job surrounded with blank
        {
            assertEquals(
                    "exist job surrounded with blank",
                    FormValidation.Kind.OK,
                    descriptor.doCheckFromJobName(
                            null,
                            "  " + existJobname + " ").kind);
        }

        // non-exist job
        {
            assertEquals(
                    "non-exist job",
                    FormValidation.Kind.WARNING,
                    descriptor.doCheckFromJobName(null, "nosuchjob").kind);
        }

        // uses variable
        {
            assertEquals(
                    "uses variable",
                    FormValidation.Kind.OK,
                    descriptor.doCheckFromJobName(
                            null,
                            "nosuchjob${name}").kind);
        }

        // escaped dollar
        {
            // Not warned even if no such job exists.
            assertEquals(
                    "escaped dollar",
                    FormValidation.Kind.OK,
                    descriptor.doCheckFromJobName(null, "$$nosuchjob").kind);
        }

        // null
        {
            assertEquals(
                    "null",
                    FormValidation.Kind.ERROR,
                    descriptor.doCheckFromJobName(null, null).kind);
        }

        // empty
        {
            assertEquals(
                    "empty",
                    FormValidation.Kind.ERROR,
                    descriptor.doCheckFromJobName(null, "").kind);
        }

        // blank
        {
            assertEquals(
                    "blank",
                    FormValidation.Kind.ERROR,
                    descriptor.doCheckFromJobName(null, "  ").kind);
        }
    }

    @Test
    public void testDescriptorDoCheckFromJobNameWithFolder() throws IOException
    {
        final JobcopyBuilder.DescriptorImpl descriptor = getDescriptor();

        // job1
        // folder1/job2
        // folder1/folder2/job3
        final FreeStyleProject job1 = j.createFreeStyleProject("job1");
        final Folder folder1 = j.getInstance().createProject(
                Folder.class,
                "folder1");
        final FreeStyleProject job2 = folder1
                .createProject(FreeStyleProject.class, "job2");
        final Folder folder2 = folder1.createProject(Folder.class, "folder2");
        @SuppressWarnings("unused")
        final FreeStyleProject job3 = folder2
                .createProject(FreeStyleProject.class, "job3");

        // exist job
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckFromJobName(job1, "job1").kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckFromJobName(job1, "folder1/job2").kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckFromJobName(job1, "folder1/../job1").kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckFromJobName(job2, "../job1").kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckFromJobName(job2, "job2").kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckFromJobName(job2, "folder2/job3").kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckFromJobName(job2, "folder2/../job2").kind);

        // non-exist job
        assertEquals(
                FormValidation.Kind.WARNING,
                descriptor.doCheckFromJobName(job1, "job2").kind);
        assertEquals(
                FormValidation.Kind.WARNING,
                descriptor.doCheckFromJobName(job1, "folder1/job1").kind);
        assertEquals(
                FormValidation.Kind.WARNING,
                descriptor.doCheckFromJobName(job1, "folder1/../job2").kind);
        assertEquals(
                FormValidation.Kind.WARNING,
                descriptor.doCheckFromJobName(job2, "job1").kind);
        assertEquals(
                FormValidation.Kind.WARNING,
                descriptor.doCheckFromJobName(job2, "folder1/job2").kind);
        assertEquals(
                FormValidation.Kind.WARNING,
                descriptor.doCheckFromJobName(job2, "job3").kind);
        assertEquals(
                FormValidation.Kind.WARNING,
                descriptor.doCheckFromJobName(job2, "../job2").kind);
    }

    @Test
    public void testDescriptorDoCheckToJobName() throws IOException
    {
        final JobcopyBuilder.DescriptorImpl descriptor = getDescriptor();
        final FreeStyleProject project = j
                .createFreeStyleProject("testDescriptorDoCheckToJobName1");
        final String existJobname = project.getName();

        // exist job, overwrite
        {
            assertEquals(
                    "exist job, overwrite",
                    FormValidation.Kind.OK,
                    descriptor.doCheckToJobName(null, existJobname, true).kind);
        }

        // exist job, not overwrite
        {
            assertEquals(
                    "exist job, not overwrite",
                    FormValidation.Kind.WARNING,
                    descriptor.doCheckToJobName(
                            null,
                            existJobname,
                            false).kind);
        }

        // exist job surrounded with blank, not overwrite
        {
            assertEquals(
                    "exist job surrounded with blank, not overwrite",
                    FormValidation.Kind.WARNING,
                    descriptor.doCheckToJobName(
                            null,
                            "  " + existJobname + "  ",
                            false).kind);
        }

        // non-exist job, overwrite
        {
            assertEquals(
                    "non-exist job",
                    FormValidation.Kind.OK,
                    descriptor.doCheckToJobName(null, "nosuchjob", true).kind);
        }

        // non-exist job, not overwrite
        {
            assertEquals(
                    "non-exist job",
                    FormValidation.Kind.OK,
                    descriptor.doCheckToJobName(null, "nosuchjob", false).kind);
        }

        // uses variable
        {
            assertEquals(
                    "uses variable",
                    FormValidation.Kind.OK,
                    descriptor.doCheckToJobName(
                            null,
                            "nosuchjob${name}",
                            false).kind);
        }

        // null
        {
            assertEquals(
                    "null",
                    FormValidation.Kind.ERROR,
                    descriptor.doCheckToJobName(null, null, false).kind);
        }

        // empty
        {
            assertEquals(
                    "empty",
                    FormValidation.Kind.ERROR,
                    descriptor.doCheckToJobName(null, "", false).kind);
        }

        // blank
        {
            assertEquals(
                    "blank",
                    FormValidation.Kind.ERROR,
                    descriptor.doCheckToJobName(null, "  ", false).kind);
        }
    }

    @Test
    public void testDescriptorDoCheckToJobNameWithFolder() throws IOException
    {
        final JobcopyBuilder.DescriptorImpl descriptor = getDescriptor();
        // job1
        // folder1/job2
        // folder1/folder2/job3
        final FreeStyleProject job1 = j.createFreeStyleProject("job1");
        final Folder folder1 = j.getInstance().createProject(
                Folder.class,
                "folder1");
        final FreeStyleProject job2 = folder1
                .createProject(FreeStyleProject.class, "job2");
        final Folder folder2 = folder1.createProject(Folder.class, "folder2");
        @SuppressWarnings("unused")
        final FreeStyleProject job3 = folder2
                .createProject(FreeStyleProject.class, "job3");

        // exist job, overwrite
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job1, "job1", true).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job1, "folder1/job2", true).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(
                        job1,
                        "folder1/../job1",
                        true).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job2, "../job1", true).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job2, "job2", true).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job2, "folder2/job3", true).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(
                        job2,
                        "folder2/../job2",
                        true).kind);

        // exist job, not overwrite
        assertEquals(
                FormValidation.Kind.WARNING,
                descriptor.doCheckToJobName(job1, "job1", false).kind);
        assertEquals(
                FormValidation.Kind.WARNING,
                descriptor.doCheckToJobName(job1, "folder1/job2", false).kind);
        assertEquals(
                FormValidation.Kind.WARNING,
                descriptor.doCheckToJobName(
                        job1,
                        "folder1/../job1",
                        false).kind);
        assertEquals(
                FormValidation.Kind.WARNING,
                descriptor.doCheckToJobName(job2, "../job1", false).kind);
        assertEquals(
                FormValidation.Kind.WARNING,
                descriptor.doCheckToJobName(job2, "job2", false).kind);
        assertEquals(
                FormValidation.Kind.WARNING,
                descriptor.doCheckToJobName(job2, "folder2/job3", false).kind);
        assertEquals(
                FormValidation.Kind.WARNING,
                descriptor.doCheckToJobName(
                        job2,
                        "folder2/../job2",
                        false).kind);

        // non-exist job, overwrite
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job1, "job2", true).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job1, "folder1/job1", true).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(
                        job1,
                        "folder1/../job2",
                        true).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job2, "job1", true).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job2, "folder1/job2", true).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job2, "job3", true).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job2, "../job2", true).kind);

        // non-exist job, not overwrite
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job1, "job2", false).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job1, "folder1/job1", false).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(
                        job1,
                        "folder1/../job2",
                        false).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job2, "job1", false).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job2, "folder1/job2", false).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job2, "job3", false).kind);
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckToJobName(job2, "../job2", false).kind);
    }

    /**
     * Test cases that builds succeed
     *
     * @throws IOException
     * @throws InterruptedException
     * @throws ExecutionException
     */
    @Test
    public void testPerform()
            throws IOException, InterruptedException, ExecutionException
    {
        final FreeStyleProject fromJob = j
                .createFreeStyleProject("testPerform1");

        final String toJobName = "JobCopiedTo";
        FreeStyleProject toJob = (FreeStyleProject) Jenkins
                .getInstance()
                .getItem(toJobName);
        if(toJob != null)
        {
            toJob.delete();
        }

        // Set up the job copied from.
        // Define parameters replaced in ReplaceOperation
        fromJob.addProperty(
                new ParametersDefinitionProperty(new StringParameterDefinition(
                        "PARAM1",
                        "DEFAULTVALUE",
                        "Description")));
        // disable the job.
        fromJob.disable();
        fromJob.save();

        final ParametersAction paramAction = new ParametersAction(
                new StringParameterValue("fromJobName", fromJob.getName()),
                new StringParameterValue("toJobName", toJobName));

        // No variable use.
        {
            final List<JobcopyOperation> lst = new ArrayList<JobcopyOperation>();
            lst.add(new EnableOperation());
            lst.add(new ReplaceOperation("PARAM1", false, "PARAM2", false));
            final JobcopyBuilder target = new JobcopyBuilder(
                    fromJob.getName(),
                    toJobName,
                    false,
                    lst,
                    null);

            final FreeStyleProject project = j
                    .createFreeStyleProject("testPerform2");
            project.getBuildersList().add(target);

            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals("No variable use", Result.SUCCESS, b.getResult());

            toJob = (FreeStyleProject) Jenkins.getInstance().getItem(toJobName);
            assertNotNull("No variable use", toJob);

            assertFalse("No variable use", toJob.isDisabled());

            final ParametersDefinitionProperty prop = toJob
                    .getAction(ParametersDefinitionProperty.class);
            assertFalse(
                    "No variable use",
                    prop.getParameterDefinitionNames().contains("PARAM1"));
            assertTrue(
                    "No variable use",
                    prop.getParameterDefinitionNames().contains("PARAM2"));

            toJob.delete();
        }

        // Using variables.
        {
            final List<JobcopyOperation> lst = new ArrayList<JobcopyOperation>();
            lst.add(new EnableOperation());
            lst.add(new ReplaceOperation("PARAM1", false, "PARAM2", false));
            final JobcopyBuilder target = new JobcopyBuilder(
                    "${fromJobName}",
                    "${toJobName}",
                    false,
                    lst,
                    null);

            final FreeStyleProject project = j
                    .createFreeStyleProject("testPerform3");
            project.getBuildersList().add(target);

            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals("Using variables", Result.SUCCESS, b.getResult());

            toJob = (FreeStyleProject) Jenkins.getInstance().getItem(toJobName);
            assertNotNull("Using variables", toJob);

            assertFalse("Using variables", toJob.isDisabled());

            final ParametersDefinitionProperty prop = toJob
                    .getAction(ParametersDefinitionProperty.class);
            assertFalse(
                    "Using variables",
                    prop.getParameterDefinitionNames().contains("PARAM1"));
            assertTrue(
                    "Using variables",
                    prop.getParameterDefinitionNames().contains("PARAM2"));

            toJob.delete();
        }

        // overwrite
        {
            // create new job
            {
                final List<JobcopyOperation> lst = new ArrayList<JobcopyOperation>();
                lst.add(new EnableOperation());
                final JobcopyBuilder target = new JobcopyBuilder(
                        fromJob.getName(),
                        toJobName,
                        false,
                        lst,
                        null);

                final FreeStyleProject project = j
                        .createFreeStyleProject("testPerform4");
                project.getBuildersList().add(target);

                FreeStyleBuild b = project
                        .scheduleBuild2(
                                project.getQuietPeriod(),
                                new Cause.UserIdCause(),
                                paramAction)
                        .get();
                while(b.isBuilding())
                {
                    Thread.sleep(100);
                }
                assertEquals(
                        "overwrite(create a new job)",
                        Result.SUCCESS,
                        b.getResult());

                toJob = (FreeStyleProject) Jenkins
                        .getInstance()
                        .getItem(toJobName);
                assertNotNull("overwrite(create a new job)", toJob);

                assertFalse("overwrite(create a new job)", toJob.isDisabled());

                final ParametersDefinitionProperty prop = toJob
                        .getAction(ParametersDefinitionProperty.class);
                assertTrue(
                        "overwrite(create a new job)",
                        prop.getParameterDefinitionNames().contains("PARAM1"));
                assertFalse(
                        "overwrite(create a new job)",
                        prop.getParameterDefinitionNames().contains("PARAM2"));

                toJob.save();
                // Execute it.
                assertTrue(
                        "overwrite(create a new job)",
                        toJob.getBuilds().isEmpty());
                b = toJob.scheduleBuild2(toJob.getQuietPeriod()).get();
                while(b.isBuilding())
                {
                    Thread.sleep(100);
                }
                assertEquals(
                        "overwrite(create a new job)",
                        1,
                        toJob.getBuilds().size());
            }

            // overwrite
            {
                final List<JobcopyOperation> lst = new ArrayList<JobcopyOperation>();
                lst.add(new ReplaceOperation("PARAM1", false, "PARAM2", false));
                final JobcopyBuilder target = new JobcopyBuilder(
                        fromJob.getName(),
                        toJobName,
                        true,
                        lst,
                        null);

                final FreeStyleProject project = j
                        .createFreeStyleProject("testPerform5");
                project.getBuildersList().add(target);

                final FreeStyleBuild b = project
                        .scheduleBuild2(
                                project.getQuietPeriod(),
                                new Cause.UserIdCause(),
                                paramAction)
                        .get();
                while(b.isBuilding())
                {
                    Thread.sleep(100);
                }
                assertEquals(
                        "overwrite(overwrite)",
                        Result.SUCCESS,
                        b.getResult());

                toJob = (FreeStyleProject) Jenkins
                        .getInstance()
                        .getItem(toJobName);
                assertNotNull("overwrite(overwrite)", toJob);

                assertTrue("overwrite(overwrite)", toJob.isDisabled());

                final ParametersDefinitionProperty prop = toJob
                        .getAction(ParametersDefinitionProperty.class);
                assertFalse(
                        "overwrite(overwrite)",
                        prop.getParameterDefinitionNames().contains("PARAM1"));
                assertTrue(
                        "overwrite(overwrite)",
                        prop.getParameterDefinitionNames().contains("PARAM2"));

                assertEquals(
                        "overwrite(overwrite)",
                        1,
                        toJob.getBuilds().size());
            }

            toJob.delete();
        }

        // null for jobcopyOperationList
        {
            final JobcopyBuilder target = new JobcopyBuilder(
                    fromJob.getName(),
                    toJobName,
                    false,
                    null,
                    null);

            final FreeStyleProject project = j
                    .createFreeStyleProject("testPerform6");
            project.getBuildersList().add(target);

            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals(
                    "null for jobcopyOperationList",
                    Result.SUCCESS,
                    b.getResult());

            toJob = (FreeStyleProject) Jenkins.getInstance().getItem(toJobName);
            assertNotNull("null for jobcopyOperationList", toJob);

            toJob.delete();
        }

        // empty for jobcopyOperationList
        {
            final JobcopyBuilder target = new JobcopyBuilder(
                    fromJob.getName(),
                    toJobName,
                    false,
                    new ArrayList<JobcopyOperation>(0),
                    null);

            final FreeStyleProject project = j
                    .createFreeStyleProject("testPerform7");
            project.getBuildersList().add(target);

            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals(
                    "empty for jobcopyOperationList",
                    Result.SUCCESS,
                    b.getResult());

            toJob = (FreeStyleProject) Jenkins.getInstance().getItem(toJobName);
            assertNotNull("empty for jobcopyOperationList", toJob);

            toJob.delete();
        }
    }

    /**
     * Used for a error case test.
     *
     * This class must be serializable, so anonymous class cannot be applied.
     */
    static private class NullJobcopyOperation extends JobcopyOperation
            implements Serializable
    {
        private static final long serialVersionUID = -4314651910414654207L;

        @Override
        public String perform(final String xmlString, final String encoding,
                final EnvVars env, final PrintStream logger)
        {
            return null;
        }
    };

    /**
     * Test cases that builds fail
     *
     * @throws IOException
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Test
    public void testPerformError()
            throws IOException, InterruptedException, ExecutionException
    {
        final FreeStyleProject project = j
                .createFreeStyleProject("testPerformError1");
        final String toJobName = "JobCopiedTo";
        final FreeStyleProject toJob = (FreeStyleProject) Jenkins
                .getInstance()
                .getItem(toJobName);
        if(toJob != null)
        {
            toJob.delete();
        }
        final ParametersAction paramAction = new ParametersAction(
                new StringParameterValue("EMPTY", ""),
                new StringParameterValue("NOSUCHJOB", "nosuchjob"));

        // From Job Name is null.
        {
            final JobcopyBuilder target = new JobcopyBuilder(
                    null,
                    toJobName,
                    true,
                    new ArrayList<JobcopyOperation>(),
                    null);
            project.getBuildersList().add(target);
            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals(
                    "From Job Name is null",
                    Result.FAILURE,
                    b.getResult());
        }

        // From Job Name is empty.
        {
            final JobcopyBuilder target = new JobcopyBuilder(
                    "",
                    toJobName,
                    true,
                    new ArrayList<JobcopyOperation>(),
                    null);
            project.getBuildersList().add(target);
            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals(
                    "From Job Name is empty",
                    Result.FAILURE,
                    b.getResult());
        }

        // From Job Name is blank.
        {
            final JobcopyBuilder target = new JobcopyBuilder(
                    "  ",
                    toJobName,
                    true,
                    new ArrayList<JobcopyOperation>(),
                    null);
            project.getBuildersList().add(target);
            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals(
                    "From Job Name is blank",
                    Result.FAILURE,
                    b.getResult());
        }

        // From Job Name gets empty.
        {
            final JobcopyBuilder target = new JobcopyBuilder(
                    "${EMPTY}",
                    toJobName,
                    true,
                    new ArrayList<JobcopyOperation>(),
                    null);
            project.getBuildersList().add(target);
            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals(
                    "From Job Name gets empty",
                    Result.FAILURE,
                    b.getResult());
        }

        // From Job Name gets blank.
        {
            final JobcopyBuilder target = new JobcopyBuilder(
                    "  ${EMPTY}  ",
                    toJobName,
                    true,
                    new ArrayList<JobcopyOperation>(),
                    null);
            project.getBuildersList().add(target);
            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals(
                    "From Job Name gets blank",
                    Result.FAILURE,
                    b.getResult());
        }

        // To Job Name is null.
        {
            final JobcopyBuilder target = new JobcopyBuilder(
                    project.getName(),
                    null,
                    true,
                    new ArrayList<JobcopyOperation>(),
                    null);
            project.getBuildersList().add(target);
            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals("To Job Name is null", Result.FAILURE, b.getResult());
        }

        // To Job Name is empty.
        {
            final JobcopyBuilder target = new JobcopyBuilder(
                    project.getName(),
                    "",
                    true,
                    new ArrayList<JobcopyOperation>(),
                    null);
            project.getBuildersList().add(target);
            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals("To Job Name is empty", Result.FAILURE, b.getResult());
        }

        // To Job Name is blank.
        {
            final JobcopyBuilder target = new JobcopyBuilder(
                    project.getName(),
                    "  ",
                    true,
                    new ArrayList<JobcopyOperation>(),
                    null);
            project.getBuildersList().add(target);
            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals("To Job Name is blank", Result.FAILURE, b.getResult());
        }

        // To Job Name gets empty.
        {
            final JobcopyBuilder target = new JobcopyBuilder(
                    project.getName(),
                    "${EMPTY}",
                    true,
                    new ArrayList<JobcopyOperation>(),
                    null);
            project.getBuildersList().add(target);
            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals(
                    "To Job Name gets empty",
                    Result.FAILURE,
                    b.getResult());
        }

        // To Job Name gets blank.
        {
            final JobcopyBuilder target = new JobcopyBuilder(
                    project.getName(),
                    "  ${EMPTY}  ",
                    true,
                    new ArrayList<JobcopyOperation>(),
                    null);
            project.getBuildersList().add(target);
            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals(
                    "To Job Name gets blank",
                    Result.FAILURE,
                    b.getResult());
        }

        // From job does not exist.
        {
            final JobcopyBuilder target = new JobcopyBuilder(
                    "nosuchjob",
                    toJobName,
                    true,
                    new ArrayList<JobcopyOperation>(),
                    null);
            project.getBuildersList().add(target);
            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals(
                    "From job does not exist",
                    Result.FAILURE,
                    b.getResult());
        }

        // From job(expanded) does not exist.
        {
            final JobcopyBuilder target = new JobcopyBuilder(
                    "${NOSUCHJOB}",
                    toJobName,
                    true,
                    new ArrayList<JobcopyOperation>(),
                    null);
            project.getBuildersList().add(target);
            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals(
                    "From job(expanded) does not exist.",
                    Result.FAILURE,
                    b.getResult());
        }

        // To job exists, and not overwrite.
        {
            final FreeStyleProject existJob = j
                    .createFreeStyleProject("testPerformError2");

            final JobcopyBuilder target = new JobcopyBuilder(
                    project.getName(),
                    existJob.getName(),
                    false,
                    new ArrayList<JobcopyOperation>(),
                    null);
            project.getBuildersList().add(target);
            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals(
                    "To job exists, and not overwrite",
                    Result.FAILURE,
                    b.getResult());
        }

        // JobcopyOperation returned error.
        {
            final List<JobcopyOperation> lst = new ArrayList<JobcopyOperation>();
            lst.add(new NullJobcopyOperation());
            final JobcopyBuilder target = new JobcopyBuilder(
                    project.getName(),
                    toJobName,
                    true,
                    lst,
                    null);
            project.getBuildersList().add(target);
            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals(
                    "JobcopyOperation returned error.",
                    Result.FAILURE,
                    b.getResult());
        }

        // Failed to create a job
        // I have no idea to achieve this...
    }

    // Test the behavior with AdditionalFileset
    @Test
    public void testPerformWithAdditionalFileset() throws IOException,
            InterruptedException, FormException, ExecutionException
    {
        final FreeStyleProject fromJob = j
                .createFreeStyleProject("testPerformWithAdditionalFileset1");

        final String toJobName = "JobCopiedTo";
        FreeStyleProject toJob = (FreeStyleProject) Jenkins
                .getInstance()
                .getItem(toJobName);
        if(toJob != null)
        {
            toJob.delete();
        }

        // Set up the job copied from.
        // Define Promoted Builds.
        {
            final JobPropertyImpl promotion = new JobPropertyImpl(fromJob);

            fromJob.addProperty(promotion);

            final PromotionProcess process1 = promotion
                    .addProcess("Downstream");
            process1.icon = "Gold Star";
            process1.conditions
                    .add(new DownstreamPassCondition("Downstream-Test-1"));

            final PromotionProcess process2 = promotion.addProcess("Manual");
            process2.icon = "Green Star";
            process2.conditions.add(new ManualCondition());
        }

        fromJob.save();

        final ParametersAction paramAction = new ParametersAction(
                new StringParameterValue("fromJobName", fromJob.getName()),
                new StringParameterValue("toJobName", toJobName));

        // Copy all files
        {
            final List<JobcopyOperation> opList = new ArrayList<JobcopyOperation>();
            opList.add(new ReplaceOperation("Test-1", false, "Test-2", false));

            final List<AdditionalFileset> filesetList = new ArrayList<AdditionalFileset>();
            filesetList.add(new AdditionalFileset(
                    "promotions/*/config.xml",
                    null,
                    false,
                    opList));

            final JobcopyBuilder target = new JobcopyBuilder(
                    fromJob.getName(),
                    toJobName,
                    false,
                    null,
                    filesetList);

            final FreeStyleProject project = j.createFreeStyleProject(
                    "testPerformWithAdditionalFileset2");
            project.getBuildersList().add(target);

            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals("Copy all files", Result.SUCCESS, b.getResult());

            toJob = (FreeStyleProject) Jenkins.getInstance().getItem(toJobName);
            assertNotNull("Copy all files", toJob);

            final JobPropertyImpl promotion = toJob
                    .getProperty(JobPropertyImpl.class);
            assertNotNull("Copy all files", promotion);

            assertEquals("Copy all files", 2, promotion.getItems().size());

            // Downstream
            // Gold Star
            // Downstream-Test-1
            final PromotionProcess process1 = promotion.getItem("Downstream");
            assertNotNull("Copy all files", process1);
            assertEquals("Copy all files", "Gold Star", process1.getIcon());
            assertEquals("Copy all files", 1, process1.conditions.size());
            assertTrue(
                    "Copy all files",
                    process1.conditions
                            .get(0) instanceof DownstreamPassCondition);
            assertEquals(
                    "Copy all files",
                    "Downstream-Test-2",
                    ((DownstreamPassCondition) process1.conditions.get(0))
                            .getJobs());

            // Manual
            // Green Star
            final PromotionProcess process2 = promotion.getItem("Manual");
            assertNotNull("Copy all files", process2);
            assertEquals("Copy all files", "Green Star", process2.getIcon());
            assertEquals("Copy all files", 1, process2.conditions.size());
            assertTrue(
                    "Copy all files",
                    process2.conditions.get(0) instanceof ManualCondition);

            toJob.delete();
        }

        // Copy part of files
        {
            final List<AdditionalFileset> filesetList = new ArrayList<AdditionalFileset>();
            filesetList.add(new AdditionalFileset(
                    "promotions/*/config.xml",
                    "promotions/Manual/*",
                    false,
                    null));

            final JobcopyBuilder target = new JobcopyBuilder(
                    fromJob.getName(),
                    toJobName,
                    false,
                    null,
                    filesetList);

            final FreeStyleProject project = j.createFreeStyleProject(
                    "testPerformWithAdditionalFileset3");
            project.getBuildersList().add(target);

            final FreeStyleBuild b = project
                    .scheduleBuild2(
                            project.getQuietPeriod(),
                            new Cause.UserIdCause(),
                            paramAction)
                    .get();
            while(b.isBuilding())
            {
                Thread.sleep(100);
            }
            assertEquals("Copy part of files", Result.SUCCESS, b.getResult());

            toJob = (FreeStyleProject) Jenkins.getInstance().getItem(toJobName);
            assertNotNull("Copy part of files", toJob);

            final JobPropertyImpl promotion = toJob
                    .getProperty(JobPropertyImpl.class);
            assertNotNull("Copy part of files", promotion);

            assertEquals("Copy part of files", 1, promotion.getItems().size());
            assertNotNull(
                    "Copy part of files",
                    promotion.getItem("Downstream"));
            assertNull("Copy part of files", promotion.getItem("Manual"));

            toJob.delete();
        }

        // Overwrite
        {
            // Copy a job
            {
                final List<JobcopyOperation> opList = new ArrayList<JobcopyOperation>();

                final List<AdditionalFileset> filesetList = new ArrayList<AdditionalFileset>();
                filesetList.add(new AdditionalFileset(
                        "promotions/*/config.xml",
                        null,
                        false,
                        opList));

                final JobcopyBuilder target = new JobcopyBuilder(
                        fromJob.getName(),
                        toJobName,
                        false,
                        null,
                        filesetList);

                final FreeStyleProject project = j.createFreeStyleProject(
                        "testPerformWithAdditionalFileset4");
                project.getBuildersList().add(target);

                final FreeStyleBuild b = project
                        .scheduleBuild2(
                                project.getQuietPeriod(),
                                new Cause.UserIdCause(),
                                paramAction)
                        .get();
                while(b.isBuilding())
                {
                    Thread.sleep(100);
                }
                assertEquals(
                        "Overwrite: Create a job",
                        Result.SUCCESS,
                        b.getResult());

                toJob = (FreeStyleProject) Jenkins
                        .getInstance()
                        .getItem(toJobName);
                assertNotNull("Overwrite: Create a job", toJob);

                final JobPropertyImpl promotion = toJob
                        .getProperty(JobPropertyImpl.class);
                assertNotNull("Overwrite: Create a job", promotion);

                // Downstream
                // Gold Star
                // Downstream-Test-1
                final PromotionProcess process1 = promotion
                        .getItem("Downstream");
                assertNotNull("Overwrite: Create a job", process1);
                assertTrue("Overwrite: Create a job", process1.conditions
                        .get(0) instanceof DownstreamPassCondition);
                assertEquals(
                        "Overwrite: Create a job",
                        "Downstream-Test-1",
                        ((DownstreamPassCondition) process1.conditions.get(0))
                                .getJobs());
            }

            // not overwrite
            {
                final List<JobcopyOperation> opList = new ArrayList<JobcopyOperation>();
                opList.add(
                        new ReplaceOperation("Test-1", false, "Test-2", false));

                final List<AdditionalFileset> filesetList = new ArrayList<AdditionalFileset>();
                filesetList.add(new AdditionalFileset(
                        "promotions/*/config.xml",
                        null,
                        false,
                        opList));

                final JobcopyBuilder target = new JobcopyBuilder(
                        fromJob.getName(),
                        toJobName,
                        true,
                        null,
                        filesetList);

                final FreeStyleProject project = j.createFreeStyleProject(
                        "testPerformWithAdditionalFileset5");
                project.getBuildersList().add(target);

                final FreeStyleBuild b = project
                        .scheduleBuild2(
                                project.getQuietPeriod(),
                                new Cause.UserIdCause(),
                                paramAction)
                        .get();
                while(b.isBuilding())
                {
                    Thread.sleep(100);
                }
                assertEquals(
                        "Overwrite: not overwrite",
                        Result.SUCCESS,
                        b.getResult());

                toJob = (FreeStyleProject) Jenkins
                        .getInstance()
                        .getItem(toJobName);
                assertNotNull("Overwrite: not overwrite", toJob);

                final JobPropertyImpl promotion = toJob
                        .getProperty(JobPropertyImpl.class);
                assertNotNull("Overwrite: not overwrite", promotion);

                // Downstream
                // Gold Star
                // Downstream-Test-1
                final PromotionProcess process1 = promotion
                        .getItem("Downstream");
                assertNotNull("Overwrite: not overwrite", process1);
                assertTrue("Overwrite: not overwrite", process1.conditions
                        .get(0) instanceof DownstreamPassCondition);
                // Not changed!
                assertEquals(
                        "Overwrite: not overwrite",
                        "Downstream-Test-1",
                        ((DownstreamPassCondition) process1.conditions.get(0))
                                .getJobs());
            }

            // overwrite
            {
                final List<JobcopyOperation> opList = new ArrayList<JobcopyOperation>();
                opList.add(
                        new ReplaceOperation("Test-1", false, "Test-3", false));

                final List<AdditionalFileset> filesetList = new ArrayList<AdditionalFileset>();
                filesetList.add(new AdditionalFileset(
                        "promotions/*/config.xml",
                        null,
                        true,
                        opList));

                final JobcopyBuilder target = new JobcopyBuilder(
                        fromJob.getName(),
                        toJobName,
                        true,
                        null,
                        filesetList);

                final FreeStyleProject project = j.createFreeStyleProject(
                        "testPerformWithAdditionalFileset6");
                project.getBuildersList().add(target);

                final FreeStyleBuild b = project
                        .scheduleBuild2(
                                project.getQuietPeriod(),
                                new Cause.UserIdCause(),
                                paramAction)
                        .get();
                while(b.isBuilding())
                {
                    Thread.sleep(100);
                }
                assertEquals(
                        "Overwrite: overwrite",
                        Result.SUCCESS,
                        b.getResult());

                toJob = (FreeStyleProject) Jenkins
                        .getInstance()
                        .getItem(toJobName);
                assertNotNull("Overwrite: overwrite", toJob);

                final JobPropertyImpl promotion = toJob
                        .getProperty(JobPropertyImpl.class);
                assertNotNull("Overwrite: overwrite", promotion);

                // Downstream
                // Gold Star
                // Downstream-Test-1
                final PromotionProcess process1 = promotion
                        .getItem("Downstream");
                assertNotNull("Overwrite: not overwrite", process1);
                assertTrue("Overwrite: not overwrite", process1.conditions
                        .get(0) instanceof DownstreamPassCondition);
                // changed!
                assertEquals(
                        "Overwrite: not overwrite",
                        "Downstream-Test-3",
                        ((DownstreamPassCondition) process1.conditions.get(0))
                                .getJobs());
            }

            toJob.delete();
        }
    }

    @Test
    public void testPerformWithFolder() throws Exception
    {
        final Folder folder1 = j.getInstance().createProject(
                Folder.class,
                "folder1");
        final Folder subfolder1 = folder1.createProject(
                Folder.class,
                "subfolder1");
        j.getInstance().createProject(Folder.class, "folder2");
        j.createFreeStyleProject("srcJob");
        folder1.createProject(FreeStyleProject.class, "srcJobInFolder");
        subfolder1.createProject(FreeStyleProject.class, "srcJobInSubFolder");

        // To a job in a folder
        {
            final FreeStyleProject copyJob = j.createFreeStyleProject();
            final String src = "srcJob";
            final String dest = "folder1/dest1";
            assertNotNull(j.getInstance().getItemByFullName(src));
            assertNull(j.getInstance().getItemByFullName(dest));

            copyJob
                    .getBuildersList()
                    .add(new JobcopyBuilder(
                            src,
                            dest,
                            true,
                            Collections.<JobcopyOperation> emptyList(),
                            Collections.<AdditionalFileset> emptyList()));
            j.assertBuildStatusSuccess(copyJob.scheduleBuild2(0));
            assertNotNull(j.getInstance().getItemByFullName(dest));
            // overwrite
            j.assertBuildStatusSuccess(copyJob.scheduleBuild2(0));
        }

        // From a job in a folder, To a job in a folder
        {
            final FreeStyleProject copyJob = j.createFreeStyleProject();
            final String src = "folder1/srcJobInFolder";
            final String dest = "folder2/dest2";
            assertNotNull(j.getInstance().getItemByFullName(src));
            assertNull(j.getInstance().getItemByFullName(dest));

            copyJob
                    .getBuildersList()
                    .add(new JobcopyBuilder(
                            src,
                            dest,
                            true,
                            Collections.<JobcopyOperation> emptyList(),
                            Collections.<AdditionalFileset> emptyList()));
            j.assertBuildStatusSuccess(copyJob.scheduleBuild2(0));
            assertNotNull(j.getInstance().getItemByFullName(dest));
            // overwrite
            j.assertBuildStatusSuccess(copyJob.scheduleBuild2(0));
        }

        // run in a folder
        {
            final FreeStyleProject copyJob = folder1
                    .createProject(FreeStyleProject.class, "copier3");
            final String src = "srcJobInFolder";
            final String dest = "dest3";
            assertNotNull(j
                    .getInstance()
                    .getItemByFullName(String.format("folder1/%s", src)));
            assertNull(j
                    .getInstance()
                    .getItemByFullName(String.format("folder1/%s", dest)));

            copyJob
                    .getBuildersList()
                    .add(new JobcopyBuilder(
                            src,
                            dest,
                            true,
                            Collections.<JobcopyOperation> emptyList(),
                            Collections.<AdditionalFileset> emptyList()));
            j.assertBuildStatusSuccess(copyJob.scheduleBuild2(0));
            assertNotNull(j
                    .getInstance()
                    .getItemByFullName(String.format("folder1/%s", dest)));
            // overwrite
            j.assertBuildStatusSuccess(copyJob.scheduleBuild2(0));
        }

        // run in a folder, use subfolders
        {
            final FreeStyleProject copyJob = folder1
                    .createProject(FreeStyleProject.class, "copier4");
            final String src = "subfolder1/srcJobInSubFolder";
            final String dest = "subfolder1/dest5";
            assertNotNull(j
                    .getInstance()
                    .getItemByFullName(String.format("folder1/%s", src)));
            assertNull(j
                    .getInstance()
                    .getItemByFullName(String.format("folder1/%s", dest)));

            copyJob
                    .getBuildersList()
                    .add(new JobcopyBuilder(
                            src,
                            dest,
                            true,
                            Collections.<JobcopyOperation> emptyList(),
                            Collections.<AdditionalFileset> emptyList()));
            j.assertBuildStatusSuccess(copyJob.scheduleBuild2(0));
            assertNotNull(j
                    .getInstance()
                    .getItemByFullName(String.format("folder1/%s", dest)));
            // overwrite
            j.assertBuildStatusSuccess(copyJob.scheduleBuild2(0));
        }

        // run in a folder, use parent
        {
            final FreeStyleProject copyJob = folder1
                    .createProject(FreeStyleProject.class, "copier5");
            final String src = "../srcJob";
            final String dest = "../dest6";
            assertNotNull(
                    j.getInstance().getItemByFullName(src.replace("../", "")));
            assertNull(
                    j.getInstance().getItemByFullName(dest.replace("../", "")));

            copyJob
                    .getBuildersList()
                    .add(new JobcopyBuilder(
                            src,
                            dest,
                            true,
                            Collections.<JobcopyOperation> emptyList(),
                            Collections.<AdditionalFileset> emptyList()));
            j.assertBuildStatusSuccess(copyJob.scheduleBuild2(0));
            assertNotNull(
                    j.getInstance().getItemByFullName(dest.replace("../", "")));
            // overwrite
            j.assertBuildStatusSuccess(copyJob.scheduleBuild2(0));
        }

        // copy a folder
        {
            final FreeStyleProject copyJob = j.createFreeStyleProject();
            final String src = "folder1";
            final String dest = "newfolder";
            assertNotNull(j.getInstance().getItemByFullName(src));
            assertNull(j.getInstance().getItemByFullName(dest));

            copyJob
                    .getBuildersList()
                    .add(new JobcopyBuilder(
                            src,
                            dest,
                            true,
                            Collections.<JobcopyOperation> emptyList(),
                            Collections.<AdditionalFileset> emptyList()));
            j.assertBuildStatusSuccess(copyJob.scheduleBuild2(0));
            assertNotNull(j.getInstance().getItemByFullName(dest));
            // overwrite
            j.assertBuildStatusSuccess(copyJob.scheduleBuild2(0));
        }
    }

    @Test
    public void testPerformWithFolderError() throws Exception
    {
        j.createFreeStyleProject("srcJob");

        // Destination folder does not exist
        {
            final FreeStyleProject copyJob = j.createFreeStyleProject();
            final String src = "srcJob";
            final String dest = "nosuchfolder/dest1";
            assertNotNull(j.getInstance().getItemByFullName(src));
            assertNull(j.getInstance().getItemByFullName("nosuchfolder"));
            assertNull(j.getInstance().getItemByFullName(dest));

            copyJob
                    .getBuildersList()
                    .add(new JobcopyBuilder(
                            src,
                            dest,
                            true,
                            Collections.<JobcopyOperation> emptyList(),
                            Collections.<AdditionalFileset> emptyList()));
            j.assertBuildStatus(
                    Result.FAILURE,
                    copyJob.scheduleBuild2(0).get());
        }

        // Copy into MatrixProject
        {
            final MatrixProject matrixPrj = createMatrixProject("matrixtest");
            final FreeStyleProject copyJob = j.createFreeStyleProject();
            final String src = "srcJob";
            final String dest = "matrixtest/dest1";
            assertNotNull(j.getInstance().getItemByFullName(src));
            assertNull(j.getInstance().getItemByFullName("nosuchfolder"));
            assertNull(j.getInstance().getItemByFullName(dest));

            copyJob
                    .getBuildersList()
                    .add(new JobcopyBuilder(
                            src,
                            dest,
                            true,
                            Collections.<JobcopyOperation> emptyList(),
                            Collections.<AdditionalFileset> emptyList()));
            j.assertBuildStatus(
                    Result.FAILURE,
                    copyJob.scheduleBuild2(0).get());
        }
    }

    @Test
    public void testView() throws IOException, SAXException
    {
        final List<JobcopyOperation> lst = new ArrayList<JobcopyOperation>();
        lst.add(new EnableOperation());
        lst.add(new ReplaceOperation("PARAM1", false, "PARAM2", false));
        final JobcopyBuilder target = new JobcopyBuilder(
                "fromJob",
                "toJob",
                false,
                lst,
                null);

        final FreeStyleProject project = j.createFreeStyleProject("testView1");
        project.getBuildersList().add(target);

        final WebClient wc = j.createWebClient();
        wc.getPage(project, "configure");
    }

    // https://github.com/ikedam/jobcopy-builder/issues/11
    @Test
    public void testOverwritingMatrix() throws Exception
    {
        final String destProjectName = "destProject";
        final Axis axis1 = new TextAxis("axis1", "value1-1", "value1-2");
        final Axis axis2 = new TextAxis("axis2", "value2-1", "value2-2");
        final String combinationFilter = "!(axis1 == \"value1-1\" && axis2 == \"value2-1\")";

        final MatrixProject srcProject = createMatrixProject();
        srcProject.setAxes(new AxisList(axis1, axis2));
        srcProject.setCombinationFilter(combinationFilter);

        srcProject.save();

        final FreeStyleProject copier = j.createFreeStyleProject();
        copier
                .getBuildersList()
                .add(new JobcopyBuilder(
                        srcProject.getName(),
                        destProjectName,
                        true,
                        Collections.<JobcopyOperation> emptyList(),
                        Collections.<AdditionalFileset> emptyList()));
        copier.save();

        j.assertBuildStatusSuccess(copier.scheduleBuild2(0));
        {
            final MatrixProject p = j
                    .getInstance()
                    .getItemByFullName(destProjectName, MatrixProject.class);
            assertNotNull(p);
            assertEquals(combinationFilter, p.getCombinationFilter());
        }

        // Remove an axis and combination filter.
        srcProject.setCombinationFilter(null);
        srcProject.setAxes(new AxisList(axis1));

        srcProject.save();

        j.assertBuildStatusSuccess(copier.scheduleBuild2(0));
        {
            final MatrixProject p = j
                    .getInstance()
                    .getItemByFullName(destProjectName, MatrixProject.class);
            assertNotNull(p);
            assertNull(p.getCombinationFilter());
        }

        srcProject.setAxes(new AxisList(axis1, axis2));
        srcProject.setCombinationFilter(combinationFilter);

        srcProject.save();
        j.assertBuildStatusSuccess(copier.scheduleBuild2(0));
        {
            final MatrixProject p = j
                    .getInstance()
                    .getItemByFullName(destProjectName, MatrixProject.class);
            assertNotNull(p);
            assertEquals(combinationFilter, p.getCombinationFilter());
        }
    }
}
