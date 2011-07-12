/*
 * RHQ Management Platform
 * Copyright (C) 2005-2011 Red Hat, Inc.
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

package org.rhq.plugins.apache;


import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.ResourceError;
import org.rhq.core.domain.resource.ResourceErrorType;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.plugins.apache.parser.ApacheDirectiveTree;
import org.rhq.plugins.apache.util.ApacheDeploymentUtil.DeploymentConfig;
import org.rhq.plugins.apache.util.RuntimeApacheConfiguration;
import org.rhq.plugins.apache.util.VirtualHostLegacyResourceKeyUtil;
import org.rhq.test.pc.PluginContainerSetup;

/**
 * 
 *
 * @author Lukas Krejci
 */
public class UpgradeSimpleConfigurationFromRHQ3_0_0Test extends UpgradeTestBase {
    
    //TODO override the *.rhq3.resource.key variables in each test to define whether
    //the resource key was discovered without SNMP, with SNMP, with Main Server URL set,
    //or not
    
    //TODO the default inventory.xml uses discovery results without snmp active. with SNMP active, all 5 vhost
    //would discovered in RHQ 3. This test should therefore use a different inventory file.
    @Test(enabled = false) //this fails until the above todo is implemented
    @PluginContainerSetup(plugins = {PLATFORM_PLUGIN, AUGEAS_PLUGIN, APACHE_PLUGIN}, numberOfInitialDiscoveries = 2)
    @Parameters({"apache2.install.dir", "apache2.exe.path"})
    public void testWithResolvableNamesWithSNMP(final String installDir, final String exePath) throws Throwable {
        testUpgrade(new TestConfiguration() {
            {
                apacheConfigurationFiles = new String[]{"/full-configurations/2.2.x/simple/httpd.conf"};
                inventoryFile = "/mocked-inventories/rhq-3.0.0/simple/inventory.xml";
                serverRoot = installDir;
                binPath = exePath;
                configurationName = DEPLOYMENT_SIMPLE_WITH_RESOLVABLE_SERVERNAMES;
            }
            
            @Override
            public void beforeTestSetup(TestSetup testSetup) throws Throwable {
                testSetup.withApacheSetup().init();
                ApacheServerComponent component = testSetup.withApacheSetup().getExecutionUtil().getServerComponent();
                ApacheDirectiveTree config = component.loadParser();
                config = RuntimeApacheConfiguration.extract(config, component.getCurrentProcessInfo(), component.getCurrentBinaryInfo(), component.getModuleNames(), false);

                DeploymentConfig deployConfig = testSetup.getDeploymentConfig();
                
                VirtualHostLegacyResourceKeyUtil keyUtil = new VirtualHostLegacyResourceKeyUtil(component, config);
                
                defaultOverrides = new HashMap<String, String>();
                defaultOverrides.put("main.rhq3.resource.key", keyUtil.getRHQ3SNMPLikeResourceKey(keyUtil.getRHQ3NonSNMPLegacyMainServerResourceKey()));
                
                if (deployConfig.vhost1 != null) {
                    String rhq3Key = keyUtil.getRHQ3NonSNMPLegacyVirtualHostResourceKey(deployConfig.vhost1.getVHostSpec());
                    defaultOverrides.put("vhost1.rhq3.resource.key", keyUtil.getRHQ3SNMPLikeResourceKey(rhq3Key));
                }
                
                if (deployConfig.vhost2 != null) {
                    String rhq3Key = keyUtil.getRHQ3NonSNMPLegacyVirtualHostResourceKey(deployConfig.vhost2.getVHostSpec());
                    defaultOverrides.put("vhost2.rhq3.resource.key", keyUtil.getRHQ3SNMPLikeResourceKey(rhq3Key));
                }
                
                if (deployConfig.vhost3 != null) {
                    String rhq3Key = keyUtil.getRHQ3NonSNMPLegacyVirtualHostResourceKey(deployConfig.vhost3.getVHostSpec());
                    defaultOverrides.put("vhost3.rhq3.resource.key", keyUtil.getRHQ3SNMPLikeResourceKey(rhq3Key));
                }
                
                if (deployConfig.vhost4 != null) {
                    String rhq3Key = keyUtil.getRHQ3NonSNMPLegacyVirtualHostResourceKey(deployConfig.vhost4.getVHostSpec());
                    defaultOverrides.put("vhost4.rhq3.resource.key", keyUtil.getRHQ3SNMPLikeResourceKey(rhq3Key));
                }
                
                testSetup.withDefaultOverrides(defaultOverrides);
            }
        });
    }
        
