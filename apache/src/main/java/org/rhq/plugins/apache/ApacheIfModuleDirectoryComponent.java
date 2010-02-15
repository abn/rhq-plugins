package org.rhq.plugins.apache;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.augeas.node.AugeasNode;
import org.rhq.augeas.tree.AugeasTree;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.ConfigurationUpdateStatus;
import org.rhq.core.domain.configuration.definition.ConfigurationDefinition;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.pluginapi.configuration.ConfigurationFacet;
import org.rhq.core.pluginapi.configuration.ConfigurationUpdateReport;
import org.rhq.core.pluginapi.inventory.DeleteResourceFacet;
import org.rhq.core.pluginapi.inventory.InvalidPluginConfigurationException;
import org.rhq.core.pluginapi.inventory.ResourceComponent;
import org.rhq.core.pluginapi.inventory.ResourceContext;
import org.rhq.plugins.apache.mapping.ApacheAugeasMapping;
import org.rhq.plugins.apache.util.AugeasNodeSearch;

public class ApacheIfModuleDirectoryComponent implements ResourceComponent<ApacheDirectoryComponent>, ConfigurationFacet, DeleteResourceFacet{ 

    private ResourceContext<ApacheDirectoryComponent> context;
    private List<String> position;
    private ApacheDirectoryComponent parentComponent; 
    private final Log log = LogFactory.getLog(this.getClass());
    private static final String IFMODULE_DIRECTIVE_NAME="<IfModule"; 
    
    public void start(ResourceContext<ApacheDirectoryComponent> context)
        throws InvalidPluginConfigurationException, Exception {
        
      this.context = context;    
      parentComponent = context.getParentResourceComponent();
      String resourceKey = context.getResourceKey();
      position = new ArrayList<String>();
      
      for (String pm : resourceKey.split(";")){
          position.add(pm);
      }
    }

    public void stop() {
    }

    public AvailabilityType getAvailability() {
       return parentComponent.getAvailability();
    }

    public Configuration loadResourceConfiguration() throws Exception {       
        ConfigurationDefinition resourceConfigDef = context.getResourceType().getResourceConfigurationDefinition();
        
        AugeasNode directoryNode = parentComponent.getNode();
        AugeasTree tree = parentComponent.getServerConfigurationTree();
        ApacheAugeasMapping mapping = new ApacheAugeasMapping(tree);
        return mapping.updateConfiguration(getNode(directoryNode), resourceConfigDef);
    }

    public void updateResourceConfiguration(ConfigurationUpdateReport report) {
        AugeasTree tree = null;
        try {
            tree = parentComponent.getServerConfigurationTree();
            ConfigurationDefinition resourceConfigDef = context.getResourceType()
                .getResourceConfigurationDefinition();
            ApacheAugeasMapping mapping = new ApacheAugeasMapping(tree);
            AugeasNode directoryNode = getNode(parentComponent.getNode());
            mapping.updateAugeas(directoryNode, report.getConfiguration(), resourceConfigDef);
            tree.save();

            report.setStatus(ConfigurationUpdateStatus.SUCCESS);
            log.info("Apache configuration was updated");
            
            context.getParentResourceComponent().finishConfigurationUpdate(report);
        } catch (Exception e) {
            if (tree != null)
                log.error("Augeas failed to save configuration " + tree.summarizeAugeasError());
            else
                log.error("Augeas failed to save configuration", e);
            report.setStatus(ConfigurationUpdateStatus.FAILURE);
        }
   }
    public void deleteResource() throws Exception {
    
    }
    
    private AugeasNode getNode(AugeasNode virtualHost) {
        List<AugeasNode> directories = AugeasNodeSearch.getNodeByParentParams(virtualHost, IFMODULE_DIRECTIVE_NAME, position);
        return directories.get(0);
      }
}

