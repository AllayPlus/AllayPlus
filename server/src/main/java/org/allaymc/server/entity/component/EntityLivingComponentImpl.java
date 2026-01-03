package org.allaymc.server.entity.component;

import lombok.Getter;
import lombok.Setter;
import org.allaymc.api.container.Container;
import org.allaymc.api.container.ContainerHolder;
import org.allaymc.api.container.ContainerTypes;
import org.allaymc.api.container.interfaces.ArmorContainer;
import org.allaymc.api.entity.Entity;
import org.allaymc.api.entity.EntityState;
import org.allaymc.api.entity.action.CriticalHit;
import org.allaymc.api.entity.action.EnchantedHit;
import org.allaymc.api.entity.action.SimpleEntityAction;
import org.allaymc.api.entity.component.EntityContainerHolderComponent;
import org.allaymc.api.entity.component.EntityLivingComponent;
import org.allaymc.api.entity.component.EntityPhysicsComponent;
import org.allaymc.api.entity.damage.DamageContainer;
import org.allaymc.api.entity.damage.DamageType;
import org.allaymc.api.entity.effect.EffectInstance;
import org.allaymc.api.entity.effect.EffectType;
import org.allaymc.api.entity.effect.EffectTypes;
import org.allaymc.api.entity.interfaces.EntityArrow;
import org.allaymc.api.entity.interfaces.EntityLiving;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.entity.interfaces.EntityProjectile;
import org.allaymc.api.entity.interfaces.EntityWarden;
import org.allaymc.api.eventbus.EventHandler;
import org.allaymc.api.eventbus.event.entity.*;
import org.allaymc.api.item.ItemHelper;
import org.allaymc.api.item.enchantment.EnchantmentTypes;
import org.allaymc.api.item.interfaces.ItemAirStack;
import org.allaymc.api.item.type.ItemTypes;
import org.allaymc.api.math.MathUtils;
import org.allaymc.api.utils.identifier.Identifier;
import org.allaymc.api.world.gamerule.GameRule;
import org.allaymc.api.world.particle.SimpleParticle;
import org.allaymc.api.world.sound.CustomSound;
import org.allaymc.api.world.sound.SimpleSound;
import org.allaymc.api.world.sound.SoundNames;
import org.allaymc.server.component.ComponentClass;
import org.allaymc.server.component.ComponentManager;
import org.allaymc.server.component.annotation.ComponentObject;
import org.allaymc.server.component.annotation.Dependency;
import org.allaymc.server.component.annotation.Manager;
import org.allaymc.server.container.impl.OffhandContainerImpl;
import org.allaymc.server.entity.component.event.*;
import org.allaymc.server.entity.component.player.EntityPlayerBaseComponentImpl;
import org.allaymc.server.entity.impl.EntityPlayerImpl;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * @author daoge_cmd
 */
public class EntityLivingComponentImpl implements EntityLivingComponent {

    @Identifier.Component
    public static final Identifier IDENTIFIER = new Identifier("minecraft:entity_living_component");

    protected static final int SHIELD_DISABLE_TICKS = 100;
    protected static final int SHIELD_STRONG_HIT_DAMAGE = 6;

    protected static final String TAG_ACTIVE_EFFECTS = "ActiveEffects";
    protected static final String TAG_FIRE = "Fire";
    protected static final String TAG_ABSORPTION = "Absorption";
    protected static final String TAG_HEALTH = "Health";
    protected static final String TAG_MAX_HEALTH = "MaxHealth";

    @Manager
    protected ComponentManager manager;
    @Dependency
    protected EntityBaseComponentImpl baseComponent;
    @Dependency(optional = true)
    protected EntityPhysicsComponent physicsComponent;
    @Dependency(optional = true)
    protected EntityContainerHolderComponent containerHolderComponent;
    @ComponentObject
    protected EntityLiving thisEntity;

    @Getter
    protected DamageContainer lastDamage;
    @Getter
    protected long lastDamageTime;

    @Getter
    protected int onFireTicks;
    @Getter
    protected int airSupplyTicks, airSupplyMaxTicks;

    @Getter
    protected float health, maxHealth;
    @Getter
    @Setter
    protected float absorption;

    protected Map<EffectType, EffectInstance> effects;
    protected int deadTimer;

