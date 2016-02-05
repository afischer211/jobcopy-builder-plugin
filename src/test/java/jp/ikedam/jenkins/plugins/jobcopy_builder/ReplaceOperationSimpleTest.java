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
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import hudson.EnvVars;

/**
 * Tests for ReplaceOperation not corresponded to Jenkins.
 */
public class ReplaceOperationSimpleTest
{
    EnvVars env;
    ByteArrayOutputStream stream;
    PrintStream logger;

    private Document getXmlDocumentFromString(final String xmlString)
            throws ParserConfigurationException, UnsupportedEncodingException, SAXException, IOException
    {
        final DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder builder = domFactory.newDocumentBuilder();
        final InputStream is = new ByteArrayInputStream(xmlString.getBytes("UTF-8"));

        return builder.parse(is);
    }

    private NodeList getNodeList(final Document doc, final String xpathExpression)
            throws XPathExpressionException
    {
        final XPathFactory factory = XPathFactory.newInstance();
        final XPath xpath = factory.newXPath();
        final XPathExpression expr = xpath.compile(xpathExpression);

        return (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
    }

    @Before
    public void initTest()
    {
        env = new EnvVars();
        env.put("VAR1", "VALUE1");
        env.put("VAR2", "VALUE2");
        env.put("EMPTY", "");
        stream = new ByteArrayOutputStream();
        logger = new PrintStream(stream);
    }

    @After
    public void cleanupTest()
    {
        env.clear();
        logger.close();
        try
        {
            stream.close();
        } catch (final IOException e)
        {
            e.printStackTrace();
        }
    }

    @Test
    public void testPerform() throws UnsupportedEncodingException, ParserConfigurationException, SAXException,
            IOException, XPathExpressionException
    {
        // Simple replace
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>"
                    + "<node>some target value</node>"
                    + "</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "target",
                    false,
                    "replaced",
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("Simple replace", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals(
                    "Simple replace",
                    "some replaced value",
                    getNodeList(doc, "/root/node").item(0).getTextContent());
        }

        // More than two nodes
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>"
                    + "<node>some target value1</node>"
                    + "<node>some target value2</node>"
                    + "<subnode><node>some target value3</node></subnode>"
                    + "</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "target",
                    false,
                    "replaced",
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("More than two nodes", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals(
                    "More than two nodes",
                    "some replaced value1",
                    getNodeList(doc, "/root/node").item(0).getTextContent());
            assertEquals(
                    "More than two nodes",
                    "some replaced value2",
                    getNodeList(doc, "/root/node").item(1).getTextContent());
            assertEquals(
                    "More than two nodes",
                    "some replaced value3",
                    getNodeList(doc, "/root/subnode/node").item(0).getTextContent());
        }

        // More than two in a node
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>"
                    + "<node>some target target value</node>"
                    + "</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "target",
                    false,
                    "replaced",
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("More than two in a node", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals(
                    "More than two in a node",
                    "some replaced replaced value",
                    getNodeList(doc, "/root/node").item(0).getTextContent());
        }

        // No nodes to be replaced
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>"
                    + "<node>some value1</node>"
                    + "<node>some value2</node>"
                    + "<subnode><node>some value3</node></subnode>"
                    + "</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "target",
                    false,
                    "replaced",
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("No nodes to be replaced", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals(
                    "No nodes to be replaced",
                    "some value1",
                    getNodeList(doc, "/root/node").item(0).getTextContent());
            assertEquals(
                    "No nodes to be replaced",
                    "some value2",
                    getNodeList(doc, "/root/node").item(1).getTextContent());
            assertEquals(
                    "No nodes to be replaced",
                    "some value3",
                    getNodeList(doc, "/root/subnode/node").item(0).getTextContent());
        }

        // Node name will not be replaced
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>"
                    + "<node>some value</node>"
                    + "</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "node",
                    false,
                    "newnode",
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("Node name will not be replaced", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals(
                    "Node name will not be replaced",
                    "some value",
                    getNodeList(doc, "/root/node").item(0).getTextContent());
            assertEquals("Node name will not be replaced", 0, getNodeList(doc, "/root/newnode").getLength());
        }

        // Attributes will not be replaced
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>"
                    + "<node attr=\"some target value\">some target value</node>"
                    + "</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "target",
                    false,
                    "replaced",
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("Attributes will not be replaced", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals(
                    "Attributes will not be replaced",
                    "some replaced value",
                    getNodeList(doc, "/root/node").item(0).getTextContent());
            assertEquals(
                    "Attributes will not be replaced",
                    "some target value",
                    getNodeList(doc, "/root/node/@attr").item(0).getTextContent());
        }

        // Dont work as a regular expression.
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>"
                    + "<node>some value</node>"
                    + "</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    ".*",
                    false,
                    "replaced",
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("Dont work as a regular expression", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals(
                    "Dont work as a regular expression",
                    "some value",
                    getNodeList(doc, "/root/node").item(0).getTextContent());
        }

        // From value not expanded/To value not expanded
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>"
                    + "<node>VALUE1</node>"
                    + "<node>${VAR1}</node>"
                    + "</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "${VAR1}",
                    false,
                    "${VAR2}",
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("From value not expanded/To value not expanded", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals(
                    "From value not expanded/To value not expanded",
                    "VALUE1",
                    getNodeList(doc, "/root/node").item(0).getTextContent());
            assertEquals(
                    "From value not expanded/To value not expanded",
                    "${VAR2}",
                    getNodeList(doc, "/root/node").item(1).getTextContent());
        }

        // From value expanded/To value not expanded
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>"
                    + "<node>VALUE1</node>"
                    + "<node>${VAR1}</node>"
                    + "</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "${VAR1}",
                    true,
                    "${VAR2}",
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("From value expanded/To value not expanded", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals(
                    "From value expanded/To value not expanded",
                    "${VAR2}",
                    getNodeList(doc, "/root/node").item(0).getTextContent());
            assertEquals(
                    "From value expanded/To value not expanded",
                    "${VAR1}",
                    getNodeList(doc, "/root/node").item(1).getTextContent());
        }

        // From value not expanded/To value expanded
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>"
                    + "<node>VALUE1</node>"
                    + "<node>${VAR1}</node>"
                    + "</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "${VAR1}",
                    false,
                    "${VAR2}",
                    true);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("From value not expanded/To value expanded", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals(
                    "From value not expanded/To value expanded",
                    "VALUE1",
                    getNodeList(doc, "/root/node").item(0).getTextContent());
            assertEquals(
                    "From value expanded/To value not expanded",
                    "VALUE2",
                    getNodeList(doc, "/root/node").item(1).getTextContent());
        }

        // From value expanded/To value expanded
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>"
                    + "<node>VALUE1</node>"
                    + "<node>${VAR1}</node>"
                    + "</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "${VAR1}",
                    true,
                    "${VAR2}",
                    true);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("From value not expanded/To value expanded", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals(
                    "From value not expanded/To value expanded",
                    "VALUE2",
                    getNodeList(doc, "/root/node").item(0).getTextContent());
            assertEquals(
                    "From value expanded/To value not expanded",
                    "${VAR1}",
                    getNodeList(doc, "/root/node").item(1).getTextContent());
        }

    }

    @Test
    public void testPerformWithEmpty() throws UnsupportedEncodingException, ParserConfigurationException, SAXException,
            IOException, XPathExpressionException
    {
        // From String is null
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>    hogehoge</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    null,
                    false,
                    "replaced",
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNull("From String is null", result);
        }

        // From String is empty
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>    hogehoge</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "",
                    false,
                    "replaced",
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNull("From String is empty", result);
        }

        // From String is blank
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>    hogehoge</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "   ",
                    false,
                    "replaced",
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("From String is blank", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals(
                    "From String is blank",
                    "replaced hogehoge",
                    getNodeList(doc, "/root").item(0).getTextContent());
        }

        /*
         * undefined value results not to be expanded. // From String gets null { String xml = "<?xml version=\"1.0\"?>" + "<root>    hogehoge</root>";
         * ReplaceOperation target = new ReplaceOperation( "${NULL}", true, "replaced", false ); String result = target.perform(xml, "UTF-8", env, logger);
         * assertNull("From String gets null", result); }
         */

        // From String gets empty
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>    hogehoge</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "${EMPTY}",
                    true,
                    "replaced",
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNull("From String gets empty", result);
        }

        // To String is null
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>"
                    + "<node>some target value</node>"
                    + "</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "target",
                    false,
                    null,
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("To String is null", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals("To String is null", "some  value", getNodeList(doc, "/root/node").item(0).getTextContent());
        }

        // To String is empty
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>"
                    + "<node>some target value</node>"
                    + "</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "target",
                    false,
                    "",
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("To String is empty", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals("To String is empty", "some  value", getNodeList(doc, "/root/node").item(0).getTextContent());
        }

        // To String is blank
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>"
                    + "<node>some target value</node>"
                    + "</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "target",
                    false,
                    "  ",
                    false);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("To String is blank", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals(
                    "To String is blank",
                    "some    value",
                    getNodeList(doc, "/root/node").item(0).getTextContent());
        }

        /*
         * undefined value results not to be expanded. // To String gets null { String xml = "<?xml version=\"1.0\"?>" + "<root>" +
         * "<node>some target value</node>" + "</root>"; ReplaceOperation target = new ReplaceOperation( "target", false, "${NULL}", true ); String result =
         * target.perform(xml, "UTF-8", env, logger); assertNotNull("To String gets null", result); Document doc = getXmlDocumentFromString(result);
         * assertEquals("To String gets null", "some  value", getNodeList(doc, "/root/node").item(0).getTextContent()); }
         */

        // To String gets empty
        {
            final String xml = "<?xml version=\"1.0\"?>"
                    + "<root>"
                    + "<node>some target value</node>"
                    + "</root>";
            final ReplaceOperation target = new ReplaceOperation(
                    "target",
                    false,
                    "${EMPTY}",
                    true);
            final String result = target.perform(xml, "UTF-8", env, logger);
            assertNotNull("To String gets empty", result);
            final Document doc = getXmlDocumentFromString(result);
            assertEquals(
                    "To String gets empty",
                    "some  value",
                    getNodeList(doc, "/root/node").item(0).getTextContent());
        }
    }
}
