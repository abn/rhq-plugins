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

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import org.rhq.core.domain.resource.ResourceCategory;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.domain.configuration.Configuration;

/**
 * @author Ian Springer
 */
@Test(groups = "as5-plugin")
public class QueueResourceTest extends AbstractResourceTest {
    @BeforeTest
    public void setup() {
        System.out.println("Running Queue test...");
        // TODO: Create a queue and send a message to it (i.e. to generate some metrics).
    }

    protected ResourceType getResourceType() {
        return new ResourceType("Queue", "JBossAS5", ResourceCategory.SERVICE, getServerResourceType());
    }

    protected Configuration getTestResourceConfiguration() {
        return new Configuration(); // TODO...
    }

    @Override
    protected void validateNumericMetricValue(String metricName, Double value) {
        if (metricName.endsWith("Count")) {
            assert value >= 0;
        } else {
            super.validateNumericMetricValue(metricName, value);
        }
    }

    @Override
    protected void validateTraitMetricValue(String metricName, String value) {
        if (metricName.equals("createdProgrammatically")) {
            assert value.equals("true");
        }
    }
}
