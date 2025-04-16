/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.reporter;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;
import org.json.JSONException;

import spade.core.AbstractReporter;
import spade.core.AbstractEdge;
import spade.core.AbstractVertex;

// Prov format vertices
import spade.vertex.prov.Activity;
import spade.vertex.prov.Agent;
import spade.vertex.prov.Entity;
import spade.edge.prov.Used;
import spade.edge.prov.WasGeneratedBy;
import spade.edge.prov.WasAssociatedWith;
import spade.edge.prov.WasDerivedFrom;

import spade.core.Settings;
import spade.utility.HelperFunctions;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import spade.reporter.P4StateTable;

/**
 * Provenance reporter for P4.
 *
 * @author Dhiraj Saharia
 */

public class P4 extends AbstractReporter {
    private Logger logger = Logger.getLogger(P4.class.getName());
    // Settings keys
    private static final String keyHostname = "rabbitmqHost", keyPort = "rabbitmqPort",
            keyUsername = "rabbitmqUsername", keyPassword = "rabbitmqPassword", keyQueueName = "rabbitmqQueueName";
    // Annotation keys
    private static final String keyAnnotationName = "name", keyAnnotationPID = "pid",
            keyAnnotationTimestamp = "timestamp", keyIndex = "index", keyValue = "value",
            keyActivityType = "activity_type", keyAgentType = "agent_type", keyAgentID = "agent_id";
    // Indices to correctly parse the message data
    private static final int indexEventType = 0, indexName = 1, indexAgentId = 2, indexTimestamp = 3,
            indexType = 4, indexCallType = 5, indexEntityData = 6;
    // Configure the RabbitMQ parameters once
    private volatile boolean shutdown = false;
    final Map<String, Activity> activityMap = new HashMap<String, Activity>();
    final Map<String, Agent> agentMap = new HashMap<String, Agent>();
    final Map<String, String> map = new HashMap<String, String>();

    // Initialize the state table
    final P4StateTable stateTable = new P4StateTable();

    ConnectionFactory factory = null;
    Connection connection = null;
    Channel channel = null;
    // Nodes and Edges
    AbstractVertex vertex = null;
    AbstractEdge edge = null;
    Agent currentAgent = null;

    DeliverCallback deliverCallback = (consumerTag, delivery) -> {
        String message = "";
        try {
            message = new String(delivery.getBody(), "UTF-8");
            logger.log(Level.INFO, " [x] Received '" + message + "'");
            parseEvent(message);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to parse message body - " + message + "Error - " + e);
        }
    };

    private void initializeRabbitMQ(Map<String, String> configMap) {
        final String inputHostName = configMap.remove(keyHostname);
        final int inputPort = Integer.parseInt(configMap.remove(keyPort));
        final String inputUsername = configMap.remove(keyUsername);
        final String inputPassword = configMap.remove(keyPassword);
        final String inputQueueName = configMap.get(keyQueueName);
        logger.log(Level.INFO,
                "Arguments: [" + keyHostname + "=" + inputHostName + ", " + keyPort + "=" + inputPort + ", "
                        + keyUsername + "=" + inputUsername + ", " + keyPassword + "=" + inputPassword + ", "
                        + keyQueueName + "=" + inputQueueName + " ]");
        factory = new ConnectionFactory();
        factory.setHost(inputHostName);
        factory.setPort(inputPort);
        factory.setUsername(inputUsername);
        factory.setPassword(inputPassword);
        try {
            connection = factory.newConnection();
            try {
                channel = connection.createChannel();
                channel.queueDeclare(inputQueueName, false, false, false, null);
                logger.log(Level.INFO, "RabbitMQ queue declared successfully");
            } catch (Exception error) {
                logger.log(Level.WARNING, "Failed to initialize connection" + error.toString());
            }
        } catch (Exception error) {
            logger.log(Level.WARNING, "Failed to initialize connection" + error);
        }
    }

