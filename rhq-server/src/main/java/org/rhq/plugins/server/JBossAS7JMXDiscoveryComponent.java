package org.rhq.plugins.server;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.pluginapi.inventory.ClassLoaderFacet;
import org.rhq.core.pluginapi.inventory.DiscoveredResourceDetails;
import org.rhq.core.pluginapi.inventory.ResourceComponent;
import org.rhq.core.pluginapi.inventory.ResourceContext;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryComponent;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryContext;

/**
 * Just returns the singleton RHQ Server Subsystems parent resource as a container for the
 * subsystem resources.
 * 
 * @author Jay Shaughnessy
 * @author John Mazzitelli
 */
public class JBossAS7JMXDiscoveryComponent<T extends ResourceComponent<JBossAS7JMXComponent<?>>> implements
    ResourceDiscoveryComponent<T>, ClassLoaderFacet<ResourceComponent<JBossAS7JMXComponent<?>>> {

    private Log log = LogFactory.getLog(JBossAS7JMXDiscoveryComponent.class);

    @Override
    public List<URL> getAdditionalClasspathUrls(
        ResourceDiscoveryContext<ResourceComponent<JBossAS7JMXComponent<?>>> context, DiscoveredResourceDetails details)
        throws Exception {

        Configuration pluginConfig = details.getPluginConfiguration();
        String clientJarLocation = pluginConfig.getSimpleValue(JBossAS7JMXComponent.PLUGIN_CONFIG_CLIENT_JAR_LOCATION);
        if (clientJarLocation == null) {
            log.warn("Missing the client jar location - cannot connect to the JBossAS instance: "
                + details.getResourceKey());
            return null;
        }

        File clientJarDir = new File(clientJarLocation);
        if (!clientJarDir.isDirectory()) {
            log.warn("The client jar location [" + clientJarDir.getAbsolutePath()
                + "] does not exist - cannot connect to the JBossAS instance: " + details.getResourceKey());
            return null;
        }

        ArrayList<URL> clientJars = new ArrayList<URL>();
        for (File clientJarFile : clientJarDir.listFiles()) {
            if (clientJarFile.getName().endsWith(".jar")) {
                clientJars.add(clientJarFile.toURI().toURL());
            }
        }

        if (clientJars.size() == 0) {
            log.warn("The client jar location [" + clientJarDir.getAbsolutePath()
                + "] is missing client jars - cannot connect to the JBossAS instance: " + details.getResourceKey());
            return null;
        }

        return clientJars;
    }

    @Override
    public Set<DiscoveredResourceDetails> discoverResources(ResourceDiscoveryContext<T> context) {

        HashSet<DiscoveredResourceDetails> result = new HashSet<DiscoveredResourceDetails>();
        ResourceContext<?> parentResourceContext = context.getParentResourceContext();
        Configuration parentPluginConfig = parentResourceContext.getPluginConfiguration();

        // TODO: use additional methods to look around for other places where this can be find
        // for example, we might be able to look in the /modules directory for some jars to
        // use if the bin/client dir is gone.  Also, if for some reason we shouldn't use this jar,
        // we may need to instead use various additional jars required by jbossas/bin/jconsole.sh.
        File rhqServerFile = null;
        File clientJarDir = null;

        String homeDirStr = parentPluginConfig.getSimpleValue("homeDir");
        if (homeDirStr != null) {

            File homeDirFile = new File(homeDirStr);
            if (homeDirFile.exists()) {
                clientJarDir = new File(homeDirFile, "bin/client");
                if (!clientJarDir.exists()) {
                    log.warn("The client jar location [" + clientJarDir.getAbsolutePath()
                        + "] does not exist - will not be able to connect to the AS7 instance");
                }

                rhqServerFile = new File(homeDirFile, "../bin/rhq-server.sh");
            }
        }

        // If the parent is not an RHQ server then just return.
        if (null == rhqServerFile || !rhqServerFile.exists()) {
            return result;
        }

        String clientJarLocation = (clientJarDir != null) ? clientJarDir.getAbsolutePath() : null;

        String key = "RHQServerSubsystems";
        String name = "RHQ Server Subsystems";
        String version = parentResourceContext.getVersion();
        String description = "Container for RHQ Server Subsystem services";

        Configuration pluginConfig = context.getDefaultPluginConfiguration();

        String hostname = parentPluginConfig.getSimpleValue(JBossAS7JMXComponent.PLUGIN_CONFIG_HOSTNAME, "127.0.0.1");
        pluginConfig.setSimpleValue(JBossAS7JMXComponent.PLUGIN_CONFIG_HOSTNAME, hostname);

        String port = parentPluginConfig.getSimpleValue(JBossAS7JMXComponent.PLUGIN_CONFIG_PORT, "9999");
        if (!"9999".equals(port)) {
            port = String.valueOf(Integer.valueOf(port) + 9);
        }
        pluginConfig.setSimpleValue(JBossAS7JMXComponent.PLUGIN_CONFIG_PORT, port);

        String user = parentPluginConfig.getSimpleValue(JBossAS7JMXComponent.PLUGIN_CONFIG_USERNAME, "rhqadmin");
        pluginConfig.setSimpleValue(JBossAS7JMXComponent.PLUGIN_CONFIG_USERNAME, user);

        String password = parentPluginConfig.getSimpleValue(JBossAS7JMXComponent.PLUGIN_CONFIG_PASSWORD, "rhqadmin");
        pluginConfig.setSimpleValue(JBossAS7JMXComponent.PLUGIN_CONFIG_PASSWORD, password);

        pluginConfig.setSimpleValue(JBossAS7JMXComponent.PLUGIN_CONFIG_CLIENT_JAR_LOCATION, clientJarLocation);

        DiscoveredResourceDetails resource = new DiscoveredResourceDetails(context.getResourceType(), key, name,
            version, description, pluginConfig, null);

        result.add(resource);

        return result;
    }
}