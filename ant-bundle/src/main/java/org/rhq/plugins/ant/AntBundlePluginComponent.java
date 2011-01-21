/*
 * RHQ Management Platform
 * Copyright (C) 2010 Red Hat, Inc.
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
package org.rhq.plugins.ant;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.Project;

import org.rhq.bundle.ant.AntLauncher;
import org.rhq.bundle.ant.BundleAntProject;
import org.rhq.bundle.ant.DeployPropertyNames;
import org.rhq.bundle.ant.DeploymentPhase;
import org.rhq.bundle.ant.InvalidBuildFileException;
import org.rhq.bundle.ant.LoggerAntBuildListener;
import org.rhq.core.domain.bundle.BundleDeployment;
import org.rhq.core.domain.bundle.BundleResourceDeployment;
import org.rhq.core.domain.bundle.BundleResourceDeploymentHistory;
import org.rhq.core.domain.bundle.BundleVersion;
import org.rhq.core.domain.bundle.BundleResourceDeploymentHistory.Category;
import org.rhq.core.domain.bundle.BundleResourceDeploymentHistory.Status;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.Property;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.pluginapi.bundle.BundleDeployRequest;
import org.rhq.core.pluginapi.bundle.BundleDeployResult;
import org.rhq.core.pluginapi.bundle.BundleFacet;
import org.rhq.core.pluginapi.bundle.BundleManagerProvider;
import org.rhq.core.pluginapi.bundle.BundlePurgeRequest;
import org.rhq.core.pluginapi.bundle.BundlePurgeResult;
import org.rhq.core.pluginapi.inventory.ResourceComponent;
import org.rhq.core.pluginapi.inventory.ResourceContext;
import org.rhq.core.system.SystemInfoFactory;
import org.rhq.core.util.file.FileUtil;
import org.rhq.core.util.stream.StreamUtil;
import org.rhq.core.util.updater.DeployDifferences;
import org.rhq.core.util.updater.DeploymentsMetadata;
import org.rhq.core.util.updater.FileHashcodeMap;

/**
 * @author John Mazzitelli
 */
@SuppressWarnings("unchecked")
public class AntBundlePluginComponent implements ResourceComponent, BundleFacet {

    private final Log log = LogFactory.getLog(AntBundlePluginComponent.class);

    private ResourceContext resourceContext;

    private File tmpDirectory;

    public void start(ResourceContext context) throws Exception {
        this.resourceContext = context;
        this.tmpDirectory = new File(context.getTemporaryDirectory(), "ant-bundle-plugin");
        this.tmpDirectory.mkdirs();
        if (!this.tmpDirectory.exists() || !this.tmpDirectory.isDirectory()) {
            throw new Exception("Failed to create tmp dir [" + this.tmpDirectory + "] - cannot process Ant bundles.");
        }
        return;
    }

    public void stop() {
        return;
    }

    public AvailabilityType getAvailability() {
        return AvailabilityType.UP;
    }

    public BundleDeployResult deployBundle(BundleDeployRequest request) {
        BundleDeployResult result = new BundleDeployResult();
        try {
            BundleResourceDeployment resourceDeployment = request.getResourceDeployment();
            BundleDeployment bundleDeployment = resourceDeployment.getBundleDeployment();
            BundleVersion bundleVersion = bundleDeployment.getBundleVersion();

            String recipe = bundleVersion.getRecipe();
            File recipeFile = File.createTempFile("ant-bundle-recipe", ".xml", request.getBundleFilesLocation());
            File logFile = File.createTempFile("ant-bundle-recipe", ".log", this.tmpDirectory);
            PrintWriter logFileOutput = null;
            try {
                // Open the log file for writing.
                logFileOutput = new PrintWriter(new FileOutputStream(logFile, true));

                // Store the recipe in the tmp recipe file.
                ByteArrayInputStream in = new ByteArrayInputStream(recipe.getBytes());
                FileOutputStream out = new FileOutputStream(recipeFile);
                StreamUtil.copy(in, out);

                // Get the bundle's configuration values and the global system facts and
                // add them as Ant properties so the Ant script can get their values.
                Properties antProps = createAntProperties(request);
                // TODO: Eventually the phase to be executed should be passed in by the PC when it calls us.
                // TODO: Invoke STOP phase.
                // TODO: Invoke START phase.

                List<BuildListener> buildListeners = new ArrayList();
                LoggerAntBuildListener logger = new LoggerAntBuildListener(null, logFileOutput, Project.MSG_DEBUG);
                buildListeners.add(logger);
                DeploymentAuditorBuildListener auditor = new DeploymentAuditorBuildListener(request
                    .getBundleManagerProvider(), resourceDeployment);
                buildListeners.add(auditor);

                // Parse and execute the Ant script.
                executeDeploymentPhase(recipeFile, antProps, buildListeners, DeploymentPhase.STOP);
                String deployDirString = bundleDeployment.getDestination().getDeployDir();
                File deployDir = new File(deployDirString);
                DeploymentsMetadata deployMetadata = new DeploymentsMetadata(deployDir);
                DeploymentPhase installPhase = (deployMetadata.isManaged()) ? DeploymentPhase.UPGRADE
                    : DeploymentPhase.INSTALL;
                BundleAntProject project = executeDeploymentPhase(recipeFile, antProps, buildListeners, installPhase);
                executeDeploymentPhase(recipeFile, antProps, buildListeners, DeploymentPhase.START);

                // Send the diffs to the Server so it can store them as an entry in the deployment history.
                BundleManagerProvider bundleManagerProvider = request.getBundleManagerProvider();
                DeployDifferences diffs = project.getDeployDifferences();

                String msg = new StringBuilder("Added files=").append(diffs.getAddedFiles().size()).append(
                    "; Deleted files=").append(diffs.getDeletedFiles().size()).append(
                    " (see attached details for more information)").toString();
                String fullDetails = formatDiff(diffs);
                bundleManagerProvider.auditDeployment(resourceDeployment, "Deployment Differences", project.getName(),
                    BundleResourceDeploymentHistory.Category.DEPLOY_STEP, null, msg, fullDetails);
            } catch (Throwable t) {
                if (log.isDebugEnabled()) {
                    try {
                        log.debug(new String(StreamUtil.slurp(new FileInputStream(logFile))));
                    } catch (Exception e) {
                    }
                }
                throw new Exception("Failed to execute the bundle Ant script", t);
            } finally {
                if (logFileOutput != null) {
                    logFileOutput.close();
                }
                recipeFile.delete();
                logFile.delete();
            }

        } catch (Throwable t) {
            log.error("Failed to deploy bundle [" + request + "]", t);
            result.setErrorMessage(t);
        }
        return result;
    }