    private void parseEvent(String data) {
        if (data == null || data.isEmpty()) {
            logger.log(Level.SEVERE, "Received null or empty data in parseEvent.");
            return;
        }
        logger.log(Level.INFO, map.toString());
        /*
         * Event message format for reference
         * Activity -
         * ACTIVITY|<activity_name>|<pid>|<timestamp>|<entity_type>
         * Agent - AGENT|<agent_name>|pid|<timestamp>|<entity_type>
         * Entity -
         * ENTITY|<entity_name>|pid|<timestamp>|<entity_type>|<entity_operation>|<
         * operation_params>
         * Here, <entity_type> -> {MATRule, Reg, Counter, Data Structure}
         * <entity_operation> -> {READ,WRITE,ADD}
         */

        String[] parts = data.split("\\|");

        final Map<String, String> annotationsMap = new HashMap<String, String>();
        final Map<String, String> edgeAnnotationMap = new HashMap<String, String>();
        String entityIndex = "", entityValue = "";

        // TODO - put the indices into const
        String eventType = parts[indexEventType];
        String nodeName = parts[indexName];
        annotationsMap.put(keyAnnotationName, nodeName);
        String currentID = parts[indexAgentId];

        if (eventType.equals("ACTIVITY")) {
            String currentActivityType = parts[indexType];
            annotationsMap.put(keyActivityType, currentActivityType);
            vertex = new Activity();
            activityMap.put(currentID, (Activity) vertex);
            // annotationsMap.put("agent_id", currentAgent.getAnnotation(keyAgentID));
            annotationsMap.put("agent_id", currentID);

            processVertex(vertex, annotationsMap);
            if ((currentActivityType.equals("EventProcessor")
                    && currentAgent.getAnnotation(keyAgentType).equals("Controller"))
                    || (currentActivityType.equals("PacketProcessor")
                            && currentAgent.getAnnotation(keyAgentType).equals("Switch"))) {
                WasAssociatedWith activityEdge = new WasAssociatedWith((Activity) vertex, currentAgent);
                // logger.log(Level.INFO, "IDS: " + currentID + " " +
                // currentAgent.getAnnotation(keyAgentID));
                if (currentID.equals(currentAgent.getAnnotation(keyAgentID))) {
                    // logger.log(Level.INFO, "here");
                    processEdge(activityEdge, edgeAnnotationMap);
                }
            }

        } else if (eventType.equals("AGENT")) {
            vertex = new Agent();
            annotationsMap.put(keyAgentType, parts[indexType]);
            annotationsMap.put(keyAgentID, currentID);
            // if PKT_IN --> add the port number to agent
            processVertex(vertex, annotationsMap);
            currentAgent = (Agent) vertex;

        } else if (eventType.equals("ENTITY")) {
            // We have to handle different entities separately because they have different
            // data inside them
            final Map<String, String> entityParams = parseEntityData(parts[parts.length -
                    1], parts[indexType]);
            String entityOperation = parts[indexCallType];
            String entity_type = parts[indexType];
            vertex = new Entity();
            if (entity_type.equals("packet_in")) {
                annotationsMap.put("src_mac", entityParams.get("src_mac"));
                annotationsMap.put("ingress_port", entityParams.get("ingress_port"));
                annotationsMap.put("ether_type", entityParams.get("ether_type"));
                annotationsMap.put("lldp_id", entityParams.get("lldp_id"));
                // TODO --> Remove agent id on pkt_in

            } else if (entity_type.equals("table_rule")) {
                annotationsMap.put("ingress_port", entityParams.get("ingress_port"));
                annotationsMap.put("egress_port", entityParams.get("egress_port"));
                annotationsMap.put("ether_type", entityParams.get("ether_type"));
            } else if (entity_type.equals("packet_out")) {
                annotationsMap.put("src_mac", entityParams.get("src_mac"));
                annotationsMap.put("egress_port", entityParams.get("egress_port"));
                annotationsMap.put("ether_type", entityParams.get("ether_type"));
                annotationsMap.put("lldp_id", entityParams.get("lldp_id"));
            } else {
                // Add register values
                entityIndex = entityParams.get(keyIndex);
                entityValue = entityParams.get(keyValue);
                annotationsMap.put(keyIndex, entityIndex);
                annotationsMap.put(keyValue, entityValue);
            }
            annotationsMap.put("entity_type", parts[indexType]);
            annotationsMap.put(keyAgentID, currentID);
            processVertex(vertex, annotationsMap);

            // Entity previousNode = stateTable.getEntity(parts[indexName], currentPID,
            // entityIndex);
            if (entity_type.equals("register")) {
                // NOTE - wDF edges are defined for stateful memory only - Registers
                AbstractEdge WDFedge = new WasDerivedFrom(null, null);
                WDFedge = stateTable.performStateCheck(entityOperation, entity_type, (Entity) vertex);
                if (WDFedge != null) {
                    putEdge(WDFedge);
                }
            }

            // New State table algorithm

            // String currentEntityValue = entityValue;
            // String previousEntityValue = previousNode.getAnnotation(keyValue);

            // if (previousEntityValue == null) {
            // logger.log(Level.WARN, "Previous Entity value NULL detected...");
            // }
            // if (entityOperation.equals("WRITE")) {
            // if !(currentEntityValue.equals(previousEntityValue)) {
            // // WasDerivedFrom Edge
            // AbstractEdge edge = new WasDerivedFrom((Entity) vertex, previousNode);
            // putEdge(edge);
            // logger.log(Level.INFO, "WasDerivedFrom Edge: " + edge);
            // // Add new value to state
            // stateTable.put(parts[indexName], currentPID, entityIndex, (Entity) vertex);
            // }

            // edgeAnnotationMap.put(keyValue, entityParams.get(keyValue));
            // edgeAnnotationMap.put(keyAnnotationTimestamp, parts[3]);
            // edgeAnnotationMap.put(keyIndex, entityParams.get(keyIndex));
            // edgeAnnotationMap.put(keyValue, entityParams.get(keyValue));
            // if (activityMap.get(keyAnnotationPID) == null) {
            // logger.log(Level.SEVERE, "Activity does not have corresponding PID.." +
            // keyAnnotationPID);
            // return;
            // }
            edge = parseEdge(entityOperation, (Entity) vertex,
                    activityMap.get(currentID));
            logger.log(Level.INFO,
                    "edgeIDs: " + currentID + " " + activityMap.get(currentID).getAnnotation(keyAgentID));
            if ((edge != null) && (currentID.equals(activityMap.get(currentID).getAnnotation(keyAgentID)))) {
                logger.log(Level.INFO, "edge - " + edge);
                processEdge(edge, edgeAnnotationMap);
            }

        } else {
            logger.log(Level.SEVERE, "Event not recognized" + eventType);
        }
    }

