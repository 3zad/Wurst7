/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.newchunks;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gl.GlUsage;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.util.math.MatrixStack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.util.RenderUtils;

public final class NewChunksRenderer
{
	private final VertexBuffer[] vertexBuffers = new VertexBuffer[4];
	
	private final SliderSetting altitude;
	private final SliderSetting opacity;
	private final ColorSetting newChunksColor;
	private final ColorSetting oldChunksColor;
	
	public NewChunksRenderer(SliderSetting altitude, SliderSetting opacity,
		ColorSetting newChunksColor, ColorSetting oldChunksColor)
	{
		this.altitude = altitude;
		this.opacity = opacity;
		this.newChunksColor = newChunksColor;
		this.oldChunksColor = oldChunksColor;
	}
	
	public void updateBuffer(int i, BuiltBuffer buffer)
	{
		if(buffer == null)
		{
			vertexBuffers[i] = null;
			return;
		}
		
		vertexBuffers[i] = new VertexBuffer(GlUsage.STATIC_WRITE);
		vertexBuffers[i].bind();
		vertexBuffers[i].upload(buffer);
		VertexBuffer.unbind();
	}
	
	public void closeBuffers()
	{
		for(int i = 0; i < vertexBuffers.length; i++)
		{
			if(vertexBuffers[i] == null)
				continue;
			
			vertexBuffers[i].close();
			vertexBuffers[i] = null;
		}
	}
	
	public void render(MatrixStack matrixStack, float partialTicks)
	{
		// GL settings
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		
		matrixStack.push();
		RenderUtils.applyRegionalRenderOffset(matrixStack);
		
		RenderSystem.setShader(ShaderProgramKeys.POSITION);
		
		Matrix4f projMatrix = RenderSystem.getProjectionMatrix();
		ShaderProgram shader = RenderSystem.getShader();
		
		float alpha = opacity.getValueF();
		double altitudeD = altitude.getValue();
		
		for(int i = 0; i < vertexBuffers.length; i++)
		{
			VertexBuffer buffer = vertexBuffers[i];
			if(buffer == null)
				continue;
			
			matrixStack.push();
			if(i == 0 || i == 2)
				matrixStack.translate(0, altitudeD, 0);
			
			if(i < 2)
				newChunksColor.setAsShaderColor(alpha);
			else
				oldChunksColor.setAsShaderColor(alpha);
			
			Matrix4f viewMatrix = matrixStack.peek().getPositionMatrix();
			buffer.bind();
			buffer.draw(viewMatrix, projMatrix, shader);
			VertexBuffer.unbind();
			
			matrixStack.pop();
		}
		
		matrixStack.pop();
		
		// GL resets
		RenderSystem.setShaderColor(1, 1, 1, 1);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);
	}
}