    public EntityLivingComponentImpl() {
        this.effects = new HashMap<>();
        this.airSupplyTicks = this.airSupplyMaxTicks = DEFAULT_MAX_AIR_SUPPLY;
        this.health = this.maxHealth = DEFAULT_MAX_HEALTH;
    }

    @Override
    public boolean attack(DamageContainer damage, boolean ignoreCoolDown) {
        if (!thisEntity.isAlive() ||
            !canBeAttacked(damage) ||
            !checkAndUpdateCoolDown(damage, ignoreCoolDown)) {
            return false;
        }

        var event = new EntityDamageEvent(thisEntity, damage);
        if (!event.call()) return false;

        damage = event.getDamageContainer();

        if (blockedByShield(damage)) {
            return false;
        }

        applyAttacker(damage);
        applyVictim(damage);
        applyDamage(damage);
        return true;
    }

    protected void applyDamage(DamageContainer damage) {
        setHealth(this.health - damage.getFinalDamage());
        thisEntity.applyAction(SimpleEntityAction.HURT);
        if (damage.isCritical()) {
            thisEntity.applyAction(new CriticalHit(55));
        }
        if (damage.isEnchanted()) {
            thisEntity.applyAction(new EnchantedHit(15));
        }

        // Call the event in advance, as it may return early due to a short circuit later
        this.manager.callEvent(CEntityAfterDamageEvent.INSTANCE);

        if (damage.getAttacker() instanceof Entity entity) {
            ((ComponentClass) entity).getManager().callEvent(CEntityAttackEvent.INSTANCE);
        } else {
            return;
        }


        if (!damage.isHasKnockback()) {
            return;
        }

        if (physicsComponent != null) {
            var kb = EntityPhysicsComponent.DEFAULT_KNOCKBACK;
            var kby = EntityPhysicsComponent.DEFAULT_KNOCKBACK;
            var additionalMotion = new Vector3d();
            if (entity instanceof EntityContainerHolderComponent component && component.hasContainer(ContainerTypes.INVENTORY)) {
                var kbEnchantmentLevel = component.getContainer(ContainerTypes.INVENTORY).getItemInHand().getEnchantmentLevel(EnchantmentTypes.KNOCKBACK);
                if (kbEnchantmentLevel != 0) {
                    kb /= 2.0;
                    additionalMotion = MathUtils.normalizeIfNotZero(MathUtils.getDirectionVector(entity.getLocation()).setComponent(1, 0));
                    additionalMotion.mul(kbEnchantmentLevel * 0.5);
                }
            }

            physicsComponent.knockback(entity.getLocation(), kb, kby, additionalMotion);
        }
    }

    protected boolean blockedByShield(DamageContainer damage) {
        if (!(thisEntity instanceof EntityPlayer player)) {
            return false;
        }

        if (!player.isBlocking() || !isShieldBlockableDamage(damage)) {
            return false;
        }

        var attackerObj = damage.getAttacker();
        if (!isShieldBlockableAttacker(attackerObj)) {
            return false;
        }
        var attacker = (Entity) attackerObj;

        if (!isShieldFacingAttacker(player, attacker)) {
            return false;
        }

        var disableAfterBlock = shouldDisableShield(player, attacker);

        var shieldSlot = findShieldSlot(player);
        if (shieldSlot == null) {
            return false;
        }

        var shield = shieldSlot.container().getItemStack(shieldSlot.slot());
        int durabilityDamage = 1;
        if (damage.getFinalDamage() >= SHIELD_STRONG_HIT_DAMAGE) {
            durabilityDamage = (int) Math.ceil(damage.getFinalDamage());
        }

        shield.tryIncreaseDamage(durabilityDamage);
        var shieldBroken = shield.isBroken();
        if (shieldBroken) {
            shieldSlot.container().clearSlot(shieldSlot.slot());
            player.getDimension().addSound(player.getLocation(), SimpleSound.ITEM_BREAK);
            player.setBlocking(false);
        } else {
            shieldSlot.container().notifySlotChange(shieldSlot.slot());
        }

        player.getDimension().addSound(player.getLocation(), new CustomSound(SoundNames.ITEM_SHIELD_BLOCK));
        if (player instanceof EntityPlayerImpl playerImpl) {
            var baseComponent = (EntityPlayerBaseComponentImpl) playerImpl.getBaseComponent();
            baseComponent.triggerShieldBlockAnimation(shieldBroken);
        }
        if (disableAfterBlock) {
            applyShieldDisable(player);
        }
        applyShieldAttackerKnockback(player, attacker, damage);
        return true;
    }

