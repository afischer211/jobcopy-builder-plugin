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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.xml.sax.SAXException;

import com.gargoylesoftware.htmlunit.html.HtmlPage;

import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;

/**
 * Tests for CopiedjobinfoAction, corresponded to Jenkins.
 */
public class CopiedjobinfoActionJenkinsTest
{
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Test that summary.jelly does not fail.
     *
     * @throws IOException
     * @throws ExecutionException
     * @throws InterruptedException
     * @throws SAXException
     */
    @Test
    public void testView() throws IOException, InterruptedException, ExecutionException, SAXException
    {
        // Create jobs to be shown.
        final FreeStyleProject fromJob = j.createFreeStyleProject();
        final FreeStyleProject toJob = j.createFreeStyleProject();
        final String fromJobUrl = fromJob.getUrl();
        final String toJobUrl = toJob.getUrl();

        // Create job, and create a build.
        final FreeStyleProject job = j.createFreeStyleProject();
        final CopiedjobinfoAction action = new CopiedjobinfoAction(fromJob, toJob, false);
        final FreeStyleBuild build = job.scheduleBuild2(job.getQuietPeriod(), new Cause.UserIdCause(), action).get();

        // Wait for build is completed.
        while(build.isBuilding())
        {
            Thread.sleep(100);
        }

        // Create a failed build.
        final CopiedjobinfoAction failedAction = new CopiedjobinfoAction(fromJob, toJob, true);
        final FreeStyleBuild failedBuild = job
                .scheduleBuild2(job.getQuietPeriod(), new Cause.UserIdCause(), failedAction)
                .get();

        // Wait for build is completed.
        while(failedBuild.isBuilding())
        {
            Thread.sleep(100);
        }

        // Access to page.
        final WebClient wc = j.createWebClient();

        // access to succeeded build.
        {
            final HtmlPage page = wc.getPage(build);

            // contains link to from job.
            {
                final List<?> nodes = page.getByXPath(String.format("//a[%s]", getEndsWithXpath("@href", fromJobUrl)));
                assertNotNull(nodes);
                assertTrue(nodes.size() > 0);
            }
            // contains link to to job.
            {
                final List<?> nodes = page.getByXPath(String.format("//a[%s]", getEndsWithXpath("@href", toJobUrl)));
                assertNotNull(nodes);
                assertTrue(nodes.size() > 0);
            }

            // does not contains warning message
            {
                final List<?> nodes = page.getByXPath("//*[@class='warning']");
                assertNotNull(nodes);
                assertEquals(0, nodes.size());
            }
        }

        // access to failed build.
        {
            final HtmlPage page = wc.getPage(failedBuild);

            // contains warning message
            {
                final List<?> nodes = page.getByXPath("//*[@class='warning']");
                assertNotNull(nodes);
                assertTrue(nodes.size() > 0);
            }
        }

        // it works even if the jobs are removed.
        fromJob.delete();
        toJob.delete();
        {
            final HtmlPage page = wc.getPage(build);

            // contains link to from job.
            {
                final List<?> nodes = page.getByXPath(String.format("//a[%s]", getEndsWithXpath("@href", fromJobUrl)));
                assertNotNull(nodes);
                assertTrue(nodes.size() > 0);
            }
            // contains link to to job.
            {
                final List<?> nodes = page.getByXPath(String.format("//a[%s]", getEndsWithXpath("@href", toJobUrl)));
                assertNotNull(nodes);
                assertTrue(nodes.size() > 0);
            }
        }
    }

    // Xpath 1.0 does not support ends-with, so do same with other functions.
    private String getEndsWithXpath(final String nodeExp, final String value)
    {
        return String.format(
                "substring(%s, string-length(%s) - string-length('%s') + 1) = '%s'",
                nodeExp,
                nodeExp,
                value,
                value);
    }
}
