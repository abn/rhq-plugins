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
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jmock.Expectations;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import org.rhq.core.clientapi.agent.metadata.PluginMetadataParser;
import org.rhq.core.clientapi.descriptor.AgentPluginDescriptorUtil;
import org.rhq.core.clientapi.descriptor.plugin.PluginDescriptor;
import org.rhq.core.clientapi.server.discovery.InventoryReport;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.discovery.AvailabilityReport;
import org.rhq.core.domain.resource.InventoryStatus;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.ResourceError;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.pc.ServerServices;
import org.rhq.core.pc.upgrade.FakeServerInventory;
import org.rhq.core.pluginapi.inventory.DiscoveredResourceDetails;
import org.rhq.core.pluginapi.inventory.PluginContainerDeployment;
import org.rhq.core.pluginapi.inventory.ResourceContext;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryComponent;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryContext;
import org.rhq.core.system.ProcessInfo;
import org.rhq.core.system.SystemInfo;
import org.rhq.core.system.SystemInfoFactory;
import org.rhq.plugins.apache.parser.ApacheDirectiveTree;
import org.rhq.plugins.apache.util.ApacheDeploymentUtil;
import org.rhq.plugins.apache.util.ApacheDeploymentUtil.DeploymentConfig;
import org.rhq.plugins.apache.util.ApacheExecutionUtil;
import org.rhq.plugins.apache.util.HttpdAddressUtility;
import org.rhq.plugins.apache.util.MockApacheBinaryInfo;
import org.rhq.plugins.apache.util.MockProcessInfo;
import org.rhq.plugins.platform.PlatformComponent;
import org.rhq.test.ObjectCollectionSerializer;
import org.rhq.test.TokenReplacingReader;
import org.rhq.test.pc.PluginContainerSetup;
import org.rhq.test.pc.PluginContainerTest;

/**
 * 
 *
 * @author Lukas Krejci
 */
@Test(groups = "apache-integration-tests")
public class UpgradeTest extends PluginContainerTest {

    private static final String PLATFORM_PLUGIN = "file:target/itest/plugins/rhq-platform-plugin-for-apache-test.jar";
    private static final String AUGEAS_PLUGIN = "file:target/itest/plugins/rhq-augeas-plugin-for-apache-test.jar";
    private static final String APACHE_PLUGIN = "file:target/itest/plugins/rhq-apache-plugin-for-apache-test.jar";

    private static final String DEPLOYMENT_SIMPLE_WITH_RESOLVABLE_SERVERNAMES = "simpleWithResolvableServerNames";

    private List<ResourceType> resourceTypesInApachePlugin;

    private Resource platform;

    private class TestSetup {
        private String configurationName;
        private FakeServerInventory fakeInventory = new FakeServerInventory();
        private String inventoryFile;
        private Resource platform;
        private ApacheSetup apacheSetup = new ApacheSetup();
        private DeploymentConfig deploymentConfig;

        public class ApacheSetup {
            private String serverRoot;
            private String exePath;
            private Collection<String> configurationFiles;
            private ApacheExecutionUtil execution;
            private boolean deploy = true;

            private ApacheSetup() {

            }

            public ApacheSetup withServerRoot(String serverRoot) {
                this.serverRoot = serverRoot;
                return this;
            }

            public ApacheSetup withExePath(String exePath) {
                this.exePath = exePath;
                return this;
            }

            public ApacheSetup withConfigurationFiles(String... classPathUris) {
                return withConfigurationFiles(Arrays.asList(classPathUris));
            }

            public ApacheSetup withConfigurationFiles(Collection<String> classPathUris) {
                this.configurationFiles = classPathUris;
                return this;
            }

            public ApacheSetup withDeploymentOnSetup() {
                this.deploy = true;
                return this;
            }

            public ApacheSetup withNoDeploymentOnSetup() {
                this.deploy = false;
                return this;
            }

            public ApacheExecutionUtil getExecutionUtil() {
                return execution;
            }