    protected boolean isShieldFacingAttacker(EntityPlayer player, Entity attacker) {
        var playerLoc = player.getLocation();
        var attackerLoc = attacker.getLocation();
        var direction = MathUtils.getDirectionVector(playerLoc.yaw(), playerLoc.pitch()).setComponent(1, 0).normalize();
        var normalizedVector = new Vector3d(playerLoc.x(), playerLoc.y(), playerLoc.z())
                .sub(attackerLoc.x(), attackerLoc.y(), attackerLoc.z())
                .normalize();
        return (normalizedVector.x * direction.x) + (normalizedVector.z * direction.z) < 0.0;
    }

    protected boolean isShieldBlockableDamage(DamageContainer damage) {
        return damage.canBeReducedByArmor();
    }

    protected boolean isShieldBlockableAttacker(Object attackerObj) {
        if (!(attackerObj instanceof Entity)) {
            return false;
        }

        if (attackerObj instanceof EntityArrow arrow && arrow.getPierceLevel() > 0) {
            return false;
        }

        return true;
    }

    protected ShieldSlot findShieldSlot(EntityPlayer player) {
        var offhand = player.getContainer(ContainerTypes.OFFHAND);
        if (offhand != null && offhand.getItemStack(OffhandContainerImpl.OFFHAND_SLOT).getItemType() == ItemTypes.SHIELD) {
            return new ShieldSlot(offhand, OffhandContainerImpl.OFFHAND_SLOT);
        }

        var inventory = player.getContainer(ContainerTypes.INVENTORY);
        if (inventory == null) {
            return null;
        }

        var handSlot = inventory.getHandSlot();
        if (inventory.getItemStack(handSlot).getItemType() == ItemTypes.SHIELD) {
            return new ShieldSlot(inventory, handSlot);
        }

        return null;
    }

    protected record ShieldSlot(Container container, int slot) {
    }

    protected boolean shouldDisableShield(EntityPlayer player, Entity attacker) {
        if (attacker instanceof EntityWarden) {
            return true;
        }

        if (attacker instanceof EntityPlayer attackerPlayer) {
            return isHoldingAxe(attackerPlayer);
        }

        if (attacker instanceof EntityContainerHolderComponent component) {
            if (!component.hasContainer(ContainerTypes.INVENTORY)) {
                return false;
            }
            var inventory = component.getContainer(ContainerTypes.INVENTORY);
            if (inventory == null) {
                return false;
            }
            return ItemHelper.isAxe(inventory.getItemInHand().getItemType());
        }

        return false;
    }

    protected boolean isHoldingAxe(EntityPlayer player) {
        var inventory = player.getContainer(ContainerTypes.INVENTORY);
        if (inventory == null) {
            return false;
        }

        return ItemHelper.isAxe(inventory.getItemInHand().getItemType());
    }

    protected void applyShieldDisable(EntityPlayer player) {
        player.setShieldBlockingDelay(SHIELD_DISABLE_TICKS);
        player.setCooldown("shield", SHIELD_DISABLE_TICKS, true);
    }

    protected void applyShieldAttackerKnockback(EntityPlayer player, Entity attacker, DamageContainer damage) {
        if (damage.getDamageType() != DamageType.ENTITY_ATTACK) {
            return;
        }
        if (attacker instanceof EntityProjectile) {
            return;
        }
        if (!(attacker instanceof EntityLiving) || !(attacker instanceof EntityPhysicsComponent attackerPhysics)) {
            return;
        }

        attackerPhysics.knockback(player.getLocation(), EntityPhysicsComponent.DEFAULT_KNOCKBACK);
    }

    protected boolean checkAndUpdateCoolDown(DamageContainer damage, boolean forceToUpdate) {
        var currentTime = thisEntity.getWorld().getTick();
        if (!forceToUpdate && lastDamage != null && currentTime - lastDamageTime <= lastDamage.getCoolDown()) {
            return false;
        }

        lastDamage = damage;
        lastDamageTime = currentTime;
        return true;
    }

