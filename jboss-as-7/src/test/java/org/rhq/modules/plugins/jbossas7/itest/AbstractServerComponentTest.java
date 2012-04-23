/*
 * RHQ Management Platform
 * Copyright (C) 2012 Red Hat, Inc.
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
package org.rhq.modules.plugins.jbossas7.itest;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.testng.Assert;

import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.domain.resource.InventoryStatus;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.pc.inventory.InventoryManager;
import org.rhq.core.pc.inventory.ResourceContainer;
import org.rhq.core.pluginapi.configuration.MapPropertySimpleWrapper;
import org.rhq.core.pluginapi.util.FileUtils;
import org.rhq.core.pluginapi.util.StartScriptConfiguration;
import org.rhq.core.system.ProcessInfo;
import org.rhq.core.system.SystemInfo;
import org.rhq.core.system.SystemInfoFactory;

import static org.testng.Assert.*;

/**
 * The base class for the integration tests for the two AS7 server types.
 *
 * @author Ian Springer
 */
public abstract class AbstractServerComponentTest extends AbstractJBossAS7PluginTest {

    public static final File JBOSS_HOME = new File(FileUtils.getCanonicalPath(System.getProperty("jboss7.home")));

    private static final Map<String, String> EAP6_VERSION_TO_AS7_VERSION_MAP = new HashMap<String, String>();
    static {
        EAP6_VERSION_TO_AS7_VERSION_MAP.put("6.0.0.Beta1", "7.1.0.Final-redhat-1");
        EAP6_VERSION_TO_AS7_VERSION_MAP.put("6.0.0.ER4", "7.1.1.Final-redhat-1");
        EAP6_VERSION_TO_AS7_VERSION_MAP.put("6.0.0.ER5", "7.1.1.Final-redhat-1");
    }

    private static final String RELEASE_VERSION_TRAIT_NAME = "_skm:release-version";

    private static final String SHUTDOWN_OPERATION_NAME = "shutdown";
    private static final String START_OPERATION_NAME = "start";

    protected abstract ResourceType getServerResourceType();

    protected abstract String getServerResourceKey();

    protected Resource getServerResource() {
        Resource platform = this.pluginContainer.getInventoryManager().getPlatform();
        return getResourceByTypeAndKey(platform, getServerResourceType(), getServerResourceKey());
    }

    public void testAutoDiscovery() throws Exception {
        Resource platform = this.pluginContainer.getInventoryManager().getPlatform();
        assertNotNull(platform);
        assertEquals(platform.getInventoryStatus(), InventoryStatus.COMMITTED);

        assertNotNull(getServerResource(),
                getServerResourceType() + " Resource with key [" + getServerResourceKey() + "] was not discovered.");
        System.out.println("\n===== Discovered: " + getServerResource());
        Configuration pluginConfig = getServerResource().getPluginConfiguration();
        System.out.println("---------- " + pluginConfig.toString(true));
        validatePluginConfiguration(pluginConfig);
    }

    protected void validatePluginConfiguration(Configuration pluginConfig) {
        // "hostname" prop
        String hostname = pluginConfig.getSimpleValue("hostname", null);
        String expectedHostname = System.getProperty(getBindAddressSystemPropertyName());
        assertEquals(hostname, expectedHostname, "Plugin config prop [hostname].");

        // "port" prop
        String portString = pluginConfig.getSimpleValue("port", null);
        Integer port = (portString != null) ? Integer.valueOf(portString) : null;
        String portOffsetString = System.getProperty(getPortOffsetSystemPropertyName());
        int portOffset = (portOffsetString != null) ? Integer.valueOf(portOffsetString) : 0;
        Integer expectedPort = portOffset + 9990;        
        assertEquals(port, expectedPort, "Plugin config prop [port].");

        // "startScriptEnv" prop
        PropertySimple startScriptEnvProp = pluginConfig.getSimple("startScriptEnv");
        MapPropertySimpleWrapper startScriptEnvPropWrapper = new MapPropertySimpleWrapper(startScriptEnvProp);
        Map<String,String> env = startScriptEnvPropWrapper.getValue();
        Assert.assertTrue(env.size() <= 4, env.toString());

        String javaHome = env.get("JAVA_HOME");
        Assert.assertNotNull(javaHome);
        Assert.assertTrue(new File(javaHome).isDirectory());

        String path = env.get("PATH");
        Assert.assertNotNull(path);
        String[] pathElements = path.split(File.pathSeparator);
        Assert.assertTrue(pathElements.length >= 1);
        Assert.assertTrue(new File(pathElements[0]).isDirectory());

        String ldLibraryPath = env.get("LD_LIBRARY_PATH");
        Assert.assertNotNull(ldLibraryPath);
        String[] ldLibraryPathElements = ldLibraryPath.split(File.pathSeparator);
        Assert.assertTrue(ldLibraryPathElements.length >= 1);
        Assert.assertTrue(new File(ldLibraryPathElements[0]).isDirectory());

        // Only domain sets JBOSS_HOME. TODO: move this into DomainServerComponentTest subclass
        String jbossHome = env.get("JBOSS_HOME");
        if (jbossHome != null) {
            Assert.assertTrue(new File(jbossHome).isDirectory());
        }
    }

