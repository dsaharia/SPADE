package spade.reporter;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.vertex.prov.Entity;

public class P4StateTable {
    // <entity_name, index> : <[hashID]>
    Map<AbstractMap.SimpleEntry<String, String>, List<Entity>> stateTable = new HashMap<>();
    public Logger logger = Logger.getLogger(P4StateTable.class.getName());

    public void put(String entityType, String entityIndex, Entity entity) {
        // Add to state table if does not exist
        AbstractMap.SimpleEntry<String, String> currentKey = new AbstractMap.SimpleEntry<>(entityType, entityIndex);
        if (stateTable.containsKey(currentKey)) {
            Entity lastEntity = getEntity(entityType, entityIndex);
            if (lastEntity != entity) {
                logger.log(Level.INFO, "here");
                stateTable.get(currentKey).add(entity);
            }
        } else {
            List<Entity> newList = new ArrayList<>();
            newList.add(entity);
            stateTable.put(currentKey, newList);
        }

    }

    public Entity getEntity(String entityType, String entityIndex) {
        AbstractMap.SimpleEntry<String, String> currentKey = new AbstractMap.SimpleEntry<>(entityType, entityIndex);

        if (stateTable.containsKey(currentKey)) {
            List<Entity> list = stateTable.get(currentKey);
            return list.get(list.size() - 1);
        }
        return null;
    }

    public String toString() {
        return stateTable.keySet().toString();
    }
}
