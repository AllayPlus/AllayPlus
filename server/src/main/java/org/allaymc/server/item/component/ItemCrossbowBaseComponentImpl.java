package org.allaymc.server.item.component;

import org.allaymc.api.container.Container;
import org.allaymc.api.container.ContainerTypes;
import org.allaymc.api.entity.EntityInitInfo;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.entity.type.EntityTypes;
import org.allaymc.api.eventbus.EventHandler;
import org.allaymc.api.eventbus.event.entity.EntityShootBowEvent;
import org.allaymc.api.eventbus.event.entity.ProjectileLaunchEvent;
import org.allaymc.api.item.ItemStack;
import org.allaymc.api.item.ItemStackInitInfo;
import org.allaymc.api.item.enchantment.EnchantmentTypes;
import org.allaymc.api.item.interfaces.ItemArrowStack;
import org.allaymc.api.item.interfaces.ItemFireworkRocketStack;
import org.allaymc.api.math.MathUtils;
import org.allaymc.api.player.GameMode;
import org.allaymc.api.utils.NBTIO;
import org.allaymc.api.world.sound.CrossbowLoadSound;
import org.allaymc.api.world.sound.CrossbowLoadSound.LoadingStage;
import org.allaymc.api.world.sound.SimpleSound;
import org.allaymc.server.container.impl.OffhandContainerImpl;
import org.allaymc.server.item.component.event.CItemLoadExtraTagEvent;
import org.allaymc.server.item.component.event.CItemSaveExtraTagEvent;
import org.allaymc.server.player.AllayPlayer;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.nbt.NbtMap;
import org.joml.Vector3d;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author ClexaGod
 */
public class ItemCrossbowBaseComponentImpl extends ItemBaseComponentImpl {

    private static final String TAG_CHARGED = "Charged";
    private static final String TAG_CHARGED_ITEM = "chargedItem";
    private static final String TAG_LAUNCH_COUNT = "launchCount";

    private static final int BASE_LOAD_TICKS = 20;
    private static final int MIN_LOAD_TICKS = 5;

    private static final double ARROW_SPEED = 3.15;
    private static final double ARROW_SPREAD = 0.0075;
    private static final double MULTISHOT_ANGLE_DELTA = 10.0;
    private static final double FIREWORK_SPEED = 1.6;
    private static final int LOAD_FIRE_DELAY_TICKS = 10;

    private boolean charged;
    private NbtMap chargedItem;
    private long lastLoadTick = -1;
    private int launchCount = 1;

    public ItemCrossbowBaseComponentImpl(ItemStackInitInfo initInfo) {
        super(initInfo);
    }

    @Override
    public boolean canUseItemInAir(EntityPlayer player) {
        if (isCharged()) {
            return false;
        }

        if (hasAmmo(player) || player.getGameMode() == GameMode.CREATIVE) {
            playLoadSound(player, LoadingStage.START);
            return true;
        }

        return false;
    }

    @Override
    public void rightClickItemInAir(EntityPlayer player) {
        if (!isCharged()) {
            super.rightClickItemInAir(player);
            return;
        }

        var world = player.getWorld();
        if (world != null && lastLoadTick >= 0 && world.getTick() - lastLoadTick <= LOAD_FIRE_DELAY_TICKS) {
            return;
        }

        shootLoaded(player);
    }

    @Override
    public boolean useItemInAir(EntityPlayer player, long usedTime) {
        if (isCharged()) {
            return false;
        }

        if (usedTime < getLoadTicks()) {
            return false;
        }

        loadCrossbow(player);
        return false;
    }

    private int getLoadTicks() {
        var level = getEnchantmentLevel(EnchantmentTypes.QUICK_CHARGE);
        var ticks = BASE_LOAD_TICKS;
        if (level > 0) {
            ticks -= level * 5;
        }
        return Math.max(MIN_LOAD_TICKS, ticks);
    }

    private boolean loadCrossbow(EntityPlayer player) {
        var ammo = findAmmo(player);
        ItemStack loadedItem;
        if (ammo == null) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                return false;
            }

