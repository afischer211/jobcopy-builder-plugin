/*
 * The MIT License
 *
 * Copyright (c) 2015 IKEDA Yasuyuki
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

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.FreeStyleProject;
import hudson.model.Item;

/**
 * Tests for {@link DisableOperation}
 */
public class DisableOperationJenkinsTest
{

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testDisableOperation() throws Exception
    {
        final FreeStyleProject copiee = j.createFreeStyleProject();
        copiee.enable();
        copiee.save();

        final FreeStyleProject copier = j.createFreeStyleProject();
        copier.getBuildersList().add(
                new JobcopyBuilder(
                        copiee.getFullName(),
                        "copied",
                        false,
                        Arrays.<JobcopyOperation> asList(new DisableOperation()),
                        Collections.<AdditionalFileset> emptyList()));
        j.assertBuildStatusSuccess(copier.scheduleBuild2(0));

        final FreeStyleProject copied = j.getInstance().getItemByFullName("copied", FreeStyleProject.class);
        assertTrue(copied.isDisabled());
    }

    @Test
    public void testConfiguration() throws Exception
    {
        final JobcopyBuilder expected = new JobcopyBuilder(
                "from",
                "to",
                false,
                Arrays.<JobcopyOperation> asList(new DisableOperation()),
                Collections.<AdditionalFileset> emptyList());

        final FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(expected);
        j.configRoundtrip(((Item) p));
        final JobcopyBuilder actual = p.getBuildersList().get(JobcopyBuilder.class);
        // assertEqualDataBoundBeans(expected, actual); // This cause NPE as actual.additionalFilesetList gets null.
        j.assertEqualDataBoundBeans(expected.getJobcopyOperationList(), actual.getJobcopyOperationList());
    }
}
