/*
 * The MIT License
 *
 * Copyright (c) 2013 IKEDA Yasuyuki
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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.acegisecurity.AccessDeniedException;
import org.apache.commons.io.FileUtils;

import hudson.EnvVars;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.search.Search;
import hudson.search.SearchIndex;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.util.NullStream;
import junit.framework.TestCase;

/**
 * Tests for AdditionalFileset not concerned with Jenkins.
 */
public class AdditionalFilesetSimpleTest extends TestCase
{
    private File createTempDir() throws IOException
    {
        final File tempFile = File.createTempFile("test", null);
        tempFile.delete();
        tempFile.mkdir();

        return tempFile;
    }

    public void testAdditionalFileset()
    {
        final String includeFile = "**/config.xml";
        final String excludeFile = "test/foobar/config.xml";
        final List<JobcopyOperation> emptyList = new ArrayList<JobcopyOperation>();
        final List<JobcopyOperation> lst = new ArrayList<JobcopyOperation>();
        lst.add(new ReplaceOperation("from", false, "to", false));
        lst.add(new EnableOperation());

        // simple values
        {
            final AdditionalFileset target = new AdditionalFileset(includeFile, excludeFile, false, lst);

            assertEquals("simple values", includeFile, target.getIncludeFile());
            assertEquals("simple values", excludeFile, target.getExcludeFile());
            assertEquals("simple values", false, target.isOverwrite());
            assertEquals("simple values", lst, target.getJobcopyOperationList());
        }
        // values surrounded with spaces
        {
            final AdditionalFileset target = new AdditionalFileset(
                    "  " + includeFile + "  ",
                    "\t" + excludeFile + "\t",
                    true,
                    emptyList);

            assertEquals("values surrounded with spaces", includeFile, target.getIncludeFile());
            assertEquals("values surrounded with spaces", excludeFile, target.getExcludeFile());
            assertEquals("values surrounded with spaces", true, target.isOverwrite());
            assertEquals("values surrounded with spaces", emptyList, target.getJobcopyOperationList());
        }

        // null
        {
            final AdditionalFileset target = new AdditionalFileset(null, null, false, null);

            assertNull("null", target.getIncludeFile());
            assertNull("null", target.getExcludeFile());
            assertNull("null", target.getJobcopyOperationList());
        }
    }

    public static class PublicAdditionalFileset extends AdditionalFileset
    {
        private static final long serialVersionUID = -8474841816751367916L;

        public PublicAdditionalFileset(final String includeFile, final String excludeFile, final boolean overwrite,
                final List<JobcopyOperation> jobcopyOperationList)
        {
            super(includeFile, excludeFile, overwrite, jobcopyOperationList);
        }

        public List<String> publicGetFilesToCopy(final File dir)
        {
            return getFilesToCopy(dir);
        }

        public boolean publicPerformToFile(final File dstFile, final File srcFile, final EnvVars env,
                final PrintStream logger)
        {
            return performToFile(dstFile, srcFile, env, logger);
        }
    }

