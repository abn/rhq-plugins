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
package org.rhq.plugins.jbossas5.util;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import org.jboss.deployers.spi.management.ManagementView;
import org.jboss.managed.api.ComponentType;
import org.jboss.managed.api.ManagedComponent;
import org.jboss.managed.api.ManagedProperty;
import org.jboss.managed.api.annotation.ViewUse;
import org.jboss.metatype.api.types.MetaType;
import org.jboss.metatype.api.values.EnumValue;
import org.jboss.metatype.api.values.SimpleValue;

/**
 * @author Ian Springer
 */
public class ManagedComponentUtils
{
    public static ManagedComponent getManagedComponent(ManagementView managementView, ComponentType componentType,
                                                       String componentName)
    {
        Set<ManagedComponent> components = getManagedComponents(managementView, componentType);
        for (ManagedComponent component : components)
        {
            if (component.getName().equals(componentName))
                return component;
        }
        return null;
    }

    public static ManagedComponent getSingletonManagedComponent(ManagementView managementView,
                                                                ComponentType componentType)
    {
        Set<ManagedComponent> components = getManagedComponents(managementView, componentType);
        if (components.size() != 1)
            throw new IllegalStateException("Found more than one component of type " + componentType + ": "
                    + components);
        @SuppressWarnings({"UnnecessaryLocalVariable"})
        ManagedComponent component = components.iterator().next();
        return component;
    }

    public static Serializable getSimplePropertyValue(ManagedComponent component, String propertyName)
    {
        ManagedProperty property = component.getProperty(propertyName);
        if (property == null)
            throw new IllegalStateException("Property named '" + propertyName + "' not found for ManagedComponent ["
                    + component + "].");
        MetaType metaType = property.getMetaType();
        Serializable value;
        if (metaType.isSimple())
        {
            SimpleValue simpleValue = (SimpleValue)property.getValue();
            value = (simpleValue != null) ? simpleValue.getValue() : null;
        }
        else if (metaType.isEnum())
        {
            EnumValue enumValue = (EnumValue)property.getValue();
            value = (enumValue != null) ? enumValue.getValue() : null;
        }
        else
        {
            throw new IllegalStateException("Type of [" + property + "] is not simple or enum.");
        }
        return value;
    }

    @NotNull
    public static EnumSet<ViewUse> getViewUses(ManagedProperty managedProperty)
    {
        EnumSet<ViewUse> viewUses = EnumSet.noneOf(ViewUse.class);
        for (ViewUse viewUse : ViewUse.values())
        {
            if (managedProperty.hasViewUse(viewUse))
                viewUses.add(viewUse);
        }
        return viewUses;
    }

    /**
     * @param name
     * @param componentType
     * @return
     */
    public static boolean isManagedComponent(ManagementView managementView, String name, ComponentType componentType)
    {
        boolean isDeployed = false;
        if (name != null)
        {
            try
            {
                ManagedComponent component = getManagedComponent(managementView, componentType, name);
                if (component != null)
                    isDeployed = true;
            }
            catch (Exception e)
            {
                // Setting it to true to be safe than sorry, since there might be a component
                // already deployed in the AS. TODO (ips): I don't think I like this.
                isDeployed = true;
            }
        }
        return isDeployed;
    }

    @NotNull
    private static Set<ManagedComponent> getManagedComponents(ManagementView managementView, ComponentType componentType)
    {
        Set<ManagedComponent> components;
        try
        {
            components = managementView.getComponentsForType(componentType);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
        return components;
    }
}