            loadedItem = org.allaymc.api.item.type.ItemTypes.ARROW.createItemStack(1);
        } else {
            loadedItem = ammo.itemStack().copy();
            loadedItem.setCount(1);
            if (player.getGameMode() != GameMode.CREATIVE) {
                consumeAmmo(ammo);
            }
        }

        launchCount = getEnchantmentLevel(EnchantmentTypes.MULTISHOT) > 0 ? 3 : 1;
        setChargedItem(loadedItem.saveNBT());
        var world = player.getWorld();
        if (world != null) {
            lastLoadTick = world.getTick();
        }
        sendChargedAnimation(player);
        player.notifyItemInHandChange();
        playLoadSound(player, LoadingStage.END);

        return true;
    }

    private boolean shootLoaded(EntityPlayer player) {
        var loadedItem = getChargedItemStack();
        if (loadedItem == null) {
            clearChargedItem();
            player.notifyItemInHandChange();
            return false;
        }

        var loadedLaunchCount = Math.max(1, launchCount);
        var location = player.getLocation();
        var dimension = player.getDimension();
        var shootPos = new Vector3d(location.x(), location.y() + player.getEyeHeight() - 0.1, location.z());

        var pierceLevel = getEnchantmentLevel(EnchantmentTypes.PIERCING);
        var fired = false;
        if (loadedItem instanceof ItemFireworkRocketStack rocketItem) {
            if (loadedLaunchCount > 1) {
                fired |= shootRocket(player, rocketItem, -MULTISHOT_ANGLE_DELTA);
                fired |= shootRocket(player, rocketItem, 0.0);
                fired |= shootRocket(player, rocketItem, MULTISHOT_ANGLE_DELTA);
            } else {
                fired |= shootRocket(player, rocketItem, 0.0);
            }
        } else if (loadedItem instanceof ItemArrowStack arrowItem) {
            if (loadedLaunchCount > 1) {
                fired |= shootArrow(player, arrowItem, -MULTISHOT_ANGLE_DELTA, pierceLevel, false);
                fired |= shootArrow(player, arrowItem, 0.0, pierceLevel, true);
                fired |= shootArrow(player, arrowItem, MULTISHOT_ANGLE_DELTA, pierceLevel, false);
            } else {
                fired |= shootArrow(player, arrowItem, 0.0, pierceLevel, true);
            }
        } else {
            clearChargedItem();
            player.notifyItemInHandChange();
            return false;
        }

        if (!fired) {
            return false;
        }

        clearChargedItem();
        launchCount = 1;
        lastLoadTick = -1;
        if (player.getGameMode() != GameMode.CREATIVE) {
            var durabilityDamage = 1;
            if (loadedItem instanceof ItemFireworkRocketStack) {
                durabilityDamage = 3;
            } else if (loadedLaunchCount > 1) {
                durabilityDamage = 3;
            }
            tryIncreaseDamage(durabilityDamage);
        }
        player.notifyItemInHandChange();
        dimension.addSound(shootPos, SimpleSound.CROSSBOW_SHOOT);

        return true;
    }

    private boolean shootArrow(EntityPlayer player, ItemArrowStack arrowItem, double yawOffset, int pierceLevel, boolean allowPickup) {
        var location = player.getLocation();
        var dimension = player.getDimension();
        var yaw = location.yaw();
        var pitch = location.pitch();

        var direction = new Vector3d(MathUtils.getDirectionVector(yaw, pitch));
        if (yawOffset != 0.0) {
            direction.rotateY(Math.toRadians(yawOffset));
        }
        var random = ThreadLocalRandom.current();
        direction.add(
                random.nextGaussian() * ARROW_SPREAD,
                random.nextGaussian() * ARROW_SPREAD,
                random.nextGaussian() * ARROW_SPREAD
        );
        direction.normalize();
        var arrowYaw = MathUtils.getYawFromVector(direction);
        var arrowPitch = MathUtils.getPitchFromVector(direction);

        var shootPos = new Vector3d(location.x(), location.y() + player.getEyeHeight() - 0.1, location.z());
        if (yawOffset != 0.0) {
            var side = new Vector3d(direction).cross(0, 1, 0);
            if (side.lengthSquared() > 0) {
                side.normalize();
                side.mul(yawOffset > 0 ? 0.25 : -0.25);
                shootPos.add(side);
            }
        }

        var baseMotion = new Vector3d(direction).mul(ARROW_SPEED);
        var arrow = EntityTypes.ARROW.createEntity(
                EntityInitInfo.builder()
                        .dimension(dimension)
                        .pos(shootPos)
                        .rot(-arrowYaw, -arrowPitch)
                        .motion(baseMotion)
                        .build()
        );
        arrow.setShooter(player);
        arrow.setPotionType(arrowItem.getPotionType());
        if (pierceLevel > 0) {
            arrow.setPierceLevel(pierceLevel);
        }
        arrow.setInfinite(player.getGameMode() == GameMode.CREATIVE);
        arrow.setPickUpDisabled(!allowPickup);

        var event = new EntityShootBowEvent(player, thisItemStack, arrow);
        if (!event.call()) {
            return false;
        }

        var launchEvent = new ProjectileLaunchEvent(arrow, player, ARROW_SPEED);
        if (!launchEvent.call()) {
            return false;
        }
        if (launchEvent.getThrowForce() != ARROW_SPEED) {
            arrow.setMotion(new Vector3d(direction).mul(launchEvent.getThrowForce()));
        }

        dimension.getEntityManager().addEntity(arrow);
        return true;
    }

    private boolean shootRocket(EntityPlayer player, ItemFireworkRocketStack rocketItem, double yawOffset) {
        var location = player.getLocation();
        var dimension = player.getDimension();
        var yaw = location.yaw();
        var pitch = location.pitch();

        var direction = new Vector3d(MathUtils.getDirectionVector(yaw, pitch));
        if (yawOffset != 0.0) {
            direction.rotateY(Math.toRadians(yawOffset));
        }
        direction.normalize();
        var rocketYaw = MathUtils.getYawFromVector(direction);
        var rocketPitch = MathUtils.getPitchFromVector(direction);

        var shootPos = new Vector3d(location.x(), location.y() + player.getEyeHeight() - 0.1, location.z());
        if (yawOffset != 0.0) {
            var side = new Vector3d(direction).cross(0, 1, 0);
            if (side.lengthSquared() > 0) {
                side.normalize();
                side.mul(yawOffset > 0 ? 0.25 : -0.25);
                shootPos.add(side);
            }
        }

        var rocket = EntityTypes.FIREWORKS_ROCKET.createEntity(
                EntityInitInfo.builder()
                        .dimension(dimension)
                        .pos(shootPos)
                        .rot(-rocketYaw, -rocketPitch)
                        .build()
        );
        rocket.setExistenceTicks(rocketItem.getRandomizedDuration());
        rocket.setExplosions(rocketItem.getExplosions());
        rocket.setMotion(direction.mul(FIREWORK_SPEED));
        dimension.getEntityManager().addEntity(rocket);
        return true;
    }

    private boolean hasAmmo(EntityPlayer player) {
        return findAmmo(player) != null;
    }

    private LoadItem findAmmo(EntityPlayer player) {
        var offhand = player.getContainer(ContainerTypes.OFFHAND);
        var offhandItem = offhand.getItemStack(OffhandContainerImpl.OFFHAND_SLOT);
        if (isLoadableAmmo(offhandItem)) {
            return new LoadItem(offhand, OffhandContainerImpl.OFFHAND_SLOT, offhandItem);
        }

        var inventory = player.getContainer(ContainerTypes.INVENTORY);
        var items = inventory.getItemStacks();
        for (var slot = 0; slot < items.size(); slot++) {
            var item = items.get(slot);
            if (isLoadableAmmo(item)) {
                return new LoadItem(inventory, slot, item);
            }
        }

        return null;
    }

    private boolean isLoadableAmmo(ItemStack itemStack) {
        if (itemStack == null || itemStack.getCount() <= 0) {
            return false;
        }

        return itemStack instanceof ItemArrowStack || itemStack instanceof ItemFireworkRocketStack;
    }

    private void consumeAmmo(LoadItem ammo) {
        var item = ammo.itemStack();
        if (item.getCount() == 1) {
            ammo.container().clearSlot(ammo.slot());
        } else {
            item.reduceCount(1);
            ammo.container().notifySlotChange(ammo.slot());
        }
    }

    private boolean isCharged() {
        return chargedItem != null && !chargedItem.isEmpty();
    }

    private void setChargedItem(NbtMap itemNbt) {
        chargedItem = Objects.requireNonNull(itemNbt);
        charged = true;
    }

    private void clearChargedItem() {
        chargedItem = null;
        charged = false;
        launchCount = 1;
    }

    private ItemStack getChargedItemStack() {
        if (!isCharged()) {
            return null;
        }

        var itemStack = NBTIO.getAPI().fromItemStackNBT(chargedItem);
        if (itemStack.getItemType() == org.allaymc.api.item.type.ItemTypes.AIR) {
            return null;
        }

        return itemStack;
    }

    private void playLoadSound(EntityPlayer player, LoadingStage stage) {
        player.getDimension().addSound(player.getLocation(), new CrossbowLoadSound(stage, false));
    }

    private void sendChargedAnimation(EntityPlayer player) {
        var packet = new EntityEventPacket();
        packet.setType(EntityEventType.FINISHED_CHARGING_ITEM);
        packet.setRuntimeEntityId(player.getRuntimeId());
        player.forEachViewers(viewer -> {
            if (viewer instanceof AllayPlayer allayViewer) {
                allayViewer.sendPacket(packet);
            }
        });
    }

    @EventHandler
    protected void onLoadExtraTag(CItemLoadExtraTagEvent event) {
        var extraTag = event.getExtraTag();
        extraTag.listenForBoolean(TAG_CHARGED, value -> charged = value);
        extraTag.listenForCompound(TAG_CHARGED_ITEM, value -> chargedItem = value);
        extraTag.listenForInt(TAG_LAUNCH_COUNT, value -> launchCount = Math.max(1, value));
        if (chargedItem != null) {
            charged = true;
        }
    }

    @EventHandler
    protected void onSaveExtraTag(CItemSaveExtraTagEvent event) {
        var extraTag = event.getExtraTag();
        if (chargedItem != null) {
            extraTag.putBoolean(TAG_CHARGED, charged);
            extraTag.putCompound(TAG_CHARGED_ITEM, chargedItem);
            extraTag.putInt(TAG_LAUNCH_COUNT, launchCount);
        }
    }


    private record LoadItem(Container container, int slot, ItemStack itemStack) {
    }
}