            public void init() throws Exception {
                File serverRootDir = new File(serverRoot);

                assertTrue(serverRootDir.exists(), "The configured server root denotes a non-existant directory: '"
                    + serverRootDir + "'.");

                File confDir = new File(serverRootDir, "conf");

                assertTrue(confDir.exists(),
                    "The configured server root denotes a directory that doesn't have a 'conf' subdirectory. This is unexpected.");

                String snmpHost = null;
                int snmpPort = 0;
                String pingUrl = null;

                if (configurationName != null) {
                    if (deploy) {
                        ApacheDeploymentUtil.deployConfiguration(confDir, configurationFiles, deploymentConfig);
                    }

                    HttpdAddressUtility.Address addr = deploymentConfig.mainServer.address1;
                    HttpdAddressUtility.Address addrToUse = new HttpdAddressUtility.Address(null, null,
                        HttpdAddressUtility.Address.NO_PORT_SPECIFIED_VALUE);
                    addrToUse.scheme = addr.scheme == null ? "http" : addr.scheme;
                    addrToUse.host = addr.host == null ? "localhost" : addr.host;
                    addrToUse.port = addr.port;
                    pingUrl = addrToUse.toString();

                    snmpHost = deploymentConfig.snmpHost;
                    snmpPort = deploymentConfig.snmpPort;
                }

                execution = new ApacheExecutionUtil(findApachePluginResourceTypeByName("Apache HTTP Server"),
                    serverRoot, exePath, confDir.getAbsolutePath() + File.separatorChar + "httpd.conf", pingUrl,
                    snmpHost, snmpPort);
                execution.init();
            }

            private void doSetup() throws Exception {
                init();
                execution.invokeOperation("restart", "start");
            }

            public TestSetup setup() throws Exception {
                return TestSetup.this.setup();
            }
        }

        public TestSetup(String configurationName) {
            this.configurationName = configurationName;
            deploymentConfig = ApacheDeploymentUtil.getDeploymentConfigurationFromSystemProperties(configurationName);
        }

        public TestSetup withInventoryFrom(String classPathUri) {
            inventoryFile = classPathUri;
            return this;
        }

        public TestSetup withPlatformResource(Resource platform) {
            this.platform = platform;
            return this;
        }

        public ApacheSetup withApacheSetup() {
            return apacheSetup;
        }

        public TestSetup withDefaultExpectations() throws Exception {
            context.checking(new Expectations() {
                {
                    addDefaultExceptations(this);
                }
            });

            return this;
        }

        @SuppressWarnings("unchecked")
        public void addDefaultExceptations(Expectations expectations) throws Exception {
            ServerServices ss = getCurrentPluginContainerConfiguration().getServerServices();

            expectations.allowing(ss.getDiscoveryServerService()).mergeInventoryReport(
                expectations.with(Expectations.any(InventoryReport.class)));
            expectations.will(fakeInventory.mergeInventoryReport(InventoryStatus.COMMITTED));

            expectations.allowing(ss.getDiscoveryServerService()).upgradeResources(
                expectations.with(Expectations.any(Set.class)));
            expectations.will(fakeInventory.upgradeResources());

            expectations.allowing(ss.getDiscoveryServerService()).getResources(
                expectations.with(Expectations.any(Set.class)), expectations.with(Expectations.any(boolean.class)));
            expectations.will(fakeInventory.getResources());

            expectations.allowing(ss.getDiscoveryServerService()).setResourceError(expectations.with(Expectations.any(ResourceError.class)));
            expectations.will(fakeInventory.setResourceError());
            
            expectations.allowing(ss.getDiscoveryServerService()).mergeAvailabilityReport(
                expectations.with(Expectations.any(AvailabilityReport.class)));

            expectations.allowing(ss.getDiscoveryServerService()).postProcessNewlyCommittedResources(
                expectations.with(Expectations.any(Set.class)));

            expectations.allowing(ss.getDiscoveryServerService()).clearResourceConfigError(
                expectations.with(Expectations.any(int.class)));
            
            expectations.ignoring(ss.getBundleServerService());
            expectations.ignoring(ss.getConfigurationServerService());
            expectations.ignoring(ss.getContentServerService());
            expectations.ignoring(ss.getCoreServerService());
            expectations.ignoring(ss.getEventServerService());
            expectations.ignoring(ss.getMeasurementServerService());
            expectations.ignoring(ss.getOperationServerService());
            expectations.ignoring(ss.getResourceFactoryServerService());
        }

        public FakeServerInventory getFakeInventory() {
            return fakeInventory;
        }

        public DeploymentConfig getDeploymentConfig() {
            return deploymentConfig;
        }
        
