package com.dia.conversion.reader.xml;

import lombok.experimental.UtilityClass;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * XML parser factory for user-uploaded documents.
 *
 * <p>A DOCTYPE is tolerated (older EA XMI 1.1 exports declare {@code <!DOCTYPE XMI SYSTEM "UML_EA.DTD">}),
 * but nothing external is ever resolved: no external entities, no external DTD/schema loading, and JAXP secure
 * processing caps entity expansion (billion laughs). This blocks XXE file reads and SSRF into the internal network.
 */
@UtilityClass
public class SecureXml {

    public DocumentBuilderFactory documentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }
}
