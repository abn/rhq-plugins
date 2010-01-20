/*
 * RHQ Management Platform
 * Copyright (C) 2005-2009 Red Hat, Inc.
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
package org.rhq.plugins.cobbler;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import net.augeas.Augeas;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.configuration.RawConfiguration;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.pluginapi.configuration.ConfigurationUpdateReport;
import org.rhq.core.pluginapi.configuration.ResourceConfigurationFacet;
import org.rhq.plugins.augeas.AugeasConfigurationComponent;

/**
 * The ResourceComponent for the "Cobbler File" ResourceType.
 *
 * @author Ian Springer
 */
public class CobblerComponent extends AugeasConfigurationComponent implements ResourceConfigurationFacet {
    private static final String MODULES_PATH = "/etc/cobbler/modules.conf";
    private static final String SETTINGS_PATH = "/etc/cobbler/settings";
    private final Log log = LogFactory.getLog(this.getClass());

    @Override
    protected void setupAugeasModules(Augeas augeas) {
        augeas.set("/augeas/load/CobblerSettings/lens", "CobblerSettings.lns");
        augeas.set("/augeas/load/CobblerSettings/incl[1]", SETTINGS_PATH);
        augeas.set("/augeas/load/CobblerModules/lens", "CobblerModules.lns");
        augeas.set("/augeas/load/CobblerModules/incl[1]", MODULES_PATH);
    }

    public AvailabilityType getAvailability() {
        return super.getAvailability();
    }

    public void updateResourceConfiguration(ConfigurationUpdateReport report) {
        super.updateResourceConfiguration(report);
    }

    public Set<RawConfiguration> loadRawConfigurations() {
        try {
            Set<RawConfiguration> configs = new HashSet<RawConfiguration>();
            RawConfiguration modules = new RawConfiguration();
            modules.setPath(MODULES_PATH);
            modules.setContents(FileUtils.readFileToByteArray(new File(MODULES_PATH)));
            configs.add(modules);

            RawConfiguration settings = new RawConfiguration();
            settings.setPath(SETTINGS_PATH);
            settings.setContents(FileUtils.readFileToByteArray(new File(SETTINGS_PATH)));
            configs.add(settings);
            return configs;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Configuration loadStructuredConfiguration() {
        try {
            return loadResourceConfiguration();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private Augeas createAugeas(String lens, byte[] contents) {
        try {
            Augeas aug = new Augeas(getAugeasRootPath(), AUGEAS_LOAD_PATH, Augeas.NO_MODL_AUTOLOAD);
            File fl = File.createTempFile("_rhq", null);
            //write the 'to' file to disk 
            FileUtils.writeByteArrayToFile(fl, contents);
            aug.set("/augeas/load/CobblerTransform/lens", lens);
            aug.set("/augeas/load/CobblerTransform/incl", fl.getAbsolutePath());
            aug.load();
            return aug;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public RawConfiguration mergeRawConfiguration(Configuration from, RawConfiguration to) {
        try {
            String lens = "CobblerModules.lns";
            if (SETTINGS_PATH.equals(to.getPath())) {
                lens = "CobblerSettings.lns";
            }
            Augeas aug = createAugeas(lens, to.getContents());
            String file = aug.get("/augeas/load/CobblerTransform/incl");
            //apply structured changes
            if (SETTINGS_PATH.equals(to.getPath())) {
                String server = from.getSimpleValue("settings/server", "");
                aug.set("/files" + file + "/settings/server", server);
            }
            aug.save();

            //FInally create a new configuration
            RawConfiguration config = new RawConfiguration();
            config.setPath(to.getPath());
            config.setContents(FileUtils.readFileToByteArray(new File(file)));
            aug.close();
            return config;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void mergeStructuredConfiguration(RawConfiguration from, Configuration to) {
        try {
            String lens = "CobblerModules.lns";
            if (SETTINGS_PATH.equals(from.getPath())) {
                lens = "CobblerSettings.lns";
                Augeas aug = createAugeas(lens, from.getContents());
                String file = aug.get("/augeas/load/CobblerTransform/incl");
                to.put(new PropertySimple("settings/server", aug.get("/files" + file + "/settings/server")));
                aug.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void persistRawConfiguration(RawConfiguration rawConfiguration) {
        try {
            FileUtils.writeByteArrayToFile(new File(rawConfiguration.getPath()), rawConfiguration.getContents());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void persistStructuredConfiguration(Configuration configuration) {
        try {
            updateStructuredConfiguration(configuration);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void validateRawConfiguration(RawConfiguration rawConfiguration) {
        // TODO Auto-generated method stub
    }

    public void validateStructuredConfiguration(Configuration configuration) {
        // TODO Auto-generated method stub
    }
}
