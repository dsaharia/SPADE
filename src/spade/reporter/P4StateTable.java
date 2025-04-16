package spade.reporter;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.vertex.prov.Entity;
import spade.edge.prov.WasDerivedFrom;

public class P4StateTable {

    String keyValue = "value", keyIndex = "index";
    public Logger logger = Logger.getLogger(P4StateTable.class.getName());
    HashMap<String, List<Entity>> stateTable = new HashMap<>();

    public AbstractEdge performStateCheck(String entityOperation, String entity_type, Entity currentEntity) {
        // TODO - Entity index is different for pkt_in, table rule or register - FIX ME
        String entityID = currentEntity.getAnnotation(keyIndex);
        String agentId = currentEntity.getAnnotation("agent_id");
        String entityKey = currentEntity.getAnnotation("name") + "_" + agentId + "_" + entityID;
        Entity previousNode = getEntity(entityKey);
        if (previousNode == null) {
            // First node for this entity. Therefore, add to StateTable -
            addToStateTable(entityKey, (Entity) currentEntity);
            logger.log(Level.INFO, "Previous Node NULL" + stateTable);
            return null;
        }
        // Previous value exists
        String previousEntityValue = previousNode.getAnnotation(keyValue);
        String currentEntityValue = currentEntity.getAnnotation(keyValue);
        if (entityOperation.equals("WRITE")) {
            if (!(currentEntityValue.equals(previousEntityValue))) {
                // WDF Edge needed
                AbstractEdge edge = new WasDerivedFrom(currentEntity, previousNode);
                // super.putEdge(edge);
                logger.log(Level.INFO, "WDF edge added: " + edge);
                // Add new state value
                addToStateTable(entityKey, (Entity) currentEntity);
                if (entityID.equals("255")) {
                    logger.log(Level.INFO, "State table " + stateTable);
                }
                return edge;
            }
        }
        return null;
    }

    public Entity getEntity(String entityKey) {
        if (stateTable.containsKey(entityKey)) {
            List<Entity> list = stateTable.get(entityKey);
            return list.get(list.size() - 1);
        }
        return null;
    }

    public void addToStateTable(String entityKey, Entity entity) {

        if (stateTable.containsKey(entityKey)) {
            stateTable.get(entityKey).add(entity);
        } else {
            // Initialize state table entry
            List<Entity> newList = new ArrayList<>();
            newList.add(entity);
            stateTable.put(entityKey, newList);
        }

    }

    public String toString() {
        return stateTable.keySet().toString();
    }
}
