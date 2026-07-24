package com.example.examplemod.ai;

import com.example.examplemod.VillageMilitiaEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.PathType;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

public class MilitiaFollowOwnerGoal extends Goal {
    private final VillageMilitiaEntity militia;
    private @Nullable LivingEntity owner;
    private final double speedModifier;
    private final PathNavigation navigation;
    private int timeToRecalcPath;
    private final float stopDistance;
    private final float startDistance;
    private float oldWaterCost;

    public MilitiaFollowOwnerGoal(VillageMilitiaEntity militia, double speedModifier, float startDistance, float stopDistance) {
        this.militia = militia;
        this.speedModifier = speedModifier;
        this.navigation = militia.getNavigation();
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        
        if (!(militia.getNavigation() instanceof GroundPathNavigation) && !(militia.getNavigation() instanceof FlyingPathNavigation)) {
            throw new IllegalArgumentException("Unsupported mob type for MilitiaFollowOwnerGoal");
        }
    }

    @Override
    public boolean canUse() {
        if (this.militia.getMilitiaMode() != VillageMilitiaEntity.MilitiaMode.FOLLOW) {
            return false;
        }

        if (this.militia.getTarget() != null && this.militia.getTarget().isAlive()) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        if (owner == null || !owner.isAlive()) {
            return false;
        }

        // 🎯 騎乘時適當放大觸發距離，避免跟隨反應過遲
        float effectiveStartDistance = this.militia.isPassenger() ? Math.max(this.startDistance, 4.0F) : this.startDistance;
        if (this.militia.distanceToSqr(owner) < (double)(effectiveStartDistance * effectiveStartDistance)) {
            return false;
        }

        this.owner = owner;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        // 🎯 騎乘時不要因為 navigation.isDone() 就直接退出 Goal，避免走走停停
        if (!this.militia.isPassenger() && this.navigation.isDone()) {
            return false;
        }
        if (this.militia.getMilitiaMode() != VillageMilitiaEntity.MilitiaMode.FOLLOW) {
            return false;
        }
        if (this.militia.getTarget() != null && this.militia.getTarget().isAlive()) {
            return false;
        }
        if (this.owner == null || !this.owner.isAlive()) {
            return false;
        }
        
        // 🎯 騎乘時放大停止距離（例如額外 +2 格），給馬匹慣性與迴轉空間
        float effectiveStopDistance = this.militia.isPassenger() ? this.stopDistance + 2.0F : this.stopDistance;
        return !(this.militia.distanceToSqr(this.owner) <= (double)(effectiveStopDistance * effectiveStopDistance));
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
        this.oldWaterCost = this.militia.getPathfindingMalus(PathType.WATER);
        this.militia.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    public void stop() {
        this.owner = null;
        this.navigation.stop();
        
        // 🎯 如果騎乘中，停止時同步讓坐騎停止
        if (this.militia.isPassenger() && this.militia.getVehicle() instanceof Mob vehicle) {
            vehicle.getNavigation().stop();
        }
        
        this.militia.setPathfindingMalus(PathType.WATER, this.oldWaterCost);
    }

    @Override
    public void tick() {
        if (this.owner == null) return;

        this.militia.getLookControl().setLookAt(this.owner, 10.0F, (float)this.militia.getMaxHeadXRot());

        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = this.adjustedTickDelay(10);
            
            // 超過 24 格直接傳送
            if (this.militia.distanceToSqr(this.owner) > 576.0D) {
                this.teleportToOwner();
            } else {
                // 🎯 騎乘時提高 1.5 倍移動速度，並直接指揮坐騎（馬匹）尋路
                double currentSpeed = this.militia.isPassenger() ? this.speedModifier * 2D : this.speedModifier;
                this.moveMilitiaOrVehicle(currentSpeed);
            }
        }
    }

    /**
     * 🎯 核心改進：判定是否騎乘，若騎馬則叫坐騎直接走向目標
     */
    private void moveMilitiaOrVehicle(double speed) {
        if (this.owner == null) return;

        if (this.militia.isPassenger() && this.militia.getVehicle() instanceof Mob vehicle) {
            // 指揮馬匹的 Navigation 走向主人
            vehicle.getNavigation().moveTo(this.owner, speed);
        } else {
            // 一般步行狀態
            this.navigation.moveTo(this.owner, speed);
        }
    }

    private LivingEntity getOwner() {
        return this.militia.level().getNearestPlayer(this.militia, 15.0D);
    }

    private void teleportToOwner() {
        if (this.owner == null) return;
        
        // 🎯 傳送前若有騎乘，先安全下馬再傳送民兵
        if (this.militia.isPassenger()) {
            this.militia.stopRiding();
        }
        
        for (int i = 0; i < 10; ++i) {
            int x = this.militia.getRandom().nextIntBetweenInclusive(-3, 3);
            int y = this.militia.getRandom().nextIntBetweenInclusive(-1, 1);
            int z = this.militia.getRandom().nextIntBetweenInclusive(-3, 3);
            
            net.minecraft.core.BlockPos targetPos = this.owner.blockPosition().offset(x, y, z);
            if (this.militia.level().getBlockState(targetPos).isAir()) {
                this.militia.teleportTo(
                    targetPos.getX() + 0.5D, 
                    (double) targetPos.getY(), 
                    targetPos.getZ() + 0.5D
                );
                this.navigation.stop();
                return;
            }
        }
    }
}