    protected void applyVictim(DamageContainer damage) {
        applyArmor(damage);
        applyEffects(damage);
    }

    // TODO: Implement breach enchantment
    protected void applyArmor(DamageContainer damage) {
        if (!damage.canBeReducedByArmor() || containerHolderComponent == null) {
            return;
        }

        var armorContainer = containerHolderComponent.getContainer(ContainerTypes.ARMOR);
        if (armorContainer == null) {
            return;
        }

        int durabilityIncreased = Math.max(1, (int) (damage.getSourceDamage() / 4f));
        var itemStackArray = armorContainer.getItemStackArray();
        for (int slot = 0; slot < itemStackArray.length; slot++) {
            var item = itemStackArray[slot];
            if (!item.getItemType().getItemData().isDamageable()) {
                continue;
            }
            item.tryIncreaseDamage(durabilityIncreased);
            armorContainer.notifySlotChange(slot);
        }

        if (damage.getDamageType() == DamageType.FALL) {
            // Fall damage can't be reduced by armor value,
            // but it can be reduced by feather falling enchantment
            applyArmorWhenFall(damage, armorContainer);
            return;
        }

        var totalArmorValue = 0f;
        var totalToughnessValue = 0f;
        var enchantmentProtectionFactor = 0;

        for (var item : armorContainer.getItemStacks()) {
            if (item == ItemAirStack.AIR_STACK) {
                continue;
            }

            totalArmorValue += item.getItemType().getItemData().armorValue();
            totalToughnessValue += item.getItemType().getItemData().toughnessValue();
            enchantmentProtectionFactor += item.getEnchantmentProtectionFactor(damage.getDamageType());
        }
        enchantmentProtectionFactor = Math.min(20, enchantmentProtectionFactor);

        // See https://minecraft.wiki/w/Armor#Damage_reduction
        final var v = totalArmorValue;
        final var t = totalToughnessValue;
        damage.updateFinalDamage(d -> {
            if (0 <= d && d <= 1.6f * v + 0.2f * v * t) {
                return (1f / (6.25f + 50f)) * d * d +
                       (1f - v / 25f) * d;
            } else {
                return (1f - v / 125f) * d;
            }
        });

        // See https://minecraft.wiki/w/Armor#Enchantments
        final var epf = enchantmentProtectionFactor;
        if (epf != 0) {
            damage.updateFinalDamage(d -> d * (1f - epf / 25f));
        }
    }

    protected void applyArmorWhenFall(DamageContainer damage, ArmorContainer armorContainer) {
        var enchantmentProtectionFactor = 0;

        for (var item : armorContainer.getItemStacks()) {
            if (item == ItemAirStack.AIR_STACK) {
                continue;
            }

            enchantmentProtectionFactor += item.getEnchantmentProtectionFactor(damage.getDamageType());
        }
        enchantmentProtectionFactor = Math.min(20, enchantmentProtectionFactor);

        if (enchantmentProtectionFactor == 0) {
            return;
        }

        final var epf = enchantmentProtectionFactor;
        damage.updateFinalDamage(d -> d * (1f - epf / 25f));
    }

    protected void applyEffects(DamageContainer damage) {
        // Damage absorption
        if (this.absorption > 0) {
            setAbsorption(Math.max(0, this.absorption - damage.getFinalDamage()));
            damage.updateFinalDamage(d -> Math.max(0, d - this.absorption));
        }

        this.effects.values().forEach(effect ->
                effect.getType().onEntityDamage(thisEntity, effect, lastDamage)
        );
    }

    protected void applyAttacker(DamageContainer damage) {
        if (damage.getAttacker() instanceof Entity attacker) {
            if (attacker instanceof EntityLivingComponent living) {
                var strengthLevel = living.getEffectLevel(EffectTypes.STRENGTH);
                if (strengthLevel > 0) {
                    damage.updateFinalDamage(d -> {
                        var pow = Math.pow(1.3, strengthLevel);
                        return (float) (d * pow + ((pow - 1) / 0.3));
                    });
                }

                var weaknessLevel = living.getEffectLevel(EffectTypes.WEAKNESS);
                if (weaknessLevel > 0) {
                    damage.updateFinalDamage(d -> {
                        var pow = Math.pow(0.8, weaknessLevel);
                        return (float) (d * pow + ((pow - 1) / 0.4));
                    });
                }
            }

            if (attacker instanceof ContainerHolder holder && holder.hasContainer(ContainerTypes.INVENTORY)) {
                var item = holder.getContainer(ContainerTypes.INVENTORY).getItemInHand();
                var sharpnessLevel = item.getEnchantmentLevel(EnchantmentTypes.SHARPNESS);
                if (sharpnessLevel > 0) {
                    damage.updateFinalDamage(d -> d + sharpnessLevel * 1.25f);
                    damage.setEnchanted(true);
                }
            }

            if (attacker instanceof EntityPhysicsComponent component) {
                damage.setCritical(component.canCriticalAttack());
            }
        }

        if (damage.isCritical()) {
            damage.updateFinalDamage(d -> d * 1.5f);
        }
    }