    // mainly test that unexpected exception not thrown...
    public void testGetFilesToCopy() throws IOException
    {
        File tempDir = null;

        try
        {
            tempDir = createTempDir();

            FileUtils.writeStringToFile(new File(tempDir, "test1/test.txt"), "hogehoge");
            FileUtils.writeStringToFile(new File(tempDir, "test2/test.txt"), "hogehoge");
            FileUtils.writeStringToFile(new File(tempDir, "test3/test/test.txt"), "hogehoge");

            // only includes 1
            {
                final List<String> files = new PublicAdditionalFileset("**/test.txt", null, false, null)
                        .publicGetFilesToCopy(tempDir);
                assertNotNull("only includes 1", files);
                assertEquals("only includes 1", 3, files.size());
            }

            // only includes 2
            {
                final List<String> files = new PublicAdditionalFileset("**/test.txt", "", false, null)
                        .publicGetFilesToCopy(tempDir);
                assertNotNull("only includes 2", files);
                assertEquals("only includes 2", 3, files.size());
            }

            // no affect excludes
            {
                final List<String> files = new PublicAdditionalFileset("**/test.txt", "**/nosuchfile.txt", false, null)
                        .publicGetFilesToCopy(tempDir);
                assertNotNull("no affect excludes", files);
                assertEquals("no affect excludes", 3, files.size());
            }

            // no files 1
            {
                final List<String> files = new PublicAdditionalFileset("**/test.txt", "**/test.txt", false, null)
                        .publicGetFilesToCopy(tempDir);
                assertNotNull("no files 1", files);
                assertEquals("no files 1", 0, files.size());
            }

            // no files 2
            {
                final List<String> files = new PublicAdditionalFileset("**/nosuchfile.txt", null, false, null)
                        .publicGetFilesToCopy(tempDir);
                assertNotNull("no files 2", files);
                assertEquals("no files 2", 0, files.size());
            }

            // no includes(null)
            {
                final List<String> files = new PublicAdditionalFileset(null, null, false, null)
                        .publicGetFilesToCopy(tempDir);
                assertNotNull("no includes 1", files);
                assertEquals("no includes 1", 0, files.size());
            }

            // no includes(empty)
            {
                final List<String> files = new PublicAdditionalFileset("", null, false, null)
                        .publicGetFilesToCopy(tempDir);
                assertNotNull("no includes 2", files);
                assertEquals("no includes 2", 0, files.size());
            }

            // no includes(only comma)
            {
                final List<String> files = new PublicAdditionalFileset(",", null, false, null)
                        .publicGetFilesToCopy(tempDir);
                assertNotNull("no includes(only comma)", files);
                assertEquals("no includes(only comma)", 3, files.size());
            }

            // root
            {
                final List<String> files = new PublicAdditionalFileset("/nosuchfile.txt", "/foobar.txt", false, null)
                        .publicGetFilesToCopy(tempDir);
                assertNotNull("root", files);
                assertEquals("root", 0, files.size());
            }
        }
        finally
        {
            if(tempDir != null && tempDir.exists())
            {
                if(tempDir.isDirectory())
                {
                    FileUtils.deleteDirectory(tempDir);
                } else
                {
                    tempDir.delete();
                }
            }
        }
    }

    private class DummyPerformToFileAdditionalFileset extends AdditionalFileset
    {
        private static final long serialVersionUID = 6038335077788905237L;

        public DummyPerformToFileAdditionalFileset(final String includeFile, final String excludeFile,
                final boolean overwrite, final List<JobcopyOperation> jobcopyOperationList)
        {
            super(includeFile, excludeFile, overwrite, jobcopyOperationList);
        }

        public List<String> filesToCopyList;
        public List<File> dstFileList = new ArrayList<File>(0);
        public List<File> srcFileList = new ArrayList<File>(0);
        public List<Boolean> returnList;

        @Override
        protected List<String> getFilesToCopy(final File dir)
        {
            return filesToCopyList;
        }

        @Override
        protected boolean performToFile(final File dstFile, final File srcFile, final EnvVars env,
                final PrintStream logger)
        {
            dstFileList.add(dstFile);
            srcFileList.add(srcFile);

            final boolean ret = returnList.get(0).booleanValue();
            returnList = returnList.subList(1, returnList.size());
            return ret;
        }
    }

