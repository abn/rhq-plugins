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
package org.rhq.plugins.perftest;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.ConfigurationUpdateStatus;
import org.rhq.core.domain.content.InstalledPackage;
import org.rhq.core.domain.content.PackageType;
import org.rhq.core.domain.content.transfer.DeployPackageStep;
import org.rhq.core.domain.content.transfer.DeployPackagesResponse;
import org.rhq.core.domain.content.transfer.RemovePackagesResponse;
import org.rhq.core.domain.content.transfer.ResourcePackageDetails;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.domain.measurement.MeasurementDataNumeric;
import org.rhq.core.domain.measurement.MeasurementReport;
import org.rhq.core.domain.measurement.MeasurementScheduleRequest;
import org.rhq.core.pluginapi.configuration.ConfigurationFacet;
import org.rhq.core.pluginapi.configuration.ConfigurationUpdateReport;
import org.rhq.core.pluginapi.content.ContentFacet;
import org.rhq.core.pluginapi.content.ContentServices;
import org.rhq.core.pluginapi.event.EventContext;
import org.rhq.core.pluginapi.event.EventPoller;
import org.rhq.core.pluginapi.inventory.InvalidPluginConfigurationException;
import org.rhq.core.pluginapi.inventory.ResourceComponent;
import org.rhq.core.pluginapi.inventory.ResourceContext;
import org.rhq.core.pluginapi.measurement.MeasurementFacet;
import org.rhq.plugins.perftest.event.PerfTestEventPoller;
import org.rhq.plugins.perftest.measurement.MeasurementFactory;
import org.rhq.plugins.perftest.configuration.SimpleConfigurationFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * JON resource component for handling resources defined in the performance test scenario.
 *
 * @author Jason Dobies
 */
public class PerfTestComponent implements ResourceComponent, MeasurementFacet, ContentFacet, ConfigurationFacet {
    // Attributes  --------------------------------------------
    private Log log = LogFactory.getLog(PerfTestComponent.class);

    private ResourceContext resourceContext;
    private EventPoller eventPoller;
    private Configuration resourceConfiguration;

    // ResourceComponent Implementation  --------------------------------------------

    public void start(ResourceContext context) throws InvalidPluginConfigurationException, Exception {
        this.resourceContext = context;
        startEventPollers();
        ScenarioManager scenarioManager = ScenarioManager.getInstance();
        if (!scenarioManager.isEnabled())
            log.warn("[" + this.resourceContext.getResourceType().getName()
                    + "] perftest Resources exist in inventory, but no Perf test scenario is enabled.");
    }

    public void stop() {
        stopEventPollers();
    }

    public AvailabilityType getAvailability() {
        return AvailabilityType.UP;
        // TODO: Return DOWN once in a while?
    }

    // MeasurementFacet Implementation  --------------------------------------------

    public void getValues(MeasurementReport report, Set<MeasurementScheduleRequest> metrics) throws Exception {
        ScenarioManager scenarioManager = ScenarioManager.getInstance();
        if (!scenarioManager.isEnabled())
            return;

        String resourceTypeName = resourceContext.getResourceType().getName();
        /* Currently this will use the same value generator for each metric defined for the resource type.
         * In other words, you either get values for every metric defined for the resource type or for none. There may
         * be an eventual need for finer grained control.
         *
         * jdobies, Jun 25, 2007
         */
        MeasurementFactory measurementFactory = scenarioManager.getMeasurementFactory(resourceTypeName);

        for (MeasurementScheduleRequest metric : metrics) {
            MeasurementDataNumeric measurementData = (MeasurementDataNumeric) measurementFactory.nextValue(metric);

            if (measurementData != null) {
                report.addData(measurementData);
            }
        }
    }

    // ArtifactFacet Implementation  --------------------------------------------

    /*
     * public Set<InstalledPackageDetails> getInstalledPackages(PackageType type) {   ScenarioManager scenarioManager =
     * ScenarioManager.getInstance();   ContentFactory factory =
     * scenarioManager.getContentFactory(resourceContext.getResourceType().getName(), type.getName());
     *
     * Set<InstalledPackageDetails> contentDetails;   if (factory != null)   {      contentDetails =
     * factory.discoverContent(type);   }   else   {      contentDetails = new HashSet<InstalledPackageDetails>();   }
     *
     * return contentDetails; }
     */

    public void installPackages(Set<InstalledPackage> packages, ContentServices contentServices) {
        return;
    }

    public InputStream retrievePackageBits(InstalledPackage pkg) {
        return null;
    }

    public List<DeployPackageStep> generateInstallationSteps(ResourcePackageDetails packageDetails) {
        return null; //To change body of implemented methods use File | Settings | File Templates.
    }

    public DeployPackagesResponse deployPackages(Set<ResourcePackageDetails> packages, ContentServices contentServices) {
        return null; //To change body of implemented methods use File | Settings | File Templates.
    }

    public RemovePackagesResponse removePackages(Set<ResourcePackageDetails> packages) {
        return null; //To change body of implemented methods use File | Settings | File Templates.
    }

    public Set<ResourcePackageDetails> discoverDeployedPackages(PackageType type) {
        return null; //To change body of implemented methods use File | Settings | File Templates.
    }

    public InputStream retrievePackageBits(ResourcePackageDetails packageDetails) {
        return null; //To change body of implemented methods use File | Settings | File Templates.
    }

    private void startEventPollers() {
        if (this.resourceContext.getEventContext() != null) {
            String pollingIntervalString = System.getProperty(PerfTestEventPoller.SYSPROP_EVENTS_POLLING_INTERVAL);
            if (pollingIntervalString != null) {
                int pollingInterval;
                try {
                    pollingInterval = Integer.parseInt(pollingIntervalString);
                } catch (Exception e) {
                    pollingInterval = EventContext.MINIMUM_POLLING_INTERVAL;
                }
                eventPoller = new PerfTestEventPoller(resourceContext);
                this.resourceContext.getEventContext().registerEventPoller(eventPoller, pollingInterval);
            }
        }
    }

    private void stopEventPollers() {
        if (this.resourceContext.getEventContext() != null) {
            if (eventPoller != null) {
                this.resourceContext.getEventContext().unregisterEventPoller(eventPoller.getEventType());
            }
        }
    }

    public Configuration loadResourceConfiguration() throws Exception {
        if (this.resourceConfiguration == null) {
            SimpleConfigurationFactory configurationFactory = new SimpleConfigurationFactory();
            this.resourceConfiguration = configurationFactory.generateConfiguration(
                    this.resourceContext.getResourceType().getResourceConfigurationDefinition());
        }
        return this.resourceConfiguration;
    }

    public void updateResourceConfiguration(ConfigurationUpdateReport report) {
        try
        {
            // Sleep for a bit to simulate the time it would take to parse and update an XML config file.
            Thread.sleep(1000);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        this.resourceConfiguration = report.getConfiguration();
        report.setStatus(ConfigurationUpdateStatus.SUCCESS);
        // TODO: Perhaps return FAILURE and INPROGRESS once in a while.
    }
}