package com.morghttpclient;

import com.morghttpclient.pojos.BankItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import com.google.inject.Provides;
import net.runelite.api.events.GameTick;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.http.api.RuneLiteAPI;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

@PluginDescriptor(
	name = "Morg HTTP Client",
	description = "Actively logs the player status to localhost on port 8081.",
	tags = {"status", "stats"},
	enabledByDefault = true
)
@Slf4j
public class HttpServerPlugin extends Plugin
{
	private static final Duration WAIT = Duration.ofSeconds(5);
	@Inject
	public Client client;
	public Skill[] skillList;
	public XpTracker xpTracker;
	public Bank bank;
	public NpcTracker npcTracker;
	public ObjectTracker objectTracker;
	public Skill mostRecentSkillGained;
	public int tickCount = 0;
	public long startTime = 0;
	public long currentTime = 0;
	public int[] xp_gained_skills;
	@Inject
	private ItemManager itemManager;
	@Inject
	public HttpServerConfig config;
	@Inject
	public ClientThread clientThread;
	public HttpServer server;
	public int MAX_DISTANCE = 1200;
	public String msg;
	@Provides
	private HttpServerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HttpServerConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		//MAX_DISTANCE = config.reachedDistance();
		skillList = Skill.values();
		xpTracker = new XpTracker(this);
		bank = new Bank(client);
		npcTracker = new NpcTracker(client);
		objectTracker = new ObjectTracker(client);
		server = HttpServer.create(new InetSocketAddress(8081), 0);
		server.createContext("/stats", this::handleStats);
		server.createContext("/inv", handlerForInv(InventoryID.INVENTORY));
		server.createContext("/equip", handlerForInv(InventoryID.EQUIPMENT));
		server.createContext("/events", this::handleEvents);
		server.createContext("/bank", this::handleBank);
		server.createContext("/npcs", this::handleNpcs);
		server.createContext("/objects", this::handleObjects);
		server.setExecutor(Executors.newSingleThreadExecutor());
		startTime = System.currentTimeMillis();
		xp_gained_skills = new int[Skill.values().length];
		int skill_count = 0;
		server.start();
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			xp_gained_skills[skill_count] = 0;
			skill_count++;
		}
	}
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		msg = event.getMessage();
		//System.out.println("onChatmsg:" + msg);
	}
	
	@Override
	protected void shutDown() throws Exception
	{
		server.stop(1);
	}
	public Client getClient() {
		return client;
	}
	@Subscribe
	public void onGameTick(GameTick tick)
	{
		currentTime = System.currentTimeMillis();
		xpTracker.update();
		bank.handleBankWindow();

		int skill_count = 0;
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			int xp_gained = handleTracker(skill);
			xp_gained_skills[skill_count] = xp_gained;
			skill_count ++;
		}
		tickCount++;
	}

	public int handleTracker(Skill skill){
		int startingSkillXp = xpTracker.getXpData(skill, 0);
		int endingSkillXp = xpTracker.getXpData(skill, tickCount);
		int xpGained = endingSkillXp - startingSkillXp;
		return xpGained;
	}

	public void handleStats(HttpExchange exchange) throws IOException
	{
		Player player = client.getLocalPlayer();
		JsonArray skills = new JsonArray();
		JsonObject headers = new JsonObject();
		headers.addProperty("username", client.getUsername());
		headers.addProperty("player name", player.getName());
		int skill_count = 0;
		skills.add(headers);
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			JsonObject object = new JsonObject();
			object.addProperty("stat", skill.getName());
			object.addProperty("level", client.getRealSkillLevel(skill));
			object.addProperty("boostedLevel", client.getBoostedSkillLevel(skill));
			object.addProperty("xp", client.getSkillExperience(skill));
			object.addProperty("xp gained", String.valueOf(xp_gained_skills[skill_count]));
			skills.add(object);
			skill_count++;
		}

		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
		{
			RuneLiteAPI.GSON.toJson(skills, out);
		}
	}

	public void handleEvents(HttpExchange exchange) throws IOException
	{
		MAX_DISTANCE = config.reachedDistance();
		Player player = client.getLocalPlayer();
		Actor npc = player.getInteracting();
		String npcName;
		int npcHealth;
		int npcHealth2;
		int health;
		int minHealth = 0;
		int maxHealth = 0;
		if (npc != null)
		{
			npcName = npc.getName();
			npcHealth = npc.getHealthScale();
			npcHealth2 = npc.getHealthRatio();
			health = 0;
			if (npcHealth2 > 0)
			{
				minHealth = 1;
				if (npcHealth > 1)
				{
					if (npcHealth2 > 1)
					{
						// This doesn't apply if healthRatio = 1, because of the special case in the server calculation that
						// health = 0 forces healthRatio = 0 instead of the expected healthRatio = 1
						minHealth = (npcHealth * (npcHealth2 - 1) + npcHealth - 2) / (npcHealth- 1);
					}
					maxHealth = (npcHealth * npcHealth2 - 1) / (npcHealth- 1);
					if (maxHealth > npcHealth)
					{
						maxHealth = npcHealth;
					}
				}
				else
				{
					// If healthScale is 1, healthRatio will always be 1 unless health = 0
					// so we know nothing about the upper limit except that it can't be higher than maxHealth
					maxHealth = npcHealth;
				}
				// Take the average of min and max possible healths
				health = (minHealth + maxHealth + 1) / 2;
			}
		}
		else
		{
			npcName = "null";
			npcHealth = 0;
			npcHealth2 = 0;
			health = 0;
		}

		WorldPoint worldLocation = client.isInInstancedRegion()?
				WorldPoint.fromLocalInstance(client, player.getLocalLocation()):
				WorldPoint.fromLocal(client, player.getLocalLocation());

		JsonObject object = new JsonObject();
		JsonObject camera = new JsonObject();
		JsonObject worldPoint = new JsonObject();
		JsonObject mouse = new JsonObject();
		JsonObject minimap = new JsonObject();
		object.addProperty("animation", player.getAnimation());
		object.addProperty("animation pose", player.getPoseAnimation());
		object.addProperty("latest msg", msg);
		object.addProperty("run energy", client.getEnergy());
		object.addProperty("game tick", client.getGameCycle());
		object.addProperty("health", client.getBoostedSkillLevel(Skill.HITPOINTS) + "/" + client.getRealSkillLevel(Skill.HITPOINTS));
		object.addProperty("interacting code", String.valueOf(player.getInteracting()));
		object.addProperty("npc name", npcName);
		object.addProperty("npc health ", minHealth);
		object.addProperty("MAX_DISTANCE", MAX_DISTANCE);
		mouse.addProperty("x", client.getMouseCanvasPosition().getX());
		mouse.addProperty("y", client.getMouseCanvasPosition().getY());
		worldPoint.addProperty("x", worldLocation.getX());
		worldPoint.addProperty("y", worldLocation.getY());
		worldPoint.addProperty("plane", player.getWorldLocation().getPlane());
		worldPoint.addProperty("regionID", getRegionIDs());
		worldPoint.addProperty("regionX", worldLocation.getRegionX());
		worldPoint.addProperty("regionY", worldLocation.getRegionY());
		camera.addProperty("yaw", client.getCameraYawTarget() & 2047);
		camera.addProperty("pitch", client.getCameraPitch());
		camera.addProperty("x", client.getCameraX());
		camera.addProperty("y", client.getCameraY());
		camera.addProperty("z", client.getCameraZ());
		camera.addProperty("x2", client.getCameraFocalPointX());
		camera.addProperty("y2", client.getCameraFocalPointY());
		camera.addProperty("z2", client.getCameraFocalPointZ());
		object.add("worldPoint", worldPoint);
		object.add("camera", camera);
		object.add("mouse", mouse);

		Widget minimap_draw_area_tl = client.isResized()?
				client.getWidget(10551326): // https://i.imgur.com/TU1DPfG.png
				client.getWidget(WidgetInfo.FIXED_VIEWPORT_MINIMAP_DRAW_AREA);

		int minimapWidth = minimap_draw_area_tl.getWidth();
		int minimapHeight = minimap_draw_area_tl.getHeight();
		int minimapCenterX = minimap_draw_area_tl.getCanvasLocation().getX() + minimapWidth / 2;
		int minimapCenterY = minimap_draw_area_tl.getCanvasLocation().getY() + minimapHeight / 2;

		minimap.addProperty("minimap_zoom", client.getMinimapZoom());
		minimap.addProperty("center_x", minimapCenterX);
		minimap.addProperty("center_y", minimapCenterY);
		object.add("minimap", minimap);

		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
		{
			RuneLiteAPI.GSON.toJson(object, out);
		}
	}
	public int getRegionIDs(){
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null) {
			// Handle the case where there is no player information available
			return 0;
		}


		WorldPoint wp = localPlayer.getWorldLocation();
		int tileX = wp.getX();
		int tileY = wp.getY();
		int z = client.getPlane();