    public void testPerform() throws IOException
    {
        final File srcDir = new File("/path/to/job1");
        final File dstDir = new File("/path/to/job2");
        final DummyJob srcJob = new DummyJob(srcDir);
        final DummyJob dstJob = new DummyJob(dstDir);

        // simple execution
        {
            final DummyPerformToFileAdditionalFileset target = new DummyPerformToFileAdditionalFileset(
                    "test.xml",
                    null,
                    false,
                    null);
            target.returnList = Arrays.asList(true, true);
            target.filesToCopyList = Arrays.asList("file1.xml", "file2.xml");
            final EnvVars env = new EnvVars();
            final PrintStream logger = new PrintStream(new NullStream());
            assertTrue("simple execution", target.perform(dstJob, srcJob, env, logger));
            assertEquals("simple execution", 2, target.srcFileList.size());
            assertEquals("simple execution", new File(srcDir, "file1.xml"), target.srcFileList.get(0));
            assertEquals("simple execution", new File(srcDir, "file2.xml"), target.srcFileList.get(1));
            assertEquals("simple execution", 2, target.dstFileList.size());
            assertEquals("simple execution", new File(dstDir, "file1.xml"), target.dstFileList.get(0));
            assertEquals("simple execution", new File(dstDir, "file2.xml"), target.dstFileList.get(1));
        }

        // no files
        {
            final DummyPerformToFileAdditionalFileset target = new DummyPerformToFileAdditionalFileset(
                    "test.xml",
                    null,
                    false,
                    null);
            target.filesToCopyList = new ArrayList<String>(0);
            final EnvVars env = new EnvVars();
            final PrintStream logger = new PrintStream(new NullStream());
            assertTrue("no files", target.perform(dstJob, srcJob, env, logger));
            assertEquals("no files", 0, target.srcFileList.size());
            assertEquals("no files", 0, target.dstFileList.size());
        }
    }

    public void testPerformError()
    {
        final File srcDir = new File("/path/to/job1");
        final File dstDir = new File("/path/to/job2");
        final DummyJob srcJob = new DummyJob(srcDir);
        final DummyJob dstJob = new DummyJob(dstDir);

        // null includeFiles
        {
            final DummyPerformToFileAdditionalFileset target = new DummyPerformToFileAdditionalFileset(
                    null,
                    null,
                    false,
                    null);
            target.returnList = Arrays.asList(true, true);
            target.filesToCopyList = Arrays.asList("file1.xml", "file2.xml");
            final EnvVars env = new EnvVars();
            final PrintStream logger = new PrintStream(new NullStream());
            assertFalse("null includeFiles", target.perform(dstJob, srcJob, env, logger));
            assertEquals("null includeFiles", 0, target.srcFileList.size());
            assertEquals("null includeFiles", 0, target.dstFileList.size());
        }

        // blank includeFiles
        {
            final DummyPerformToFileAdditionalFileset target = new DummyPerformToFileAdditionalFileset(
                    "  ",
                    null,
                    false,
                    null);
            target.returnList = Arrays.asList(true, true);
            target.filesToCopyList = Arrays.asList("file1.xml", "file2.xml");
            final EnvVars env = new EnvVars();
            final PrintStream logger = new PrintStream(new NullStream());
            assertFalse("blank includeFiles", target.perform(dstJob, srcJob, env, logger));
            assertEquals("blank includeFiles", 0, target.srcFileList.size());
            assertEquals("blank includeFiles", 0, target.dstFileList.size());
        }

        // fail at begin
        {
            final DummyPerformToFileAdditionalFileset target = new DummyPerformToFileAdditionalFileset(
                    "test.xml",
                    null,
                    false,
                    null);
            target.returnList = Arrays.asList(false, true);
            target.filesToCopyList = Arrays.asList("file1.xml", "file2.xml");
            final EnvVars env = new EnvVars();
            final PrintStream logger = new PrintStream(new NullStream());
            assertFalse("fail at begin", target.perform(dstJob, srcJob, env, logger));
            assertEquals("fail at begin", 2, target.srcFileList.size());
            assertEquals("fail at begin", new File(srcDir, "file1.xml"), target.srcFileList.get(0));
            assertEquals("fail at begin", new File(srcDir, "file2.xml"), target.srcFileList.get(1));
            assertEquals("fail at begin", 2, target.dstFileList.size());
            assertEquals("fail at begin", new File(dstDir, "file1.xml"), target.dstFileList.get(0));
            assertEquals("fail at begin", new File(dstDir, "file2.xml"), target.dstFileList.get(1));
        }
    }

    private class DummyOperation extends JobcopyOperation
    {
        public String xmlString;
        public String encoding;
        public String returnString;

        public DummyOperation(final String returnString)
        {
            this.returnString = returnString;
        }

