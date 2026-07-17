package com.example.examplemod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.Ravager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import java.util.EnumSet;

@EventBusSubscriber(modid = ExampleMod.MODID)
public class ModEntityEvents {

    // 建立一個「完全不看 isAlliedTo、不看任何陣營」的強行開火/鎖定 Goal
    public static class ForceTargetGuardGoal extends Goal {
        private final Mob mob;
        private final double range = 16.0D; // 偵測守衛的範圍（半徑 16 格）

        public ForceTargetGuardGoal(Mob mob) {
            this.mob = mob;
            // 宣告這個 AI 會佔用「目標鎖定（TARGET）」的行為權限
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        // 💡 每刻檢查：什麼時候該啟動這個 AI？
        @Override
        public boolean canUse() {
            // 如果目前已經有活著的目標了，就先維持現狀
            if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
                return false;
            }

            // 🔍 用最純粹的空間雷達，抓周圍最近的守衛實體
            java.util.List<VillageMilitiaEntity> targets = this.mob.level().getEntitiesOfClass(
                VillageMilitiaEntity.class,
                this.mob.getBoundingBox().inflate(range, 4.0D, range),
                LivingEntity::isAlive
            );

            if (!targets.isEmpty()) {
                // 找到最近的守衛，直接繞過所有檢查，強行寫入仇恨欄位！
                this.mob.setTarget(targets.get(0));
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity currentTarget = this.mob.getTarget();
            return currentTarget instanceof VillageMilitiaEntity && currentTarget.isAlive();
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        // 防呆：如果是守衛自己出生，完全不要動，避免內鬨
        if (event.getEntity() instanceof VillageMilitiaEntity) {
            return;
        }

        // 🧟 1. 讓所有殭屍打守衛（殭屍本來就可以用原生的，但用這個更穩）
        if (event.getEntity() instanceof Zombie zombie) {
            zombie.targetSelector.addGoal(1, new ForceTargetGuardGoal(zombie));
        }

        // 🪓 2. 讓整支災厄村民大軍（Pillager, Vindicator, Evoker...）主動打守衛
        // 💡 拋棄被 final 擋死的 NearestAttackableTargetGoal，改用我們不受限制的空間雷達！
        if (event.getEntity() instanceof AbstractIllager illager) {
            illager.targetSelector.addGoal(1, new ForceTargetGuardGoal(illager));
        }

        // 🦏 3. 讓劫掠獸主動打守衛
        if (event.getEntity() instanceof Ravager ravager) {
            ravager.targetSelector.addGoal(1, new ForceTargetGuardGoal(ravager));
        }
    }
}