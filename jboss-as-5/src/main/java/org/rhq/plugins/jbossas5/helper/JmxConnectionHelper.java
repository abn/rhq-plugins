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
package org.rhq.plugins.jbossas5.helper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mc4j.ems.connection.ConnectionFactory;
import org.mc4j.ems.connection.EmsConnection;
import org.mc4j.ems.connection.settings.ConnectionSettings;
import org.mc4j.ems.connection.support.ConnectionProvider;
import org.mc4j.ems.connection.support.metadata.ConnectionTypeDescriptor;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.plugins.jmx.JMXDiscoveryComponent;

import java.util.Properties;

/**
 * Shared helper class to connect to a remote server
 *
 * @author Heiko W. Rupp
 */
public class JmxConnectionHelper {

   private static final Log log = LogFactory.getLog(JmxConnectionHelper.class);

   private static EmsConnection connection;
   private static Configuration configuration;

   /**
    * Controls the dampening of connection error stack traces in an attempt to control spam to the log file. Each time
    * a connection error is encountered, this will be incremented. When the connection is finally established, this
    * will be reset to zero.
    */
   private static int consecutiveConnectionErrors;


   /**
    * Obtain an EmsConnection for the passed connection properties. The properties will be retained.
    * To create a connection with different properties, use this method again with a different set
    * of properties.
    * @param config Configuration properties for this connection
    * @return an EmsConnection or null in case of failure
    * @see #getEmsConnection()
    */
   public EmsConnection getEmsConnection(Configuration config) {
      EmsConnection emsConnection = null;
      configuration = config;

      try {
         emsConnection = loadConnection(config);
      } catch (Exception e) {
         log.error("Component attempting to access a connection that could not be loaded");
      }

      return emsConnection;
   }


   /**
    * Obtain an EmsConnection. This will only work if the connection properties have passed
    * before via a call to {@link #getEmsConnection(Configuration)}
    * @return an EmsConnection or null in case of failure
    * @see #getEmsConnection(org.rhq.core.domain.configuration.Configuration)
    */
      public EmsConnection getEmsConnection() {
         EmsConnection emsConnection = null;
         if (configuration==null) {
            throw new RuntimeException("No configuration set");
         }

         try {
            emsConnection = loadConnection(configuration);
         } catch (Exception e) {
            log.error("Component attempting to access a connection that could not be loaded");
         }

         return emsConnection;
   }


   /**
    * This is the preferred way to use a connection from within this class; methods should not access the connection
    * property directly as it may not have been instantiated if the connection could not be made.
    * <p/>
    * <p>If the connection has already been established, return the object reference to it. If not, attempt to make a
    * live connection to the JMX server.</p>
    * <p/>
    * <p>If the connection could not be made in the start(org.rhq.core.pluginapi.inventory.ResourceContext) method,
    * this method will effectively try to load the connection on each attempt to use it. As such, multiple threads may
    * attempt to access the connection through this means at a time. Therefore, the method has been made synchronized
    * on instances of the class.</p>
    * <p/>
    * <p>If any errors are encountered, this method will log the error, taking into account logic to prevent spamming
    * the log file. Calling methods should take care to not redundantly log the exception thrown by this method.</p>
    *
    * @param pluginConfig
    * @return live connection to the JMX server; this will not be <code>null</code>
    * @throws Exception if there are any issues at all connecting to the server
    */
   private static synchronized EmsConnection loadConnection(Configuration pluginConfig) throws Exception {
      if (connection == null) {
         try {
            //Configuration pluginConfig = this.resourceContext.getPluginConfiguration();

            ConnectionSettings connectionSettings = new ConnectionSettings();

            String connectionTypeDescriptorClass = pluginConfig.getSimple(JMXDiscoveryComponent.CONNECTION_TYPE)
                  .getStringValue();
            PropertySimple serverUrl = pluginConfig
                  .getSimple(JMXDiscoveryComponent.CONNECTOR_ADDRESS_CONFIG_PROPERTY);

            connectionSettings.initializeConnectionType((ConnectionTypeDescriptor) Class.forName(
                  connectionTypeDescriptorClass).newInstance());
            // if not provided use the default serverUrl
            if (null != serverUrl) {
               connectionSettings.setServerUrl(serverUrl.getStringValue());
            }

//                connectionSettings.setPrincipal(pluginConfig.getSimpleValue(PRINCIPAL_CONFIG_PROP, null));
//                connectionSettings.setCredentials(pluginConfig.getSimpleValue(CREDENTIALS_CONFIG_PROP, null));

            if (connectionSettings.getAdvancedProperties() == null) {
               connectionSettings.setAdvancedProperties(new Properties());
            }

            ConnectionFactory connectionFactory = new ConnectionFactory();


            ConnectionProvider connectionProvider = connectionFactory.getConnectionProvider(connectionSettings);
            connection = connectionProvider.connect();

            connection.loadSynchronous(false); // this loads all the MBeans

            consecutiveConnectionErrors = 0;

            if (log.isDebugEnabled())
               log.debug("Successfully made connection to the remote server instance");
         } catch (Exception e) {

            // The connection will be established even in the case that the principal cannot be authenticated,
            // but the connection will not work. That failure seems to come from the call to loadSynchronous after
            // the connection is established. If we get to this point that an exception was thrown, close any
            // connection that was made and null it out so we can try to establish it again.
            if (connection != null) {
               if (log.isDebugEnabled())
                  log.debug("Connection created but an exception was thrown. Closing the connection.", e);
               connection.close();
               connection = null;
            }

            // Since the connection is attempted each time it's used, failure to connect could result in log
            // file spamming. Log it once for every 10 consecutive times it's encountered.
            if (consecutiveConnectionErrors % 10 == 0) {
               log.warn("Could not establish connection to the instance ["
                     + (consecutiveConnectionErrors + 1) + "] times.", e);
            }

            if (log.isDebugEnabled())
               log.debug("Could not connect to the instance for resource ", e);

            consecutiveConnectionErrors++;

            throw e;
         }
      }

      return connection;
   }


   /**
    * If necessary attempt to close the EMS connection, then set this.connection null.  Synchronized ensure we play
    * well with loadConnection.
    */
   public synchronized void closeConnection() {
      if (connection != null) {
         try {
            connection.close();
         } catch (Exception e) {
            log.error("Error closing EMS connection: " + e);
         }
         connection = null;
      }
    }


}
