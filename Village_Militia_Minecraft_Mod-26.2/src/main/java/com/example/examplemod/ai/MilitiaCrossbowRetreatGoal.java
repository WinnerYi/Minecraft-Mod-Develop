package com.example.examplemod.ai;

import com.example.examplemod.VillageMilitiaEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import java.util.EnumSet;

public class MilitiaCrossbowRetreatGoal extends Goal {
    private final VillageMilitiaEntity mob;
    private int sideDirection = 0;
    private int attackDelay = 0;

    public MilitiaCrossbowRetreatGoal(VillageMilitiaEntity mob) {
        this.mob = mob;
        // 🌟 接管移動與視線
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
        this.sideDirection = this.mob.getRandom().nextBoolean() ? 1 : -1;
        this.attackDelay = 0;
    }

    @Override
    public void stop() {
        this.mob.stopUsingItem();
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;

        // 1. 眼神與身體死死鎖定敵人（維持弩的瞄準角度）
        this.mob.getLookControl().setLookAt(target, 100.0F, 100.0F);
        this.mob.setYRot(this.mob.yHeadRot);
        this.mob.yBodyRot = this.mob.yHeadRot;

        ItemStack crossbow = this.mob.getMainHandItem();
        boolean isCharged = CrossbowItem.isCharged(crossbow);

        // 🛑 切斷尋路
        this.mob.getNavigation().stop();

        if (!isCharged) {
            // 🔄 【狀態：換彈中】
            if (!this.mob.isUsingItem()) {
                // 如果還沒開始拉弦，立刻開始裝彈
                this.mob.startUsingItem(InteractionHand.MAIN_HAND);
            } else {
                // 🌟 完美避開 protected 方法：
                // 我們什麼都不用呼叫！因為 startUsingItem 之後，Minecraft 的 LivingEntity.aiStep() 
                // 本身每 tick 就會自動幫我們更新弩的拉弦進度與動畫。
                // 我們只需要在這邊監聽：一旦拉弦時間到了，就手動幫他放開，觸發自動裝填！
                if (this.mob.getTicksUsingItem() >= CrossbowItem.getChargeDuration(crossbow, this.mob)) {
                    this.mob.releaseUsingItem(); // 模擬放開右鍵，原版會自動把弩刷成已裝填
                }
            }
            if (!this.mob.isInWater() && !this.mob.isInLava()) {
                double deltaX = this.mob.getX() - target.getX();
                double deltaZ = this.mob.getZ() - target.getZ();
                double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

                if (distance > 0) {
                    double backX = deltaX / distance;
                    double backZ = deltaZ / distance;
                    double sideX = -backZ * this.sideDirection;
                    double sideZ = backX * this.sideDirection;

                    double speed = 0.09D; 
                    double moveX = (backX + sideX * 0.8) * speed;
                    double moveZ = (backZ + sideZ * 0.8) * speed;

                    this.mob.setDeltaMovement(moveX, this.mob.getDeltaMovement().y, moveZ);
                }

            }
            // 【戰術斜後退物理走位】
            
        } else {
            // 🎯 【狀態：裝填完畢，準備射擊】
            this.mob.stopUsingItem(); 
            
            if (this.attackDelay > 0) {
                this.attackDelay--;
            } else {
                if (this.mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    if (crossbow.getItem() instanceof CrossbowItem crossbowItem) {
                        crossbowItem.performShooting(serverLevel, this.mob, InteractionHand.MAIN_HAND, crossbow, 1.6F, 1.0F, target);
                    }
                    // 發射後清空充能組件
                    crossbow.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES, net.minecraft.world.item.component.ChargedProjectiles.EMPTY);
                }
                
                this.sideDirection = this.mob.getRandom().nextBoolean() ? 1 : -1;
                this.attackDelay = 25; 
            }
        }
    }
}