        @Override
        public String perform(final String xmlString, final String encoding, final EnvVars env,
                final PrintStream logger)
        {
            this.xmlString = xmlString;
            this.encoding = encoding;
            return returnString;
        }
    }

    public void testPerformFile() throws IOException
    {
        File workDir = null;
        final String dummySrc = "This is a dummy source contents.";
        final String dummyConv = "This is a dummy converted contents.";
        final String dummyDest = "This is a dummy destination contents.";
        try
        {
            workDir = createTempDir();

            // simple execution
            {
                final DummyOperation op = new DummyOperation(dummyConv);
                final PublicAdditionalFileset target = new PublicAdditionalFileset(
                        null,
                        null,
                        false,
                        Arrays.asList((JobcopyOperation) op));

                final File srcFile = new File(workDir, "simple_execution_src.xml");
                final File dstFile = new File(workDir, "simple_execution_dst.xml");
                final EnvVars env = new EnvVars();
                final PrintStream logger = new PrintStream(new NullStream());

                FileUtils.writeStringToFile(srcFile, dummySrc);

                assertTrue("simple execution", target.publicPerformToFile(dstFile, srcFile, env, logger));
                assertEquals("simple execution", dummySrc, op.xmlString);
                assertEquals("simple execution", "UTF-8", op.encoding.toUpperCase());
                assertEquals("simple execution", dummyConv, FileUtils.readFileToString(dstFile));
            }

            // not overwrite
            {
                final DummyOperation op = new DummyOperation(dummyConv);
                final PublicAdditionalFileset target = new PublicAdditionalFileset(
                        null,
                        null,
                        false,
                        Arrays.asList((JobcopyOperation) op));

                final File srcFile = new File(workDir, "simple_execution_src.xml");
                final File dstFile = new File(workDir, "simple_execution_dst.xml");
                final EnvVars env = new EnvVars();
                final PrintStream logger = new PrintStream(new NullStream());

                FileUtils.writeStringToFile(srcFile, dummySrc);
                FileUtils.writeStringToFile(dstFile, dummyDest);

                assertTrue("not overwrite", target.publicPerformToFile(dstFile, srcFile, env, logger));
                assertEquals("not overwrite", dummyDest, FileUtils.readFileToString(dstFile));
            }

            // overwrite
            {
                final DummyOperation op = new DummyOperation(dummyConv);
                final PublicAdditionalFileset target = new PublicAdditionalFileset(
                        null,
                        null,
                        true,
                        Arrays.asList((JobcopyOperation) op));

                final File srcFile = new File(workDir, "overwrite_src.xml");
                final File dstFile = new File(workDir, "overwrite_dst.xml");
                final EnvVars env = new EnvVars();
                final PrintStream logger = new PrintStream(new NullStream());

                FileUtils.writeStringToFile(srcFile, dummySrc);
                FileUtils.writeStringToFile(dstFile, dummyDest);

                assertTrue("overwrite", target.publicPerformToFile(dstFile, srcFile, env, logger));
                assertEquals("overwrite", dummyConv, FileUtils.readFileToString(dstFile));
            }

            // null operation list
            {
                final PublicAdditionalFileset target = new PublicAdditionalFileset(null, null, false, null);

                final File srcFile = new File(workDir, "null_operation_list_src.xml");
                final File dstFile = new File(workDir, "null_operation_list_dst.xml");
                final EnvVars env = new EnvVars();
                final PrintStream logger = new PrintStream(new NullStream());

                FileUtils.writeStringToFile(srcFile, dummySrc);

                assertTrue("null operation list", target.publicPerformToFile(dstFile, srcFile, env, logger));
                assertEquals("null operation list", dummySrc, FileUtils.readFileToString(dstFile));
            }

            // empty operation list
            {
                final PublicAdditionalFileset target = new PublicAdditionalFileset(
                        null,
                        null,
                        false,
                        new ArrayList<JobcopyOperation>(0));

                final File srcFile = new File(workDir, "empty_operation_list_src.xml");
                final File dstFile = new File(workDir, "empty_operation_list_dst.xml");
                final EnvVars env = new EnvVars();
                final PrintStream logger = new PrintStream(new NullStream());

                FileUtils.writeStringToFile(srcFile, dummySrc);

                assertTrue("empty operation list", target.publicPerformToFile(dstFile, srcFile, env, logger));
                assertEquals("empty operation list", dummySrc, FileUtils.readFileToString(dstFile));
            }
        }
        finally
        {
            if(workDir != null)
            {
                FileUtils.deleteDirectory(workDir);
            }
        }
    }

