package com.example.examplemod.ai; // 💡 確保這裡的 package 路徑與你的資料夾結構一致

import com.example.examplemod.VillageMilitiaEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;

public class MilitiaSwordAndShieldAttackGoal extends Goal {
    private final VillageMilitiaEntity mob;
    private int attackCooldown = 0;
    private int shieldTicks = 0;
    private int retreatTicks = 0;

    public MilitiaSwordAndShieldAttackGoal(VillageMilitiaEntity mob) {
        this.mob = mob;
        // 宣告這個 AI 會同時控制移動 (MOVE) 與看向敵人 (LOOK)
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        // 1. 基本檢查：有目標、目標還活著
        if (target == null || !target.isAlive()) {
            return false;
        }
        
        // 2. 檢查手上拿的是不是遠程武器（弩）
        // 如果拿的是弩，就「不要」用這個近戰 AI，讓給遠程 AI 去執行
        if (this.mob.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem) {
            return false;
        }
        
        // 3. 只要不是拿弩，剩下的情況（空手、拿劍、拿任何近戰武器）都允許啟動這個拉扯 AI！
        return true;
    }

    @Override
    public void start() {
        this.attackCooldown = 0;
        this.shieldTicks = 0;
        this.retreatTicks = 0;
    }

    @Override
    public void stop() {
        this.mob.stopUsingItem(); // 確保 AI 停止時盾牌會安全放下
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;

        // 1. 強制盯著目標看（鎖定視線與身體）
        this.mob.getLookControl().setLookAt(target, 100.0F, 100.0F);
        this.mob.setYRot(this.mob.yHeadRot);
        this.mob.yBodyRot = this.mob.yHeadRot;

        double distanceSq = this.mob.distanceToSqr(target);

        net.minecraft.world.item.Item currentWeapon = this.mob.getMainHandItem().getItem();
        double attackReach = 8.5D; 
        if (this.mob.getMainHandItem().has(net.minecraft.core.component.DataComponents.KINETIC_WEAPON)) {
            attackReach = 13.0D;
        }

        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }

        // 🔄 戰鬥拉扯狀態機
        if (this.retreatTicks > 0) {
            // 【階段 1】：砍完人，直直倒退嚕
            this.retreatTicks--;

            // 🛑 完全切斷原版尋路
            this.mob.getNavigation().stop();

            // 🚀 【物理強推】計算從目標「背對」離開的向量
            double deltaX = this.mob.getX() - target.getX();
            double deltaZ = this.mob.getZ() - target.getZ();
            double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

            if (distance > 0) {
                // 正規化方向，並乘以倒退速度（例如 0.25）
                double speed = 0.18D; 
                double vecX = (deltaX / distance) * speed;
                double vecZ = (deltaZ / distance) * speed;
                
                // 直接修改實體的運動向量（Delta Movement），給予往後的物理推力
                // 保持原本的 Y 軸（地心引力/跳躍），只強推 X 和 Z
                this.mob.setDeltaMovement(vecX, this.mob.getDeltaMovement().y, vecZ);
            }

            // 後退快結束時，順便把盾牌舉起來
            if (this.retreatTicks <= 5 && !this.mob.isUsingItem()) {
                this.mob.startUsingItem(InteractionHand.OFF_HAND);
                this.shieldTicks = 25; // 舉盾維持 1.25 秒
            }
        } else if (this.shieldTicks > 0) {
            // 【階段 2】：舉盾格擋
            this.shieldTicks--;
            // 格擋時以較慢的速度逼近目標，維持防禦架勢
            this.mob.getNavigation().moveTo(target, 0.5D);
            
            if (this.shieldTicks == 0) {
                this.mob.stopUsingItem(); // 時間到，放下盾牌
            }
        } else {
            // 【階段 3】：衝鋒進攻
            this.mob.getNavigation().moveTo(target, 1.2D);
            
            // 進入攻擊距離且冷卻完畢
            if (distanceSq <= attackReach && this.attackCooldown <= 0) {
                // 揮刀砍擊！
                this.mob.swing(InteractionHand.MAIN_HAND);
                if (this.mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    this.mob.doHurtTarget(serverLevel, target);
                }
                
                // 觸發循環
                this.attackCooldown = 20; // 攻擊冷卻 1 秒
                this.retreatTicks = 12;   // 砍完後退 0.6 秒 (物理推力下 12 tick 就很夠了)
            }
        }
    }
}