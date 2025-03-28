/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui;

import java.util.ArrayList;

import net.minecraft.client.font.TextRenderer;
import net.wurstclient.WurstClient;

public class Window
{
	private String title;
	private int x;
	private int y;
	private int width;
	private int height;
	
	private boolean valid;
	private final ArrayList<Component> children = new ArrayList<>();
	
	private boolean dragging;
	private int dragOffsetX;
	private int dragOffsetY;
	
	private boolean minimized;
	private boolean minimizable = true;
	
	private boolean pinned;
	private boolean pinnable = true;
	
	private boolean closable;
	private boolean closing;
	
	private boolean invisible;
	
	private boolean fixedWidth;
	private int innerHeight;
	private int maxInnerHeight;
	private int scrollOffset;
	private boolean scrollingEnabled;
	
	private boolean draggingScrollbar;
	private int scrollbarDragOffsetY;
	
	public Window(String title)
	{
		this.title = title;
	}
	
	public final String getTitle()
	{
		return title;
	}
	
	public final void setTitle(String title)
	{
		this.title = title;
	}
	
	/**
	 * Returns the X position of the window, adjusted to fit inside the screen.
	 */
	public final int getX()
	{
		// prevent window from going off the right side of the screen
		net.minecraft.client.util.Window mcWindow = WurstClient.MC.getWindow();
		if(x > mcWindow.getScaledWidth() - 1)
			return mcWindow.getScaledWidth() - 1;
		
		// prevent window from going off the left side of the screen
		if(x <= -width)
			return -width + 1;
		
		return x;
	}
	
	/**
	 * Returns the actual X position of the window, without any adjustments.
	 * This should only be used for saving the window's position to the config
	 * file.
	 */
	public final int getActualX()
	{
		return x;
	}
	
	public final void setX(int x)
	{
		this.x = x;
	}
	
	/**
	 * Returns the Y position of the window, adjusted to fit inside the screen.
	 */
	public final int getY()
	{
		// prevent window from going off the bottom of the screen
		net.minecraft.client.util.Window mcWindow = WurstClient.MC.getWindow();
		if(y > mcWindow.getScaledHeight() - 1)
			return mcWindow.getScaledHeight() - 1;
		
		// prevent window from going off the top of the screen
		if(y <= -12)
			return -12;
		
		return y;
	}
	
	/**
	 * Returns the actual Y position of the window, without any adjustments.
	 * This should only be used for saving the window's position to the config
	 * file.
	 */
	public final int getActualY()
	{
		return y;
	}
	
	public final void setY(int y)
	{
		this.y = y;
	}
	
	public final int getWidth()
	{
		return width;
	}
	
	public final void setWidth(int width)
	{
		if(fixedWidth)
			return;
		
		if(this.width != width)
			invalidate();
		
		this.width = width;
	}
	
	public final int getHeight()
	{
		return height;
	}
	
	public final void setHeight(int height)
	{
		if(this.height != height)
			invalidate();
		
		this.height = height;
	}
	
	public final void pack()
	{
		int maxChildWidth = 0;
		for(Component c : children)
			if(c.getDefaultWidth() > maxChildWidth)
				maxChildWidth = c.getDefaultWidth();
		maxChildWidth += 4;
		
		TextRenderer tr = WurstClient.MC.textRenderer;
		int titleBarWidth = tr.getWidth(title) + 4;
		if(minimizable)
			titleBarWidth += 11;
		if(pinnable)
			titleBarWidth += 11;
		if(closable)
			titleBarWidth += 11;
		
		int childrenHeight = 13;
		for(Component c : children)
			childrenHeight += c.getHeight() + 2;
		childrenHeight += 2;
		
		if(maxInnerHeight > 0 && childrenHeight > maxInnerHeight + 13)
		{
			setWidth(Math.max(maxChildWidth + 3, titleBarWidth));
			setHeight(maxInnerHeight + 13);
			
		}else
		{
			setWidth(Math.max(maxChildWidth, titleBarWidth));
			setHeight(childrenHeight);
		}
		
		validate();
	}
	
	public final void validate()
	{
		if(valid)
			return;
		
		int offsetY = 2;
		int cWidth = width - 4;
		for(Component c : children)
		{
			c.setX(2);
			c.setY(offsetY);
			c.setWidth(cWidth);
			offsetY += c.getHeight() + 2;
		}
		
		innerHeight = offsetY;
		
		if(maxInnerHeight == 0 || innerHeight < maxInnerHeight)
			setHeight(innerHeight + 13);
		else
			setHeight(maxInnerHeight + 13);
		
		scrollingEnabled = innerHeight + 13 > height;
		if(scrollingEnabled)
			cWidth -= 3;
		
		scrollOffset = Math.min(scrollOffset, 0);
		scrollOffset = Math.max(scrollOffset, -innerHeight + height - 13);
		
		for(Component c : children)
			c.setWidth(cWidth);
		
		valid = true;
	}
	
