package com.morghttpclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;

public class ObjectTracker {
    private static final Logger logger = LoggerFactory.getLogger(ObjectTracker.class);

    private final Client client;
    private JsonArray visibleGameObjects;
    private JsonArray visibleDecorativeObjects;
    private JsonArray visibleWallObjects;

    public ObjectTracker(Client client) {
        this.client = client;
    }

    public JsonObject getVisibleObjects(){
        this.visibleGameObjects = new JsonArray();
        this.visibleDecorativeObjects = new JsonArray();
        this.visibleWallObjects = new JsonArray();

        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        int z = client.getPlane();
        int viewportWidth = client.getViewportWidth();
        int viewportHeight = client.getViewportHeight();
        int xOffset = client.getViewportXOffset();
        int yOffset = client.getViewportYOffset();
        Rectangle gameView = new Rectangle(xOffset, yOffset, viewportWidth, viewportHeight);

        for (int x = 0; x < Constants.SCENE_SIZE; x++) {
            for (int y = 0; y < Constants.SCENE_SIZE; y++) {
                Tile tile = tiles[z][x][y];
                if (tile == null) continue;

                processGameObjects(tile.getGameObjects(), gameView);
                processDecorativeObject(tile.getDecorativeObject(), gameView);
                processWallObject(tile.getWallObject(), gameView);
            }
        }

        JsonObject objectData = new JsonObject();
        objectData.add("GameObjects", this.visibleGameObjects);
        objectData.add("DecorativeObjects", this.visibleDecorativeObjects);
        objectData.add("WallObjects", this.visibleWallObjects);

        return objectData;
    }

    private void processGameObjects(GameObject[] gameObjects, Rectangle viewport) {
        if (gameObjects == null) return;
        for (GameObject gameObject : gameObjects) {
            if (gameObject != null && isObjectVisible(gameObject, viewport)) {
                logObject("GameObject", gameObject);

                // Get the clickbox and calculate the center
                Shape clickbox = gameObject.getConvexHull();
                if (clickbox != null) {
                    Rectangle bounds = clickbox.getBounds();

                    int centerX = bounds.x + bounds.width / 2;
                    int centerY = bounds.y + bounds.height / 2;

                    WorldPoint worldLocation = client.isInInstancedRegion()?
                            WorldPoint.fromLocalInstance(client, gameObject.getLocalLocation()):
                            WorldPoint.fromLocal(client, gameObject.getLocalLocation());

                    JsonObject objectData = new JsonObject();
                    objectData.addProperty("id", gameObject.getId());
                    objectData.addProperty("canvasX", centerX);
                    objectData.addProperty("canvasY", centerY);
                    objectData.addProperty("worldX", worldLocation.getX());
                    objectData.addProperty("worldY", worldLocation.getY());
                    objectData.addProperty("plane", worldLocation.getPlane());

                    if (objectData.size() > 0)
                        this.visibleGameObjects.add(objectData);
                }
            }
        }
    }

    private void processWallObject(TileObject tileObject, Rectangle viewport) {
        JsonObject wallObject = processTileObject(tileObject, viewport);
        if(wallObject.size() > 0)
            this.visibleWallObjects.add(wallObject);
    }

    private void processDecorativeObject(TileObject tileObject, Rectangle viewport) {
        JsonObject decorativeObject = processTileObject(tileObject, viewport);
        if(decorativeObject.size() > 0)
            this.visibleDecorativeObjects.add(decorativeObject);
    }

    private JsonObject processTileObject(TileObject tileObject, Rectangle viewport) {
        JsonObject objectData = new JsonObject();
        if (tileObject != null && isObjectVisible(tileObject, viewport)) {
            logObject("TileObject", tileObject);

            WorldPoint worldLocation = client.isInInstancedRegion()?
                    WorldPoint.fromLocalInstance(client, tileObject.getLocalLocation()):
                    WorldPoint.fromLocal(client, tileObject.getLocalLocation());

            objectData.addProperty("id", tileObject.getId());
            objectData.addProperty("canvasX", tileObject.getCanvasLocation().getX());
            objectData.addProperty("canvasY", tileObject.getCanvasLocation().getY());
            objectData.addProperty("worldX", worldLocation.getX());
            objectData.addProperty("worldY", worldLocation.getY());
            objectData.addProperty("plane", worldLocation.getPlane());
        }
        return objectData;
    }

    private boolean isObjectVisible(TileObject object, Rectangle gameView) {
        Point canvasPoint = Perspective.localToCanvas(client, object.getLocalLocation(), client.getPlane());
        return canvasPoint != null && gameView.contains(canvasPoint.getX(), canvasPoint.getY());
    }

    private void logObject(String type, TileObject object) {

        Point canvasPoint = Perspective.localToCanvas(client, object.getLocalLocation(), client.getPlane());
        if (canvasPoint != null) {
            System.out.println(type + " ID: " + object.getId() + ", CanvasX: " + canvasPoint.getX() + ", CanvasY: " + canvasPoint.getY());
        }
    }
}
