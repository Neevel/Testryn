package com.testryn.publisher;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads one JUnit-compatible XML report (Maven Surefire and Failsafe both produce
 * this exact schema -- no Failsafe-specific code needed, see ADR 0013) into a
 * {@link ResultBatch}. Implements the same {@link ResultBatchReader} seam
 * {@link JsonResultBatchReader} does (Abschnitt 2): the publisher core
 * ({@link TestrynApiClient}) and everything downstream of a {@link ResultBatch}
 * neither knows nor cares that this reader's input was XML.
 *
 * <p>Reads exactly one file per call, same as {@link JsonResultBatchReader} reads
 * exactly one input stream -- combining multiple report files into one batch is
 * {@link JUnitReportImporter}'s job, one layer up, so this class stays a pure,
 * single-responsibility parser.
 *
 * <p><b>Supported roots:</b> a bare {@code <testsuite>} or a {@code <testsuites>}
 * wrapping one or more {@code <testsuite>} elements (Abschnitt 3).
 *
 * <p><b>automationReference convention</b> (ADR 0013): {@code classname + "#" + name},
 * taken verbatim from the XML attributes -- e.g. {@code com.example.LoginTest#successfulLogin}.
 * No attempt to normalize parameterized-test display names (Abschnitt 10): whatever
 * Surefire put in {@code name} is used as-is, so {@code successfulLogin(String)[1]}
 * becomes part of the reference. If {@code classname} is absent, the reference is
 * {@code name} alone (rare; most real-world reports always include it).
 */
public class JUnitXmlResultBatchReader implements ResultBatchReader {

    @Override
    public ResultBatch read(InputStream input) throws IOException {
        Document document = parseSecurely(input);
        Element root = document.getDocumentElement();
        if (root == null) {
            throw new IOException("XML has no root element");
        }

        List<Element> testsuites;
        switch (root.getTagName()) {
            case "testsuites" -> testsuites = directChildElements(root, "testsuite");
            case "testsuite" -> testsuites = List.of(root);
            default -> throw new IOException(
                    "Unexpected root element <" + root.getTagName() + ">, expected <testsuite> or <testsuites>");
        }
        if (testsuites.isEmpty()) {
            throw new IOException("<testsuites> contains no <testsuite> elements");
        }

        List<PublisherResultInput> results = new ArrayList<>();
        for (Element testsuite : testsuites) {
            for (Element testcase : directChildElements(testsuite, "testcase")) {
                PublisherResultInput result = toResultInput(testcase);
                if (result.automationReference() == null || result.automationReference().isBlank()) {
                    throw new IOException("<testcase> is missing both 'name' and 'classname' attributes");
                }
                results.add(result);
            }
        }
        // executionId is never present in JUnit XML -- resolved solely from
        // --execution-id by the CLI layer, same principle as ADR 0010/0011.
        return new ResultBatch(null, results);
    }

    private PublisherResultInput toResultInput(Element testcase) {
        String name = testcase.getAttribute("name");
        String classname = testcase.hasAttribute("classname") ? testcase.getAttribute("classname") : null;
        String automationReference = classname != null && !classname.isBlank() ? classname + "#" + name : name;
        Long durationMs = parseDurationMs(testcase.getAttribute("time"));

        Element failure = firstChildElement(testcase, "failure");
        Element error = firstChildElement(testcase, "error");
        Element skipped = firstChildElement(testcase, "skipped");

        String status;
        String actualResult = null;
        String failureDetails = null;
        if (failure != null || error != null) {
            status = "FAILED";
            Element node = failure != null ? failure : error;
            actualResult = attributeOrNull(node, "message");
            failureDetails = combineTypeAndBody(node);
        } else if (skipped != null) {
            status = "SKIPPED";
            String message = attributeOrNull(skipped, "message");
            String body = textContentOrNull(skipped);
            actualResult = message != null ? message : body;
        } else {
            status = "PASSED";
        }

        // comment/executor/resultId deliberately left null: this reader must never
        // report a value for a field it has no real data for -- the outgoing bulk
        // request omits null fields entirely (TestrynApiClient), so any comment a
        // human already left on this result survives untouched (Abschnitt 32).
        return new PublisherResultInput(null, automationReference, status, null, durationMs, null,
                actualResult, failureDetails);
    }

    /** {@code type} attribute plus the element's stacktrace/body text, joined so
     * neither is lost -- Abschnitt 6's recommended split (message -> actualResult,
     * type+body -> failureDetails). */
    private String combineTypeAndBody(Element node) {
        String type = attributeOrNull(node, "type");
        String body = textContentOrNull(node);
        if (type == null) {
            return body;
        }
        return body == null ? type : type + "\n" + body;
    }

    /** Converts JUnit's fractional-seconds `time` attribute to whole milliseconds via
     * {@link BigDecimal} -- never {@code Double}/{@code Float} parsing, which would
     * risk exactly the kind of silent precision drift Abschnitt 5 warns about
     * (e.g. 1.1 seconds as a double is not exactly 1100ms internally). An
     * unparseable or absent value yields no duration rather than failing the whole
     * file -- a non-critical field, not structural corruption (Abschnitt 23). */
    private Long parseDurationMs(String timeAttribute) {
        if (timeAttribute == null || timeAttribute.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(timeAttribute.trim())
                    .multiply(BigDecimal.valueOf(1000))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private String attributeOrNull(Element element, String name) {
        return element.hasAttribute(name) ? element.getAttribute(name) : null;
    }

    private String textContentOrNull(Element element) {
        String text = element.getTextContent();
        return (text == null || text.isBlank()) ? null : text.strip();
    }

    private List<Element> directChildElements(Element parent, String tagName) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                children.add((Element) node);
            }
        }
        return children;
    }

    private Element firstChildElement(Element parent, String tagName) {
        List<Element> children = directChildElements(parent, tagName);
        return children.isEmpty() ? null : children.get(0);
    }

    /**
     * JUnit/Surefire XML is untrusted input (Abschnitt 22) -- hardened against XXE,
     * external entity resolution, and DTD processing following the OWASP-recommended
     * configuration for {@link DocumentBuilderFactory}. No new dependency: this is
     * all built into the JDK.
     */
    private Document parseSecurely(InputStream input) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External entity resolution is disabled: " + systemId);
            });
            return builder.parse(input);
        } catch (ParserConfigurationException e) {
            throw new IOException("Could not configure a secure XML parser", e);
        } catch (SAXException e) {
            throw new IOException("Malformed or disallowed XML: " + e.getMessage(), e);
        }
    }
}