	public final void invalidate()
	{
		valid = false;
	}
	
	public final int countChildren()
	{
		return children.size();
	}
	
	public final Component getChild(int index)
	{
		return children.get(index);
	}
	
	public final void add(Component component)
	{
		children.add(component);
		component.setParent(this);
		invalidate();
	}
	
	public final void remove(int index)
	{
		children.get(index).setParent(null);
		children.remove(index);
		invalidate();
	}
	
	public final void remove(Component component)
	{
		children.remove(component);
		component.setParent(null);
		invalidate();
	}
	
	public final boolean isDragging()
	{
		return dragging;
	}
	
	public final void startDragging(int mouseX, int mouseY)
	{
		dragging = true;
		dragOffsetX = getX() - mouseX;
		dragOffsetY = getY() - mouseY;
	}
	
	public final void dragTo(int mouseX, int mouseY)
	{
		x = mouseX + dragOffsetX;
		y = mouseY + dragOffsetY;
	}
	
	public final void stopDragging()
	{
		dragging = false;
		dragOffsetX = 0;
		dragOffsetY = 0;
	}
	
	public final boolean isMinimized()
	{
		return minimized;
	}
	
	public final void setMinimized(boolean minimized)
	{
		this.minimized = minimized;
	}
	
	public final boolean isMinimizable()
	{
		return minimizable;
	}
	
	public final void setMinimizable(boolean minimizable)
	{
		this.minimizable = minimizable;
	}
	
	public final boolean isPinned()
	{
		return pinned;
	}
	
	public final void setPinned(boolean pinned)
	{
		this.pinned = pinned;
	}
	
	public final boolean isPinnable()
	{
		return pinnable;
	}
	
	public final void setPinnable(boolean pinnable)
	{
		this.pinnable = pinnable;
	}
	
	public final boolean isClosable()
	{
		return closable;
	}
	
	public final void setClosable(boolean closable)
	{
		this.closable = closable;
	}
	
	public final boolean isClosing()
	{
		return closing;
	}
	
	public final void close()
	{
		closing = true;
	}
	
	public final boolean isInvisible()
	{
		return invisible;
	}
	
	public final void setInvisible(boolean invisible)
	{
		this.invisible = invisible;
	}
	
	public final boolean isFixedWidth()
	{
		return fixedWidth;
	}
	
	public final void setFixedWidth(boolean fixedWidth)
	{
		this.fixedWidth = fixedWidth;
	}
	
	public final int getInnerHeight()
	{
		return innerHeight;
	}
	
	public final void setMaxInnerHeight(int maxInnerHeight)
	{
		if(maxInnerHeight < 0)
			maxInnerHeight = 0;
		
		if(this.maxInnerHeight != maxInnerHeight)
			invalidate();
		
		this.maxInnerHeight = maxInnerHeight;
	}
	
	public final void setMaxHeight(int maxHeight)
	{
		setMaxInnerHeight(maxHeight - 13);
	}
	
	public final int getScrollOffset()
	{
		return scrollOffset;
	}
	
	public final void setScrollOffset(int scrollOffset)
	{
		this.scrollOffset = scrollOffset;
	}
	
	public final boolean isScrollingEnabled()
	{
		return scrollingEnabled;
	}
	
	public final boolean isDraggingScrollbar()
	{
		return draggingScrollbar;
	}
	
	public final void startDraggingScrollbar(int mouseY)
	{
		draggingScrollbar = true;
		double outerHeight = height - 13;
		double scrollbarY =
			outerHeight * (-scrollOffset / (double)innerHeight) + 1;
		scrollbarDragOffsetY = (int)(scrollbarY - mouseY);
	}
	
	public final void dragScrollbarTo(int mouseY)
	{
		int scrollbarY = mouseY + scrollbarDragOffsetY;
		double outerHeight = height - 13;
		scrollOffset = (int)((scrollbarY - 1) / outerHeight * innerHeight * -1);
		scrollOffset = Math.min(scrollOffset, 0);
		scrollOffset = Math.max(scrollOffset, -innerHeight + height - 13);
	}
	
	public final void stopDraggingScrollbar()
	{
		draggingScrollbar = false;
		scrollbarDragOffsetY = 0;
	}
}
