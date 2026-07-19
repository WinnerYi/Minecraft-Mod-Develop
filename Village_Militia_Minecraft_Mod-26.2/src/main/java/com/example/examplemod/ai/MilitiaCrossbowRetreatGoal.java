package com.example.examplemod.ai;

import com.example.examplemod.VillageMilitiaEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import java.util.EnumSet;

public class MilitiaCrossbowRetreatGoal extends Goal {
    private final VillageMilitiaEntity mob;
    private double sideDirection = 0.0D;
    private int attackDelay = 0;

    public MilitiaCrossbowRetreatGoal(VillageMilitiaEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() 
            && this.mob.getMainHandItem().getItem() instanceof CrossbowItem;
    }

    @Override
    public void start() {
        double randomFactor = 0.55D + this.mob.getRandom().nextDouble() * 0.3D;
        this.sideDirection = this.mob.getRandom().nextBoolean() ? randomFactor : -randomFactor;
        this.attackDelay = 0;
    }

    @Override
    public void stop() {
        this.mob.stopUsingItem();
        this.mob.getNavigation().stop();
        if (this.mob.getVehicle() instanceof PathfinderMob vehicleMob) {
            vehicleMob.getNavigation().stop();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;

        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        ItemStack crossbow = this.mob.getMainHandItem();
        boolean isCharged = CrossbowItem.isCharged(crossbow);
        boolean isRiding = this.mob.isPassenger() && this.mob.getVehicle() instanceof PathfinderMob;

        if (!isCharged) {
            if (!this.mob.isUsingItem()) {
                this.mob.startUsingItem(InteractionHand.MAIN_HAND);
            } else {
                if (this.mob.getTicksUsingItem() >= CrossbowItem.getChargeDuration(crossbow, this.mob)) {
                    this.mob.releaseUsingItem(); 
                }
            }

            if (isRiding) {
                PathfinderMob vehicleMob = (PathfinderMob) this.mob.getVehicle();
                
                if (this.mob.tickCount % 5 == 0) {
                    
                    int randomDistance = 7 + this.mob.getRandom().nextInt(15);
                    net.minecraft.world.phys.Vec3 retreatPos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(
                        vehicleMob, randomDistance, 5, target.position()
                    );
                    if (retreatPos != null) {
                        vehicleMob.getNavigation().moveTo(retreatPos.x, retreatPos.y, retreatPos.z, 1.7D);
                    }
                }
            } else {
                this.mob.getNavigation().stop();
                if (!this.mob.isInWater() && !this.mob.isInLava()) {
                    double deltaX = this.mob.getX() - target.getX();
                    double deltaZ = this.mob.getZ() - target.getZ();
                    double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

                    if (distance > 0) {
                        double backX = deltaX / distance;
                        double backZ = deltaZ / distance;
                        double sideX = -backZ * this.sideDirection;
                        double sideZ = backX * this.sideDirection;

                        double speed = 0.06D; 
                        double moveX = (backX + sideX * 0.8) * speed;
                        double moveZ = (backZ + sideZ * 0.8) * speed;

                        this.mob.setDeltaMovement(moveX, this.mob.getDeltaMovement().y, moveZ);
                        this.mob.hurtMarked = true;
                    }
                }
            }
            
        } else {
            this.mob.stopUsingItem(); 

            if (isRiding) {
                PathfinderMob vehicleMob = (PathfinderMob) this.mob.getVehicle();
                vehicleMob.getNavigation().stop();
            } else {
                this.mob.getNavigation().stop();
            }
            
            if (this.attackDelay > 0) {
                this.attackDelay--;
            } else {
                if (this.mob.level() instanceof ServerLevel serverLevel) {
                    if (crossbow.getItem() instanceof CrossbowItem crossbowItem) {
                       
                        float randomInaccuracy = 0.6F + this.mob.getRandom().nextFloat() * 0.8F;
                        
                        crossbowItem.performShooting(serverLevel, this.mob, InteractionHand.MAIN_HAND, crossbow, 1.6F, randomInaccuracy, target);
                    }
                    crossbow.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES, net.minecraft.world.item.component.ChargedProjectiles.EMPTY);
                }
                
                
                double randomFactor = 0.55D + this.mob.getRandom().nextDouble() * 0.3D;
                this.sideDirection = this.mob.getRandom().nextBoolean() ? randomFactor : -randomFactor;
                
                
                this.attackDelay = 20 + this.mob.getRandom().nextInt(16); 
            }
        }
    }
}