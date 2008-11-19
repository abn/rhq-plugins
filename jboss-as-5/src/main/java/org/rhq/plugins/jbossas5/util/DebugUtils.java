/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
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
package org.rhq.plugins.jbossas5.util;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;

import org.jboss.managed.api.ManagedComponent;
import org.jboss.managed.api.ManagedProperty;
import org.jboss.metatype.api.values.MetaValue;
import org.jboss.metatype.api.values.CollectionValue;

/**
 * Utility methods for converting various Profile Service objects into Strings for debugging purposes.
 *
 * @author Ian Springer
 */
public abstract class DebugUtils {
    public static String convertPropertiesToString(ManagedComponent managedComponent) {
        StringBuilder buf = new StringBuilder();
        buf.append("Properties for managed component [").append(managedComponent.getName()).append("]:");
        Map<String, ManagedProperty> managedProperties = managedComponent.getProperties();
        List<ManagedProperty> props = new ArrayList<ManagedProperty>(managedProperties.values());
        Collections.sort(props, new ManagedPropertyComparator()); // sort by name
        for (ManagedProperty managedProperty : props) {
            buf.append("\n\tname=").append(managedProperty.getName());
            if (!managedProperty.getName().equals(managedProperty.getMappedName()))
                buf.append(", mappedName=").append(managedProperty.getMappedName());
            buf.append(", required=").append(managedProperty.isMandatory());
            Object value = managedProperty.getValue();
            if (value instanceof MetaValue)
                value = convertMetaValueToString((MetaValue)value);
            buf.append(", value=").append(value);
        }
        return buf.toString();
    }

    public static String convertMetaValueToString(MetaValue metaValue) {
        if (metaValue == null)
            return "null";
        StringBuilder buffer = new StringBuilder();
        if (metaValue.getMetaType().isCollection()) {
            CollectionValue collectionValue = (CollectionValue)metaValue;
            buffer.append(collectionValue).append("\n");
            buffer.append("\t").append("Elements:\n");
            for (MetaValue elementMetaValue : collectionValue.getElements())
                buffer.append("\t").append(convertMetaValueToString(elementMetaValue)).append("\n");
        } else {
            buffer.append(metaValue);
        }
        return buffer.toString();
    }

    private static class ManagedPropertyComparator implements Comparator<ManagedProperty> {
        public int compare(ManagedProperty prop1, ManagedProperty prop2) {
            return prop1.getName().compareTo(prop2.getName());
        }
    }

    private DebugUtils() {
    }
}
