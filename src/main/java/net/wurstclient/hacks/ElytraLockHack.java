/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.Comparator;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.filterlists.ElytraLockFilterList;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"elytra, lock"})
public final class ElytraLockHack extends Hack
	implements UpdateListener, RenderListener, GUIRenderListener
{
	private final EnumSetting<Priority> priority = new EnumSetting<>("Priority",
		"Determines which entity will be aimed at first.\n"
			+ "\u00a7lDistance\u00a7r - Aims at the closest entity.\n"
			+ "\u00a7lAngle\u00a7r - Aimes at the entity that requires the least head movement.\n"
			+ "\u00a7lAngle+Dist\u00a7r - A hybrid of Angle and Distance. This is usually the best at figuring out what you want to aim at.\n"
			+ "\u00a7lHealth\u00a7r - Aims at the weakest entity.",
		Priority.values(), Priority.ANGLE_DIST);
	
	private final SliderSetting cooldownSlider = new SliderSetting("Cooldown",
		"The amount of time from beginning an elytra flight to locking on an entity. This will allow proper time to launch and gain speed.",
		3, 0, 10, 1, ValueDisplay.DECIMAL);
	
	private final EntityFilterList entityFilters =
		ElytraLockFilterList.create();
	
	private final ColorSetting color = new ColorSetting("ESP color",
		"Color of the box that ElytraLock draws around the target.", Color.RED);
	
	private static final Box TARGET_BOX =
		new Box(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5);
	
	private Entity target;
	private float velocity;
	
	private double cooldown;
	
	public ElytraLockHack()
	{
		super("ElytraLock");
		
		setCategory(Category.COMBAT);
		addSetting(priority);
		addSetting(cooldownSlider);
		
		entityFilters.forEach(this::addSetting);
		
		addSetting(color);
	}
	
	@Override
	protected void onEnable()
	{
		// disable conflicting hacks
		WURST.getHax().excavatorHack.setEnabled(false);
		WURST.getHax().templateToolHack.setEnabled(false);
		
		// register event listeners
		EVENTS.add(GUIRenderListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		if(!MC.player.isGliding())
		{
			target = null;
			cooldown = cooldownSlider.getValue();
			return;
		}
		
		if(cooldown >= 0)
		{
			cooldown -= 0.1;
			return;
		}
		
		if(!MC.options.jumpKey.isPressed())
		{
			target = null;
			return;
		}
		
		ClientPlayerEntity player = MC.player;
		
		// set target
		if(filterEntities(Stream.of(target)) == null)
			target = filterEntities(StreamSupport
				.stream(MC.world.getEntities().spliterator(), true));
		
		if(target == null)
			return;
		
		// set velocity
		velocity = (72000 - player.getItemUseTimeLeft()) / 20F;
		velocity = (velocity * velocity + velocity * 2) / 3;
		if(velocity > 1)
			velocity = 1;
		
		// set position to aim at
		double d = RotationUtils.getEyesPos()
			.distanceTo(target.getBoundingBox().getCenter());
		double posX = target.getX() + (target.getX() - target.lastRenderX) * d
			- player.getX();
		double posY = target.getY() + (target.getY() - target.lastRenderY) * d
			+ target.getHeight() * 0.5 - player.getY()
			- player.getEyeHeight(player.getPose());
		double posZ = target.getZ() + (target.getZ() - target.lastRenderZ) * d
			- player.getZ();
		
		// set yaw
		float neededYaw = (float)Math.toDegrees(Math.atan2(posZ, posX)) - 90;
		MC.player.setYaw(
			RotationUtils.limitAngleChange(MC.player.getYaw(), neededYaw));
		
		// calculate needed pitch
		double hDistance = Math.sqrt(posX * posX + posZ * posZ);
		double hDistanceSq = hDistance * hDistance;
		float g = 0.006F;
		
		float neededPitch = (float)-Math.toDegrees(
			Math.atan((1 - Math.sqrt(1 - g * (g * hDistanceSq + 2 * posY * 1)))
				/ (g * hDistance)));
		
		// set pitch
		if(Float.isNaN(neededPitch))
			WURST.getRotationFaker()
				.faceVectorClient(target.getBoundingBox().getCenter());
		else
			MC.player.setPitch(neededPitch);
	}
	
	private Entity filterEntities(Stream<Entity> s)
	{
		Stream<Entity> stream = s.filter(EntityUtils.IS_ATTACKABLE);
		stream = entityFilters.applyTo(stream);
		
		return stream.min(priority.getSelected().comparator).orElse(null);
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(target == null)
			return;
		
		// GL settings
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		
		matrixStack.push();
		
		RegionPos region = RenderUtils.getCameraRegion();
		RenderUtils.applyRegionalRenderOffset(matrixStack, region);
		
		// set position
		matrixStack.translate(target.getX() - region.x(), target.getY(),
			target.getZ() - region.z());
		
		// set size
		float boxWidth = target.getWidth() + 0.1F;
		float boxHeight = target.getHeight() + 0.1F;
		matrixStack.scale(boxWidth, boxHeight, boxWidth);
		
		// move to center
		matrixStack.translate(0, 0.5, 0);
		
		float v = 1 / velocity;
		matrixStack.scale(v, v, v);
		
		RenderSystem.setShader(ShaderProgramKeys.POSITION);
		
		// draw outline
		color.setAsShaderColor(0.5F * velocity);
		RenderUtils.drawOutlinedBox(TARGET_BOX, matrixStack);
		
		// draw box
		color.setAsShaderColor(0.25F * velocity);
		RenderUtils.drawSolidBox(TARGET_BOX, matrixStack);
		
		matrixStack.pop();
		
		// GL resets
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);
		RenderSystem.setShaderColor(1, 1, 1, 1);
	}
	
	@Override
	public void onRenderGUI(DrawContext context, float partialTicks)
	{
		MatrixStack matrixStack = context.getMatrices();
		if(target == null)
			return;
		
		// GL settings
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_CULL_FACE);
		
		matrixStack.push();
		
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		
		String message = "Locked on!";
		
		TextRenderer tr = MC.textRenderer;
		
		// translate to center
		Window sr = MC.getWindow();
		int msgWidth = tr.getWidth(message);
		matrixStack.translate(sr.getScaledWidth() / 2 - msgWidth / 2,
			sr.getScaledHeight() / 2 + 1, 0);
		
		// background
		RenderSystem.setShader(ShaderProgramKeys.POSITION);
		RenderSystem.setShaderColor(0, 0, 0, 0.5F);
		BufferBuilder bufferBuilder = tessellator
			.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
		bufferBuilder.vertex(matrix, 0, 0, 0);
		bufferBuilder.vertex(matrix, msgWidth + 3, 0, 0);
		bufferBuilder.vertex(matrix, msgWidth + 3, 10, 0);
		bufferBuilder.vertex(matrix, 0, 10, 0);
		BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
		
		// text
		RenderSystem.setShaderColor(1, 1, 1, 1);
		context.drawText(MC.textRenderer, message, 2, 1, 0xffffffff, false);
		
		matrixStack.pop();
		
		// GL resets
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_BLEND);
		RenderSystem.setShaderColor(1, 1, 1, 1);
	}
	
	private enum Priority
	{
		DISTANCE("Distance", e -> MC.player.squaredDistanceTo(e)),
		
		ANGLE("Angle",
			e -> RotationUtils
				.getAngleToLookVec(e.getBoundingBox().getCenter())),
		
		ANGLE_DIST("Angle+Dist",
			e -> Math
				.pow(RotationUtils
					.getAngleToLookVec(e.getBoundingBox().getCenter()), 2)
				+ MC.player.squaredDistanceTo(e)),
		
		HEALTH("Health", e -> e instanceof LivingEntity
			? ((LivingEntity)e).getHealth() : Integer.MAX_VALUE);
		
		private final String name;
		private final Comparator<Entity> comparator;
		
		private Priority(String name, ToDoubleFunction<Entity> keyExtractor)
		{
			this.name = name;
			comparator = Comparator.comparingDouble(keyExtractor);
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
