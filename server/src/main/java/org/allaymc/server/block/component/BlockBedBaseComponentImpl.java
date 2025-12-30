package org.allaymc.server.block.component;

import com.google.common.base.Preconditions;
import org.allaymc.api.block.BlockBehavior;
import org.allaymc.api.block.component.BlockBlockEntityHolderComponent;
import org.allaymc.api.block.data.BlockFace;
import org.allaymc.api.block.data.BlockTags;
import org.allaymc.api.block.dto.Block;
import org.allaymc.api.block.dto.PlayerInteractInfo;
import org.allaymc.api.block.property.type.BlockPropertyTypes;
import org.allaymc.api.block.type.BlockState;
import org.allaymc.api.block.type.BlockType;
import org.allaymc.api.blockentity.interfaces.BlockEntityBed;
import org.allaymc.api.entity.Entity;
import org.allaymc.api.eventbus.event.block.BlockExplodeEvent;
import org.allaymc.api.item.ItemStack;
import org.allaymc.api.math.location.Location3i;
import org.allaymc.api.math.position.Position3ic;
import org.allaymc.api.message.TrKeys;
import org.allaymc.api.player.GameMode;
import org.allaymc.api.utils.identifier.Identifier;
import org.allaymc.api.world.Dimension;
import org.allaymc.api.world.Explosion;
import org.allaymc.api.world.data.DimensionInfo;
import org.allaymc.api.world.WorldData;
import org.allaymc.api.world.data.Weather;
import org.allaymc.api.world.gamerule.GameRule;
import org.allaymc.server.component.annotation.Dependency;
import org.allaymc.server.entity.data.EntityId;
import org.allaymc.server.utils.BedUtils;
import org.allaymc.server.world.AllayWorld;
import org.joml.Vector3ic;

import java.util.Set;

/**
 * @author harry-xi
 */
public class BlockBedBaseComponentImpl extends BlockBaseComponentImpl {

    @Dependency
    BlockBlockEntityHolderComponent<BlockEntityBed> blockEntityHolderComponent;

    private static final int SLEEP_MONSTER_HORIZONTAL_RANGE = 8;
    private static final int SLEEP_MONSTER_VERTICAL_RANGE = 6;
    private static final Set<Identifier> SLEEP_BLOCKING_IDS = Set.of(
            EntityId.BLAZE.getIdentifier(),
            EntityId.BOGGED.getIdentifier(),
            EntityId.BREEZE.getIdentifier(),
            EntityId.CAVE_SPIDER.getIdentifier(),
            EntityId.CREAKING.getIdentifier(),
            EntityId.CREEPER.getIdentifier(),
            EntityId.DROWNED.getIdentifier(),
            EntityId.ELDER_GUARDIAN.getIdentifier(),
            EntityId.ELDER_GUARDIAN_GHOST.getIdentifier(),
            EntityId.ENDERMAN.getIdentifier(),
            EntityId.ENDERMITE.getIdentifier(),
            EntityId.EVOCATION_ILLAGER.getIdentifier(),
            EntityId.GHAST.getIdentifier(),
            EntityId.GUARDIAN.getIdentifier(),
            EntityId.HOGLIN.getIdentifier(),
            EntityId.HUSK.getIdentifier(),
            EntityId.MAGMA_CUBE.getIdentifier(),
            EntityId.PHANTOM.getIdentifier(),
            EntityId.PIGLIN.getIdentifier(),
            EntityId.PIGLIN_BRUTE.getIdentifier(),
            EntityId.PILLAGER.getIdentifier(),
            EntityId.RAVAGER.getIdentifier(),
            EntityId.SHULKER.getIdentifier(),
            EntityId.SILVERFISH.getIdentifier(),
            EntityId.SKELETON.getIdentifier(),
            EntityId.SLIME.getIdentifier(),
            EntityId.SPIDER.getIdentifier(),
            EntityId.STRAY.getIdentifier(),
            EntityId.VEX.getIdentifier(),
            EntityId.VINDICATOR.getIdentifier(),
            EntityId.WARDEN.getIdentifier(),
            EntityId.WITCH.getIdentifier(),
            EntityId.WITHER.getIdentifier(),
            EntityId.WITHER_SKELETON.getIdentifier(),
            EntityId.ZOGLIN.getIdentifier(),
            EntityId.ZOMBIE.getIdentifier(),
            EntityId.ZOMBIE_PIGMAN.getIdentifier(),
            EntityId.ZOMBIE_VILLAGER.getIdentifier(),
            EntityId.ZOMBIE_VILLAGER_V2.getIdentifier()
    );

