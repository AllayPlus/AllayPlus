package org.allaymc.server.entity.component.projectile;

import org.allaymc.api.block.dto.Block;
import org.allaymc.api.entity.Entity;
import org.allaymc.api.entity.component.EntityPhysicsComponent;
import org.allaymc.api.entity.damage.DamageContainer;
import org.allaymc.api.entity.interfaces.EntityLiving;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.math.MathUtils;
import org.allaymc.api.math.location.Location3d;
import org.allaymc.api.world.particle.Particle;
import org.allaymc.api.world.particle.SimpleParticle;
import org.allaymc.api.world.sound.SimpleSound;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;

/**
 * Wind charge projectile behavior.
 *
 * @author ClexaGod
 */
public class EntityWindChargePhysicsComponentImpl extends EntityProjectilePhysicsComponentImpl {
    private static final double KNOCKBACK_Y = 0.6;
    private static final double SELF_VERTICAL_ONLY_RANGE = 1.0;
    private static final double SELF_VERTICAL_ONLY_RANGE_SQUARED = SELF_VERTICAL_ONLY_RANGE * SELF_VERTICAL_ONLY_RANGE;
    private static final double SELF_VERTICAL_KNOCKBACK_Y = 1.159;
    private static final double SELF_VERTICAL_HORIZONTAL_DAMPING = 0.05;

    @Override
    public boolean hasGravity() {
        return true;
    }

    @Override
    public double getGravity() {
        return 0.0001;
    }

    @Override
    public double getDragFactorInAir() {
        return 0.0;
    }

    @Override
    public boolean applyMotion() {
        if (motion.lengthSquared() == 0) {
            return false;
        }

        var location = thisEntity.getLocation();
        var newPos = new Location3d(location);
        newPos.add(motion);

        var dimension = thisEntity.getDimension();
        var dimensionInfo = dimension.getDimensionInfo();
        if (newPos.y() < dimensionInfo.minHeight() - 1 || newPos.y() > dimensionInfo.maxHeight() + 1) {
            playBurstEffect();
            notifyRemoval("Out of world bounds");
            thisEntity.remove();
            return true;
        }

        if (dimension.getChunkManager().getChunkByDimensionPos((int) newPos.x(), (int) newPos.z()) == null) {
            notifyRemoval("Entered unloaded chunk");
            thisEntity.remove();
            return true;
        }

        return super.applyMotion();
    }

    @Override
    protected void onHitEntity(Entity other, Vector3dc hitPos) {
        if (thisEntity.willBeDespawnedNextTick()) {
            return;
        }

        if (other instanceof EntityLiving living) {
            var damage = DamageContainer.projectile(thisEntity, 1);
            damage.setHasKnockback(false);
            living.attack(damage);
        }

        knockbackNearbyEntities();
        playBurstEffect();
        thisEntity.remove();
    }

    @Override
    protected void onHitBlock(Block block, Vector3dc hitPos) {
        if (thisEntity.willBeDespawnedNextTick()) {
            return;
        }

        knockbackNearbyEntities();
        playBurstEffect();
        thisEntity.remove();
    }

    protected void knockbackNearbyEntities() {
        var radius = getBurstRadius();
        var aabb = MathUtils.grow(new AABBd(thisEntity.getOffsetAABB()), new Vector3d(radius, radius, radius));
        var entities = thisEntity.getDimension().getEntityManager().getPhysicsService().computeCollidingEntities(aabb);
        var location = thisEntity.getLocation();
        var radiusSquared = radius * radius;
        for (var entity : entities) {
            if (entity == thisEntity || !(entity instanceof EntityLiving living)) {
                continue;
            }
            if (living.getLocation().distanceSquared(location) > radiusSquared) {
                continue;
            }
            if (living instanceof EntityPhysicsComponent physicsComponent) {
                applyKnockback(living, physicsComponent);
            }
        }
    }

    protected void applyKnockback(Entity target, EntityPhysicsComponent physicsComponent) {
        var source = thisEntity.getLocation();
        if (shouldUseVerticalOnlyKnockback(target, source)) {
            applyVerticalOnlyKnockback(physicsComponent, getSelfKnockbackY());
        } else {
            physicsComponent.knockback(source, getKnockbackStrength(), getKnockbackY());
        }
    }

    protected void playBurstEffect() {
        var location = thisEntity.getLocation();
        var dimension = thisEntity.getDimension();
        dimension.addSound(location, getBurstSound());
        dimension.addParticle(location, getBurstParticle());
    }

    protected Particle getBurstParticle() {
        return SimpleParticle.WIND_EXPLOSION;
    }

    protected double getBurstRadius() {
        return 2.0;
    }

    protected double getKnockbackStrength() {
        return 0.5;
    }

    protected double getKnockbackY() {
        return KNOCKBACK_Y;
    }

    protected double getSelfKnockbackY() {
        return SELF_VERTICAL_KNOCKBACK_Y;
    }

    protected boolean shouldUseVerticalOnlyKnockback(Entity target, Vector3dc source) {
        if (!(target instanceof EntityPlayer)) {
            return false;
        }
        if (projectileComponent.getShooter() != target) {
            return false;
        }
        var location = target.getLocation();
        var dx = source.x() - location.x();
        var dz = source.z() - location.z();
        return (dx * dx + dz * dz) <= SELF_VERTICAL_ONLY_RANGE_SQUARED;
    }

    protected void applyVerticalOnlyKnockback(EntityPhysicsComponent physicsComponent, double kby) {
        var factor = 1.0 - physicsComponent.getKnockbackResistance();
        var motion = new Vector3d(physicsComponent.getMotion());
        motion.x *= SELF_VERTICAL_HORIZONTAL_DAMPING;
        motion.z *= SELF_VERTICAL_HORIZONTAL_DAMPING;
        var baseY = motion.y;
        motion.y = baseY * 0.5 + kby * factor;
        physicsComponent.setMotion(motion);
        physicsComponent.resetFallDistance();
    }

    protected SimpleSound getBurstSound() {
        return SimpleSound.WIND_CHARGE_BURST;
    }

    protected void notifyRemoval(String reason) {
        var shooter = projectileComponent.getShooter();
        if (shooter instanceof EntityPlayer player) {
            player.sendMessage("§c[WindCharge] §fRemoved: §7" + reason);
        }
    }
}
