/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.chestesp.ChestEspGroup;
import net.wurstclient.hacks.chestesp.ChestEspGroupManager;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.chunk.ChunkUtils;

public class ChestEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	static
	{
		try
		{
			Class.forName("org.sqlite.JDBC");
		}catch(ClassNotFoundException e)
		{
			System.err.println("SQLite JDBC driver not found!");
			e.printStackTrace();
		}
	}
	
	private final EspStyleSetting style = new EspStyleSetting();
	private final ChestEspGroupManager groups = new ChestEspGroupManager();
	
	private final CheckboxSetting logChests = new CheckboxSetting("Log chests",
		"Writes to a specified database when a double chest is found.", false);
	
	public final TextFieldSetting sqliteDatabasePath =
		new TextFieldSetting("Path to SQLite database",
			"Path to the SQLite database file.", "db.sqlite3");
	
	private final Set<BlockPos> loggedChests = ConcurrentHashMap.newKeySet();
	
	private String serverIp;
	private int serverPort;
	private String pathString;
	
	public ChestEspHack()
	{
		super("ChestESP");
		setCategory(Category.RENDER);
		addSetting(style);
		groups.allGroups.stream().flatMap(ChestEspGroup::getSettings)
			.forEach(this::addSetting);
		addSetting(logChests);
		addSetting(sqliteDatabasePath);
		
		ServerData networkHandler = Minecraft.getInstance().getCurrentServer();
		
		if(networkHandler != null)
		{
			String address = networkHandler.ip;
			String[] parts = address.split(":");
			this.serverIp = parts[0];
			this.serverPort =
				(parts.length > 1) ? Integer.parseInt(parts[1]) : 25565;
		}else
		{
			this.serverIp = "singleplayer";
			this.serverPort = 25565;
		}
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
		
		pathString = sqliteDatabasePath.getValue();
		
		// Sanitize path string for SQLite JDBC
		pathString = pathString.replace("\\\\", "\\");
		pathString = pathString.replace("\\", "/");
		
		loggedChests.clear();
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		groups.allGroups.forEach(ChestEspGroup::clear);
	}
	
	@Override
	public void onUpdate()
	{
		groups.allGroups.forEach(ChestEspGroup::clear);
		ChunkUtils.getLoadedBlockEntities().forEach(be -> {
			groups.blockGroups.forEach(group -> group.addIfMatches(be));
			
			// Check for double chests and log them
			if(logChests.isChecked())
			{
				checkAndLogDoubleChest(be);
			}
		});
		MC.level.entitiesForRendering().forEach(
			e -> groups.entityGroups.forEach(group -> group.addIfMatches(e)));
	}
	
	private void checkAndLogDoubleChest(BlockEntity be)
	{
		if(!(be instanceof ChestBlockEntity))
			return;
		
		ChestBlockEntity chestBE = (ChestBlockEntity)be;
		BlockState state = chestBE.getBlockState();
		
		if(!state.hasProperty(ChestBlock.TYPE))
			return;
		
		ChestType chestType = state.getValue(ChestBlock.TYPE);
		
		// Only log double chests, and only log the RIGHT side to avoid
		// duplicates
		if(chestType != ChestType.RIGHT)
			return;
		
		BlockPos pos = chestBE.getBlockPos();
		
		// Skip if already logged
		if(loggedChests.contains(pos))
			return;
		
		// Mark as logged
		loggedChests.add(pos);
		
		// Also mark the connected chest to avoid duplicate logging
		BlockPos connectedPos =
			pos.relative(ChestBlock.getConnectedDirection(state));
		loggedChests.add(connectedPos);
		
		// Log to database
		insertDoubleChest(pos.getX(), pos.getY(), pos.getZ());
		System.out.println("Double chest found at " + pos);
	}
	
	private void insertDoubleChest(int x, int y, int z)
	{
		try(Connection conn =
			DriverManager.getConnection("jdbc:sqlite:" + pathString))
		{
			String sql =
				"INSERT INTO doublechests (server_ip, port, x, y, z) VALUES (?, ?, ?, ?, ?)";
			PreparedStatement pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, this.serverIp);
			pstmt.setInt(2, this.serverPort);
			pstmt.setInt(3, x);
			pstmt.setInt(4, y);
			pstmt.setInt(5, z);
			pstmt.executeUpdate();
		}catch(SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		if(style.hasLines())
			event.cancel();
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		groups.entityGroups.stream().filter(ChestEspGroup::isEnabled)
			.forEach(g -> g.updateBoxes(partialTicks));
		
		if(style.hasBoxes())
			renderBoxes(matrixStack);
		
		if(style.hasLines())
			renderTracers(matrixStack, partialTicks);
	}
	
	private void renderBoxes(PoseStack matrixStack)
	{
		for(ChestEspGroup group : groups.allGroups)
		{
			if(!group.isEnabled())
				continue;
			
			List<AABB> boxes = group.getBoxes();
			int quadsColor = group.getColorI(0x40);
			int linesColor = group.getColorI(0x80);
			
			RenderUtils.drawSolidBoxes(matrixStack, boxes, quadsColor, false);
			RenderUtils.drawOutlinedBoxes(matrixStack, boxes, linesColor,
				false);
		}
	}
	
	private void renderTracers(PoseStack matrixStack, float partialTicks)
	{
		for(ChestEspGroup group : groups.allGroups)
		{
			if(!group.isEnabled())
				continue;
			
			List<AABB> boxes = group.getBoxes();
			List<Vec3> ends = boxes.stream().map(AABB::getCenter).toList();
			int color = group.getColorI(0x80);
			
			RenderUtils.drawTracers(matrixStack, partialTicks, ends, color,
				false);
		}
	}
}