    private Map<String, String> parseEntityData(String entityData, String entity_type) {
        Map<String, String> entityParams = new HashMap<String, String>();
        // logger.log(Level.INFO, "Entity type - " + entity_type);
        try {
            JSONObject jsonObject = new JSONObject(entityData);
            try {
                if (entity_type.equals("MATRule")) {
                    JSONObject value = jsonObject.getJSONObject("value");
                    entityParams.put(keyIndex, String.valueOf(value.get("match_key")));
                    entityParams.put(keyValue, String.valueOf(jsonObject.get(keyValue)));
                } else if (entity_type.equals("packet_in")) {
                    entityParams.put("src_mac",
                            jsonObject.has("src_mac") ? String.valueOf(jsonObject.get("src_mac")) : "0");
                    entityParams.put("lldp_id",
                            jsonObject.has("lldp_id") ? String.valueOf(jsonObject.get("lldp_id")) : "0");
                    entityParams.put("ingress_port",
                            jsonObject.has("ingress_port") ? String.valueOf(jsonObject.get("ingress_port"))
                                    : "-1");
                    entityParams.put("ether_type",
                            jsonObject.has("ether_type") ? String.valueOf(jsonObject.get("ether_type"))
                                    : "-1");
                } else if (entity_type.equals("table_rule")) {
                    // JSONObject value = jsonObject.getJSONObject("value");
                    entityParams.put("ingress_port", String.valueOf(jsonObject.get("ingress_port")));
                    entityParams.put("egress_port", String.valueOf(jsonObject.get("egress_port")));
                    entityParams.put("ether_type", String.valueOf(jsonObject.get("ether_type")));
                } else if (entity_type.equals("packet_out")) {
                    entityParams.put("src_mac",
                            jsonObject.has("src_mac") ? String.valueOf(jsonObject.get("src_mac")) : "0");
                    entityParams.put("lldp_id",
                            jsonObject.has("lldp_id") ? String.valueOf(jsonObject.get("lldp_id")) : "0");
                    entityParams.put("egress_port",
                            jsonObject.has("egress_port") ? String.valueOf(jsonObject.get("egress_port"))
                                    : "-1");
                    entityParams.put("ether_type",
                            jsonObject.has("ether_type") ? String.valueOf(jsonObject.get("ether_type"))
                                    : "-1");
                } else {
                    entityParams.put(keyIndex, String.valueOf(jsonObject.get(keyIndex)));
                    entityParams.put(keyValue, String.valueOf(jsonObject.get(keyValue)));
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Cannot find entity params", e);
            }
        } catch (JSONException err) {
            logger.log(Level.WARNING, err.toString());
        }
        return entityParams;
    }

    private void processVertex(AbstractVertex vertex, Map<String, String> map) {
        vertex.addAnnotations(map);
        putVertex(vertex);
    }

    private void processEdge(AbstractEdge edge, Map<String, String> map) {
        edge.addAnnotations(map);
        putEdge(edge);
    }

    private AbstractEdge parseEdge(String operation, Entity vertex, Activity currentActivity) {
        AbstractEdge edge = null;
        if (operation.equals("READ")) {
            edge = new Used(currentActivity, (Entity) vertex);
        } else if ((operation.equals("WRITE")) || (operation.equals("ADD"))) {
            edge = new WasGeneratedBy((Entity) vertex, currentActivity);
        } else {
            logger.log(Level.WARNING, "Operation not recognized.." + operation);
        }
        return edge;
    }

    @Override
    public boolean launch(String arguments) {
        logger.log(Level.INFO, "P4 reporter started");
        /* Extract the RabbitMQ settings from cfg file */
        try {
            final String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());
            map.putAll(HelperFunctions.parseKeyValuePairsFrom(arguments, configFilePath, null));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to parse arguments and/or storage config file" + e);
            return false;
        }
        initializeRabbitMQ(map);
        try {
            final Thread thread = new Thread(main, this.getClass().getSimpleName() + "-reporter-thread");
            thread.start();

        } catch (Exception e) {
            logger.log(Level.WARNING, "thread error");
            shutdown();
            // throw new Exception("Failed to start main thread", e);
        }
        return true;
    }

    private final Runnable main = new Runnable() {
        public void run() {
            try {
                while (!isShutdown()) {
                    try {
                        channel.basicConsume(map.get(keyQueueName), true, deliverCallback, consumerTag -> {
                        });
                    } catch (Exception error) {
                        logger.log(Level.WARNING, "Failed to consume message from queue - " + error);
                        // return;
                    }
                }
            } finally {
                logger.log(Level.INFO, "Exited main thread");
            }
        }

    };

    private final boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean shutdown() {
        shutdown = true;
        // TODO - close connection function
        logger.log(Level.INFO, "Closing channel and connection");
        // channel.close();
        // connection.close();
        return true;
    }

}