package org.allaymc.server.entity.component.projectile;

import org.allaymc.api.world.particle.Particle;
import org.allaymc.api.world.particle.SimpleParticle;
import org.allaymc.api.world.sound.SimpleSound;

/**
 * Breeze wind charge projectile behavior.
 *
 * @author ClexaGod
 */
public class EntityBreezeWindChargePhysicsComponentImpl extends EntityWindChargePhysicsComponentImpl {
    @Override
    protected double getBurstRadius() {
        return 3.0;
    }

    @Override
    protected double getKnockbackStrength() {
        return super.getKnockbackStrength() * 0.9;
    }

    @Override
    protected SimpleSound getBurstSound() {
        return SimpleSound.BREEZE_WIND_CHARGE_BURST;
    }

    @Override
    protected double getKnockbackY() {
        return super.getKnockbackY() * 0.9;
    }

    @Override
    protected Particle getBurstParticle() {
        return SimpleParticle.BREEZE_WIND_EXPLOSION;
    }
}