    protected abstract String getBindAddressSystemPropertyName();
    
    protected abstract String getPortOffsetSystemPropertyName();

    public void testReleaseVersionTrait() throws Exception {
        String releaseVersion = collectTrait(getServerResource(), RELEASE_VERSION_TRAIT_NAME);
        String as7Version = System.getProperty( "as7.version" );
        String expectedReleaseVersion;
        if (as7Version.startsWith("6.")) {
            // EAP6
            expectedReleaseVersion = EAP6_VERSION_TO_AS7_VERSION_MAP.get(as7Version);
            if (expectedReleaseVersion == null) {
                throw new Exception("No AS7 version mapping is defined for EAP6 version [" + as7Version + "].");
            }
        } else {
            // AS7
            expectedReleaseVersion = as7Version;
        }
        assertEquals(releaseVersion, expectedReleaseVersion,
                "Unexpected value for trait [" + RELEASE_VERSION_TRAIT_NAME + "].");
    }

    public void testShutdownAndStartOperations() throws Exception {
        // First make sure the server is up.
        AvailabilityType avail = getAvailability(getServerResource());
        assertEquals(avail, AvailabilityType.UP);

        // Now shut it down using the Shutdown op and make sure it has gone down.
        invokeOperationAndAssertSuccess(getServerResource(), SHUTDOWN_OPERATION_NAME, null);
        avail = getAvailability(getServerResource());
        assertEquals(avail, AvailabilityType.DOWN);

        // Before restarting it, add some stuff to the 'startScriptEnv' and 'startScriptArgs' props so we can verify
        // they are used correctly by the Start op.
        Configuration pluginConfig = getServerResource().getPluginConfiguration();
        StartScriptConfiguration startScriptConfig = new StartScriptConfiguration(pluginConfig);

        // Add a var to the start script env.
        Map<String, String> env = startScriptConfig.getStartScriptEnv();
        env.put("foo", "bar");
        startScriptConfig.setStartScriptEnv(env);

        // Add an arg to the start script args.
        List<String> args = startScriptConfig.getStartScriptArgs();
        args.add("-Dfoo=bar");
        startScriptConfig.setStartScriptArgs(args);

        // Restart the server ResourceComponent so it picks up the changes we just made to the plugin config.
        InventoryManager inventoryManager = this.pluginContainer.getInventoryManager();
        inventoryManager.deactivateResource(getServerResource());
        ResourceContainer serverContainer = inventoryManager.getResourceContainer(getServerResource());
        inventoryManager.activateResource(getServerResource(), serverContainer, true);

        // Finally restart it using the Start op and make sure it has come back up.
        invokeOperationAndAssertSuccess(getServerResource(), START_OPERATION_NAME, null);
        avail = getAvailability(getServerResource());
        assertEquals(avail, AvailabilityType.UP);

        List<ProcessInfo> processes = getServerProcesses();
        //Assert.assertEquals(processes.size(), 1, getCommandLines(processes).toString());
        ProcessInfo serverProcess = processes.get(0);
        Map<String, String> processEnv = serverProcess.getEnvironmentVariables();
        assertEquals(processEnv.get("foo"), "bar", processEnv.toString());

        List<String> processArgs = Arrays.asList(serverProcess.getCommandLine());
        assertTrue(processArgs.contains("-Dfoo=bar"), processArgs.toString());
    }

    public void killServerProcesses() {
        List<ProcessInfo> processes = getServerProcesses();
        System.out.println("\n=== Killing " + processes.size() + " " + getServerResourceType() + " processes...");
        Sigar sigar = new Sigar();
        for (ProcessInfo process : processes) {
            System.out.println("====== Killing process with pid [" + process.getPid() + "] and command line ["
                    + Arrays.toString(process.getCommandLine()) + "]...");
            try {
                sigar.kill(process.getPid(), "KILL");
            } catch (SigarException e) {
                System.err.println("Failed to kill " + process + ": " + e);
            }
        }
        sigar.close();
        processes = getServerProcesses();
        Assert.assertEquals(processes.size(), 0,
                "Failed to kill " + processes.size() + " " + getServerResourceType() + " processes: " + processes);
    }

    protected abstract int getPortOffset();

    private List<ProcessInfo> getServerProcesses() {
        SystemInfo systemInfo = SystemInfoFactory.createSystemInfo();
        return systemInfo.getProcesses("arg|*|match=org\\.jboss\\.as\\..+,arg|-Djboss.socket.binding.port-offset|match="
                    + getPortOffset());
    }

}