        public TestSetup setup() throws Exception {
            apacheSetup.doSetup();

            Map<String, String> replacements = deploymentConfig.getTokenReplacements();
            replacements.put("server.root", apacheSetup.serverRoot);
            replacements.put("exe.path", apacheSetup.exePath);
            replacements.put("localhost", determineLocalhost());

            HttpdAddressUtility addressUtility = apacheSetup.getExecutionUtil().getServerComponent()
                .getAddressUtility();
            ApacheDirectiveTree runtimeConfig = apacheSetup.getExecutionUtil().getRuntimeConfiguration();

            replacements.put("snmp.identifier",
                addressUtility.getHttpdInternalMainServerAddressRepresentation(runtimeConfig).toString(false, false));

            String vhost1Address = deploymentConfig.vhost1 == null ? null : deploymentConfig.vhost1.address1.toString(
                false, false);
            String vhost2Address = deploymentConfig.vhost2 == null ? null : deploymentConfig.vhost2.address1.toString(
                false, false);
            String vhost3Address = deploymentConfig.vhost3 == null ? null : deploymentConfig.vhost3.address1.toString(
                false, false);
            String vhost4Address = deploymentConfig.vhost4 == null ? null : deploymentConfig.vhost4.address1.toString(
                false, false);

            if (vhost1Address != null) {
                replacements.put(
                    "vhost1.snmp.identifier",
                    addressUtility.getHttpdInternalVirtualHostAddressRepresentation(runtimeConfig, vhost1Address,
                        deploymentConfig.vhost1.getServerName()).toString(false, false));
            }

            if (vhost2Address != null) {
                replacements.put(
                    "vhost2.snmp.identifier",
                    addressUtility.getHttpdInternalVirtualHostAddressRepresentation(runtimeConfig, vhost2Address,
                        deploymentConfig.vhost2.getServerName()).toString(false, false));
            }

            if (vhost3Address != null) {
                replacements.put(
                    "vhost3.snmp.identifier",
                    addressUtility.getHttpdInternalVirtualHostAddressRepresentation(runtimeConfig, vhost3Address,
                        deploymentConfig.vhost3.getServerName()).toString(false, false));
            }

            if (vhost4Address != null) {
                replacements.put(
                    "vhost4.snmp.identifier",
                    addressUtility.getHttpdInternalVirtualHostAddressRepresentation(runtimeConfig, vhost4Address,
                        deploymentConfig.vhost4.getServerName()).toString(false, false));
            }

            InputStream dataStream = getClass().getResourceAsStream(inventoryFile);

            Reader rdr = new TokenReplacingReader(new InputStreamReader(dataStream), replacements);

            @SuppressWarnings("unchecked")
            List<Resource> inventory = (List<Resource>) new ObjectCollectionSerializer().deserialize(rdr);

            //fix up the parent relationships, because they might not be reconstructed correctly by 
            //JAXB - we're missing XmlID and XmlIDRef annotations in our model
            fixupParent(null, inventory);

            fakeInventory.prepopulateInventory(platform, inventory);

            return this;
        }

        private void fixupParent(Resource parent, Collection<Resource> children) {
            for (Resource child : children) {
                child.setParentResource(parent);
                if (child.getChildResources() != null) {
                    fixupParent(child, child.getChildResources());
                }
            }
        }

        private String determineLocalhost() {
            try {
                return InetAddress.getLocalHost().getCanonicalHostName();
            } catch (UnknownHostException e) {
                return "127.0.0.1";
            }
        }
    }

    @BeforeClass
    public void parseResourceTypesFromApachePlugin() throws Exception {
        resourceTypesInApachePlugin = getResourceTypesInPlugin(APACHE_PLUGIN);
        platform = discoverPlatform();
    }

    @AfterClass
    @Parameters({ "apache2.install.dir", "apache2.exe.path" })
    public void shutdownApache(String apacheInstallationDirectory, String exePath) throws Exception {

        //it really doesn't matter which configuration i use here
        TestSetup.ApacheSetup apacheSetup = new TestSetup(DEPLOYMENT_SIMPLE_WITH_RESOLVABLE_SERVERNAMES)
            .withApacheSetup().withServerRoot(apacheInstallationDirectory).withExePath(exePath)
            .withNoDeploymentOnSetup();
        apacheSetup.init();
        apacheSetup.getExecutionUtil().invokeOperation("stop");
    }

