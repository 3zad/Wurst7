/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.portalesp;

import java.util.Objects;
import java.util.stream.Stream;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gl.GlUsage;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

public final class PortalEspRenderer
{
	private static VertexBuffer solidBox;
	private static VertexBuffer outlinedBox;
	
	private final MatrixStack matrixStack;
	private final RegionPos region;
	private final Vec3d start;
	
	public PortalEspRenderer(MatrixStack matrixStack, float partialTicks)
	{
		this.matrixStack = matrixStack;
		region = RenderUtils.getCameraRegion();
		start = RotationUtils.getClientLookVec(partialTicks)
			.add(RenderUtils.getCameraPos()).subtract(region.toVec3d());
	}
	
	public void renderBoxes(PortalEspBlockGroup group)
	{
		float[] colorF = group.getColorF();
		
		for(Box box : group.getBoxes())
		{
			matrixStack.push();
			
			matrixStack.translate(box.minX - region.x(), box.minY,
				box.minZ - region.z());
			
			matrixStack.scale((float)(box.maxX - box.minX),
				(float)(box.maxY - box.minY), (float)(box.maxZ - box.minZ));
			
			Matrix4f viewMatrix = matrixStack.peek().getPositionMatrix();
			Matrix4f projMatrix = RenderSystem.getProjectionMatrix();
			ShaderProgram shader = RenderSystem.getShader();
			
			RenderUtils.setShaderColor(colorF, 0.25F);
			solidBox.bind();
			solidBox.draw(viewMatrix, projMatrix, shader);
			VertexBuffer.unbind();
			
			RenderUtils.setShaderColor(colorF, 0.5F);
			outlinedBox.bind();
			outlinedBox.draw(viewMatrix, projMatrix, shader);
			VertexBuffer.unbind();
			
			matrixStack.pop();
		}
	}
	
	public void renderLines(PortalEspBlockGroup group)
	{
		if(group.getBoxes().isEmpty())
			return;
		
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		
		float[] colorF = group.getColorF();
		RenderUtils.setShaderColor(colorF, 0.5F);
		
		BufferBuilder bufferBuilder = tessellator
			.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
		
		for(Box box : group.getBoxes())
		{
			Vec3d end = box.getCenter().subtract(region.toVec3d());
			
			bufferBuilder.vertex(matrix, (float)start.x, (float)start.y,
				(float)start.z);
			
			bufferBuilder.vertex(matrix, (float)end.x, (float)end.y,
				(float)end.z);
		}
		
		BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
	}
	
	public static void prepareBuffers()
	{
		closeBuffers();
		solidBox = new VertexBuffer(GlUsage.STATIC_WRITE);
		outlinedBox = new VertexBuffer(GlUsage.STATIC_WRITE);
		
		Box box = new Box(BlockPos.ORIGIN);
		RenderUtils.drawSolidBox(box, solidBox);
		RenderUtils.drawOutlinedBox(box, outlinedBox);
	}
	
	public static void closeBuffers()
	{
		Stream.of(solidBox, outlinedBox).filter(Objects::nonNull)
			.forEach(VertexBuffer::close);
	}
}
