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
package org.rhq.plugins.modcluster;

import org.rhq.core.domain.configuration.ConfigurationUpdateStatus;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.configuration.definition.ConfigurationDefinition;
import org.rhq.core.domain.configuration.definition.PropertyDefinitionSimple;
import org.rhq.core.pluginapi.configuration.ConfigurationUpdateReport;
import org.rhq.core.util.exception.ThrowableUtil;
import org.rhq.plugins.jmx.MBeanResourceComponent;
import org.rhq.plugins.modcluster.config.JBossWebServerFile;

@SuppressWarnings({ "rawtypes", "deprecation" })
public class CatalinaServiceComponent extends MBeanResourceComponent {
    private static final String SERVER_HOME_DIR = "serverHomeDir";
    private static final String CONFIGURATION_FILE_RELATIVE_PATH = "/deploy/jboss-web.deployer/server.xml";

    @Override
    public void updateResourceConfiguration(ConfigurationUpdateReport report) {
        super.updateResourceConfiguration(report);

        try {
            saveResouceConfigurationToFile(report, true);
        } catch (Exception e) {
        }
    }

    private void saveResouceConfigurationToFile(ConfigurationUpdateReport report, boolean ignoreReadOnly) {
        ConfigurationDefinition configurationDefinition = this.getResourceContext().getResourceType()
            .getResourceConfigurationDefinition();

        // assume we succeed - we'll set to failure if we can't set all properties
        report.setStatus(ConfigurationUpdateStatus.SUCCESS);

        try {
            JBossWebServerFile jbossWebServerFile = getJBossWebServerFileInstance();

            for (String key : report.getConfiguration().getSimpleProperties().keySet()) {
                PropertySimple property = report.getConfiguration().getSimple(key);
                if (property != null) {
                    try {
                        PropertyDefinitionSimple def = configurationDefinition.getPropertyDefinitionSimple(property
                            .getName());
                        if (!(ignoreReadOnly && def.isReadOnly())) {
                            jbossWebServerFile.setPropertyValue(property.getName(), property.getStringValue());
                        }
                    } catch (Exception e) {
                        property.setErrorMessage(ThrowableUtil.getStackAsString(e));
                        report
                            .setErrorMessage("Failed setting resource configuration - see property error messages for details");
                        log.info("Failure setting MBean Resource configuration value for " + key, e);
                    }
                }
            }

            jbossWebServerFile.saveConfigurationFile();
        } catch (Exception e) {
            log.debug("Unable to save mod_cluster configuration file.", e);
        }
    }

    private JBossWebServerFile getJBossWebServerFileInstance() throws Exception {
        ModClusterServerComponent modClusterComponent = (ModClusterServerComponent) this.resourceContext
            .getParentResourceComponent();

        PropertySimple property = modClusterComponent.getResourceContext().getPluginConfiguration()
            .getSimple(SERVER_HOME_DIR);

        if (property != null) {
            String fileName = property.getStringValue() + CONFIGURATION_FILE_RELATIVE_PATH;
            return new JBossWebServerFile(fileName);
        }

        return null;
    }
}
