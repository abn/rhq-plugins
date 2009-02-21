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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.jboss.on.plugins.tomcat.helper.CreateResourceHelper;
import org.jboss.on.plugins.tomcat.helper.FileContentDelegate;
import org.jboss.on.plugins.tomcat.helper.TomcatApplicationDeployer;
import org.mc4j.ems.connection.EmsConnection;
import org.mc4j.ems.connection.bean.attribute.EmsAttribute;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.ConfigurationUpdateStatus;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.content.PackageDetailsKey;
import org.rhq.core.domain.content.transfer.ResourcePackageDetails;
import org.rhq.core.domain.resource.CreateResourceStatus;
import org.rhq.core.pluginapi.configuration.ConfigurationUpdateReport;
import org.rhq.core.pluginapi.content.ContentContext;
import org.rhq.core.pluginapi.content.ContentServices;
import org.rhq.core.pluginapi.inventory.ApplicationServerComponent;
import org.rhq.core.pluginapi.inventory.CreateChildResourceFacet;
import org.rhq.core.pluginapi.inventory.CreateResourceReport;
import org.rhq.plugins.jmx.MBeanResourceComponent;

/**
 * Handle generic information about a virtual host in tomcat
 * 
 * @author Jay Shaughnessy
 * @author Heiko W. Rupp
 *
 */
public class TomcatVHostComponent extends MBeanResourceComponent<TomcatServerComponent> implements ApplicationServerComponent, CreateChildResourceFacet {

    public static final String CONFIG_ALIASES = "aliases";
    public static final String CONFIG_APP_BASE = "appBase";
    public static final String CONTENT_CONFIG_EXPLODE_ON_DEPLOY = "explodeOnDeploy";
    public static final String PLUGIN_CONFIG_NAME = "name";

    /**
     * Roles and Groups are handled as comma delimited lists and offered up as a String array of object names by the MBean 
     */
    @Override
    public Configuration loadResourceConfiguration() {
        Configuration configuration = super.loadResourceConfiguration();
        try {
            resetConfig(CONFIG_ALIASES, configuration);
        } catch (Exception e) {
            log.error("Failed to reset role property value", e);
        }

        return configuration;
    }

    // Reset the StringArray provided by the MBean with a more user friendly longString
    private void resetConfig(String property, Configuration configuration) {
        EmsAttribute attribute = getEmsBean().getAttribute(property);
        Object valueObject = attribute.refresh();
        String[] vals = (String[]) valueObject;
        if (vals.length > 0) {
            String delim = "";
            StringBuilder sb = new StringBuilder();
            for (String val : vals) {
                sb.append(delim);
                sb.append(val);
                delim = "\n";
            }
            configuration.put(new PropertySimple(property, sb.toString()));
        } else {
            configuration.put(new PropertySimple(property, null));
        }
    }

    @Override
    public void updateResourceConfiguration(ConfigurationUpdateReport report) {
        // updating Role membership is done via MBean operation, not manipulation of the attribute

        Configuration reportConfiguration = report.getConfiguration();
        // reserve the new alias settings 
        PropertySimple newAliases = reportConfiguration.getSimple(CONFIG_ALIASES);
        // get the current alias settings
        resetConfig(CONFIG_ALIASES, reportConfiguration);
        PropertySimple currentAliases = reportConfiguration.getSimple(CONFIG_ALIASES);
        // remove the aliases config from the report so they are ignored by the mbean config processing
        reportConfiguration.remove(CONFIG_ALIASES);

        // perform standard processing on remaining config
        super.updateResourceConfiguration(report);

        // add back the aliases config so the report is complete
        reportConfiguration.put(newAliases);

        // if the mbean update failed, return now
        if (ConfigurationUpdateStatus.SUCCESS != report.getStatus()) {
            return;
        }

        // try updating the alias settings
        try {
            consolidateSettings(newAliases, currentAliases, "addAlias", "removeAlias", "alias");
        } catch (Exception e) {
            newAliases.setErrorMessageFromThrowable(e);
            report.setErrorMessage("Failed setting resource configuration - see property error messages for details");
            log.info("Failure setting Tomcat VHost aliases configuration value", e);
        }
    }

    private void consolidateSettings(PropertySimple newVals, PropertySimple currentVals, String addOp, String removeOp, String arg) throws Exception {

        // add new values not in the current settings
        String currentValsLongString = currentVals.getStringValue();
        String newValsLongString = newVals.getStringValue();
        StringTokenizer tokenizer = null;
        Configuration opConfig = null;

        if (null != newValsLongString) {
            tokenizer = new StringTokenizer(newValsLongString, "\n");
            opConfig = new Configuration();
            while (tokenizer.hasMoreTokens()) {
                String newVal = tokenizer.nextToken().trim();
                if ((null == currentValsLongString) || !currentValsLongString.contains(newVal)) {
                    opConfig.put(new PropertySimple(arg, newVal));
                    try {
                        invokeOperation(addOp, opConfig);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Could not add " + arg + "=" + newVal + ". Please check spelling/existence.");
                    }
                }
            }
        }

        if (null != currentValsLongString) {
            tokenizer = new StringTokenizer(currentValsLongString, "\n");
            while (tokenizer.hasMoreTokens()) {
                String currentVal = tokenizer.nextToken().trim();
                if ((null == newValsLongString) || !newValsLongString.contains(currentVal)) {
                    opConfig.put(new PropertySimple(arg, currentVal));
                    try {
                        invokeOperation(removeOp, opConfig);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Could not remove " + arg + "=" + currentVal + ". Please check spelling/existence.");
                    }
                }
            }
        }
    }

