/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.Screen;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.clickgui.screens.EditBlockListScreen;
import net.wurstclient.events.GetAmbientOcclusionLightLevelListener;
import net.wurstclient.events.RenderBlockEntityListener;
import net.wurstclient.events.SetOpaqueCubeListener;
import net.wurstclient.events.ShouldDrawSideListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.ISimpleOption;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.ChatUtils;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.feature.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SearchTags({"XRay", "x ray", "OreFinder", "ore finder"})
public final class OreSimHack extends Hack implements UpdateListener,
	SetOpaqueCubeListener, GetAmbientOcclusionLightLevelListener,
	ShouldDrawSideListener, RenderBlockEntityListener
{
	private final BlockListSetting ores = new BlockListSetting("Ores",
		"A list of blocks that X-Ray will show. They don't have to be just ores"
			+ " - you can add any block you want.\n\n"
			+ "Remember to restart X-Ray when changing this setting.",
		"minecraft:amethyst_cluster", "minecraft:ancient_debris",
		"minecraft:anvil", "minecraft:beacon", "minecraft:bone_block",
		"minecraft:bookshelf", "minecraft:brewing_stand",
		"minecraft:budding_amethyst", "minecraft:chain_command_block",
		"minecraft:chest", "minecraft:coal_block", "minecraft:coal_ore",
		"minecraft:command_block", "minecraft:copper_ore", "minecraft:crafter",
		"minecraft:crafting_table", "minecraft:creaking_heart",
		"minecraft:decorated_pot", "minecraft:deepslate_coal_ore",
		"minecraft:deepslate_copper_ore", "minecraft:deepslate_diamond_ore",
		"minecraft:deepslate_emerald_ore", "minecraft:deepslate_gold_ore",
		"minecraft:deepslate_iron_ore", "minecraft:deepslate_lapis_ore",
		"minecraft:deepslate_redstone_ore", "minecraft:diamond_block",
		"minecraft:diamond_ore", "minecraft:dispenser", "minecraft:dropper",
		"minecraft:emerald_block", "minecraft:emerald_ore",
		"minecraft:enchanting_table", "minecraft:end_portal",
		"minecraft:end_portal_frame", "minecraft:ender_chest",
		"minecraft:furnace", "minecraft:glowstone", "minecraft:gold_block",
		"minecraft:gold_ore", "minecraft:hopper", "minecraft:iron_block",
		"minecraft:iron_ore", "minecraft:ladder", "minecraft:lapis_block",
		"minecraft:lapis_ore", "minecraft:lava", "minecraft:lodestone",
		"minecraft:mossy_cobblestone", "minecraft:nether_gold_ore",
		"minecraft:nether_portal", "minecraft:nether_quartz_ore",
		"minecraft:raw_copper_block", "minecraft:raw_gold_block",
		"minecraft:raw_iron_block", "minecraft:redstone_block",
		"minecraft:redstone_ore", "minecraft:repeating_command_block",
		"minecraft:sculk_catalyst", "minecraft:sculk_sensor",
		"minecraft:sculk_shrieker", "minecraft:spawner",
		"minecraft:suspicious_gravel", "minecraft:suspicious_sand",
		"minecraft:tnt", "minecraft:torch", "minecraft:trapped_chest",
		"minecraft:trial_spawner", "minecraft:vault", "minecraft:water");
	
	private final CheckboxSetting onlyExposed = new CheckboxSetting(
		"Only show exposed",
		"Only shows ores that would be visible in caves. This can help against"
			+ " anti-X-Ray plugins.\n\n"
			+ "Remember to restart X-Ray when changing this setting.",
		false);
	
	private final SliderSetting opacity = new SliderSetting("Opacity",
		"Opacity of non-ore blocks when X-Ray is enabled.\n\n"
			+ "Does not work when Sodium is installed.\n\n"
			+ "Remember to restart X-Ray when changing this setting.",
		0, 0, 0.99, 0.01, ValueDisplay.PERCENTAGE.withLabel(0, "off"));

	public enum AirCheck
	{
		ON_LOAD,
		RECHECK,
		OFF
	}
	
	private final EnumSetting<OreSimHack.AirCheck> airCheck = new EnumSetting<>(
		"Air check mode.", "Checks if there is air at a calculated ore pos.",
		OreSimHack.AirCheck.values(), OreSimHack.AirCheck.RECHECK);
	
	private final String optiFineWarning;
	private final String renderName =
		Math.random() < 0.01 ? "X-Wurst" : getName();
	
	private ArrayList<String> oreNamesCache;
	private final ThreadLocal<BlockPos.Mutable> mutablePosForExposedCheck =
		ThreadLocal.withInitial(BlockPos.Mutable::new);
	
	public OreSimHack()
	{
		super("OreSim");
		setCategory(Category.RENDER);
		addSetting(airCheck);
		addSetting(ores);
		addSetting(onlyExposed);
		addSetting(opacity);
		optiFineWarning = checkOptiFine();
	}
	
	@Override
	public String getRenderName()
	{
		return renderName;
	}
	
	@Override
	protected void onEnable()
	{
		// cache block names in case the setting changes while X-Ray is enabled
		oreNamesCache = new ArrayList<>(ores.getBlockNames());
		
		// add event listeners
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(SetOpaqueCubeListener.class, this);
		EVENTS.add(GetAmbientOcclusionLightLevelListener.class, this);
		EVENTS.add(ShouldDrawSideListener.class, this);
		EVENTS.add(RenderBlockEntityListener.class, this);
		
		// reload chunks
		MC.worldRenderer.reload();
		
		// display warning if OptiFine is detected
		if(optiFineWarning != null)
			ChatUtils.warning(optiFineWarning);
	}
	
	@Override
	protected void onDisable()
	{
		// remove event listeners
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(SetOpaqueCubeListener.class, this);
		EVENTS.remove(GetAmbientOcclusionLightLevelListener.class, this);
		EVENTS.remove(ShouldDrawSideListener.class, this);
		EVENTS.remove(RenderBlockEntityListener.class, this);
		
		// reload chunks
		MC.worldRenderer.reload();
		
		// reset gamma
		FullbrightHack fullbright = WURST.getHax().fullbrightHack;
		if(!fullbright.isChangingGamma())
			ISimpleOption.get(MC.options.getGamma())
				.forceSetValue(fullbright.getDefaultGamma());
	}
	
    public static BlockPos findNearestOre(ClientWorld world, BlockPos playerPos, Block targetOre, int searchRadius) {
        BlockPos nearestOrePos = null;
        double nearestDistance = Double.MAX_VALUE;

        // Iterate through blocks in a cubic area around the player
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                for (int y = world.getBottomY(); y <= 255; y++) {
                    BlockPos currentPos = playerPos.add(x, y - playerPos.getY(), z);

                    // Check if the block at the position matches the target ore
                    if (world.getBlockState(currentPos).isOf(targetOre)) {
                        double distance = playerPos.getSquaredDistance(new Vec3i(currentPos.getX(), currentPos.getY(), currentPos.getZ()));
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearestOrePos = currentPos;
                        }
                    }
                }
            }
        }

        return nearestOrePos;
    }

    public static Set<BlockPos> findAllNearbyOres(ServerWorld world, BlockPos playerPos, Block targetOre, int searchRadius) {
        Set<BlockPos> foundOres = new HashSet<>();

        // Iterate through blocks in a cubic area around the player
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                for (int y = world.getBottomY(); y <= 255; y++) {
                    BlockPos currentPos = playerPos.add(x, y - playerPos.getY(), z);

                    // Check if the block at the position matches the target ore
                    if (world.getBlockState(currentPos).isOf(targetOre)) {
                        foundOres.add(currentPos);
                    }
                }
            }
        }

        return foundOres;
    }

	@Override
	public void onUpdate()
	{
		System.out.println(findNearestOre(MC.world, new BlockPos(0,0,0), Blocks.DIAMOND_ORE, 100));
		// force gamma to 16 so that ores are bright enough to see
		ISimpleOption.get(MC.options.getGamma()).forceSetValue(16.0);
	}
	
	@Override
	public void onSetOpaqueCube(SetOpaqueCubeEvent event)
	{
		event.cancel();
	}
	
	@Override
	public void onGetAmbientOcclusionLightLevel(
		GetAmbientOcclusionLightLevelEvent event)
	{
		event.setLightLevel(1);
	}
	
	@Override
	public void onShouldDrawSide(ShouldDrawSideEvent event)
	{
		boolean visible =
			isVisible(event.getState().getBlock(), event.getPos());
		if(!visible && opacity.getValue() > 0)
			return;
		
		event.setRendered(visible);
	}
	
	@Override
	public void onRenderBlockEntity(RenderBlockEntityEvent event)
	{
		BlockPos pos = event.getBlockEntity().getPos();
		if(!isVisible(BlockUtils.getBlock(pos), pos))
			event.cancel();
	}
	
	public boolean isVisible(Block block, BlockPos pos)
	{
		String name = BlockUtils.getName(block);
		int index = Collections.binarySearch(oreNamesCache, name);
		boolean visible = index >= 0;
		
		if(visible && onlyExposed.isChecked() && pos != null)
			return isExposed(pos);
		
		return visible;
	}
	
	private boolean isExposed(BlockPos pos)
	{
		BlockPos.Mutable mutablePos = mutablePosForExposedCheck.get();
		for(Direction direction : Direction.values())
			if(!BlockUtils.isOpaqueFullCube(mutablePos.set(pos, direction)))
				return true;
			
		return false;
	}
	
	public boolean isOpacityMode()
	{
		return isEnabled() && opacity.getValue() > 0;
	}
	
	public int getOpacityColorMask()
	{
		return (int)(opacity.getValue() * 255) << 24 | 0xFFFFFF;
	}
	
	public float getOpacityFloat()
	{
		return opacity.getValueF();
	}
	
	/**
	 * Checks if OptiFine/OptiFabric is installed and returns a warning message
	 * if it is.
	 */
	private String checkOptiFine()
	{
		Stream<String> mods = FabricLoader.getInstance().getAllMods().stream()
			.map(ModContainer::getMetadata).map(ModMetadata::getId);
		
		Pattern optifine = Pattern.compile("opti(?:fine|fabric).*");
		
		if(mods.anyMatch(optifine.asPredicate()))
			return "OptiFine is installed. X-Ray will not work properly!";
		
		return null;
	}
	
	public void openBlockListEditor(Screen prevScreen)
	{
		MC.setScreen(new EditBlockListScreen(prevScreen, ores));
	}
	
	
	// ====================================
	// Mojang code
	// ====================================
	
	private ArrayList<Vec3d> generateNormal(ClientWorld world,
		ChunkRandom random, BlockPos blockPos, int veinSize, float discardOnAir)
	{
		float f = random.nextFloat() * 3.1415927F;
		float g = (float)veinSize / 8.0F;
		int i = MathHelper.ceil(((float)veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);
		double d = (double)blockPos.getX() + Math.sin(f) * (double)g;
		double e = (double)blockPos.getX() - Math.sin(f) * (double)g;
		double h = (double)blockPos.getZ() + Math.cos(f) * (double)g;
		double j = (double)blockPos.getZ() - Math.cos(f) * (double)g;
		double l = (blockPos.getY() + random.nextInt(3) - 2);
		double m = (blockPos.getY() + random.nextInt(3) - 2);
		int n = blockPos.getX() - MathHelper.ceil(g) - i;
		int o = blockPos.getY() - 2 - i;
		int p = blockPos.getZ() - MathHelper.ceil(g) - i;
		int q = 2 * (MathHelper.ceil(g) + i);
		int r = 2 * (2 + i);
		
		for(int s = n; s <= n + q; ++s)
		{
			for(int t = p; t <= p + q; ++t)
			{
				if(o <= world.getTopY(Heightmap.Type.MOTION_BLOCKING, s, t))
				{
					return this.generateVeinPart(world, random, veinSize, d, e,
						h, j, l, m, n, o, p, q, r, discardOnAir);
				}
			}
		}
		
		return new ArrayList<>();
	}
	
	private ArrayList<Vec3d> generateVeinPart(ClientWorld world,
		ChunkRandom random, int veinSize, double startX, double endX,
		double startZ, double endZ, double startY, double endY, int x, int y,
		int z, int size, int i, float discardOnAir)
	{
		
		BitSet bitSet = new BitSet(size * i * size);
		BlockPos.Mutable mutable = new BlockPos.Mutable();
		double[] ds = new double[veinSize * 4];
		
		ArrayList<Vec3d> poses = new ArrayList<>();
		
		int n;
		double p;
		double q;
		double r;
		double s;
		for(n = 0; n < veinSize; ++n)
		{
			float f = (float)n / (float)veinSize;
			p = MathHelper.lerp(f, startX, endX);
			q = MathHelper.lerp(f, startY, endY);
			r = MathHelper.lerp(f, startZ, endZ);
			s = random.nextDouble() * (double)veinSize / 16.0D;
			double m =
				((double)(MathHelper.sin(3.1415927F * f) + 1.0F) * s + 1.0D)
					/ 2.0D;
			ds[n * 4] = p;
			ds[n * 4 + 1] = q;
			ds[n * 4 + 2] = r;
			ds[n * 4 + 3] = m;
		}
		
		for(n = 0; n < veinSize - 1; ++n)
		{
			if(!(ds[n * 4 + 3] <= 0.0D))
			{
				for(int o = n + 1; o < veinSize; ++o)
				{
					if(!(ds[o * 4 + 3] <= 0.0D))
					{
						p = ds[n * 4] - ds[o * 4];
						q = ds[n * 4 + 1] - ds[o * 4 + 1];
						r = ds[n * 4 + 2] - ds[o * 4 + 2];
						s = ds[n * 4 + 3] - ds[o * 4 + 3];
						if(s * s > p * p + q * q + r * r)
						{
							if(s > 0.0D)
							{
								ds[o * 4 + 3] = -1.0D;
							}else
							{
								ds[n * 4 + 3] = -1.0D;
							}
						}
					}
				}
			}
		}
		
		for(n = 0; n < veinSize; ++n)
		{
			double u = ds[n * 4 + 3];
			if(!(u < 0.0D))
			{
				double v = ds[n * 4];
				double w = ds[n * 4 + 1];
				double aa = ds[n * 4 + 2];
				int ab = Math.max(MathHelper.floor(v - u), x);
				int ac = Math.max(MathHelper.floor(w - u), y);
				int ad = Math.max(MathHelper.floor(aa - u), z);
				int ae = Math.max(MathHelper.floor(v + u), ab);
				int af = Math.max(MathHelper.floor(w + u), ac);
				int ag = Math.max(MathHelper.floor(aa + u), ad);
				
				for(int ah = ab; ah <= ae; ++ah)
				{
					double ai = ((double)ah + 0.5D - v) / u;
					if(ai * ai < 1.0D)
					{
						for(int aj = ac; aj <= af; ++aj)
						{
							double ak = ((double)aj + 0.5D - w) / u;
							if(ai * ai + ak * ak < 1.0D)
							{
								for(int al = ad; al <= ag; ++al)
								{
									double am = ((double)al + 0.5D - aa) / u;
									if(ai * ai + ak * ak + am * am < 1.0D)
									{
										int an = ah - x + (aj - y) * size
											+ (al - z) * size * i;
										if(!bitSet.get(an))
										{
											bitSet.set(an);
											mutable.set(ah, aj, al);
											if(aj >= -64 && aj < 320
												&& (airCheck
													.getSelected() == AirCheck.OFF
													|| world
														.getBlockState(mutable)
														.isOpaque()))
											{
												if(shouldPlace(world, mutable,
													discardOnAir, random))
												{
													poses.add(
														new Vec3d(ah, aj, al));
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
		
		return poses;
	}
	
	private boolean shouldPlace(ClientWorld world, BlockPos orePos,
		float discardOnAir, ChunkRandom random)
	{
		if(discardOnAir == 0F
			|| (discardOnAir != 1F && random.nextFloat() >= discardOnAir))
		{
			return true;
		}
		
		for(Direction direction : Direction.values())
		{
			if(!world.getBlockState(orePos.add(direction.getVector()))
				.isOpaque() && discardOnAir != 1F)
			{
				return false;
			}
		}
		return true;
	}
	
	private ArrayList<Vec3d> generateHidden(ClientWorld world,
		ChunkRandom random, BlockPos blockPos, int size)
	{
		
		ArrayList<Vec3d> poses = new ArrayList<>();
		
		int i = random.nextInt(size + 1);
		
		for(int j = 0; j < i; ++j)
		{
			size = Math.min(j, 7);
			int x = this.randomCoord(random, size) + blockPos.getX();
			int y = this.randomCoord(random, size) + blockPos.getY();
			int z = this.randomCoord(random, size) + blockPos.getZ();
			if(airCheck.getSelected() == AirCheck.OFF
				|| world.getBlockState(new BlockPos(x, y, z)).isOpaque())
			{
				if(shouldPlace(world, new BlockPos(x, y, z), 1F, random))
				{
					poses.add(new Vec3d(x, y, z));
				}
			}
		}
		
		return poses;
	}
	
	private int randomCoord(ChunkRandom random, int size)
	{
		return Math
			.round((random.nextFloat() - random.nextFloat()) * (float)size);
	}
}
