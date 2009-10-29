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
package org.rhq.plugins.augeas;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.augeas.Augeas;
import net.augeas.AugeasException;
import net.augeas.jna.Aug;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.Nullable;

import org.rhq.core.domain.configuration.AbstractPropertyMap;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.ConfigurationUpdateStatus;
import org.rhq.core.domain.configuration.Property;
import org.rhq.core.domain.configuration.PropertyList;
import org.rhq.core.domain.configuration.PropertyMap;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.configuration.definition.ConfigurationDefinition;
import org.rhq.core.domain.configuration.definition.PropertyDefinition;
import org.rhq.core.domain.configuration.definition.PropertyDefinitionList;
import org.rhq.core.domain.configuration.definition.PropertyDefinitionMap;
import org.rhq.core.domain.configuration.definition.PropertyDefinitionSimple;
import org.rhq.core.domain.configuration.definition.PropertySimpleType;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.domain.resource.CreateResourceStatus;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.pluginapi.configuration.ConfigurationFacet;
import org.rhq.core.pluginapi.configuration.ConfigurationUpdateReport;
import org.rhq.core.pluginapi.inventory.CreateChildResourceFacet;
import org.rhq.core.pluginapi.inventory.CreateResourceReport;
import org.rhq.core.pluginapi.inventory.DeleteResourceFacet;
import org.rhq.core.pluginapi.inventory.InvalidPluginConfigurationException;
import org.rhq.core.pluginapi.inventory.ResourceComponent;
import org.rhq.core.pluginapi.inventory.ResourceContext;
import org.rhq.plugins.augeas.helper.AugeasNode;
import org.rhq.plugins.augeas.helper.Glob;

/**
 * @author Ian Springer
 * @author Lukas Krejci
 */
