/*
 * Jopr Management Platform
 * Copyright (C) 2005-2009 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2, as
 * published by the Free Software Foundation, and/or the GNU Lesser
 * General Public License, version 2.1, also as published by the Free
 * Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License and the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and the GNU Lesser General Public License along with this program;
 * if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.rhq.plugins.jbossas5.test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.plugins.jbossas5.connection.ProfileServiceConnection;
import org.rhq.plugins.jbossas5.util.DebugUtils;

import org.jboss.deployers.spi.management.ManagementView;
import org.jboss.managed.api.ComponentType;
import org.jboss.managed.api.DeploymentTemplateInfo;
import org.jboss.managed.api.ManagedComponent;
import org.jboss.managed.api.ManagedProperty;
import org.jboss.managed.plugins.ManagedPropertyImpl;
import org.jboss.metatype.api.types.SimpleMetaType;
import org.jboss.metatype.api.values.SimpleValueSupport;

/**
 * @author Ian Springer
 */
public class AbstractManagedComponentTest
{
    private final Log log = LogFactory.getLog(this.getClass());

    protected ManagementView managementView;

    public AbstractManagedComponentTest(ProfileServiceConnection connection)
    {
        System.out.println("Initializing profile service...");
        this.managementView = connection.getManagementView();
        this.managementView.load();
    }

    protected Set<ManagedProperty> getMandatoryProperties(DeploymentTemplateInfo template)
    {
        Map<String, ManagedProperty> managedProperties = template.getProperties();
        Set<ManagedProperty> mandatoryProperties = new HashSet();
        for (ManagedProperty managedProperty : managedProperties.values())
        {
            if (managedProperty.isMandatory())
                mandatoryProperties.add(managedProperty);
        }
        return mandatoryProperties;
    }

    protected Set<ManagedProperty> getNonMandatoryProperties(DeploymentTemplateInfo template)
    {
        Map<String, ManagedProperty> managedProperties = template.getProperties();
        Set<ManagedProperty> mandatoryProperties = new HashSet();
        for (ManagedProperty managedProperty : managedProperties.values())
        {
            if (!managedProperty.isMandatory())
                mandatoryProperties.add(managedProperty);
        }
        return mandatoryProperties;
    }

    protected ManagedComponent createComponent(ComponentType componentType, String componentName, DeploymentTemplateInfo template)
            throws Exception
    {
        log.info("Creating new " + componentType + " component with properties:\n"
                + DebugUtils.convertPropertiesToString(template.getProperties()) + "...");
        this.managementView.applyTemplate(componentName, template);
        this.managementView.process();
        ManagedComponent component = this.managementView.getComponent(componentName, componentType);
        assert component != null;
        assert component.getType().equals(componentType);
        assert component.getName().equals(componentName);
        return component;
    }

    protected void createComponentWithFailureExpected(ComponentType componentType, String componentName, DeploymentTemplateInfo template)
            throws Exception
    {
        log.info("Creating new " + componentType + " component with properties:\n"
                + DebugUtils.convertPropertiesToString(template.getProperties()) + "...");
        try
        {
            this.managementView.applyTemplate(componentName, template);
            this.managementView.process();
        }
        catch (Exception e)
        {
            log.info("Exception thrown as expected: " + e);
        }
        ManagedComponent component = this.managementView.getComponent(componentName, componentType);
        if (component != null)
            this.managementView.removeComponent(component);
        assert component == null;
    }

    protected void setSimpleStringProperty(Map<String, ManagedProperty> properties, String propName, String propValue)
    {
        if (!properties.containsKey(propName))
            properties.put(propName, new ManagedPropertyImpl(propName));
        ManagedProperty property = properties.get(propName);
        property.setValue(new SimpleValueSupport(SimpleMetaType.STRING, propValue));
    }
}
