package com.example.examplemod.ai;

import com.example.examplemod.VillageMilitiaEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class MilitiaCrossbowRetreatGoal extends Goal {
    private final VillageMilitiaEntity mob;
    
    // --- 隨機與尋路參數 ---
    private int pathUpdateTimer = 0;
    private double moveSpeedFactor = 1.0D;     // 個人走位速度微調
    private int shootDelay = 0;               // 裝填完畢後的瞄準停頓計時
    private int chargeDelay = 0;              // 發射後到下一次拉弦的反應時間計時
    private int strafeTimer = 0;              // 控制走位換向的計時器
    private int strafeInterval = 20;          // 多少 tick 換一次走位方向

    public MilitiaCrossbowRetreatGoal(VillageMilitiaEntity mob) {
        this.mob = mob;
        // 移除 JUMP Flag，僅保留 MOVE 與 LOOK
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
        this.resetRandomParams();
        this.pathUpdateTimer = 0;
    }

    @Override
    public void stop() {
        this.mob.setChargingCrossbow(false);
        this.mob.stopUsingItem();
        this.mob.setShiftKeyDown(false); // 停止 AI 時解除蹲下
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

        // 永遠看向目標
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        ItemStack crossbow = this.mob.getMainHandItem();
        boolean isCharged = CrossbowItem.isCharged(crossbow);
        boolean isRiding = this.mob.isPassenger() && this.mob.getVehicle() instanceof PathfinderMob;
        PathfinderMob movingEntity = isRiding ? (PathfinderMob) this.mob.getVehicle() : this.mob;

        // 定期隨機變換走位速度與頻率（防止鏡像同步）
        this.strafeTimer++;
        if (this.strafeTimer >= this.strafeInterval) {
            this.strafeTimer = 0;
            this.strafeInterval = 15 + this.mob.getRandom().nextInt(25);
            this.moveSpeedFactor = 0.8D + this.mob.getRandom().nextDouble() * 0.4D; // 0.8x ~ 1.2x
        }

        // ----------------------------------------------------
        // 階段 1：未裝填（拉弦 + 自動尋路安全後撤 + 蹲下姿態）
        // ----------------------------------------------------
        if (!isCharged) {
            // 剛射完箭的反應時間（喘息/拉弦準備）
            if (this.chargeDelay > 0) {
                this.chargeDelay--;
                movingEntity.getNavigation().stop();
                return;
            }

            // 🥷 保持蹲下姿態 (非騎乘狀態下蹲下)
            if (!isRiding) {
                this.mob.setShiftKeyDown(true);
            }

            this.mob.setChargingCrossbow(true);

            if (!this.mob.isUsingItem()) {
                this.mob.startUsingItem(InteractionHand.MAIN_HAND);
            }

            this.pathUpdateTimer++;
            if (this.pathUpdateTimer >= 8 || movingEntity.getNavigation().isDone()) {
                this.pathUpdateTimer = 0;

                int retreatDistance = isRiding ? 10 : 7;
                Vec3 safeRetreatPos = DefaultRandomPos.getPosAway(
                    movingEntity, 
                    retreatDistance, 
                    4, 
                    target.position()
                );

                if (safeRetreatPos != null) {
                    double baseSpeed = isRiding ? 1.15D : 0.45D;
                    double finalSpeed = baseSpeed * this.moveSpeedFactor;
                    movingEntity.getNavigation().moveTo(safeRetreatPos.x, safeRetreatPos.y, safeRetreatPos.z, finalSpeed);
                }
            }
            
        } 
        // ----------------------------------------------------
        // 階段 2：已裝填（瞄準 + 蹲下靜止發射）
        // ----------------------------------------------------
        else {
            this.mob.setChargingCrossbow(false);
            this.mob.stopUsingItem(); 

            // 🥷 裝填完成準備開槍，依然保持蹲下瞄準姿態
            if (!isRiding) {
                this.mob.setShiftKeyDown(true);
            }

            // 瞄準時停止移動以確保射擊精度
            movingEntity.getNavigation().stop();

            // 裝填好後隨機瞄準停頓（4~12 ticks）
            if (this.shootDelay > 0) {
                this.shootDelay--;
                return;
            }
            
            // 執行發射
            if (this.mob.level() instanceof ServerLevel) {
                float randomVelocity = 1.4F + this.mob.getRandom().nextFloat() * 0.4F; 
                this.mob.performRangedAttack(target, randomVelocity);
            }

            // 開完槍，解除蹲下狀態並重置參數
            this.mob.setShiftKeyDown(false);
            this.resetRandomParams();
        }
    }

    /**
     * 重置並生成下一輪攻擊的隨機數值
     */
    private void resetRandomParams() {
        this.shootDelay = 9 + this.mob.getRandom().nextInt(15);
        this.chargeDelay = 2 + this.mob.getRandom().nextInt(7);
        this.moveSpeedFactor = 0.8D + this.mob.getRandom().nextDouble() * 0.35D;
        this.strafeTimer = 0;
        this.strafeInterval = 15 + this.mob.getRandom().nextInt(20);
        this.pathUpdateTimer = 99; // 重置時強制下次 tick 立刻尋路
    }
}