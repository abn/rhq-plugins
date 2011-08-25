/*
 * RHQ Management Platform
 * Copyright (C) 2005-2009 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.rhq.plugins.modcluster.config;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author snegrea
 *
 */
public class ModClusterBeanFile extends AbstractConfigurationFile {

    private Node beanNode;

    public ModClusterBeanFile(String className) throws ParserConfigurationException, SAXException, IOException {
        super(
            "/home/snegrea/Downloads/jboss51eap/jboss-as/server/all/deploy/mod_cluster.sar/META-INF/mod_cluster-jboss-beans.xml");

        beanNode = this.getBeanNodeByClass(className);
    }

    public ModClusterBeanFile(String className, String constructorArgumentClassName)
        throws ParserConfigurationException, SAXException, IOException {
        super(
            "/home/snegrea/Downloads/jboss51eap/jboss-as/server/all/deploy/mod_cluster.sar/META-INF/mod_cluster-jboss-beans.xml");

        Node primaryBeanNode = this.getBeanNodeByClass(className);
        String dependencyName = this.getBeanFromConstructorArgument(primaryBeanNode, constructorArgumentClassName);

        beanNode = this.getBeanNodeByName(dependencyName);
    }

    private String getBeanFromConstructorArgument(Node beanNode, String constructorArgumentClassName) {
        List<Node> tempNodeList = this.getChildNodesByName(beanNode, "constructor");

        if (tempNodeList.size() > 0) {
            Node constructorNode = tempNodeList.get(0);

            tempNodeList = this.getChildNodesByName(constructorNode, "parameter");

            if (tempNodeList.size() > 0) {
                Node parameterNode = null;

                for (Node currentNode : tempNodeList) {
                    if (currentNode.getAttributes().getNamedItem("class") != null
                        && constructorArgumentClassName.equals(currentNode.getAttributes().getNamedItem("class")
                            .getTextContent())) {
                        parameterNode = currentNode;
                        break;
                    }
                }

                if (parameterNode != null) {
                    tempNodeList = this.getChildNodesByName(parameterNode, "inject");

                    if (tempNodeList.size() > 0) {
                        Node injectNode = tempNodeList.get(0);
                        Node beanAttribute = injectNode.getAttributes().getNamedItem("bean");

                        if (beanAttribute != null) {
                            return beanAttribute.getTextContent();
                        }
                    }
                }
            }
        }

        return null;
    }

    private List<Node> getChildNodesByName(Node node, String nodeName) {
        List<Node> listOfNodes = new ArrayList<Node>();

        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
            Node currentNode = node.getChildNodes().item(i);

            if (nodeName.equals(currentNode.getNodeName())) {
                listOfNodes.add(currentNode);
            }
        }

        return listOfNodes;
    }

    public void setPropertyValue(String propertyName, String value) {
        boolean propertyFound = false;
        for (int i = 0; i < beanNode.getChildNodes().getLength(); i++) {
            Node currentNode = beanNode.getChildNodes().item(i);

            if (currentNode.getNodeName().equals("property")
                && currentNode.getAttributes().getNamedItem("name") != null
                && propertyName.equals(currentNode.getAttributes().getNamedItem("name").getTextContent())) {

                if (value != null) {
                    currentNode.setTextContent(value);
                } else {
                    beanNode.removeChild(currentNode);
                }

                propertyFound = true;
            }
        }

        if (value != null && !propertyFound) {
            Node propertyChild = this.getDocument().createElement("property");
            Attr nameProperty = this.getDocument().createAttribute("name");
            nameProperty.setValue(propertyName);
            propertyChild.setTextContent(value);
            propertyChild.getAttributes().setNamedItem(nameProperty);
            beanNode.appendChild(propertyChild);
        }
    }

    public String getPropertyValue(String propertyName) {
        for (int i = 0; i < beanNode.getChildNodes().getLength(); i++) {
            Node currentNode = beanNode.getChildNodes().item(i);

            if (currentNode.getNodeName().equals("property")
                && currentNode.getAttributes().getNamedItem("name") != null
                && propertyName.equals(currentNode.getAttributes().getNamedItem("name").getTextContent())) {

                return currentNode.getTextContent();
            }
        }

        return null;
    }

    public void saveConfigFile() throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "no");

        //initialize StreamResult with File object to save to file
        StreamResult result = new StreamResult(new StringWriter());
        DOMSource source = new DOMSource(this.getDocument());
        transformer.transform(source, result);

        String xmlString = result.getWriter().toString();
        System.out.println(xmlString);
    }

    public Node getBeanNodeByClass(String className) {
        NodeList result = this.getDocument().getElementsByTagName("bean");

        for (int i = 1; i < result.getLength(); i++) {
            Node node = result.item(i);
            if (node.getAttributes().getNamedItem("class") != null
                && className.equals(node.getAttributes().getNamedItem("class").getTextContent())) {
                System.out.println(node.getAttributes().getNamedItem("class").getTextContent());
                return node;
            }
        }

        return null;
    }

    public Node getBeanNodeByName(String beanName) {
        NodeList result = this.getDocument().getElementsByTagName("bean");

        for (int i = 1; i < result.getLength(); i++) {
            Node node = result.item(i);
            if (node.getAttributes().getNamedItem("name") != null
                && beanName.equals(node.getAttributes().getNamedItem("name").getTextContent())) {
                System.out.println(node.getAttributes().getNamedItem("name").getTextContent());
                return node;
            }
        }

        return null;
    }

}
