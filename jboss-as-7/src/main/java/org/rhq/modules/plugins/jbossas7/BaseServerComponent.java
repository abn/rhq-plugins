/*
 * RHQ Management Platform
 * Copyright (C) 2005-2011 Red Hat, Inc.
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
package org.rhq.modules.plugins.jbossas7;

import java.io.File;
import java.net.ConnectException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.pluginapi.operation.OperationResult;
import org.rhq.core.pluginapi.util.ProcessExecutionUtility;
import org.rhq.core.system.ProcessExecution;
import org.rhq.core.system.ProcessExecutionResults;
import org.rhq.modules.plugins.jbossas7.json.Result;

/**
 * Base component for functionality that is common to Standalone AS and HostControllers
 * @author Heiko W. Rupp
 */
public class BaseServerComponent extends BaseComponent {

    private static final String STANDALONE_XML = "standalone.xml";
    private static final String SEPARATOR = "\n-----------------------\n";
    final Log log = LogFactory.getLog(BaseServerComponent.class);

    /**
     * Restart the server by first executing a 'shutdown' operation via its API. Then call
     * the #startServer method to start it again.
     *
     * @param parameters Parameters to pass to the (recursive) invocation of #invokeOperation
     * @param mode
     * @return State of execution
     * @throws Exception If anything goes wrong
     */
    protected OperationResult restartServer(Configuration parameters, Mode mode) throws Exception {
        OperationResult tmp = invokeOperation("shutdown",parameters);

        if (tmp.getErrorMessage()!=null) {
            tmp.setErrorMessage("Restart failed while failing to shut down: " + tmp.getErrorMessage());
            return tmp;
        }
        Thread.sleep(500); // Wait 0.5s -- this is plenty
        return startServer(mode);
    }

    /**
     * Start the server by calling the start script listed in the plugin configuration. If a different
     * config is given, this is passed via --server-config
     * @return State of Execution.
     * @param mode
     */
    protected OperationResult startServer(Mode mode) {
        OperationResult operationResult = new OperationResult();
        Configuration conf = context.getPluginConfiguration();
        String startScript = conf.getSimpleValue("startScript", mode.getStartScript());
        String baseDir = conf.getSimpleValue("baseDir","");
        if (baseDir.isEmpty()) {
            operationResult.setErrorMessage("No base directory provided");
            return operationResult;
        }
        String script = baseDir + File.separator + startScript;
        String config = conf.getSimpleValue("config", mode.getXmlFile());

        ProcessExecution processExecution;
        processExecution = ProcessExecutionUtility.createProcessExecution(new File(script));
        if (!config.equals(mode.getXmlFile())) {
            processExecution.getArguments().add(mode.getConfigArg());
            processExecution.getArguments().add(config);
        }
        processExecution.setWorkingDirectory(baseDir);
        processExecution.setCaptureOutput(true);
        processExecution.setWaitForCompletion(2000L); // 2 seconds // TODO: Should we wait longer than two seconds?
        processExecution.setKillOnTimeout(false);


        long start = System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            log.debug("About to execute the following process: [" + processExecution + "]");
        }
        ProcessExecutionResults results = context.getSystemInformation().executeProcess(processExecution);
        logExecutionResults(results);
        if (results.getError()!=null) {
            operationResult.setErrorMessage(results.getError().getMessage());
        } else {
            operationResult.setSimpleResult("Success");
        }

        return operationResult;

    }

    private void logExecutionResults(ProcessExecutionResults results) {
        // Always log the output at info level. On Unix we could switch depending on a exitCode being !=0, but ...
        log.info("Exit code from process execution: " + results.getExitCode());
        log.info("Output from process execution: " + SEPARATOR + results.getCapturedOutput() + SEPARATOR);
    }

    /**
     * Do some post processing of the Result - especially the 'shutdown' operation needs a special
     * treatment.
     * @param name Name of the operation
     * @param res Result of the operation vs. AS7
     * @return OperationResult filled in from values of res
     */
    protected OperationResult postProcessResult(String name, Result res) {
        OperationResult operationResult = new OperationResult();
        if (name.equals("shutdown")) {
            /*
             * Shutdown needs a special treatment, because after sending the operation, if shutdown suceeds,
             * the server connection is closed and we can't read from it. So if we get connection refused for
             * reading, this is a good sign.
             */
            if (!res.isSuccess()) {
                if (res.getThrowable()!=null && (res.getThrowable() instanceof ConnectException || res.getThrowable().getMessage().equals("Connection refused")))
                    operationResult.setSimpleResult("Success");
                else
                    operationResult.setErrorMessage(res.getFailureDescription());
            }
        }
        else {
            if (res.isSuccess()) {
                if (res.getResult()!=null)
                    operationResult.setSimpleResult(res.getResult().toString());
                else
                    operationResult.setSimpleResult("-None provided by server-");
            }
            else
                operationResult.setErrorMessage(res.getFailureDescription());
        }
        return operationResult;
    }

    enum Mode {
        STANDALONE(STANDALONE_XML,"standalone","--server-config","bin/standalone.sh"),
        DOMAIN("domain.xml","domain", "--domain-config","bin/domain.sh"),
        HOST("host.xml","domain", "--host-config","bin/domain.sh");

        private String xmlFile;
        private String baseDir;
        private String configArg;
        private String startScript;

        private Mode(String xmlFile, String baseDir, String configArg, String startScript) {
            this.xmlFile = xmlFile;
            this.baseDir = baseDir;
            this.configArg = configArg;
            this.startScript = startScript;
        }

        public String getXmlFile() {
            return xmlFile;
        }

        public String getBaseDir() {
            return baseDir;
        }

        public String getConfigArg() {
            return configArg;
        }

        public String getStartScript() {
            return startScript;
        }
    }
}
