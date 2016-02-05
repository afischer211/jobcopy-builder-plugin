package jp.ikedam.jenkins.plugins.jobcopy_builder;

import java.io.PrintStream;

import hudson.EnvVars;

public interface IJobcopyOperation
{

    /**
     * Returns modified XML string of the job configuration.
     *
     * @param xmlString
     *            the XML string of the job to be copied (job/NAME/config.xml)
     * @param encoding
     *            the encoding of the XML.
     * @param env
     *            Variables defined in the build.
     * @param logger
     *            The output stream to log.
     * @return modified XML string. Returns null if an error occurs.
     */
    String perform(String xmlString, String encoding, EnvVars env, PrintStream logger);
}