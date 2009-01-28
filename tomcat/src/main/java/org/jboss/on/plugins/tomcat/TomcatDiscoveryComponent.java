/*
 * Jopr Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
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

package org.jboss.on.plugins.tomcat;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.on.plugins.tomcat.helper.TomcatConfig;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.pluginapi.inventory.DiscoveredResourceDetails;
import org.rhq.core.pluginapi.inventory.ProcessScanResult;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryComponent;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryContext;
import org.rhq.core.system.ProcessExecution;
import org.rhq.core.system.ProcessExecutionResults;
import org.rhq.core.system.ProcessInfo;
import org.rhq.core.system.SystemInfo;
import org.rhq.plugins.jmx.JMXDiscoveryComponent;

/**
 * Discovers JBoss EWS Tomcat5, Tomcat6 server instances.
 *
 * @author Jay Shaughnessy
 */
public class TomcatDiscoveryComponent implements ResourceDiscoveryComponent {
    private final Log log = LogFactory.getLog(this.getClass());

    /**
     * Indicates the version information could not be determined.
     */
    public static final String UNKNOWN_VERSION = "Unknown Version";

    /**
     * Formal name used to identify the server.
     */
    private static final String PRODUCT_NAME_EWS = "JBoss EWS";
    private static final String PRODUCT_NAME_APACHE = "Apache Tomcat";

    /**
     * Formal description of the product passed into discovered resources.
     */
    private static final String PRODUCT_DESCRIPTION_EWS = "JBoss Enterprise Web Application Server";
    private static final String PRODUCT_DESCRIPTION_APACHE = "Apache Tomcat Web Application Server";

    /**
     * Patterns used to parse out the Tomcat server version from the version script output. For details on which of these
     * patterns will be used, check {@link #determineVersion(String, org.rhq.core.system.SystemInfo)}.
     */
    private static final Pattern TOMCAT_6_VERSION_PATTERN = Pattern.compile(".*Server number:.*");
    private static final Pattern TOMCAT_5_VERSION_PATTERN = Pattern.compile(".*Version:.*");

    /** 
     * EWS Install path pattern used to distinguish from standalone Tomcat installs.
     */
    private static final Pattern EWS_PATTERN = Pattern.compile(".*ews.*tomcat[56]");

    /**
     * EWS Install path substrings used to identify EWS and/or EWS tomcat version
     */
    private static final String EWS_TOMCAT_6 = "tomcat6";
    private static final String EWS_TOMCAT_5 = "tomcat5";

    /**
     * Plugin configuration property name.
     */
    private static final String PROP_JMX_URL = "jmxUrl";

    @SuppressWarnings("unchecked")
    public Set<DiscoveredResourceDetails> discoverResources(ResourceDiscoveryContext context) {
        log.debug("Discovering Tomcat servers...");

        Set<DiscoveredResourceDetails> resources = new HashSet<DiscoveredResourceDetails>();

        // For each Tomcat process found in the context, create a resource details instance for thos
        List<ProcessScanResult> autoDiscoveryResults = context.getAutoDiscoveredProcesses();
        for (ProcessScanResult autoDiscoveryResult : autoDiscoveryResults) {
            if (log.isDebugEnabled()) {
                log.debug("Discovered potential Tomcat process: " + autoDiscoveryResult);
            }

            try {
                DiscoveredResourceDetails resource = parseTomcatProcess(context, autoDiscoveryResult);
                if (resource != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Verified Tomcat process: " + autoDiscoveryResult);
                    }

                    resources.add(resource);
                }
            } catch (Exception e) {
                log.error("Error creating discovered resource for process: " + autoDiscoveryResult, e);
            }
        }

