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
import spade.edge.prov.WasAttributedTo;

import spade.core.Settings;
import spade.utility.HelperFunctions;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

/**
 * Prov reporter for P4.
 *
 * @author Dhiraj Saharia
 */

public class P4 extends AbstractReporter {
    private Logger logger = Logger.getLogger(P4.class.getName());
    // Settings keys
    private static final String keyHostname = "rabbitmqHost", keyPort = "rabbitmqPort",
            keyUsername = "rabbitmqUsername", keyPassword = "rabbitmqPassword", keyQueueName = "rabbitmqQueueName";
    private static final String keyAnnotationName = "name", keyAnnotationPID = "pid",
            keyAnnotationTimestamp = "timestamp", keyIndex = "index", keyValue = "value";
    // Configure the RabbitMQ parameters once
    private volatile boolean shutdown = false;
    final Map<String, Activity> activityMap = new HashMap<String, Activity>();
    final Map<String, String> map = new HashMap<String, String>();
    ConnectionFactory factory = null;
    Connection connection = null;
    Channel channel = null;
    // Nodes and Edges
    AbstractVertex vertex = null;
    AbstractEdge edge = null;

    DeliverCallback deliverCallback = (consumerTag, delivery) -> {
        try {
            String message = new String(delivery.getBody(), "UTF-8");
            logger.log(Level.INFO, " [x] Received '" + message + "'");
            parseEvent(message);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to parse message body", e);
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
                logger.log(Level.WARNING, "Failed to initialize connection", error.toString());
            }
        } catch (Exception error) {
            logger.log(Level.WARNING, "Failed to initialize connection", error);
        }
    }

    private void parseEvent(String data) {
        if (data == null || data.isEmpty()) {
            logger.log(Level.SEVERE, "Received null or empty data in parseEvent.");
            return;
        }
        /*
         * Event message format for reference
         * Activity - ACTIVITY|<activity_name>|<pid>|<timestamp>|<entity_type>
         * Agent - AGENT|<agent_name>|pid|<timestamp>|<entity_type>
         * Entity -
         * ENTITY|<entity_name>|pid|<timestamp>|<entity_type>|<entity_operation>|<
         * operation_params>
         * Here, <entity_type> -> {MATRule, Reg, Counter, Data Structure}
         * <entity_operation> -> {READ,WRITE,ADD}
         */

        String[] parts = data.split("\\|");
        // logger.log(Level.INFO, "parts: " + parts);

        final Map<String, String> annotationsMap = new HashMap<String, String>();
        final Map<String, String> edgeAnnotationMap = new HashMap<String, String>();

        // TODO - put the indices into const
        String eventType = parts[0];
        annotationsMap.put(keyAnnotationName, parts[1]);
        annotationsMap.put(keyAnnotationPID, parts[2]);
        // logger.log(Level.INFO, "annotations map " + annotationsMap.toString());

        if (eventType.equals("ACTIVITY")) {
            vertex = new Activity();
            // Save the current activity for connecting different entity events
            activityMap.put(annotationsMap.get(keyAnnotationPID), (Activity) vertex);
            processVertex(vertex, annotationsMap);
        } else if (eventType.equals("AGENT")) {
            vertex = new Agent();
            processVertex(vertex, annotationsMap);
        } else if (eventType.equals("ENTITY")) {
            vertex = new Entity();
            processVertex(vertex, annotationsMap);
            final Map<String, String> entityParams = parseEntityData(parts[parts.length -
                    1]);

            edgeAnnotationMap.put(keyAnnotationTimestamp, parts[3]);
            edgeAnnotationMap.put(keyIndex, entityParams.get(keyIndex));
            edgeAnnotationMap.put(keyValue, entityParams.get(keyValue));
            logger.log(Level.INFO, "pid " + annotationsMap.get(keyAnnotationPID));
            logger.log(Level.INFO, "activity map: " + activityMap);
            logger.log(Level.INFO, "activity map pid: " + activityMap.get(annotationsMap.get(keyAnnotationPID)));
            // if (activityMap.get(keyAnnotationPID) == null) {
            // logger.log(Level.SEVERE, "Activity does not have corresponding PID.." +
            // keyAnnotationPID);
            // return;
            // }
            edge = parseEdge(parts[5], (Entity) vertex,
                    activityMap.get(annotationsMap.get(keyAnnotationPID)));
            if (edge != null) {
                processEdge(edge, edgeAnnotationMap);
            }

        } else {
            logger.log(Level.SEVERE, "Event not recognized", eventType);
        }
    }

    private Map<String, String> parseEntityData(String entityData) {
        Map<String, String> entityParams = new HashMap<String, String>();

        try {
            JSONObject jsonObject = new JSONObject(entityData);
            try {
                entityParams.put(keyIndex, String.valueOf(jsonObject.get(keyIndex)));
                entityParams.put(keyValue, String.valueOf(jsonObject.get(keyValue)));

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Cannot find entity params as index and value", e);
            }
        } catch (JSONException err) {
            logger.log(Level.WARNING, err.toString());
        }
        return entityParams;
    }

    private void processVertex(AbstractVertex vertex, Map<String, String> map) {
        // logger.log(Level.INFO, "process vertex");
        vertex.addAnnotations(map);
        putVertex(vertex);
    }

    private void processEdge(AbstractEdge edge, Map<String, String> map) {
        // logger.log(Level.INFO, "process edge");
        edge.addAnnotations(map);
        putEdge(edge);
    }

    private AbstractEdge parseEdge(String operation, Entity vertex, Activity currentActivity) {
        AbstractEdge edge = null;
        logger.log(Level.INFO, "activity: " + currentActivity);

        if (operation.equals("READ")) {
            edge = new Used(currentActivity, vertex);

        } else if ((operation.equals("WRITE")) | (operation.equals("ADD"))) {
            edge = new WasGeneratedBy(vertex, currentActivity);
        } else {
            logger.log(Level.WARNING, "Operation not recognized..", operation);
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
            logger.log(Level.SEVERE, "Failed to parse arguments and/or storage config file", e);
            return false;
        }
        initializeRabbitMQ(map);
        try {
            final Thread thread = new Thread(main, this.getClass().getSimpleName() + "-reporter-thread");
            thread.start();

        } catch (Exception e) {
            shutdown();
            // throw new Exception("Failed to start main thread", e);
        }
        return true;
    }

    private final Runnable main = new Runnable() {
        @Override
        public void run() {
            try {
                while (!isShutdown()) {
                    try {
                        channel.basicConsume(map.get(keyQueueName), true, deliverCallback,
                                consumerTag -> {
                                });
                    } catch (Exception error) {
                        logger.log(Level.WARNING, "Failed to consume message from queue", error);
                    }
                    // logger.log(Level.INFO, "Main thread started");
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
