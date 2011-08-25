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
package org.rhq.plugins.modcluster.test;

import org.testng.annotations.Test;

import org.rhq.plugins.modcluster.config.JBossWebServerFile;
import org.rhq.plugins.modcluster.config.ModClusterBeanFile;

public class ServerConfigTest {

    @Test
    public void TestModClusterConfig() {

        try {
            //Test simple bean configuration
            ModClusterBeanFile config = new ModClusterBeanFile("org.jboss.modcluster.ha.HAModClusterService",
                "/home/snegrea/Downloads/jboss51eap/jboss-as/server/all/deploy/mod_cluster.sar/META-INF/mod_cluster-jboss-beans.xml");
            config.setPropertyValue("processStatusFrequency", "4");
            config.setPropertyValue("test", "5");
            config.setPropertyValue("test", "123");

            config.setPropertyValue("test", null);
            config.setPropertyValue("processStatusFrequency", null);

            config.saveConfigurationFile();

            //Test bean configuration with constructor dependency
            config = new ModClusterBeanFile("org.jboss.modcluster.ha.HAModClusterService",
                "org.jboss.modcluster.config.ha.HAModClusterConfig",
                "/home/snegrea/Downloads/jboss51eap/jboss-as/server/all/deploy/mod_cluster.sar/META-INF/mod_cluster-jboss-beans.xml");
            config.setPropertyValue("processStatusFrequency", "4");
            config.setPropertyValue("test", "5");
            config.setPropertyValue("test", "123");

            config.setPropertyValue("test", null);
            config.setPropertyValue("processStatusFrequency", null);
            config.saveConfigurationFile();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Test
    public void TestJBossWebConfig() {

        try {
            //Test simple bean configuration
            JBossWebServerFile config = new JBossWebServerFile(
                "/home/snegrea/Downloads/jboss42/server/all/deploy/jboss-web.deployer/server.xml");
            config.setPropertyValue("processStatusFrequency", "4");
            config.setPropertyValue("test", "5");
            config.setPropertyValue("test", "123");

            config.setPropertyValue("test", null);
            config.setPropertyValue("processStatusFrequency", null);

            config.saveConfigurationFile();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