// Check if the player is in an instanced area and adjust coordinates
		if (client.isInInstancedRegion()) {
			int[][][] instanceTemplateChunks = client.getInstanceTemplateChunks();
			LocalPoint localPoint = localPlayer.getLocalLocation();
			int chunkData = instanceTemplateChunks[z][localPoint.getSceneX() / 8][localPoint.getSceneY() / 8];

			tileX = (chunkData >> 14 & 0x3FF) * 8 + (tileX % 8);
			tileY = (chunkData >> 3 & 0x7FF) * 8 + (tileY % 8);
		}

		int regionX = tileX / 64;
		int regionY = tileY / 64;
		int regionID = (regionX << 8) | regionY;
		return regionID;
	}
	public void handleBank(HttpExchange exchange) throws IOException
	{
		Player player = client.getLocalPlayer();
		JsonArray items = new JsonArray();

		List<BankItem> bankItems = bank.getItems();
		for(BankItem bankItem : bankItems)
		{
			JsonObject object = new JsonObject();
			object.addProperty("id", bankItem.getId());
			object.addProperty("quantity", bankItem.getQuantity());
			items.add(object);
		}

		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
		{
			RuneLiteAPI.GSON.toJson(items, out);
		}
	}

	public void handleNpcs(HttpExchange exchange) throws IOException
	{
		JsonArray visibleNpcs = npcTracker.getVisibleNpcs();

		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
		{
			RuneLiteAPI.GSON.toJson(visibleNpcs, out);
		}
	}

	public void handleObjects(HttpExchange exchange) throws IOException
	{
		JsonObject visibleObjects = invokeAndWait(() -> {
			return objectTracker.getVisibleObjects();
		});

		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
		{
			RuneLiteAPI.GSON.toJson(visibleObjects, out);
		}
	}

	private HttpHandler handlerForInv(InventoryID inventoryID)
	{
		return exchange -> {
			Item[] items = invokeAndWait(() -> {
				ItemContainer itemContainer = client.getItemContainer(inventoryID);
				if (itemContainer != null)
				{
					return itemContainer.getItems();
				}
				return null;
			});

			if (items == null)
			{
				exchange.sendResponseHeaders(204, 0);
				return;
			}

			exchange.sendResponseHeaders(200, 0);
			try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
			{
				RuneLiteAPI.GSON.toJson(items, out);
			}
		};
	}
	private <T> T invokeAndWait(Callable<T> r)
	{
		try
		{
			AtomicReference<T> ref = new AtomicReference<>();
			Semaphore semaphore = new Semaphore(0);
			clientThread.invokeLater(() -> {
				try
				{

					ref.set(r.call());
				}
				catch (Exception e)
				{
					throw new RuntimeException(e);
				}
				finally
				{
					semaphore.release();
				}
			});
			semaphore.acquire();
			return ref.get();
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
}