    public BundlePurgeResult purgeBundle(BundlePurgeRequest request) {
        BundlePurgeResult result = new BundlePurgeResult();
        try {
            BundleResourceDeployment deploymentToPurge = request.getLiveResourceDeployment();
            BundleDeployment bundleDeployment = deploymentToPurge.getBundleDeployment();
            File deployDir = new File(bundleDeployment.getDestination().getDeployDir());
            String deployDirAbsolutePath = deployDir.getAbsolutePath();
            BundleManagerProvider bundleManagerProvider = request.getBundleManagerProvider();

            // If the receipe copied file(s) outside of the deployment directory (external, raw files), they will still exist.
            // Let's get the metadata information that tells us if such files exist, and if so, remove them.
            DeploymentsMetadata metadata = new DeploymentsMetadata(deployDir);
            if (metadata.isManaged()) {
                int totalExternalFiles = 0;
                ArrayList<String> deleteSuccesses = new ArrayList<String>(0);
                ArrayList<String> deleteFailures = new ArrayList<String>(0);
                FileHashcodeMap fileHashcodes = metadata.getCurrentDeploymentFileHashcodes();
                for (String filePath : fileHashcodes.keySet()) {
                    File file = new File(filePath);
                    if (file.isAbsolute()) {
                        totalExternalFiles++;
                        if (file.exists()) {
                            if (file.delete()) {
                                deleteSuccesses.add(filePath);
                            } else {
                                deleteFailures.add(filePath);
                            }
                        } else {
                            deleteSuccesses.add(filePath); // someone already deleted it, consider it removed successfully
                        }
                    }
                }
                if (totalExternalFiles > 0) {
                    if (!deleteSuccesses.isEmpty()) {
                        StringBuilder deleteSuccessesDetails = new StringBuilder();
                        for (String path : deleteSuccesses) {
                            deleteSuccessesDetails.append(path).append("\n");
                        }
                        bundleManagerProvider.auditDeployment(deploymentToPurge, "Purge", "External files were purged",
                            Category.AUDIT_MESSAGE, Status.SUCCESS, "[" + deleteSuccesses.size() + "] of ["
                                + totalExternalFiles
                                + "] external files were purged. See attached details for the list",
                            deleteSuccessesDetails.toString());
                    }
                    if (!deleteFailures.isEmpty()) {
                        StringBuilder deleteFailuresDetails = new StringBuilder();
                        for (String path : deleteFailures) {
                            deleteFailuresDetails.append(path).append("\n");
                        }
                        bundleManagerProvider.auditDeployment(deploymentToPurge, "Purge",
                            "External files failed to be purged", Category.AUDIT_MESSAGE, Status.FAILURE, "["
                                + deleteFailures.size() + "] of [" + totalExternalFiles
                                + "] external files failed to be purged. See attached details for the list",
                            deleteFailuresDetails.toString());
                    }
                }
            }

            // completely purge the deployment directory.
            FileUtil.purge(deployDir, true);

            if (!deployDir.exists()) {
                bundleManagerProvider.auditDeployment(deploymentToPurge, "Purge",
                    "The destination directory has been purged", Category.AUDIT_MESSAGE, Status.SUCCESS,
                    "Directory purged: " + deployDirAbsolutePath, null);
            } else {
                bundleManagerProvider.auditDeployment(deploymentToPurge, "Purge",
                    "The destination directory failed to be purged", Category.AUDIT_MESSAGE, Status.FAILURE,
                    "The directory that failed to be purged: " + deployDirAbsolutePath, null);
            }
        } catch (Throwable t) {
            log.error("Failed to purge bundle [" + request + "]", t);
            result.setErrorMessage(t);
        }
        return result;
    }