    public void testPerformFileError() throws IOException
    {
        File workDir = null;
        final String dummySrc = "This is a dummy source contents.";
        try
        {
            workDir = createTempDir();

            // fail to read
            {
                final PublicAdditionalFileset target = new PublicAdditionalFileset(null, null, false, null);

                final File srcFile = new File(workDir, "fail_to_read.src.xml");
                final File dstFile = new File(workDir, "fail_to_read.dst.xml");
                final EnvVars env = new EnvVars();
                final PrintStream logger = new PrintStream(new NullStream());

                assertFalse("fail to read", target.publicPerformToFile(dstFile, srcFile, env, logger));
            }

            // fail in operation
            {
                final DummyOperation op = new DummyOperation(null);
                final PublicAdditionalFileset target = new PublicAdditionalFileset(
                        null,
                        null,
                        false,
                        Arrays.asList((JobcopyOperation) op));

                final File srcFile = new File(workDir, "fail_in_operation.src.xml");
                final File dstFile = new File(workDir, "fail_in_operation.dst.xml");
                final EnvVars env = new EnvVars();
                final PrintStream logger = new PrintStream(new NullStream());

                FileUtils.writeStringToFile(srcFile, dummySrc);

                assertFalse("fail in operation", target.publicPerformToFile(dstFile, srcFile, env, logger));
            }

            // fail to write
            // no way to achieve this...
        }
        finally
        {
            if(workDir != null)
            {
                FileUtils.deleteDirectory(workDir);
            }
        }
    }

    private static class DummyJob implements TopLevelItem
    {
        private File rootDir;

        public DummyJob(final File rootDir)
        {
            this.rootDir = rootDir;
        }

        @Override
        public File getRootDir()
        {
            return rootDir;
        }

        // implement interface methods(no contents)
        @Override
        public ItemGroup<? extends Item> getParent()
        {
            return null;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Collection<? extends Job> getAllJobs()
        {
            return null;
        }

        @Override
        public String getName()
        {
            return null;
        }

        @Override
        public String getFullName()
        {
            return null;
        }

        @Override
        public String getDisplayName()
        {
            return null;
        }

        @Override
        public String getFullDisplayName()
        {
            return null;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public String getRelativeNameFrom(final ItemGroup g)
        {
            return null;
        }

        @Override
        public String getRelativeNameFrom(final Item item)
        {
            return null;
        }

        @Override
        public String getUrl()
        {
            return null;
        }

        @Override
        public String getShortUrl()
        {
            return null;
        }

        @Override
        public String getAbsoluteUrl()
        {
            return null;
        }

        @Override
        public void onLoad(final ItemGroup<? extends Item> parent, final String name) throws IOException
        {

        }

        @Override
        public void onCopiedFrom(final Item src)
        {
        }

        @Override
        public void onCreatedFromScratch()
        {
        }

        @Override
        public void save() throws IOException
        {
        }

        @Override
        public void delete() throws IOException, InterruptedException
        {
        }

        @Override
        public Search getSearch()
        {
            return null;
        }

        @Override
        public String getSearchName()
        {
            return null;
        }

        @Override
        public String getSearchUrl()
        {
            return null;
        }

        @Override
        public SearchIndex getSearchIndex()
        {
            return null;
        }

        @Override
        public ACL getACL()
        {
            return null;
        }

        @Override
        public void checkPermission(final Permission permission) throws AccessDeniedException
        {
        }

        @Override
        public boolean hasPermission(final Permission permission)
        {
            return false;
        }

        @Override
        public TopLevelItemDescriptor getDescriptor()
        {
            return null;
        }
    }
}