    public File getInstallationPath() {
        return getResourceContext().getParentResourceComponent().getInstallationPath();
    }

    public String getName() {
        return getResourceContext().getPluginConfiguration().getSimpleValue(PLUGIN_CONFIG_NAME, "localhost");
    }

    public File getConfigurationPath() {
        String appBase = (String) getEmsBean().getAttribute(CONFIG_APP_BASE).getValue();
        if (null == appBase) {
            appBase = "webapps";
        }

        return new File(getInstallationPath(), appBase);
    }

    public CreateResourceReport createResource(CreateResourceReport report) {
        String resourceTypeName = report.getResourceType().getName();
        try {
            if (TomcatWarComponent.RESOURCE_TYPE_NAME.equals(resourceTypeName)) {
                warCreate(report);
            } else {
                throw new UnsupportedOperationException("Unsupported Resource type: " + resourceTypeName);
            }
        } catch (Exception e) {
            CreateResourceHelper.setErrorOnReport(report, e);
        }
        return report;
    }

    private void warCreate(CreateResourceReport report) throws Exception {
        ResourcePackageDetails details = report.getPackageDetails();
        PackageDetailsKey key = details.getKey();
        String archiveName = key.getName();

        if (!archiveName.toLowerCase().endsWith(".war")) {
            CreateResourceHelper.setErrorOnReport(report, "Deployed file must have a .war extension");
            return;
        }

        Configuration deployTimeConfiguration = details.getDeploymentTimeConfiguration();
        PropertySimple explodeOnDeployProp = deployTimeConfiguration.getSimple(CONTENT_CONFIG_EXPLODE_ON_DEPLOY);

        if (explodeOnDeployProp == null || explodeOnDeployProp.getBooleanValue() == null) {
            CreateResourceHelper.setErrorOnReport(report, "Explode On Deploy property is required.");
            return;
        }
        boolean explodeOnDeploy = explodeOnDeployProp.getBooleanValue();

        // Perform the deployment        
        File deployDir = getConfigurationPath();
        FileContentDelegate fileContent = new FileContentDelegate(deployDir, details.getPackageTypeName());

        if (explodeOnDeploy) {
            // trim off the .war suffix because we want to deploy into a root directory named after the app name
            archiveName = archiveName.substring(0, archiveName.length() - 4);
        }

        File path = new File(deployDir, archiveName);
        if (path.exists()) {
            CreateResourceHelper.setErrorOnReport(report, "A web application named " + path.getName() + " is already deployed with path " + path + ".");
            return;
        }

        File tempDir = getResourceContext().getTemporaryDirectory();
        File tempFile = new File(tempDir.getAbsolutePath(), "tomcat-war.bin");
        OutputStream osForTempDir = new BufferedOutputStream(new FileOutputStream(tempFile));
        ContentContext contentContext = getResourceContext().getContentContext();

        ContentServices contentServices = contentContext.getContentServices();
        contentServices.downloadPackageBitsForChildResource(contentContext, TomcatWarComponent.RESOURCE_TYPE_NAME, key, osForTempDir);

        osForTempDir.close();

        // check for content
        boolean valid = isWebApplication(tempFile);
        if (!valid) {
            CreateResourceHelper.setErrorOnReport(report, "Expected a " + TomcatWarComponent.RESOURCE_TYPE_NAME + " file, but its format/content did not match");
            return;
        }

        InputStream isForTempDir = new BufferedInputStream(new FileInputStream(tempFile));
        fileContent.createContent(path, isForTempDir, explodeOnDeploy);

        // Resource key should match the following:        
        // Catalina:j2eeType=WebModule,name=//localhost/<archiveName>,J2EEApplication=none,J2EEServer=none        

        String resourceKey = "Catalina:j2eeType=WebModule,J2EEApplication=none,J2EEServer=none,name=//localhost/" + archiveName;

        report.setResourceName(archiveName);
        report.setResourceKey(resourceKey);
        report.setStatus(CreateResourceStatus.SUCCESS);
    }

    private boolean isWebApplication(File file) {
        JarFile jfile = null;
        try {
            jfile = new JarFile(file);
            JarEntry entry = jfile.getJarEntry("WEB-INF/web.xml");

            return (null != entry);
        } catch (Exception e) {
            log.info(e.getMessage());
            return false;
        } finally {
            if (jfile != null)
                try {
                    jfile.close();
                } catch (IOException e) {
                    log.info("Exception when trying to close the war file: " + e.getMessage());
                }
        }
    }

    public TomcatApplicationDeployer getDeployer() {
        TomcatApplicationDeployer deployer = null;
        EmsConnection connection = null;

        try {
            connection = getEmsConnection();
            if (null != connection) {
                deployer = new TomcatApplicationDeployer(connection);
            }
        } catch (Throwable e) {
            log.error("Unable to access MainDeployer MBean required for creation and deletion of managed resources - this should never happen. Cause: " + e);
        }

        return deployer;
    }

    void undeployWar(String contextRoot) throws TomcatApplicationDeployer.DeployerException {
        // As it stands Tomcat will respond to the placement or removal of the physical Web App itself. We
        // call removeServiced prior to the file delete to let TC know to stop servicing the app, hopefully
        // for a cleaner removal.
        // There is no additional MBean interaction required, the deploy is done in a file-based way.

        TomcatApplicationDeployer deployer = getDeployer();
        if (null == deployer) {
            throw new IllegalStateException("Unable to undeploy " + contextRoot + ", because MainDeployer MBean could " + "not be accessed - this should never happen.");
        }

        deployer.undeploy(contextRoot);
    }

}