    @PluginContainerSetup(plugins = { PLATFORM_PLUGIN, AUGEAS_PLUGIN, APACHE_PLUGIN })
    @Parameters({ "apache2.install.dir", "apache2.exe.path" })
    public void testSimpleConfigurationWithResolvableServerNames_Apache2_upgradeFromRHQ1_3(
        String apacheInstallationDirectory, String exePath) throws Exception {

        final TestSetup setup = new TestSetup(DEPLOYMENT_SIMPLE_WITH_RESOLVABLE_SERVERNAMES)
            .withInventoryFrom("/mocked-inventories/rhq-1.3.x/includes/inventory.xml").withPlatformResource(platform)
            .withDefaultExpectations().withApacheSetup()
            .withConfigurationFiles("/full-configurations/simple/httpd.conf", "/snmpd.conf", "/mime.types")
            .withServerRoot(apacheInstallationDirectory).withExePath(exePath).setup();

        startConfiguredPluginContainer();

        //ok, now we should see the resources upgraded in the fake server inventory.
        ResourceType serverResourceType = findApachePluginResourceTypeByName("Apache HTTP Server");
        ResourceType vhostResourceType = findApachePluginResourceTypeByName("Apache Virtual Host");

        Set<Resource> servers = setup.getFakeInventory().findResourcesByType(serverResourceType);

        assertTrue(servers.size() == 1, "There should be exactly one apache server discovered.");

        Resource server = servers.iterator().next();

        String expectedResourceKey = ApacheServerDiscoveryComponent.formatResourceKey(apacheInstallationDirectory,
            apacheInstallationDirectory + "/conf/httpd.conf");

        assertEquals(server.getResourceKey(), expectedResourceKey,
            "The server resource key doesn't seem to be upgraded.");

        Set<Resource> vhosts = setup.getFakeInventory().findResourcesByType(vhostResourceType);
        
        assertTrue(vhosts.size() == 5, "There should be 5 vhosts discovered but found " + vhosts.size());
        
        List<String> expectedResourceKeys = new ArrayList<String>(5);
        
        DeploymentConfig dc = setup.getDeploymentConfig();

        expectedResourceKeys.add(ApacheVirtualHostServiceComponent.MAIN_SERVER_RESOURCE_KEY);
        expectedResourceKeys.add(ApacheVirtualHostServiceDiscoveryComponent.createResourceKey(dc.vhost1.getServerName(), dc.vhost1.getAddresses()));
        expectedResourceKeys.add(ApacheVirtualHostServiceDiscoveryComponent.createResourceKey(dc.vhost2.getServerName(), dc.vhost2.getAddresses()));
        expectedResourceKeys.add(ApacheVirtualHostServiceDiscoveryComponent.createResourceKey(dc.vhost3.getServerName(), dc.vhost3.getAddresses()));
        expectedResourceKeys.add(ApacheVirtualHostServiceDiscoveryComponent.createResourceKey(dc.vhost4.getServerName(), dc.vhost4.getAddresses()));
        
        for(Resource vhost : vhosts) {
            assertTrue(expectedResourceKeys.contains(vhost.getResourceKey()), "Unexpected virtual host resource key: '" + vhost.getResourceKey() + "'.");
        }
    }

    private ResourceType findApachePluginResourceTypeByName(String resourceTypeName) {
        for (ResourceType rt : resourceTypesInApachePlugin) {
            if (resourceTypeName.equals(rt.getName())) {
                return rt;
            }
        }

        return null;
    }

    private static List<ResourceType> getResourceTypesInPlugin(String pluginUri) throws Exception {
        PluginDescriptor descriptor = AgentPluginDescriptorUtil.loadPluginDescriptorFromUrl(new URI(pluginUri).toURL());
        PluginMetadataParser parser = new PluginMetadataParser(descriptor,
            Collections.<String, PluginMetadataParser> emptyMap());

        return parser.getAllTypes();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Resource discoverPlatform() throws Exception {
        PluginDescriptor descriptor = AgentPluginDescriptorUtil.loadPluginDescriptorFromUrl(new URI(PLATFORM_PLUGIN)
            .toURL());
        PluginMetadataParser parser = new PluginMetadataParser(descriptor,
            Collections.<String, PluginMetadataParser> emptyMap());

        List<ResourceType> platformTypes = parser.getAllTypes();

        for (ResourceType rt : platformTypes) {
            Class discoveryClass = Class.forName(parser.getDiscoveryComponentClass(rt));

            ResourceDiscoveryComponent discoveryComponent = (ResourceDiscoveryComponent) discoveryClass.newInstance();

            ResourceDiscoveryContext context = new ResourceDiscoveryContext(rt, null, null,
                SystemInfoFactory.createSystemInfo(), null, null, PluginContainerDeployment.AGENT);

            Set<DiscoveredResourceDetails> results = discoveryComponent.discoverResources(context);

            if (!results.isEmpty()) {
                DiscoveredResourceDetails details = results.iterator().next();

                Resource platform = new Resource();

                platform.setDescription(details.getResourceDescription());
                platform.setResourceKey(details.getResourceKey());
                platform.setName(details.getResourceName());
                platform.setVersion(details.getResourceVersion());
                platform.setPluginConfiguration(details.getPluginConfiguration());
                platform.setResourceType(rt);
                platform.setUuid(UUID.randomUUID().toString());
                platform.setId(1);

                return platform;
            }
        }

        return null;
    }
}
