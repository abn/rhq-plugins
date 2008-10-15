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
package org.rhq.plugins.jbossas5;

import org.rhq.core.domain.content.Package;
import org.rhq.core.domain.content.PackageType;
import org.rhq.core.domain.content.transfer.ContentResponseResult;
import org.rhq.core.domain.content.transfer.DeployPackageStep;
import org.rhq.core.domain.content.transfer.DeployPackagesResponse;
import org.rhq.core.domain.content.transfer.RemovePackagesResponse;
import org.rhq.core.domain.content.transfer.ResourcePackageDetails;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.pluginapi.content.ContentFacet;
import org.rhq.core.pluginapi.content.ContentServices;
import org.rhq.plugins.jbossas5.factory.ProfileServiceFactory;
import org.jboss.deployers.spi.management.deploy.DeploymentManager;
import org.jboss.deployers.spi.management.ManagementView;
import org.jboss.deployers.spi.management.KnownComponentTypes;
import org.jboss.managed.api.ManagedDeployment;

import java.io.InputStream;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

public class ContentDeploymentComponent implements ContentFacet
{
    public DeployPackagesResponse deployPackages(Set<ResourcePackageDetails> packages, ContentServices contentServices)
    {
        DeployPackagesResponse response = new DeployPackagesResponse(ContentResponseResult.SUCCESS);
        return response;
    }

    public Set<ResourcePackageDetails> discoverDeployedPackages(PackageType type)
    {
        Set<ResourcePackageDetails> discoveredPackages = new HashSet<ResourcePackageDetails>();
        return discoveredPackages;
    }

    public List<DeployPackageStep> generateInstallationSteps(ResourcePackageDetails packageDetails)
    {
        return null;
    }

    public RemovePackagesResponse removePackages(Set<ResourcePackageDetails> packages)
    {
        RemovePackagesResponse response = new RemovePackagesResponse(ContentResponseResult.SUCCESS);
        return response;
    }

    public InputStream retrievePackageBits(ResourcePackageDetails packageDetails)
    {
        return null;
    }
}