public class AugeasConfigurationComponent<T extends ResourceComponent> implements ResourceComponent<T>,
    ConfigurationFacet, CreateChildResourceFacet, DeleteResourceFacet {
    public static final String INCLUDE_GLOBS_PROP = "configurationFilesInclusionPatterns";
    public static final String EXCLUDE_GLOBS_PROP = "configurationFilesExclusionPatterns";
    public static final String RESOURCE_CONFIGURATION_ROOT_NODE_PROP = "resourceConfigurationRootNode";
    public static final String AUGEAS_MODULE_NAME_PROP = "augeasModuleName";
    public static final String NEW_NODE_EXPRESSION = "newNodeExpression";

    private static final boolean IS_WINDOWS = (File.separatorChar == '\\');
    private static final String AUGEAS_LOAD_PATH = "/usr/share/augeas/lenses";
    public static final String AUGEAS_ROOT_PATH = "/";

    private final Log log = LogFactory.getLog(this.getClass());

    private ResourceContext<T> resourceContext;
    private List<String> includeGlobs;
    private List<String> excludeGlobs;
    private Augeas augeas;
    private AugeasNode resourceConfigRootNode;

    public void start(ResourceContext<T> resourceContext) throws InvalidPluginConfigurationException, Exception {
        this.resourceContext = resourceContext;
        Configuration pluginConfig = this.resourceContext.getPluginConfiguration();

        initGlobs(pluginConfig);

        this.augeas = createAugeas();

        if (this.augeas != null) {
            String resourceConfigRootPath = getResourceConfigurationRootPath();
            if (resourceConfigRootPath.indexOf(AugeasNode.SEPARATOR_CHAR) != 0) {
                this.resourceConfigRootNode = new AugeasNode("/files/", resourceConfigRootPath);
            } else {
                this.resourceConfigRootNode = new AugeasNode(resourceConfigRootPath);
            }
        }
    }

    public void stop() {
        this.augeas.close();
    }

    public AvailabilityType getAvailability() {
        for (File f : getConfigurationFiles()) {
            singleFileAvailabilityCheck(f);
        }
        return AvailabilityType.UP;
    }

    public Configuration loadResourceConfiguration() throws Exception {
        // Load the config file from disk and build a tree representation of it.
        this.augeas.load();

        ConfigurationDefinition resourceConfigDef = this.resourceContext.getResourceType()
            .getResourceConfigurationDefinition();
        Configuration resourceConfig = new Configuration();
        resourceConfig.setNotes("Loaded from Augeas at " + new Date());

        Collection<PropertyDefinition> propDefs = resourceConfigDef.getPropertyDefinitions().values();

        for (PropertyDefinition propDef : propDefs) {
            loadProperty(propDef, resourceConfig, this.augeas, this.resourceConfigRootNode);
        }

        return resourceConfig;
    }

    public void updateResourceConfiguration(ConfigurationUpdateReport report) {
        if (!validateResourceConfiguration(report)) {
            log.debug("Validation of updated Resource configuration for " + this.getResourceContext().getResourceType()
                + " Resource with key '" + this.getResourceContext().getResourceKey()
                + "' failed with the following errors: " + report.getErrorMessage());
            report.setStatus(ConfigurationUpdateStatus.FAILURE);
            return;
        }

        // Load the config file from disk and build a tree representation of it in memory.
        this.augeas.load();

        ConfigurationDefinition resourceConfigDef = this.resourceContext.getResourceType()
            .getResourceConfigurationDefinition();
        Configuration resourceConfig = report.getConfiguration();

        Collection<PropertyDefinition> propDefs = resourceConfigDef.getPropertyDefinitions().values();
        for (PropertyDefinition propDef : propDefs) {
            setNode(propDef, resourceConfig, this.augeas, this.resourceConfigRootNode);
        }

        // Write the updated tree out to the config file.
        saveConfigurationFile();

        // If we got this far, we've succeeded in our mission.
        report.setStatus(ConfigurationUpdateStatus.SUCCESS);
    }

    public CreateResourceReport createResource(CreateResourceReport report) {
        Configuration resourceConfig = report.getResourceConfiguration();
        ConfigurationDefinition resourceConfigDef = report.getResourceType().getResourceConfigurationDefinition();

        // First insert the root node corresponding to the new child Resource.
        String rootPath = getChildResourceConfigurationRootPath(report.getResourceType(), report
            .getResourceConfiguration());
        AugeasNode rootNode = new AugeasNode(rootPath);
        if (this.augeas.exists(rootNode.getPath())) {
            report.setStatus(CreateResourceStatus.FAILURE);
            report.setErrorMessage("An Augeas node already exists with path " + rootPath);
            return report;
        }
        String rootLabel = getChildResourceConfigurationRootLabel(report.getResourceType(), report
            .getResourceConfiguration());
        this.augeas.set(rootNode.getPath(), rootLabel);

        // Then set all its child nodes.
        Collection<PropertyDefinition> propDefs = resourceConfigDef.getPropertyDefinitions().values();
        for (PropertyDefinition propDef : propDefs) {
            setNode(propDef, resourceConfig, this.augeas, rootNode);
        }

        // Write the updated tree out to the config file.
        saveConfigurationFile();

        // If we got this far, we've succeeded in our mission.
        report.setStatus(CreateResourceStatus.SUCCESS);
        return report;
    }

    public void deleteResource() throws Exception {
        String rootPath = getResourceConfigurationRootPath();
        Augeas augeas = getAugeas();
        augeas.remove(rootPath);
        augeas.save();
    }

    /**
     * Subclasses should override this method in order to perform any validation that is not encapsulated
     * in the Configuration metadata.
     *
     * @param report the report to which any validation errors should be added
     *
     * @return true if the Configuration is valid, or false if it is not
     */
    protected boolean validateResourceConfiguration(ConfigurationUpdateReport report) {
        return true;
    }

    protected AugeasNode getExistingChildNodeForListMemberPropertyMap(AugeasNode parentNode,
        PropertyDefinitionList propDefList, PropertyMap propMap) {
        String mapKey = getListMemberMapKey(propDefList);
        if (mapKey != null) {
            String existingChildNodeName = propMap.getSimple(mapKey).getStringValue();
            AugeasNode existingChildNode = new AugeasNode(parentNode, existingChildNodeName);
            return (this.augeas.exists(existingChildNode.getPath())) ? existingChildNode : null;
        } else {
            return null;
        }
    }

    protected String getResourceConfigurationRootPath() {
        Configuration pluginConfig = this.resourceContext.getPluginConfiguration();
        return pluginConfig.getSimpleValue(RESOURCE_CONFIGURATION_ROOT_NODE_PROP, null);
    }

    /**
     * Subclasses that wish to support child Resource creation must override this method.
     *
     * @param resourceType the type of child Resource being created
     * @param resourceConfig the Resource configuration for the child Resource being created
     *
     * @return the path of the Augeas node that should be created for the child Resource being created
     */
    protected String getChildResourceConfigurationRootPath(ResourceType resourceType, Configuration resourceConfig) {
        throw new IllegalStateException("Class " + getClass().getName()
            + " does not override getChildResourceConfigurationRootPath() for " + resourceType + ".");
    }

    /**
     * Subclasses that wish to support child Resource creation must override this method.
     *
     * @param resourceType the type of child Resource being created
     * @param resourceConfig the Resource configuration for the child Resource being created
     *
     * @return the value of the Augeas node that should be created for the child Resource being created
     */
    protected String getChildResourceConfigurationRootLabel(ResourceType resourceType, Configuration resourceConfig) {
        throw new IllegalStateException("Class " + getClass().getName()
            + " does not override getChildResourceConfigurationRootLabel() for " + resourceType + ".");
    }

    public ResourceContext<T> getResourceContext() {
        return resourceContext;
    }

    public List<File> getConfigurationFiles() {
        List<File> files = Glob.matchAll(new File(AUGEAS_ROOT_PATH), includeGlobs);
        Glob.excludeAll(files, excludeGlobs);
        return files;
    }

    public Augeas getAugeas() {
        return this.augeas;
    }

    private Augeas createAugeas() {
        Configuration pluginConfig = this.resourceContext.getPluginConfiguration();
        String augeasModuleName = pluginConfig.getSimpleValue(AUGEAS_MODULE_NAME_PROP, null);
        if (augeasModuleName == null) {
            return null;
        }

        if (!initAugeasJnaProxy()) {
            return null;
        }

        Augeas augeas;
        try {
            augeas = new Augeas(AUGEAS_ROOT_PATH, AUGEAS_LOAD_PATH, Augeas.NO_MODL_AUTOLOAD);
            augeas.set("/augeas/load/" + augeasModuleName + "/lens", augeasModuleName + ".lns");

            int idx = 1;
            for (String incl : includeGlobs) {
                augeas.set("/augeas/load/" + augeasModuleName + "/incl[" + (idx++) + "]", incl);
            }
            idx = 1;
            for (String excl : excludeGlobs) {
                augeas.set("/augeas/load/" + augeasModuleName + "/excl[" + (idx++) + "]", excl);
            }
        } catch (RuntimeException e) {
            augeas = null;
            log.warn("Failed to initialize Augeas Java API.", e);
        }
        return augeas;
    }

    private boolean initAugeasJnaProxy() {
        Aug aug;
        try {
            aug = Aug.INSTANCE;
        } catch (NoClassDefFoundError e) {
            if (!IS_WINDOWS) {
                log.warn("Augeas shared library not found. If on Fedora or RHEL, yum install augeas.");
            }
            return false;
        }
        if (log.isTraceEnabled()) {
            log.trace("Aug JNA object: " + aug);
        }
        return true;
    }

    protected String getAugeasPathRelativeToParent(PropertyDefinition propDef, AugeasNode parentNode, Augeas augeas) {
        return propDef.getName();
    }
    
    protected void loadProperty(PropertyDefinition propDef, AbstractPropertyMap parentPropMap, Augeas augeas,
        AugeasNode parentNode) {
        String propName = getAugeasPathRelativeToParent(propDef, parentNode, augeas);
        AugeasNode node = (propName.equals(".")) ? parentNode : new AugeasNode(parentNode, propName);
        Property prop;
        if (propDef instanceof PropertyDefinitionSimple) {
            prop = createPropertySimple((PropertyDefinitionSimple) propDef, augeas, node);
        } else if (propDef instanceof PropertyDefinitionMap) {
            prop = createPropertyMap((PropertyDefinitionMap) propDef, augeas, node);
        } else if (propDef instanceof PropertyDefinitionList) {
            prop = createPropertyList((PropertyDefinitionList) propDef, augeas, node);
        } else {
            throw new IllegalStateException("Unsupported PropertyDefinition subclass: " + propDef.getClass().getName());
        }
        parentPropMap.put(prop);
    }

    protected Object toPropertyValue(PropertyDefinitionSimple propDefSimple, Augeas augeas, AugeasNode node) {
        return augeas.get(node.getPath());
    }

    protected Property createPropertySimple(PropertyDefinitionSimple propDefSimple, Augeas augeas, AugeasNode node) {
        Object value;
        if (propDefSimple.getType() == PropertySimpleType.LONG_STRING) {
            List<String> childPaths = augeas.match(node.getPath());
            if (childPaths.isEmpty()) {
                return null;
            }
            StringBuilder propValue = new StringBuilder();
            for (String childPath : childPaths) {
                String childValue = augeas.get(childPath);
                propValue.append(childValue).append("\n");
            }
            // Chop the final newline char.
            propValue.deleteCharAt(propValue.length() - 1);
            value = propValue.toString();
        } else {
            value = toPropertyValue(propDefSimple, augeas, node);
        }
        return new PropertySimple(propDefSimple.getName(), value);
    }

    protected PropertyMap createPropertyMap(PropertyDefinitionMap propDefMap, Augeas augeas, AugeasNode node) {
        PropertyMap propMap = new PropertyMap(propDefMap.getName());
        populatePropertyMap(propDefMap, propMap, augeas, node);
        return propMap;
    }

    protected Property createPropertyList(PropertyDefinitionList propDefList, Augeas augeas, AugeasNode node) {
        PropertyDefinition listMemberPropDef = propDefList.getMemberDefinition();
        if (!(listMemberPropDef instanceof PropertyDefinitionMap)) {
            throw new IllegalArgumentException(
                "Invalid Resource ConfigurationDefinition - only lists of maps are supported.");
        }
        PropertyDefinitionMap listMemberPropDefMap = (PropertyDefinitionMap) listMemberPropDef;

        PropertyList propList = new PropertyList(propDefList.getName());

        String mapKey = getListMemberMapKey(propDefList);
        String mapPath = getAugeasPathRelativeToParent(listMemberPropDefMap, node, augeas);
        String listMemberPathsExpression = node.getPath() + AugeasNode.SEPARATOR_CHAR + mapPath;
        List<String> listMemberPaths = augeas.match(listMemberPathsExpression);
        for (String listMemberPath : listMemberPaths) {
            AugeasNode listMemberNode = new AugeasNode(listMemberPath);
            PropertyMap listMemberPropMap = new PropertyMap(listMemberPropDefMap.getName());
            propList.add(listMemberPropMap);

            // Add the "key" prop, if defined, to the map.
            if (mapKey != null) {
                PropertySimple keyProp = new PropertySimple(mapKey, listMemberNode.getName());
                listMemberPropMap.put(keyProp);
            }

            // Populate the rest of the map child properties.
            populatePropertyMap(listMemberPropDefMap, listMemberPropMap, augeas, listMemberNode);
        }

        return propList;
    }

    protected void populatePropertyMap(PropertyDefinitionMap propDefMap, PropertyMap propMap, Augeas augeas,
        AugeasNode mapNode) {
        for (PropertyDefinition mapEntryPropDef : propDefMap.getPropertyDefinitions().values()) {
            loadProperty(mapEntryPropDef, propMap, augeas, mapNode);
        }
    }

    protected void setNode(PropertyDefinition propDef, AbstractPropertyMap parentPropMap, Augeas augeas,
        AugeasNode parentNode) {
        String propName = getAugeasPathRelativeToParent(propDef, parentNode, augeas);
        AugeasNode node = (propName.equals(".")) ? parentNode : new AugeasNode(parentNode, propName);

        if (isPropertyDefined(propDef, parentPropMap)) {
            // The property *is* defined, which means we either need to add or update the corresponding node in the
            // Augeas tree.
            if (propDef instanceof PropertyDefinitionSimple) {
                PropertyDefinitionSimple propDefSimple = (PropertyDefinitionSimple) propDef;
                PropertySimple propSimple = parentPropMap.getSimple(propDefSimple.getName());
                setNodeFromPropertySimple(augeas, node, propDefSimple, propSimple);
            } else if (propDef instanceof PropertyDefinitionMap) {
                PropertyDefinitionMap propDefMap = (PropertyDefinitionMap) propDef;
                PropertyMap propMap = parentPropMap.getMap(propDefMap.getName());
                setNodeFromPropertyMap(propDefMap, propMap, augeas, node);
            } else if (propDef instanceof PropertyDefinitionList) {
                PropertyDefinitionList propDefList = (PropertyDefinitionList) propDef;
                PropertyList propList = parentPropMap.getList(propDefList.getName());
                setNodeFromPropertyList(propDefList, propList, augeas, node);
            } else {
                throw new IllegalStateException("Unsupported PropertyDefinition subclass: "
                    + propDef.getClass().getName());
            }
        } else {
            // The property *is not* defined - remove the corresponding node from the Augeas tree if it exists.
            removeNodeIfItExists(augeas, node);
        }
    }

    protected String toNodeValue(Augeas augeas, AugeasNode node, PropertyDefinitionSimple propDefSimple,
        PropertySimple propSimple) {
        return propSimple.getStringValue();
    }

    protected void setNodeFromPropertySimple(Augeas augeas, AugeasNode node, PropertyDefinitionSimple propDefSimple,
        PropertySimple propSimple) {
        String value = toNodeValue(augeas, node, propDefSimple, propSimple);
        if (propDefSimple.getType() == PropertySimpleType.LONG_STRING) {
            // First remove the existing items.
            List<String> childPaths = augeas.match(node.getPath());
            for (String childPath : childPaths) {
                augeas.remove(childPath);
            }

            // Now add the updated items.
            String[] tokens = value.trim().split("\\s+");
            for (int i = 0, tokensLength = tokens.length; i < tokensLength; i++) {
                String itemPath = node.getPath() + "[" + (i + 1) + "]";
                String itemValue = tokens[i];
                augeas.set(itemPath, itemValue);
            }
        } else {
            // Update the value of the existing node.
            augeas.set(node.getPath(), value);
        }
    }

    protected void setNodeFromPropertyMap(PropertyDefinitionMap propDefMap, PropertyMap propMap, Augeas augeas,
        AugeasNode mapNode) {
        for (PropertyDefinition mapEntryPropDef : propDefMap.getPropertyDefinitions().values()) {
            setNode(mapEntryPropDef, propMap, augeas, mapNode);
        }
    }

    protected void setNodeFromPropertyList(PropertyDefinitionList propDefList, PropertyList propList, Augeas augeas,
        AugeasNode listNode) {
        PropertyDefinition listMemberPropDef = propDefList.getMemberDefinition();

        if (!(listMemberPropDef instanceof PropertyDefinitionMap)) {
            throw new IllegalArgumentException(
                "Invalid Resource ConfigurationDefinition - only lists of maps are supported.");
        }
        PropertyDefinitionMap listMemberPropDefMap = (PropertyDefinitionMap) listMemberPropDef;

        int listIndex = 0;
        String listMemberPropDefMapPath = getAugeasPathRelativeToParent(listMemberPropDefMap, listNode, augeas);
        List<String> existingListMemberPaths = augeas.match(listNode.getPath() + AugeasNode.SEPARATOR_CHAR
            + listMemberPropDefMapPath);
        List<AugeasNode> existingListMemberNodes = new ArrayList<AugeasNode>();
        for (String existingListMemberPath : existingListMemberPaths) {
            existingListMemberNodes.add(new AugeasNode(existingListMemberPath));
        }
        Set<AugeasNode> updatedListMemberNodes = new HashSet<AugeasNode>();
        for (Property listMemberProp : propList.getList()) {
            PropertyMap listMemberPropMap = (PropertyMap) listMemberProp;
            AugeasNode memberNodeToUpdate = getExistingChildNodeForListMemberPropertyMap(listNode, propDefList,
                listMemberPropMap);
            if (memberNodeToUpdate != null) {
                // Keep track of the existing nodes that we'll be updating, so that we can remove all other existing
                // nodes.
                updatedListMemberNodes.add(memberNodeToUpdate);
            } else {
                // The maps in the list are non-keyed, or there is no map in the list with the same key as the map
                // being added, so create a new node for the map to add to the list.
                memberNodeToUpdate = new AugeasNode(listNode, getAugeasPathRelativeToParent(listMemberPropDefMap, listNode, augeas) + "[" + listIndex + "]");
            }

            listIndex++;
            // Update the node's children.
            setNodeFromPropertyMap(listMemberPropDefMap, listMemberPropMap, augeas, memberNodeToUpdate);
        }

        // Now remove any existing nodes that we did not update in the previous loop.
        for (AugeasNode existingListMemberNode : existingListMemberNodes) {
            if (!updatedListMemberNodes.contains(existingListMemberNode)) {
                augeas.remove(existingListMemberNode.getPath());
            }
        }
    }

    private boolean isPropertyDefined(PropertyDefinition propDef, AbstractPropertyMap parentPropMap) {
        Property prop = parentPropMap.get(propDef.getName());
        if (prop == null) {
            return false;
        } else {
            return (!(prop instanceof PropertySimple) || ((PropertySimple) prop).getStringValue() != null);
        }
    }

    private void removeNodeIfItExists(Augeas augeas, AugeasNode node) {
        if (augeas.exists(node.getPath())) {
            log.debug("Removing node " + node + " from Augeas tree...");
            augeas.remove(node.getPath());
        }
    }

    @Nullable
    private String getListMemberMapKey(PropertyDefinitionList propDefList) {
        Configuration pluginConfig = this.resourceContext.getPluginConfiguration();
        PropertyMap mapKeyNames = pluginConfig.getMap("listMemberMapKeyNames");
        if (mapKeyNames == null) {
            return null;
        }
        String listName = propDefList.getName();
        return mapKeyNames.getSimpleValue(listName, null);
    }

    private void saveConfigurationFile() {
        // TODO: Backup original file.
        try {
            this.augeas.save();
        } catch (AugeasException e) {
            throw new RuntimeException(summarizeAugeasError(), e);
        }
    }

    private void initGlobs(Configuration pluginConfiguration) {
        PropertySimple includes = pluginConfiguration.getSimple(INCLUDE_GLOBS_PROP);
        PropertySimple excludes = pluginConfiguration.getSimple(EXCLUDE_GLOBS_PROP);

        includeGlobs = new ArrayList<String>();
        excludeGlobs = new ArrayList<String>();

        includeGlobs.addAll(Arrays.asList(includes.getStringValue().split("\\s*\\|\\s*")));
        if (excludes != null && excludes.getStringValue() != null) {
            excludeGlobs.addAll(Arrays.asList(excludes.getStringValue().split("\\s*\\|\\s*")));
        }
    }

    private void singleFileAvailabilityCheck(File f) {
        if (!f.isAbsolute()) {
            throw new InvalidPluginConfigurationException("Path '" + f.getPath() + "' is not an absolute path.");
        }
        if (!f.exists()) {
            throw new InvalidPluginConfigurationException("File '" + f.getPath() + "' does not exist.");
        }
        if (f.isDirectory()) {
            throw new InvalidPluginConfigurationException("Path '" + f.getPath()
                + "' is a directory, not a regular file.");
        }
    }
    protected String summarizeAugeasError() {
        StringBuilder summary = new StringBuilder();
        String metadataNodePrefix = "/augeas/files";
        for(String glob : includeGlobs) {
            if (glob.startsWith(AugeasNode.SEPARATOR)) {
                glob = glob.substring(1);
            }
            AugeasNode metadataNode = new AugeasNode(metadataNodePrefix, glob);
            AugeasNode errorNode = new AugeasNode(metadataNode, "error");
            List<String> nodePaths = this.augeas.match(errorNode.getPath() + "/*");
            for (String path : nodePaths) {
                String error = this.augeas.get(path);
                summary.append("File \"").append(path.substring(metadataNodePrefix.length(), path.length()))
                .append("\":\n").append(error).append("\n");
            }
        }

        return summary.toString();
    }
}
