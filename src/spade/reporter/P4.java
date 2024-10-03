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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

// import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
// import spade.core.AbstractVertex;
// import spade.core.Edge;
// import spade.core.Vertex;
// import spade.utility.Execute;
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
    // Configure the RabbitMQ parameters once
    private volatile boolean shutdown;
    ConnectionFactory factory = null;
    Connection connection = null;
    Channel channel = null;

    DeliverCallback deliverCallback = (consumerTag, delivery) -> {
        String message = new String(delivery.getBody(), "UTF-8");
        parseEvent(message);
        logger.log(Level.INFO, " [x] Received '" + message + "'");
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
        String[] parts = data.split("\\|");
    }

    @Override
    public boolean launch(String arguments) {
        logger.log(Level.INFO, "P4 reporter started");
        /* Extract the RabbitMQ settings from cfg file */
        final Map<String, String> map = new HashMap<String, String>();
        try {
            final String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());
            map.putAll(HelperFunctions.parseKeyValuePairsFrom(arguments, configFilePath, null));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to parse arguments and/or storage config file", e);
            return false;
        }
        initializeRabbitMQ(map);

        while (!shutdown) {
            try {
                channel.basicConsume(map.get(keyQueueName), true, deliverCallback, consumerTag -> {
                });
            } catch (Exception error) {
                logger.log(Level.WARNING, "Failed to consume message from queue", error);
            }
            shutdown = true;
        }
        return true;
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
