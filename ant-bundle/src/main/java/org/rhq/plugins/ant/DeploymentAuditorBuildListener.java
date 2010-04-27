/*
 * RHQ Management Platform
 * Copyright (C) 2010 Red Hat, Inc.
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
package org.rhq.plugins.ant;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.util.StringUtils;
import org.rhq.core.domain.bundle.BundleDeploymentAction;
import org.rhq.core.domain.bundle.BundleDeploymentStatus;
import org.rhq.core.domain.bundle.BundleResourceDeployment;
import org.rhq.core.pluginapi.bundle.BundleManagerProvider;

/**
 * @author Ian Springer
 */
public class DeploymentAuditorBuildListener implements BuildListener {
    private BundleManagerProvider bundleManagerProvider;
    private BundleResourceDeployment bundleResourceDeployment;

    public DeploymentAuditorBuildListener(BundleManagerProvider bundleManagerProvider, 
                                          BundleResourceDeployment bundleResourceDeployment) {
        this.bundleManagerProvider = bundleManagerProvider;
        this.bundleResourceDeployment = bundleResourceDeployment;
    }

    public void buildStarted(BuildEvent event) {
        return;
    }

    public void buildFinished(BuildEvent event) {
        return;
    }

    public void targetStarted(BuildEvent event) {
        return;
    }

    public void targetFinished(BuildEvent event) {
        auditEvent(event);
    }

    public void taskStarted(BuildEvent event) {
        return;
    }

    public void taskFinished(BuildEvent event) {
        auditEvent(event);
    }

    public void messageLogged(BuildEvent event) {
        return;
    }

    private void auditEvent(BuildEvent event) {
        BundleDeploymentStatus status = (event.getException() == null) ? BundleDeploymentStatus.SUCCESS :
                BundleDeploymentStatus.FAILURE;
        String message = createMessage(event);
        try {
            this.bundleManagerProvider.auditDeployment(this.bundleResourceDeployment,
                    BundleDeploymentAction.DEPLOYMENT_STEP, status, message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return;
    }

    private static String createMessage(BuildEvent event) {
        StringBuilder msg = new StringBuilder();
        if (event.getTarget() != null) {
            msg.append("[").append(event.getTarget().getName()).append("] ");
        }

        if (event.getTask() != null) {
            msg.append("<").append(event.getTask().getTaskName()).append("> ");
        }

        if (event.getMessage() != null) {
            msg.append(event.getMessage());
        }

        //noinspection ThrowableResultOfMethodCallIgnored
        if (event.getException() != null) {
            msg.append("\n");
            //noinspection ThrowableResultOfMethodCallIgnored
            msg.append(StringUtils.getStackTrace(event.getException()));
        }
        return msg.toString();
    }
}
