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
// import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

// import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
// import spade.core.AbstractVertex;
// import spade.core.Edge;
// import spade.core.Vertex;
// import spade.utility.Execute;

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
    // Configure the RabbitMQ parameters once
    private volatile boolean shutdown;
    private Logger logger = Logger.getLogger(P4.class.getName());
    ConnectionFactory factory = null;
    Connection connection = null;
    Channel channel = null;

    DeliverCallback deliverCallback = (consumerTag, delivery) -> {
        String message = new String(delivery.getBody(), "UTF-8");
        parseMessage(message);
        logger.log(Level.INFO, " [x] Received '" + message + "'");
    };

    private void initializeRabbitMQ(String QUEUE_NAME) {
        factory = new ConnectionFactory();
        // TODO - This parameters should come from a config file
        factory.setHost("172.18.0.2");
        factory.setPort(5672);
        factory.setUsername("guest");
        factory.setPassword("guest");
        try {
            connection = factory.newConnection();
            try {
                channel = connection.createChannel();
                channel.queueDeclare(QUEUE_NAME, false, false, false, null);
                logger.log(Level.INFO, "RabbitMQ queue declared successfully");
            }
            catch (Exception error){
                logger.log(Level.WARNING, "Failed to initialize connection",error.toString());
            }
        }
        catch (Exception error){
            logger.log(Level.WARNING, "Failed to initialize connection", error);
        }
    }
    private void parseMessage(String data) {
        String[] parts = data.split("\\|");
    }


    @Override
    public boolean launch(String arguments) {
        logger.log(Level.INFO, "P4 reporter started");
        String QUEUE_NAME = "test";
        initializeRabbitMQ(QUEUE_NAME);

        while (!shutdown) {
            try {
                channel.basicConsume(QUEUE_NAME, true, deliverCallback, consumerTag -> { });
            }
            catch (Exception error) {
                logger.log(Level.WARNING, "Failed to consume message from queue", error);
            }
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
