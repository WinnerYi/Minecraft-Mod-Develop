package com.example.examplemod.ai;

import com.example.examplemod.VillageMilitiaEntity;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.phys.AABB;

public class MilitiaAttackTargetGoal extends Goal {
    private final Mob mob;
    private final double range = 16.0D;

    public MilitiaAttackTargetGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override // 攻擊判定（important）
    public boolean canUse() {
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            return false;
        }

        AABB searchBox = this.mob.getBoundingBox().inflate(range, 4.0D, range);
        List<LivingEntity> enemies = this.mob.level().getEntitiesOfClass(
            LivingEntity.class,
            searchBox,
            entity -> {
                if (!entity.isAlive() || !this.mob.hasLineOfSight(entity)) {
                    return false;
                }
                if (entity instanceof Monster ) {
                    return true;
                }
                return false;
            }
        );

        if (!enemies.isEmpty()) {
            enemies.sort((e1, e2) -> Double.compare(this.mob.distanceToSqr(e1), this.mob.distanceToSqr(e2)));

            this.mob.setTarget(enemies.get(0));
            return true;
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity currentTarget = this.mob.getTarget();
        if (currentTarget == null || !currentTarget.isAlive()) {
            return false;
        }
        return this.mob.distanceToSqr(currentTarget) <= range * range;
    }

    
}
