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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import org.rhq.modules.plugins.jbossas7.json.ComplexResult;
import org.rhq.modules.plugins.jbossas7.json.Operation;
import org.rhq.modules.plugins.jbossas7.json.Result;

/**
 * Provide connections to the AS and reading / writing date from/to it.
 * @author Heiko W. Rupp
 */
public class ASConnection {

    private final Log log = LogFactory.getLog(ASConnection.class);
    URL url;
    String urlString;
    private ObjectMapper mapper;

    public ASConnection(String host, int port) {

        try {
            url = new URL("http",host,port,"/domain-api");
            urlString = url.toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        mapper = new ObjectMapper();
    }



    static boolean isErrorReply(JsonNode in) {
        if (in == null)
            return true;

        if (in.has("outcome")) {
            String outcome = null;
            try {
                JsonNode outcomeNode = in.findValue("outcome");
                outcome = outcomeNode.getTextValue();
                if (outcome.equals("failed")) {
                    JsonNode reasonNode = in.findValue("failure-description");

                    String reason = reasonNode.getTextValue();
//                    log.info(reason);
                    return true;
                }

            } catch (Exception e) {
                e.printStackTrace(); // TODO
                return true;
            }
        }
        return false;
    }

    /**
     * Execute an operation against the domain api
     * @return JsonNode that describes the result
     * @param operation an Operation that should be run on the domain controller
     */
    public JsonNode executeRaw(Operation operation) {

        InputStream inputStream = null;
        BufferedReader br=null;
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            OutputStream out = conn.getOutputStream();

            String result = mapper.writeValueAsString(operation);
            System.out.println("Json to send: " + result);
            System.out.flush();
            mapper.writeValue(out, operation);

            out.flush();
            out.close();

            int responseCode = conn.getResponseCode();
            if (responseCode ==HttpURLConnection.HTTP_OK) {
                inputStream = conn.getInputStream();
            } else {
                inputStream = conn.getErrorStream();
            }

            if (inputStream!=null) {

            br = new BufferedReader(new InputStreamReader(
                    inputStream));
            String line;
            StringBuilder builder = new StringBuilder();
            while ((line = br.readLine()) != null) {
                builder.append(line);
            }

            String outcome;
            JsonNode operationResult=null;
            if (builder !=null) {
                outcome= builder.toString();
                operationResult = mapper.readTree(outcome);
            }
            else {
                outcome="- no response from server -";
            }
            System.out.println("==> " + outcome);
            return operationResult;
            }
            else {
                System.err.println("IS was null and code was " + responseCode);
            }


        } catch (IOException e) {
            log.error("Failed to get data: " + e.getMessage()  );
        } finally {
            if (br!=null)
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();  // TODO: Customise this generated block
                }
        }

        return null;
    }

    public Result execute(Operation op, boolean isComplex){
        JsonNode node = executeRaw(op);

        try {
            Result res;
            if (isComplex)
                res = mapper.readValue(node,ComplexResult.class);
            else
                res = mapper.readValue(node,Result.class);
            return res;
        } catch (IOException e) {
            e.printStackTrace();  // TODO: Customise this generated block
            return null;
        }
    }


    private URL getBaseUrl(String base, String ops) throws MalformedURLException {
        String spec;
        URL url2;
        if (base!=null && !base.isEmpty()) {
            if (!base.startsWith("/")) {
                spec = urlString + "/" + base;
            }
            else {
                spec = urlString + base;
            }
            if (ops!=null) {
                if (!ops.startsWith("?"))
                    ops = "?" + ops;
                spec += ops;
            }

            url2 = new URL(spec);
        }
        else
            url2 = url;
        return url2;
    }

    public static String getFailureDescription(JsonNode jsonNode) {
        if (jsonNode==null)
            return "getFailureDescription: -input was null-";
        JsonNode node = jsonNode.findValue("failure-description");
        return node.getValueAsText();
    }

    public static String getSuccessDescription(JsonNode jsonNode) {
        if (jsonNode==null)
            return "No message found";
        JsonNode node = jsonNode.findValue("result");
        return node.getValueAsText();
    }
}
