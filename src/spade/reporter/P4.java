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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Vertex;
import spade.utility.Execute;

// import com.rabbitmq.client.Channel;
// import com.rabbitmq.client.Connection;
// import com.rabbitmq.client.ConnectionFactory;
// import com.rabbitmq.client.DeliverCallback;

/**
 * Prov reporter for P4.
 *
 * @author Dhiraj Saharia
 */
public class P4 extends AbstractReporter {

    private volatile boolean shutdown;
    //private HashMap<String, AbstractVertex> vertices;
    private Logger logger = Logger.getLogger(P4.class.getName());

    @Override
    public boolean launch(String arguments) {
        logger.log(Level.INFO, "Hello P4");
        return true;
	}

    @Override
    public boolean shutdown() {
        logger.log(Level.INFO, "BYE");
        return true;
    }

}
