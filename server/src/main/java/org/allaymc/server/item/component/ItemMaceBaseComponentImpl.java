package org.allaymc.server.item.component;

import org.allaymc.api.entity.Entity;
import org.allaymc.api.entity.component.EntityPhysicsComponent;
import org.allaymc.api.entity.interfaces.EntityLiving;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.item.ItemStackInitInfo;
import org.allaymc.api.block.type.BlockTypes;
import org.allaymc.api.world.Dimension;
import org.allaymc.api.world.particle.SimpleParticle;
import org.allaymc.api.world.sound.CustomSound;
import org.allaymc.api.world.sound.SoundNames;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;


/**
 * @author ClexaGod
 */
public class ItemMaceBaseComponentImpl extends ItemBaseComponentImpl {

    private static final double SMASH_TRIGGER_FALL_DISTANCE = 1.5;
    private static final int SMASH_BLOCKS_HIGH = 3;
    private static final int SMASH_BLOCKS_MID = 5;
    private static final float SMASH_DAMAGE_HIGH = 3f;
    private static final float SMASH_DAMAGE_MID = 1.5f;
    private static final float SMASH_DAMAGE_LOW = 1f;
    private static final float HEAVY_SMASH_DAMAGE = 16f;
    private static final double SMASH_RECOIL_Y = 0.05;
    private static final double SMASH_KNOCKBACK_RADIUS = 3.0;
    private static final double SMASH_KNOCKBACK_VERTICAL_RANGE = 2.0;
    private static final double SMASH_AOE_KNOCKBACK_STRENGTH = 0.35;
    private static final double SMASH_AOE_KNOCKBACK_Y = 0.60;
    private static final double SMASH_VICTIM_KNOCKBACK_STRENGTH = 0.35;
    private static final double SMASH_VICTIM_KNOCKBACK_Y = 0.60;

    public ItemMaceBaseComponentImpl(ItemStackInitInfo initInfo) {
        super(initInfo);
    }

    @Override
    public float calculateAttackDamage(Entity attacker, Entity target) {
        var baseDamage = getItemType().getItemData().attackDamage();
        return baseDamage + calculateSmashBonus(attacker);
    }

    @Override
    public void onAttackEntity(Entity attacker, Entity victim) {
        super.onAttackEntity(attacker, victim);

        if (!isSmashAttack(attacker)) {
            return;
        }

        applySmashEffects(attacker, victim);
    }

    private boolean isSmashAttack(Entity attacker) {
        return getFallDistance(attacker) > SMASH_TRIGGER_FALL_DISTANCE;
    }

    private float calculateSmashBonus(Entity attacker) {
        var fallDistance = getFallDistance(attacker);
        if (fallDistance <= SMASH_TRIGGER_FALL_DISTANCE) {
            return 0f;
        }

        var fallBlocks = (int) Math.floor(fallDistance);
        var bonus = 0f;
        for (int i = 1; i <= fallBlocks; i++) {
            if (i <= SMASH_BLOCKS_HIGH) {
                bonus += SMASH_DAMAGE_HIGH;
            } else if (i <= SMASH_BLOCKS_HIGH + SMASH_BLOCKS_MID) {
                bonus += SMASH_DAMAGE_MID;
            } else {
                bonus += SMASH_DAMAGE_LOW;
            }
        }

        return bonus;
    }

    private double getFallDistance(Entity attacker) {
        if (attacker instanceof EntityPhysicsComponent physicsComponent) {
            return physicsComponent.getFallDistance();
        }

        return 0;
    }

    private void applySmashEffects(Entity attacker, Entity victim) {
        if (!(attacker instanceof EntityPhysicsComponent physicsComponent)) {
            return;
        }

        var dimension = attacker.getDimension();
        // Use victim's location for effects, as the smash happens *on* the target
        var effectLocation = victim.getLocation();
        var smashBonus = calculateSmashBonus(attacker);
        var smashDamage = getItemType().getItemData().attackDamage() + smashBonus;

        physicsComponent.resetFallDistance();

        var motion = physicsComponent.getMotion();
        physicsComponent.setMotion(new Vector3d(motion.x(), SMASH_RECOIL_Y, motion.z()));

        if (physicsComponent.isOnGround()) {
            if (smashDamage >= HEAVY_SMASH_DAMAGE) {
                dimension.addSound(effectLocation, new CustomSound(SoundNames.MACE_HEAVY_SMASH_GROUND));
            } else {
                dimension.addSound(effectLocation, new CustomSound(SoundNames.MACE_SMASH_GROUND));
            }
        } else {
            dimension.addSound(effectLocation, new CustomSound(SoundNames.MACE_SMASH_AIR));
        }

        spawnSmashParticles(dimension, victim);
        applySmashVictimKick(attacker, victim);
        applySmashKnockback(dimension, attacker, victim);
    }

    private void spawnSmashParticles(Dimension dimension, Entity victim) {
        var center = victim.getLocation();
        var aabb = victim.getOffsetAABB();
        var blockX = (int) Math.floor(center.x());
        var blockZ = (int) Math.floor(center.z());
        var blockY = (int) Math.floor(aabb.minY() - 0.01);
        var below = dimension.getBlockState(blockX, blockY, blockZ);
        if (below.getBlockType() == BlockTypes.AIR) {
            return;
        }

        var collisionShape = below.getBlockStateData().collisionShape();
        if (collisionShape.getSolids().isEmpty()) {
            return;
        }

        var dustY = blockY + collisionShape.unionAABB().maxY() + 0.02;
        dimension.addParticle(center.x(), dustY, center.z(), SimpleParticle.SMASH_ATTACK_GROUND_DUST);
    }

    private void applySmashVictimKick(Entity attacker, Entity victim) {
        if (victim instanceof EntityPhysicsComponent physicsComponent) {
            physicsComponent.knockback(attacker.getLocation(), SMASH_VICTIM_KNOCKBACK_STRENGTH, SMASH_VICTIM_KNOCKBACK_Y);
        }
    }

    private void applySmashKnockback(Dimension dimension, Entity attacker, Entity victim) {
        // Expand AABB from the VICTIM, not the attacker
        var aabb = victim.getOffsetAABB().expand(SMASH_KNOCKBACK_RADIUS, new AABBd());
        var centerLocation = victim.getLocation();
        var centerX = centerLocation.x();
        var centerY = centerLocation.y();
        var centerZ = centerLocation.z();
        var horizontalRadiusSquared = SMASH_KNOCKBACK_RADIUS * SMASH_KNOCKBACK_RADIUS;
        
        dimension.getEntityManager()
                .getPhysicsService()
                .computeCollidingEntities(aabb)
                .forEach(entity -> {
                    if (entity == attacker || entity == victim || !(entity instanceof EntityLiving)) {
                        return;
                    }
                    var entityLocation = entity.getLocation();
                    if (Math.abs(entityLocation.y() - centerY) > SMASH_KNOCKBACK_VERTICAL_RANGE) {
                        return;
                    }
                    var dx = entityLocation.x() - centerX;
                    var dz = entityLocation.z() - centerZ;
                    if ((dx * dx + dz * dz) > horizontalRadiusSquared) {
                        return;
                    }
                    if (entity instanceof EntityPhysicsComponent physicsComponent) {
                        // Knockback away from the impact point (victim's location)
                        physicsComponent.knockback(centerLocation, SMASH_AOE_KNOCKBACK_STRENGTH, SMASH_AOE_KNOCKBACK_Y);
                    }
                });
    }
}