    @Override
    public boolean canBeAttacked(DamageContainer damage) {
        if (!hasFallDamage() && damage.getDamageType() == DamageType.FALL) {
            return false;
        }

        if (!hasFireDamage() &&
            (damage.getDamageType() == DamageType.FIRE ||
             damage.getDamageType() == DamageType.LAVA ||
             damage.getDamageType() == DamageType.FIRE_TICK)
        ) {
            return false;
        }

        if (!hasDrowningDamage() && damage.getDamageType() == DamageType.DROWN) {
            return false;
        }

        if (!hasVoidDamage() && damage.getDamageType() == DamageType.VOID) {
            return false;
        }

        return true;
    }

    @Override
    public boolean hasFallDamage() {
        return (physicsComponent != null && physicsComponent.hasGravity()) ||
               (!hasEffect(EffectTypes.LEVITATION) && !hasEffect(EffectTypes.SLOW_FALLING)) ||
               thisEntity.getWorld().getWorldData().<Boolean>getGameRuleValue(GameRule.FALL_DAMAGE);
    }

    @Override
    public boolean hasFireDamage() {
        return !hasEffect(EffectTypes.FIRE_RESISTANCE) &&
               !isFireproof() &&
               thisEntity.getWorld().getWorldData().<Boolean>getGameRuleValue(GameRule.FIRE_DAMAGE);
    }

    @Override
    public boolean hasDrowningDamage() {
        return !hasEffect(EffectTypes.WATER_BREATHING);
    }

    @Override
    public boolean hasVoidDamage() {
        return true;
    }

    @Override
    public boolean isFireproof() {
        return false;
    }

    @Override
    public boolean setOnFireTicks(int newOnFireTicks) {
        if (this.isFireproof()) {
            return false;
        }

        var updateState = false;
        if (this.onFireTicks > 0 && newOnFireTicks <= 0) {
            updateState = true;
        } else if (this.onFireTicks <= 0 && newOnFireTicks > 0) {
            updateState = true;
            // The first tick of fire damage is applied immediately
            attack(DamageContainer.fireTick(1));
        }

        this.onFireTicks = newOnFireTicks;
        if (updateState) {
            this.baseComponent.broadcastState();
        }

        return true;
    }

    @Override
    public void setAirSupplyTicks(int ticks) {
        this.airSupplyTicks = ticks;
        this.baseComponent.broadcastState();
    }

    @Override
    public void setAirSupplyMaxTicks(int ticks) {
        this.airSupplyMaxTicks = ticks;
        this.baseComponent.broadcastState();
    }

    @Override
    public Map<EffectType, EffectInstance> getEffects() {
        return Collections.unmodifiableMap(effects);
    }

    @Override
    public boolean hasEffect(EffectType effectType) {
        return effects.containsKey(effectType);
    }

    @Override
    public int getEffectLevel(EffectType effectType) {
        var effect = effects.get(effectType);
        return effect == null ? 0 : effect.getLevel();
    }

    @Override
    public boolean addEffect(EffectInstance effectInstance) {
        if (!canApplyEffect(effectInstance.getType())) {
            return false;
        }

        var event = new EntityEffectAddEvent(thisEntity, effectInstance);
        if (!event.call()) {
            return false;
        }

        effectInstance = event.getEffect();
        var old = effects.put(effectInstance.getType(), effectInstance);
        if (old == null) {
            effectInstance.getType().onAdd(thisEntity, effectInstance);
            this.baseComponent.broadcastState();
        }
        sendEffects(effectInstance, old);

        return true;
    }