    private BundleAntProject executeDeploymentPhase(File recipeFile, Properties antProps,
        List<BuildListener> buildListeners, DeploymentPhase phase) throws InvalidBuildFileException {
        AntLauncher antLauncher = new AntLauncher();
        antProps.setProperty(DeployPropertyNames.DEPLOY_PHASE, phase.name());
        BundleAntProject project = antLauncher.executeBundleDeployFile(recipeFile, antProps, buildListeners);
        return project;
    }

    private Properties createAntProperties(BundleDeployRequest request) {
        Properties antProps = new Properties();

        BundleResourceDeployment resourceDeployment = request.getResourceDeployment();
        BundleDeployment bundleDeployment = resourceDeployment.getBundleDeployment();
        String deployDir = bundleDeployment.getDestination().getDeployDir();
        if (deployDir == null) {
            throw new IllegalStateException("Bundle deployment does not specify install dir: " + bundleDeployment);
        }
        antProps.setProperty(DeployPropertyNames.DEPLOY_DIR, deployDir);

        int deploymentId = bundleDeployment.getId();
        antProps.setProperty(DeployPropertyNames.DEPLOY_ID, Integer.toString(deploymentId));
        antProps.setProperty(DeployPropertyNames.DEPLOY_NAME, bundleDeployment.getName());
        antProps.setProperty(DeployPropertyNames.DEPLOY_REVERT, String.valueOf(request.isRevert()));
        antProps.setProperty(DeployPropertyNames.DEPLOY_CLEAN, String.valueOf(request.isCleanDeployment()));

        Map<String, String> sysFacts = SystemInfoFactory.fetchTemplateEngine().getTokens();
        for (Map.Entry<String, String> fact : sysFacts.entrySet()) {
            antProps.setProperty(fact.getKey(), fact.getValue());
        }

        Configuration config = bundleDeployment.getConfiguration();
        if (config != null) {
            Map<String, Property> allProperties = config.getAllProperties();
            for (Map.Entry<String, Property> entry : allProperties.entrySet()) {
                String name = entry.getKey();
                Property prop = entry.getValue();
                String value;
                if (prop instanceof PropertySimple) {
                    value = ((PropertySimple) prop).getStringValue();
                } else {
                    // for now, just skip all property lists and maps, just assume we are getting simples
                    continue;
                }
                antProps.setProperty(name, value);
            }
        }
        return antProps;
    }

    private String formatDiff(DeployDifferences diffs) {
        String indent = "    ";
        String nl = "\n";
        StringBuilder str = new StringBuilder("DEPLOYMENT DETAILS:");
        str.append(nl);

        str.append("Added Files: ").append(diffs.getAddedFiles().size()).append(nl);
        for (String f : diffs.getAddedFiles()) {
            str.append(indent).append(f).append(nl);
        }

        str.append("Deleted Files: ").append(diffs.getDeletedFiles().size()).append(nl);
        for (String f : diffs.getDeletedFiles()) {
            str.append(indent).append(f).append(nl);
        }

        str.append("Changed Files: ").append(diffs.getChangedFiles().size()).append(nl);
        for (String f : diffs.getChangedFiles()) {
            str.append(indent).append(f).append(nl);
        }

        str.append("Backed Up Files: ").append(diffs.getBackedUpFiles().size()).append(nl);
        for (Map.Entry<String, String> entry : diffs.getBackedUpFiles().entrySet()) {
            str.append(indent).append(entry.getKey()).append(" -> ").append(entry.getValue()).append(nl);
        }

        str.append("Restored Files: ").append(diffs.getRestoredFiles().size()).append(nl);
        for (Map.Entry<String, String> entry : diffs.getRestoredFiles().entrySet()) {
            str.append(indent).append(entry.getKey()).append(" <- ").append(entry.getValue()).append(nl);
        }

        str.append("Ignored Files: ").append(diffs.getIgnoredFiles().size()).append(nl);
        for (String f : diffs.getIgnoredFiles()) {
            str.append(indent).append(f).append(nl);
        }

        str.append("Realized Files: ").append(diffs.getRealizedFiles().size()).append(nl);
        for (String f : diffs.getRealizedFiles().keySet()) {
            str.append(indent).append(f).append(nl);
        }

        str.append("Was Cleaned?: ").append(diffs.wasCleaned()).append(nl);

        str.append("Errors: ").append(diffs.getErrors().size()).append(nl);
        for (Map.Entry<String, String> entry : diffs.getErrors().entrySet()) {
            str.append(indent).append(entry.getKey()).append(" : ").append(entry.getValue()).append(nl);
        }

        return str.toString();
    }
}
