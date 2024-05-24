package com.morghttpclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
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

            Shape clickbox = npc.getConvexHull();
            if (clickbox != null) {
                Rectangle bounds = clickbox.getBounds();
                int centerX = bounds.x + bounds.width / 2;
                int centerY = bounds.y + bounds.height / 2;

                // Check if the center of the convex hull is within the game view
                if (gameView.contains(centerX, centerY)) {
                    npcData.addProperty("canvasX", centerX);
                    npcData.addProperty("canvasY", centerY);

                    LocalPoint worldLocation = npc.getLocalLocation();
                    npcData.addProperty("worldX", worldLocation.getX());
                    npcData.addProperty("worldY", worldLocation.getY());
                    npcData.addProperty("plane", client.getPlane());
                    visibleNpcs.add(npcData);
                }
            }
        }

        return visibleNpcs;
    }
}