    @Override
    public void removeEffect(EffectType effectType) {
        var removed = effects.get(effectType);
        if (removed == null) {
            return;
        }

        var event = new EntityEffectRemoveEvent(thisEntity, removed);
        if (!event.call()) {
            return;
        }

        effects.remove(effectType);
        effectType.onRemove(thisEntity, removed);
        sendEffects(null, removed);
        this.baseComponent.broadcastState();
    }

    protected void sendEffects(EffectInstance newEffect, EffectInstance oldEffect) {
        thisEntity.forEachViewers(viewer -> viewer.viewEntityEffectChange(thisEntity, newEffect, oldEffect));
    }

    @Override
    public void removeAllEffects() {
        // Prevent ConcurrentModificationException
        for (EffectType effectType : this.effects.keySet().toArray(EffectType[]::new)) {
            removeEffect(effectType);
        }
    }

    @Override
    public void setHealth(float value) {
        if (value > 0 && value < 1) {
            // Client will think he is dead if the health is less than 1 But server doesn't think so, which
            // would cause bug So we need to set the health to 0 if it's less than 1 and bigger than 0
            value = 0;
        } else {
            value = max(0, min(value, this.maxHealth));
        }

        var event = new EntityHealthChangeEvent(thisEntity, this.health, value);
        if (!event.call()) {
            return;
        }

        this.health = event.getNewHealth();
    }

    @Override
    public void setMaxHealth(float maxHealth) {
        this.maxHealth = maxHealth;
        this.health = Math.min(this.health, this.maxHealth);
    }

    @EventHandler
    protected void onTick(CEntityTickEvent event) {
        var currentTick = event.getCurrentTick();
        tickVoid(currentTick);
        tickFire(currentTick);
        tickBreathe();
        tickEffects();
        tickDead();
    }

    /// Check the void damage every 20 ticks. If the entity's y coordinate is lower than {@code minHeight - 18},
    /// 10 points damage will be given to the entity.
    protected void tickVoid(long currentTick) {
        if (currentTick % 20 != 0) {
            // Check void damage every 20 ticks
            return;
        }

        int minY = thisEntity.getDimension().getDimensionInfo().minHeight() - 18;
        if (thisEntity.getLocation().y() <= minY) {
            attack(DamageContainer.voidDamage(10));
        }
    }

    /// Update the {@link #onFireTicks} of the entity, fire tick damage will be applied to the entity if the entity
    /// is on fire every second.
    protected void tickFire(long currentTick) {
        if (!isOnFire()) {
            return;
        }

        // Do not do onFireTicks-- directly, because we also
        // need to update the ON_FIRE flag of the entity, and
        // this method will update the flag.
        this.setOnFireTicks(onFireTicks - 1);
        if (currentTick % 20 == 0) {
            attack(DamageContainer.fireTick(1));
        }
    }

    /// Update the {@link #airSupplyTicks} of the entity. Drown damage will be applied to the entity every second
    /// if the entity's air supply runs out. If the entity is able to breathe, the air supply will be regenerated
    /// by four points every tick.
    protected void tickBreathe() {
        var newAirSupplyTicks = this.airSupplyTicks;
        if (!canBreathe()) {
            newAirSupplyTicks = this.airSupplyTicks - 1;
            if (newAirSupplyTicks <= -20) {
                attack(DamageContainer.drown(2));
                newAirSupplyTicks = 0;
            }
        } else if (this.airSupplyTicks < this.airSupplyMaxTicks) {
            newAirSupplyTicks = this.airSupplyTicks + 4;
        }
        if (this.airSupplyTicks != newAirSupplyTicks) {
            this.airSupplyTicks = newAirSupplyTicks;
            this.baseComponent.broadcastState();
        }
    }

    @Override
    public boolean canBreathe() {
        return hasEffect(EffectTypes.WATER_BREATHING) || hasEffect(EffectTypes.CONDUIT_POWER) || !thisEntity.isEyesInWater();
    }

    /// Tick and update the duration of all the active effects this entity is holding currently.
    protected void tickEffects() {
        if (this.effects.isEmpty()) {
            return;
        }

        for (var effect : this.effects.values().toArray(EffectInstance[]::new)) {
            effect.setDuration(effect.getDuration() - 1);
            effect.getType().onTick(thisEntity, effect);
            if (effect.getDuration() <= 0) {
                removeEffect(effect.getType());
            }
        }
    }