        return resources;
    }

    /**
     * Processes a process that has been detected to be a Tomcat server process. If a standalone
     * Apache or EWS Tomcat instance return a resource ready to be returned as part of the discovery report.
     *
     * @param  context             discovery context making this call
     * @param  autoDiscoveryResult process scan being parsed for an EWS resource
     *
     * @return resource object describing the Tomcat server running in the specified process
     */
    @SuppressWarnings("unchecked")
    private DiscoveredResourceDetails parseTomcatProcess(ResourceDiscoveryContext context, ProcessScanResult autoDiscoveryResult) {
        // Pull out data from the discovery call
        ProcessInfo processInfo = autoDiscoveryResult.getProcessInfo();
        SystemInfo systemInfo = context.getSystemInformation();
        String[] commandLine = processInfo.getCommandLine();

        if (!isStandalone(commandLine)) {
            log.info("Ignoring embedded tomcat instance with following command line, ignoring: " + Arrays.toString(commandLine));
            return null;
        }

        String[] classpath = determineClassPath(commandLine);
        String installationPath = determineInstallationPath(classpath);
        TomcatConfig tomcatConfig = parseTomcatConfig(installationPath);

        // Create pieces necessary for the resource creation
        String resourceVersion = determineVersion(installationPath, systemInfo);
        String hostname = systemInfo.getHostname();
        boolean isEWS = isEWS(installationPath);
        String productName = isEWS ? PRODUCT_NAME_EWS : PRODUCT_NAME_APACHE;
        String productDescription = isEWS ? PRODUCT_DESCRIPTION_EWS : PRODUCT_DESCRIPTION_APACHE;
        String resourceName = ((hostname == null) ? "" : (hostname + " ")) + productName + " " + resourceVersion + " ("
            + ((tomcatConfig.getAddress() == null) ? "" : (tomcatConfig.getAddress() + ":")) + tomcatConfig.getPort() + ")";
        String resourceKey = installationPath;

        Configuration pluginConfiguration = populatePluginConfiguration(installationPath, commandLine);

        DiscoveredResourceDetails resource = new DiscoveredResourceDetails(context.getResourceType(), resourceKey, resourceName, resourceVersion, productDescription, pluginConfiguration, processInfo);

        return resource;
    }

    /**
     * Check from the command line if this is a standalone tomcat
     *
     * @param  commandLine
     *
     * @return
     */
    private boolean isStandalone(String[] commandLine) {
        for (String item : commandLine) {
            if (item.contains("catalina.home")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check from the command line if this is an EWS tomcat
     *
     * @param  commandLine
     *
     * @return
     */
    private boolean isEWS(String installationPath) {
        boolean isEws = EWS_PATTERN.matcher(installationPath).matches();

        // The match will succeed if EWS is installed and the installation directory is not renamed.
        if (!isEws) {
            // still match in a weaker way if the install directory still ends with one of the EWS wrapper directories.
            isEws = (isEWSTomcat5(installationPath) || isEWSTomcat6(installationPath));
        }

        return isEws;
    }

    private boolean isEWSTomcat5(String installationPath) {
        return ((null != installationPath) && installationPath.endsWith(EWS_TOMCAT_5));
    }

    private boolean isEWSTomcat6(String installationPath) {
        return ((null != installationPath) && installationPath.endsWith(EWS_TOMCAT_6));
    }

    /**
     * Searches through the command line arguments for the classpath setting.
     *
     * @param  arguments command line arguments passed to the java process
     *
     * @return array of entries in the classpath; <code>null</code> if the classpath is not specified using -cp or
     *         -classpath
     */
    private String[] determineClassPath(String[] arguments) {
        for (int ii = 0; ii < (arguments.length - 1); ii++) {
            String arg = arguments[ii];
            if ("-cp".equals(arg) || "-classpath".equals(arg)) {
                String[] classpath = arguments[ii + 1].split(File.pathSeparator);
                return classpath;
            }
        }

        return null;
    }

    /**
     * Looks for a known JAR in the classpath to determine the installation path of the Tomcat instance.
     *
     * @param  classpath classpath of the java call
     *
     * @return
     */
    private String determineInstallationPath(String[] classpath) {
        for (String classpathEntry : classpath) {
            if (classpathEntry.endsWith("bootstrap.jar")) {
                // Directory of bootstrap.jar
                String installationPath = classpathEntry.substring(0, classpathEntry.lastIndexOf(File.separatorChar));

                // bootstrap.jar is in the /bin directory, so move one directory up
                installationPath = installationPath.substring(0, installationPath.lastIndexOf(File.separatorChar));

                return installationPath;
            }
        }

        return null;
    }

    /**
     * Parses the tomcat config file (server.xml) and returns a value object with access to its relevant contents.
     *
     * @param  installationPath installation path of the tomcat instance
     *
     * @return value object; <code>null</code> if the config file cannot be found
     */
    private TomcatConfig parseTomcatConfig(String installationPath) {
        String configFileName = installationPath + File.separator + "conf" + File.separator + "server.xml";
        File configFile = new File(configFileName);
        TomcatConfig config = TomcatConfig.getConfig(configFile);
        return config;
    }

    /**
     * Executes the necessary script to determine the Tomcat version number.
     *
     * @param  installationPath path to the Tomcat instance being checked
     * @param  systemInfo       used to make the script call
     *
     * @return version of the tomcat instance; unknown version message if it cannot be determined
     */
    private String determineVersion(String installationPath, SystemInfo systemInfo) {
        String version = UNKNOWN_VERSION;
        boolean isNix = File.separatorChar == '/';
        String versionScriptFileName = null;

        if (this.isEWS(installationPath)) {
            // Execute the appropriate EWS script with the 'version' parameter
            versionScriptFileName = installationPath + File.separator + "bin" + File.separator + ((isEWSTomcat5(installationPath) ? EWS_TOMCAT_5 : EWS_TOMCAT_6) + " version");
        } else {
            versionScriptFileName = installationPath + File.separator + "bin" + File.separator + "version." + (isNix ? "sh" : "bat");
        }

        ProcessExecution processExecution = new ProcessExecution(versionScriptFileName);

        TomcatServerOperationsDelegate.setProcessExecutionEnvironment(processExecution, installationPath);

        processExecution.setCaptureOutput(true);
        processExecution.setWaitForCompletion(5000L);
        processExecution.setKillOnTimeout(true);

        ProcessExecutionResults results = systemInfo.executeProcess(processExecution);
        String versionOutput = results.getCapturedOutput();

        // try more recent Tomcat version string format first
        Matcher matcher = TOMCAT_6_VERSION_PATTERN.matcher(versionOutput);
        if (matcher.find()) {
            String serverNumberString = matcher.group();
            String[] serverNumberParts = serverNumberString.split(":");
            version = serverNumberParts[1].trim();
        } else {
            matcher = TOMCAT_5_VERSION_PATTERN.matcher(versionOutput);
            if (matcher.find()) {
                String serverNumberString = matcher.group();
                String[] serverNumberParts = serverNumberString.split("/");
                version = serverNumberParts[1].trim();
            }
        }

        if (UNKNOWN_VERSION.equals(version)) {
            log.warn("Failed to determine Tomcat Server Version Given:\nVersionInfo:" + versionOutput + "\ninstallationPath: " + installationPath + "\nScript:" + versionScriptFileName
                + "\ntimeout=5000L");
        }

        return version;
    }

    private Configuration populatePluginConfiguration(String installationPath, String[] commandLine) {
        Configuration configuration = new Configuration();

        configuration.put(new PropertySimple(TomcatServerComponent.PROP_INSTALLATION_PATH, installationPath));

        String binPath = installationPath + File.separator + "bin" + File.separator;
        if (isEWS(installationPath)) {
            String script = this.isEWSTomcat5(installationPath) ? EWS_TOMCAT_5 : EWS_TOMCAT_6;

            configuration.put(new PropertySimple(TomcatServerComponent.PROP_START_SCRIPT, binPath + script + " start"));
            configuration.put(new PropertySimple(TomcatServerComponent.PROP_SHUTDOWN_SCRIPT, binPath + script + " stop"));
        } else {
            String scriptExtension = (File.separatorChar == '/') ? ".sh" : ".bat";

            configuration.put(new PropertySimple(TomcatServerComponent.PROP_START_SCRIPT, binPath + "startup" + scriptExtension));
            configuration.put(new PropertySimple(TomcatServerComponent.PROP_SHUTDOWN_SCRIPT, binPath + "shutdown" + scriptExtension));
        }

        populateJMXConfiguration(configuration, commandLine);

        return configuration;
    }

    private void populateJMXConfiguration(Configuration configuration, String[] commandLine) {
        String portProp = "com.sun.management.jmxremote.port";

        String port = null;
        for (String argument : commandLine) {
            String cmdLineArg = "-D" + portProp + "=";
            if (argument.startsWith(cmdLineArg)) {
                port = argument.substring(cmdLineArg.length());
                break;
            }
        }

        configuration.put(new PropertySimple(JMXDiscoveryComponent.CONNECTION_TYPE, "org.mc4j.ems.connection.support.metadata.Tomcat55ConnectionTypeDescriptor"));
        // this should be set but just in case we'll use the default connection server url later on 
        if (null != port) {
            configuration.put(new PropertySimple(JMXDiscoveryComponent.CONNECTOR_ADDRESS_CONFIG_PROPERTY, "service:jmx:rmi:///jndi/rmi://localhost:" + port + "/jmxrmi"));
        }
    }

}
