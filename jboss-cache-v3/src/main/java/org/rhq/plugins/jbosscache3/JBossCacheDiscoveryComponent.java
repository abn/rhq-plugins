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
package org.rhq.plugins.jbosscache3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.mc4j.ems.connection.EmsConnection;
import org.mc4j.ems.connection.bean.EmsBean;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.pluginapi.inventory.DiscoveredResourceDetails;
import org.rhq.core.pluginapi.inventory.InvalidPluginConfigurationException;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryComponent;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryContext;
import org.rhq.plugins.jbossas5.ProfileServiceComponent;
import org.rhq.plugins.jmx.ObjectNameQueryUtility;

/**
 * 
 * @author Filip Drabek
 * 
 */
public class JBossCacheDiscoveryComponent implements
		ResourceDiscoveryComponent<ProfileServiceComponent> {

	private Configuration defaultConfig;
	private String REGEX = "(,|:)jmx-resource=[^,]*(,|\\z)";
	private static String[] CACHE_OBJECT_NAME = { "cluster", "config" };

	public Set<DiscoveredResourceDetails> discoverResources(
			ResourceDiscoveryContext<ProfileServiceComponent> context)
			throws InvalidPluginConfigurationException, Exception {

		ProfileServiceComponent parentComponent = context
				.getParentResourceComponent();

		this.defaultConfig = context.getDefaultPluginConfiguration();

		List<Configuration> configurations = context.getPluginConfigurations();

		EmsConnection connection = parentComponent.getEmsConnection();

		Set<DiscoveredResourceDetails> resources = new HashSet<DiscoveredResourceDetails>();

		Pattern p = Pattern.compile(REGEX);

		if (configurations.isEmpty())
			configurations.add(defaultConfig);

		for (Configuration config : configurations) {

			ObjectNameQueryUtility queryUtility = new ObjectNameQueryUtility(
					getValue(config, JBossCacheComponent.CACHE_SEARCH_STRING));

			List<EmsBean> cacheBeans = connection.queryBeans(queryUtility
					.getTranslatedQuery());

			HashSet<String> names = new HashSet<String>();

			for (EmsBean bean : cacheBeans) {
				String beanName = bean.getBeanName().toString();

				Matcher m = p.matcher(beanName);
				if (m.find()) {
					beanName = m.replaceFirst(m.group(2).equals(",") ? m
							.group(1) : "");

					if (!names.contains(beanName)) {
						names.add(beanName);
					}
				}
			}

			for (String key : names) {
				Configuration conf = new Configuration();

				conf.put(new PropertySimple(
						JBossCacheComponent.CACHE_SEARCH_STRING, key));

				String resourceName = key;
				ObjectName obName = new ObjectName(key);
				for (String objectName : CACHE_OBJECT_NAME) {
					if (obName.getKeyProperty(objectName) != null)
						resourceName = obName.getKeyProperty(objectName);
				}

				ResourceType resourceType = context.getResourceType();
				resources.add(new DiscoveredResourceDetails(resourceType, key,
						resourceName, "", "JBoss Cache", conf, null));

			}
		}
		return resources;
	}

	private String getValue(Configuration config, String name) {

		if (config.get(name) != null)
			return config.getSimple(name).getStringValue();
		else
			return defaultConfig.getSimple(name).getStringValue();
	}

}
