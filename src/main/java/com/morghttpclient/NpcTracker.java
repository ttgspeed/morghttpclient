package com.morghttpclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.*;
import net.runelite.api.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;

public class NpcTracker {
    private static final Logger logger = LoggerFactory.getLogger(NpcTracker.class);

    private final Client client;

    public NpcTracker(Client client) {
        this.client = client;
    }


    public JsonArray getVisibleNpcs(){
        List<NPC> npcs = client.getNpcs();
        JsonArray visibleNpcs = new JsonArray();
        Rectangle gameView = client.getCanvas().getBounds();

        for (NPC npc : npcs) {
            JsonObject npcData = new JsonObject();
            npcData.addProperty("id", npc.getId());
            npcData.addProperty("name", npc.getName());
			Point canvasPosition = Perspective.localToCanvas(client, npc.getLocalLocation(), client.getPlane());
			LocalPoint worldLocation = npc.getLocalLocation();
			int plane = client.getPlane();  // Retrieves the plane level the NPC is on
            if (canvasPosition != null && gameView.contains(canvasPosition.getX(), canvasPosition.getY())) {
                npcData.addProperty("canvasX", canvasPosition.getX());
                npcData.addProperty("canvasY", canvasPosition.getY());
                npcData.addProperty("worldX", worldLocation.getX());
		npcData.addProperty("worldY", worldLocation.getY());
                npcData.addProperty("plane", plane);
                visibleNpcs.add(npcData);
            }
        }

        return visibleNpcs;
    }
}