    @Test    
    @PluginContainerSetup(plugins = {PLATFORM_PLUGIN, AUGEAS_PLUGIN, APACHE_PLUGIN})
    @Parameters({"apache2.install.dir", "apache2.exe.path"})
    public void testWithResolvableNamesWithoutSNMP(final String installDir, final String exePath) throws Throwable {
        testUpgrade(new TestConfiguration() {
            {
                apacheConfigurationFiles = new String[]{"/full-configurations/2.2.x/simple/httpd.conf"};
                inventoryFile = "/mocked-inventories/rhq-3.0.0/simple/inventory.xml";
                serverRoot = installDir;
                binPath = exePath;
                configurationName = DEPLOYMENT_SIMPLE_WITH_RESOLVABLE_SERVERNAMES;                     
                upgradeShouldSucceed = false;
            }
            
            @Override
            public void beforeTestSetup(TestSetup testSetup) throws Throwable {
                testSetup.withApacheSetup().init();
                ApacheServerComponent component = testSetup.withApacheSetup().getExecutionUtil().getServerComponent();
                ApacheDirectiveTree config = component.loadParser();
                config = RuntimeApacheConfiguration.extract(config, component.getCurrentProcessInfo(), component.getCurrentBinaryInfo(), component.getModuleNames(), false);

                DeploymentConfig deployConfig = testSetup.getDeploymentConfig();
                
                VirtualHostLegacyResourceKeyUtil keyUtil = new VirtualHostLegacyResourceKeyUtil(component, config);
                
                defaultOverrides = new HashMap<String, String>();
                defaultOverrides.put("main.rhq3.resource.key", keyUtil.getRHQ3NonSNMPLegacyMainServerResourceKey());
                
                if (deployConfig.vhost1 != null) {                    
                    defaultOverrides.put("vhost1.rhq3.resource.key", keyUtil.getRHQ3NonSNMPLegacyVirtualHostResourceKey(deployConfig.vhost1.getVHostSpec()));
                }
                
                if (deployConfig.vhost2 != null) {
                    defaultOverrides.put("vhost2.rhq3.resource.key", keyUtil.getRHQ3NonSNMPLegacyVirtualHostResourceKey(deployConfig.vhost2.getVHostSpec()));
                }
                
                if (deployConfig.vhost3 != null) {
                    defaultOverrides.put("vhost3.rhq3.resource.key", keyUtil.getRHQ3NonSNMPLegacyVirtualHostResourceKey(deployConfig.vhost3.getVHostSpec()));
                }
                
                if (deployConfig.vhost4 != null) {
                    defaultOverrides.put("vhost4.rhq3.resource.key", keyUtil.getRHQ3NonSNMPLegacyVirtualHostResourceKey(deployConfig.vhost4.getVHostSpec()));
                }
                
                testSetup.withDefaultOverrides(defaultOverrides);
            }

            /**
             * Do our own tests here, because the test method won't do much, since
             * we told it that the upgrade won't succeed.
             */
            @Override
            public void beforeTests(TestSetup setup) throws Throwable {
                //ok, now we should see the resources upgraded in the fake server inventory.
                ResourceType serverResourceType = findApachePluginResourceTypeByName("Apache HTTP Server");
                ResourceType vhostResourceType = findApachePluginResourceTypeByName("Apache Virtual Host");
        
                Set<Resource> servers = setup.getFakeInventory().findResourcesByType(serverResourceType);

                assertEquals(servers.size(), 1, "There should be exactly one apache server discovered.");
        
                Resource server = servers.iterator().next();
        
                String expectedResourceKey = ApacheServerDiscoveryComponent.formatResourceKey(this.serverRoot, this.serverRoot
                    + "/conf/httpd.conf");
        
                assertEquals(server.getResourceKey(), expectedResourceKey,
                    "The server resource key doesn't seem to be upgraded.");
        
                Set<Resource> vhosts = setup.getFakeInventory().findResourcesByType(vhostResourceType);
        
                assertEquals(vhosts.size(), 4, "Unexpected number of vhosts discovered found");

                //let's check that the main vhost has a an upgrade error attached to it
                ApacheServerComponent component = setup.withApacheSetup().getExecutionUtil().getServerComponent();
                ApacheDirectiveTree config = component.loadParser();
                config = RuntimeApacheConfiguration.extract(config, component.getCurrentProcessInfo(), component.getCurrentBinaryInfo(), component.getModuleNames(), false);
                VirtualHostLegacyResourceKeyUtil keyUtil = new VirtualHostLegacyResourceKeyUtil(component, config);

                String mainVhostRK = keyUtil.getRHQ3NonSNMPLegacyMainServerResourceKey();
                
                Resource mainVhost = null;
                for(Resource r : vhosts) {
                    if (mainVhostRK.equals(r.getResourceKey())) {
                        mainVhost = r;
                        break;
                    }
                }
                
                assertNotNull(mainVhost, "Couldn't find the main vhost with the expected resource key '" + mainVhostRK + "'.");
                
                List<ResourceError> errors = mainVhost.getResourceErrors(ResourceErrorType.UPGRADE);
                assertNotNull(errors, "The main vhost doesn't have any upgrade errors.");
                assertEquals(errors.size(), 1, "There should be exactly one upgrade error on the main vhost.");
            }
        });
    }
    
}
