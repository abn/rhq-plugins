/*
 * Jopr Management Platform
 * Copyright (C) 2012 Red Hat, Inc.
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
package org.rhq.plugins.jbossas5.itest;

import org.rhq.core.plugin.testutil.AbstractAgentPluginTest;

/**
 * The base class for all jboss-as-5 plugin integration tests.
 *
 * @author Ian Springer
 */
public abstract class AbstractJBossAS5PluginTest extends AbstractAgentPluginTest {

    protected static final String PLUGIN_NAME = "JBossAS5";
    private static final int TYPE_HIERARCHY_DEPTH = 4;

    @Override
    protected String getPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    protected int getTypeHierarchyDepth() {
        return TYPE_HIERARCHY_DEPTH;
    }

}