    public BlockBedBaseComponentImpl(BlockType<? extends BlockBehavior> blockType) {
        super(blockType);
    }

    public static Block getPairBlock(Block block) {
        var otherPos = posOfOtherPart(block);
        return new Block(block.getDimension(), otherPos);
    }

    private static Vector3ic posOfOtherPart(Block block) {
        var head = block.getPropertyValue(BlockPropertyTypes.HEAD_PIECE_BIT);
        var face = Preconditions.checkNotNull(BlockFace.fromHorizontalIndex(block.getPropertyValue(BlockPropertyTypes.DIRECTION_4)));
        return (head ? face.opposite() : face).offsetPos(block.getPosition());
    }

    private static boolean posEqVec3ic(Position3ic pos, Vector3ic other) {
        return pos.x() == other.x() && pos.y() == other.y() && pos.z() == other.z();
    }

    private static boolean isPairValid(Block block, Block otherPart) {
        if (otherPart.getBlockType() != block.getBlockType()) {
            return false;
        }

        var head = block.getPropertyValue(BlockPropertyTypes.HEAD_PIECE_BIT);
        var otherHead = otherPart.getPropertyValue(BlockPropertyTypes.HEAD_PIECE_BIT);
        if (head == otherHead) {
            return false;
        }

        var dir = block.getPropertyValue(BlockPropertyTypes.DIRECTION_4);
        var otherDir = otherPart.getPropertyValue(BlockPropertyTypes.DIRECTION_4);
        return otherDir.intValue() == dir.intValue();
    }

    private static Block getValidHead(Block block) {
        var otherPart = getPairBlock(block);
        if (!isPairValid(block, otherPart)) {
            return null;
        }
        return block.getPropertyValue(BlockPropertyTypes.HEAD_PIECE_BIT) ? block : otherPart;
    }

    private static Block getValidFoot(Block block) {
        var otherPart = getPairBlock(block);
        if (!isPairValid(block, otherPart)) {
            return null;
        }
        return block.getPropertyValue(BlockPropertyTypes.HEAD_PIECE_BIT) ? otherPart : block;
    }

    @Override
    public boolean place(Dimension dimension, BlockState blockState, Vector3ic placeBlockPos, PlayerInteractInfo placementInfo) {
        var playerFace = placementInfo.player().getHorizontalFace();
        var nextPos = playerFace.offsetPos(placeBlockPos);
        var nextBlockState = dimension.getBlockState(nextPos);
        if (!nextBlockState.getBlockType().hasBlockTag(BlockTags.REPLACEABLE)) {
            return false;
        }

        blockState = blockState
                .setPropertyValue(BlockPropertyTypes.DIRECTION_4, playerFace.getHorizontalIndex())
                .setPropertyValue(BlockPropertyTypes.OCCUPIED_BIT, false);

        return dimension.setBlockState(
                placeBlockPos.x(), placeBlockPos.y(), placeBlockPos.z(),
                processBlockProperties(blockState.setPropertyValue(BlockPropertyTypes.HEAD_PIECE_BIT, false), placeBlockPos, placementInfo),
                placementInfo
        ) && dimension.setBlockState(
                nextPos.x(), nextPos.y(), nextPos.z(),
                processBlockProperties(blockState.setPropertyValue(BlockPropertyTypes.HEAD_PIECE_BIT, true), nextPos, placementInfo),
                placementInfo
        );
    }

