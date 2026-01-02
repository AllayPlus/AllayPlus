package org.allaymc.server.entity.component.projectile;

import org.allaymc.api.block.dto.Block;
import org.allaymc.api.entity.Entity;
import org.allaymc.api.entity.action.ArrowShakeAction;
import org.allaymc.api.entity.component.EntityAgeComponent;
import org.allaymc.api.entity.component.EntityArrowBaseComponent;
import org.allaymc.api.entity.component.EntityPhysicsComponent;
import org.allaymc.api.entity.component.EntityProjectileComponent;
import org.allaymc.api.entity.damage.DamageContainer;
import org.allaymc.api.entity.interfaces.EntityLiving;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.entity.interfaces.EntityProjectile;
import org.allaymc.api.math.MathUtils;
import org.allaymc.api.math.location.Location3d;
import org.allaymc.api.math.position.Position3i;
import org.allaymc.api.world.sound.SimpleSound;
import org.allaymc.server.component.annotation.Dependency;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector2d;
import org.joml.primitives.AABBd;
import org.joml.primitives.Rayd;

import java.util.concurrent.ThreadLocalRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * @author harryxi | daoge_cmd
 */
public class EntityArrowPhysicsComponentImpl extends EntityProjectilePhysicsComponentImpl {

    @Dependency
    protected EntityArrowBaseComponent arrowBaseComponent;
    @Dependency
    protected EntityProjectileComponent projectileComponent;
    @Dependency
    protected EntityAgeComponent ageComponent;

    // Indicates whether the arrow has already hit a block
    protected boolean hitBlock;
    // Remaining entity hits allowed for piercing arrows.
    protected int remainingPierceHits = -1;
    protected final LongSet piercedEntities = new LongOpenHashSet();

    @Override
    public double getGravity() {
        return 0.05;
    }

    @Override
    public Vector3d updateMotion(boolean hasLiquidMotion) {
        if (hitBlock && arrowBaseComponent.checkBlockCollision()) {
            // Set motion to zero if collided with blocks after hit block
            return new Vector3d(0, 0, 0);
        }

        return new Vector3d(
                this.motion.x * (1 - this.getDragFactorInAir()),
                (this.motion.y - this.getGravity()) * (1 - this.getDragFactorInAir()),
                this.motion.z * (1 - this.getDragFactorInAir())
        );
    }

    @Override
    protected void onHitEntity(Entity other, Vector3dc hitPos) {
        if (thisEntity.willBeDespawnedNextTick()) {
            return;
        }

        if (isPiercingEnabled()) {
            if (!shouldPierceHit(other)) {
                return;
            }
        }

        addHitSound(hitPos);
        if (other instanceof EntityLiving living) {
            var potionType = arrowBaseComponent.getPotionType();
            if (potionType != null) {
                potionType.applyTo(living);
            }

            double damage = arrowBaseComponent.getBaseDamage();
            if (projectileComponent.getShooter() instanceof EntityPlayer) {
                damage = damage
                         + 0.11 * getDifficultyBonus()
                         + 0.25 * ThreadLocalRandom.current().nextGaussian()
                         + 0.97 * motion.length();
                if (arrowBaseComponent.isCritical()) {
                    double criticalBonus = 0.5 * ThreadLocalRandom.current().nextDouble() * damage + 2 * ThreadLocalRandom.current().nextDouble();
                    double criticalDamage = damage + criticalBonus;
                    damage = Math.max(10, Math.min(9, criticalDamage));
                }
            }
            if (arrowBaseComponent.getPowerLevel() > 0) {
                damage = 1.25 * damage + 0.25 * arrowBaseComponent.getPowerLevel() + damage;
            }

            var damageContainer = DamageContainer.projectile(thisEntity, (float) damage);
            damageContainer.setHasKnockback(false);
            if (living.attack(damageContainer) && other instanceof EntityPhysicsComponent physicsComponent) {
                var kb = EntityPhysicsComponent.DEFAULT_KNOCKBACK;
                var additionalMotion = new Vector3d();
                var punchLevel = arrowBaseComponent.getPunchLevel();
                if (punchLevel != 0) {
                    kb /= 2.0;
                    additionalMotion = MathUtils.normalizeIfNotZero(this.motion).setComponent(1, 0);
                    additionalMotion.mul(punchLevel * 0.5);
                }
                // Use the last location as the knockback source
                physicsComponent.knockback(hitPos.sub(this.motion, new Vector3d()), kb, EntityPhysicsComponent.DEFAULT_KNOCKBACK, additionalMotion);
            }

            if (this.livingComponent.isOnFire()) {
                living.setOnFireTicks(20 * 5);
            }
        }

        if (!isPiercingEnabled()) {
            thisEntity.remove();
        }
    }