    /// Check if the entity should dead and update the dead timer after the death if dead timer is
    /// enabled for this entity.
    protected void tickDead() {
        if (this.health == 0 && !this.baseComponent.isDead()) {
            onDie();
        }

        if (this.baseComponent.isDead()) {
            var manager = this.baseComponent.getDimension().getEntityManager();
            if (hasDeadTimer()) {
                if (deadTimer > 0) deadTimer--;
                if (deadTimer == 0) {
                    // Spawn dead particle
                    spawnDeadParticle();
                    manager.removeEntity(thisEntity);
                }
            } else {
                manager.removeEntity(thisEntity);
            }
        }
    }

    /// Whether the entity's body should keep existing in the dimension in a period of time after
    /// death. This method also determines whether to create a cloud of white smoke when the entity's
    /// body disappears. If the dead timer is disabled, the entity will be removed from the dimension
    /// immediately when dead.
    protected boolean hasDeadTimer() {
        return true;
    }

    /// Called when the entity dead (health == 0)
    protected void onDie() {
        manager.callEvent(CEntityDieEvent.INSTANCE);
        new EntityDieEvent(thisEntity).call();

        this.effects.values().forEach(effect -> effect.getType().onEntityDies(thisEntity, effect));
        this.baseComponent.setState(EntityState.DEAD);
        if (hasDeadTimer()) {
            this.deadTimer = 20;
        }

        this.baseComponent.applyAction(SimpleEntityAction.DEATH);
    }

    /// Spawn white smoke inside the entity's aabb box.
    protected void spawnDeadParticle() {
        var offsetAABB = this.baseComponent.getOffsetAABB();
        for (double x = offsetAABB.minX(); x <= offsetAABB.maxX(); x += 0.5) {
            for (double z = offsetAABB.minZ(); z <= offsetAABB.maxZ(); z += 0.5) {
                for (double y = offsetAABB.minY(); y <= offsetAABB.maxY(); y += 0.5) {
                    this.baseComponent.getDimension().addParticle(x, y, z, SimpleParticle.EXPLODE);
                }
            }
        }
    }

    @EventHandler
    protected void onSaveNBT(CEntitySaveNBTEvent event) {
        var nbt = event.getNbt();
        nbt.putShort(TAG_FIRE, (short) onFireTicks);
        if (!effects.isEmpty()) {
            nbt.putList(TAG_ACTIVE_EFFECTS, NbtType.COMPOUND, effects.values().stream().map(EffectInstance::saveNBT).toList());
        }
        nbt.putFloat(TAG_ABSORPTION, this.absorption);
        nbt.putFloat(TAG_HEALTH, this.health);
        nbt.putFloat(TAG_MAX_HEALTH, this.maxHealth);
    }

    @EventHandler
    protected void onLoadNBT(CEntityLoadNBTEvent event) {
        var nbt = event.getNbt();
        nbt.listenForShort(TAG_FIRE, this::setOnFireTicks);
        nbt.listenForList(TAG_ACTIVE_EFFECTS, NbtType.COMPOUND, activeEffects -> {
            for (NbtMap activeEffect : activeEffects) {
                var effectInstance = EffectInstance.fromNBT(activeEffect);
                addEffect(effectInstance);
            }
        });
        nbt.listenForFloat(TAG_ABSORPTION, this::setAbsorption);
        nbt.listenForFloat(TAG_HEALTH, this::setHealth);
        nbt.listenForFloat(TAG_MAX_HEALTH, this::setMaxHealth);
    }

    @EventHandler
    protected void onFall(CEntityFallEvent event) {
        if (!hasFallDamage()) {
            // Short circuit here to avoid unnecessary block querying
            return;
        }

        // Physics component won't be null here, because CEntityFallEvent is called in physics component
        var blockStateStandingOn = physicsComponent.getBlockStateStandingOn();
        double rawDamage = (event.getFallDistance() - 3) - getEffectLevel(EffectTypes.JUMP_BOOST);
        var damage = Math.round(rawDamage * (1 - blockStateStandingOn.getBehavior().getFallDamageReductionFactor()));
        if (damage > 0) {
            attack(DamageContainer.fall(damage));
        }
    }
}