    @Override
    public boolean onInteract(ItemStack itemStack, Dimension dimension, PlayerInteractInfo interactInfo) {
        if (dimension.getDimensionInfo() != DimensionInfo.OVERWORLD &&
            dimension.getWorld().getWorldData().<Boolean>getGameRuleValue(GameRule.RESPAWN_BLOCKS_EXPLODE)) {
            var explosion = new Explosion(5, true);
            var event = new BlockExplodeEvent(interactInfo.getClickedBlock(), explosion);
            if (!event.call()) {
                return false;
            }

            explosion.explode(dimension, interactInfo.clickedBlockPos());
            return true;
        }

        if (dimension.getDimensionInfo() != DimensionInfo.OVERWORLD) {
            return true;
        }

        var player = interactInfo.player();
        if (player == null) {
            return false;
        }

        var clickedBlock = interactInfo.getClickedBlock();
        var headBlock = getValidHead(clickedBlock);
        var footBlock = getValidFoot(clickedBlock);
        if (headBlock == null || footBlock == null) {
            player.sendTranslatable(TrKeys.MC_TILE_BED_NOTVALID);
            return true;
        }

        if (!player.canReachBlock(headBlock.getPosition())) {
            player.sendTranslatable(TrKeys.MC_TILE_BED_TOOFAR);
            return true;
        }

        var headPos = headBlock.getPosition();
        var currentSpawn = player.validateAndGetSpawnPoint();
        var currentDim = currentSpawn.dimension();
        var sameSpawn = currentDim != null &&
                currentDim.getWorld() == dimension.getWorld() &&
                currentDim.getDimensionInfo().dimensionId() == dimension.getDimensionInfo().dimensionId() &&
                currentSpawn.x() == headPos.x() &&
                currentSpawn.y() == headPos.y() &&
                currentSpawn.z() == headPos.z();
        if (!sameSpawn) {
            player.setSpawnPoint(new Location3i(headPos.x(), headPos.y(), headPos.z(), dimension));
            player.sendTranslatable(TrKeys.MC_TILE_BED_RESPAWNSET);
        }

        var world = dimension.getWorld();
        var time = world.getWorldData().getTimeOfDay();
        var isNight = time >= WorldData.TIME_NIGHT && time < WorldData.TIME_SUNRISE;
        if (!isNight && world.getWeather() != Weather.THUNDER) {
            player.sendTranslatable(TrKeys.MC_TILE_BED_NOSLEEP);
            return true;
        }

        if (headBlock.getPropertyValue(BlockPropertyTypes.OCCUPIED_BIT) ||
            BedUtils.isBedOccupiedByPlayer(dimension, headPos)) {
            player.sendTranslatable(TrKeys.MC_TILE_BED_OCCUPIED);
            return true;
        }

        if (player.getGameMode() != GameMode.CREATIVE && isSleepBlockedByMonsters(dimension, headPos)) {
            player.sendTranslatable(TrKeys.MC_TILE_BED_NOTSAFE);
            return true;
        }

        if (!player.sleepOn(headBlock)) {
            player.sendTranslatable(TrKeys.MC_TILE_BED_OCCUPIED);
            return true;
        }

        if (world instanceof AllayWorld allayWorld) {
            allayWorld.scheduleSleepCheck();
        }
        return true;
    }

    @Override
    public void onNeighborUpdate(Block block, Block neighbor, BlockFace face) {
        super.onNeighborUpdate(block, neighbor, face);
        if (posEqVec3ic(neighbor.getPosition(), posOfOtherPart(block))
            && neighbor.getBlockType() != getBlockType()) {
            block.breakBlock();
        }
    }

    @Override
    public void onBreak(Block block, ItemStack usedItem, Entity entity) {
        wakeSleepingPlayers(block);
        if (block.getPropertyValue(BlockPropertyTypes.HEAD_PIECE_BIT)) {
            var footPos = posOfOtherPart(block);
            block.getDimension().breakBlock(footPos, null, entity);
        }
        super.onBreak(block, usedItem, entity);
    }

    @Override
    public Set<ItemStack> getDrops(Block block, ItemStack usedItem, Entity entity) {
        return block.getPropertyValue(BlockPropertyTypes.HEAD_PIECE_BIT) ? Set.of() : createBedDrop(block);
    }

    private Set<ItemStack> createBedDrop(Block blockState) {
        var blockEntity = blockEntityHolderComponent.getBlockEntity(blockState.getPosition());
        var drop = blockState.toItemStack();
        drop.setMeta(blockEntity.getColor().ordinal());
        return Set.of(drop);
    }

    private boolean isSleepBlockedByMonsters(Dimension dimension, Vector3ic bedPos) {
        double bedX = bedPos.x() + 0.5;
        double bedY = bedPos.y() + 0.5;
        double bedZ = bedPos.z() + 0.5;

        for (var entry : dimension.getEntities().values()) {
            if (entry.isDead()) {
                continue;
            }
            if (!SLEEP_BLOCKING_IDS.contains(entry.getEntityType().getIdentifier())) {
                continue;
            }

            var loc = entry.getLocation();
            if (Math.abs(loc.x() - bedX) > SLEEP_MONSTER_HORIZONTAL_RANGE ||
                Math.abs(loc.z() - bedZ) > SLEEP_MONSTER_HORIZONTAL_RANGE ||
                Math.abs(loc.y() - bedY) > SLEEP_MONSTER_VERTICAL_RANGE) {
                continue;
            }

            return true;
        }

        return false;
    }

    private void wakeSleepingPlayers(Block bedBlock) {
        var headBlock = getValidHead(bedBlock);
        if (headBlock == null) {
            return;
        }

        var headPos = headBlock.getPosition();
        for (var player : BedUtils.getSleepingPlayersAtBed(headPos.dimension(), headPos)) {
            player.stopSleep();
        }
    }
}