    @Override
    protected void onHitBlock(Block block, Vector3dc hitPos) {
        if (thisEntity.willBeDespawnedNextTick() || this.hitBlock) {
            return;
        }

        addHitSound(hitPos);
        this.arrowBaseComponent.applyAction(new ArrowShakeAction(7));
        this.arrowBaseComponent.setCritical(false);
        this.hitBlock = true;
    }

    private void addHitSound(Vector3dc hitPos) {
        this.arrowBaseComponent.getDimension().addSound(hitPos, SimpleSound.ARROW_HIT);
    }

    private int getDifficultyBonus() {
        return switch (thisEntity.getWorld().getWorldData().getDifficulty()) {
            case EASY -> 1;
            case NORMAL -> 2;
            case HARD -> 3;
            default -> 0;
        };
    }

    private boolean isPiercingEnabled() {
        return getEffectivePierceLevel() > 0;
    }

    private int getEffectivePierceLevel() {
        var level = arrowBaseComponent.getPierceLevel();
        if (level > 127) {
            // Vanilla: piercing > 127 stops working.
            return 0;
        }
        return Math.max(0, level);
    }

    private int getRemainingPierceHits() {
        if (remainingPierceHits < 0) {
            remainingPierceHits = getEffectivePierceLevel() + 1;
        }
        return remainingPierceHits;
    }

    private boolean shouldPierceHit(Entity other) {
        if (getRemainingPierceHits() <= 0) {
            return false;
        }

        var id = other.getRuntimeId();
        if (piercedEntities.contains(id)) {
            return false;
        }

        piercedEntities.add(id);
        remainingPierceHits--;
        return true;
    }

    @Override
    public boolean applyMotion() {
        if (!isPiercingEnabled()) {
            return super.applyMotion();
        }

        if (motion.lengthSquared() == 0) {
            return false;
        }

        // The position we expected to get to if no blocks/entities prevent us
        var location = thisEntity.getLocation();
        var newPos = new Location3d(location);
        newPos.add(motion);
        var aabb = new AABBd(
                Math.min(location.x(), newPos.x),
                Math.min(location.y(), newPos.y),
                Math.min(location.z(), newPos.z),
                Math.max(location.x(), newPos.x),
                Math.max(location.y(), newPos.y),
                Math.max(location.z(), newPos.z)
        );
        var dimension = thisEntity.getDimension();
        var ray = new Rayd(location, newPos.sub(location, new Vector3d()));

        final class RayCastResult {
            Block hit = null;
            double result = Double.MAX_VALUE;
        }
        var blockHitResult = new RayCastResult();

        // Ray cast blocks
        dimension.forEachBlockStates(aabb, 0, (x, y, z, block) -> {
            var result = new Vector2d();
            if (block.getBlockStateData().computeOffsetCollisionShape(x, y, z).intersectsRay(ray, result)) {
                if (result.x() < blockHitResult.result) {
                    blockHitResult.result = result.x();
                    blockHitResult.hit = new Block(block, new Position3i(x, y, z, dimension));
                }
            }
        });

        // Ray cast entities (piercing ignores entity collision for motion)
        var maxDistance = blockHitResult.result;
        List<HitEntity> hitEntities = new ArrayList<>();
        dimension.getEntityManager().getPhysicsService().computeCollidingEntities(aabb).forEach(entity -> {
            if (entity == thisEntity || (ageComponent.getAge() <= 10 && entity == projectileComponent.getShooter())) {
                return;
            }

            if (piercedEntities.contains(entity.getRuntimeId())) {
                return;
            }

            var result = new Vector2d();
            if (entity.getOffsetAABB().intersectsRay(ray, result) && result.x() < maxDistance) {
                hitEntities.add(new HitEntity(entity, result.x()));
            }
        });

        hitEntities.sort(Comparator.comparingDouble(hit -> hit.distance));
        for (var hit : hitEntities) {
            if (getRemainingPierceHits() <= 0) {
                break;
            }

            var hitPos = new Vector3d(location).add(motion.mul(hit.distance, new Vector3d()));
            if (callHitEvent(hitPos, hit.entity, null)) {
                hit.entity.onProjectileHit((EntityProjectile) thisEntity, hitPos);
                onHitEntity(hit.entity, hitPos);
            }
        }

        // Let's move as far as possible if there are blocks in our way
        if (blockHitResult.hit != null) {
            newPos = new Location3d(location);
            newPos.add(motion.mul(blockHitResult.result, new Vector3d()));
        }

        if (newPos.distance(location) > 0) {
            computeRotationFromMotion(newPos, this.motion);
        }

        if (!newPos.equals(location) && thisEntity.trySetLocation(newPos)) {
            if (blockHitResult.hit != null && callHitEvent(newPos, null, blockHitResult.hit)) {
                blockHitResult.hit.getBehavior().onProjectileHit(blockHitResult.hit, (EntityProjectile) thisEntity, newPos);
                onHitBlock(blockHitResult.hit, newPos);
            }

            return true;
        }

        return false;
    }

    private record HitEntity(Entity entity, double distance) {
    }
}
