package com.example.examplemod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

// 🌟 移除了不必要的 MenuProvider 介面
public class VillageMilitiaEntity extends Pillager {


    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_PATROLLING = 
        net.minecraft.network.syncher.SynchedEntityData.defineId(VillageMilitiaEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    public VillageMilitiaEntity(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Pillager.createAttributes()
            .add(Attributes.MAX_HEALTH, 40.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.3D)
            .add(Attributes.ATTACK_DAMAGE, 5.0D)
            .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PATROLLING, false);
    }

    public boolean isPatrolling() {
        return this.entityData.get(DATA_PATROLLING);
    }

    public void setPatrolling(boolean patrolling) {
        this.entityData.set(DATA_PATROLLING, patrolling);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        
        // 換彈期間的斜後退蹲下走位 AI（優先級最高）
        this.goalSelector.addGoal(1, new com.example.examplemod.ai.MilitiaCrossbowRetreatGoal(this));
       // this.goalSelector.addGoal(1, new RangedCrossbowAttackGoal<>(this, 1.0D, 8.0F));
        this.goalSelector.addGoal(2, new com.example.examplemod.ai.MilitiaSwordAndShieldAttackGoal(this));
        
        // 跟隨玩家邏輯
        this.goalSelector.addGoal(3, new Goal() {
            @Override
            public boolean canUse() {
                if (!VillageMilitiaEntity.this.isPatrolling()) return false;
                Player closestPlayer = VillageMilitiaEntity.this.level().getNearestPlayer(VillageMilitiaEntity.this, 16.0D);
                return closestPlayer != null && VillageMilitiaEntity.this.distanceToSqr(closestPlayer) > 16.0D;
            }

            @Override
            public void tick() {
                Player closestPlayer = VillageMilitiaEntity.this.level().getNearestPlayer(VillageMilitiaEntity.this, 16.0D);
                if (closestPlayer != null) {
                    VillageMilitiaEntity.this.getNavigation().moveTo(closestPlayer, 1.2D);
                }
            }
        });

        this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0D));
        
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D) {
            @Override
            public boolean canUse() {
                return VillageMilitiaEntity.this.isPatrolling() && super.canUse();
            }
        });

        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new MilitiaAttackTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Zombie.class, true));
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, DifficultyInstance difficulty) {
        int weaponChoice = random.nextInt(3);
        if (weaponChoice == 0) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        } else if (weaponChoice == 1) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        } else {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
            this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, SpawnGroupData spawnData) {
        // 🌟 移除將物品塞入舊 inventory 的多餘程式碼
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnData);
    }

    // ================== 【 洗白身分機制 】 ==================
    @Override
    public boolean canJoinRaid() { return false; }
    @Override
    public boolean hasActiveRaid() { return false; }
    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() { return net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT; }
    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource damageSource) { return net.minecraft.sounds.SoundEvents.VILLAGER_HURT; }
    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() { return net.minecraft.sounds.SoundEvents.VILLAGER_DEATH; }

    // ================== 【 右鍵智慧換裝與互動機制 】 ==================
    @Override
    public net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {

        ItemStack itemInHand = player.getItemInHand(hand);
        
        // 確保在伺服器端處理，且玩家手上拿著東西
        if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel && !itemInHand.isEmpty()) {
            EquipmentSlot slotToEquip = null;

           
            if (itemInHand.getItem() instanceof net.minecraft.world.item.ShieldItem) {
                slotToEquip = EquipmentSlot.OFFHAND;
            } 
            // 1.21 新版防具/裝備判定：透過 DataComponents 獲取 Equippable 組件
            else if (itemInHand.has(net.minecraft.core.component.DataComponents.EQUIPPABLE)) {
                var equippable = itemInHand.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
                if (equippable != null) {
                    slotToEquip = equippable.slot(); // 使用你剛查到的 equippable.slot() 方法
                }
            }
            // 3. ⚔️ 武器與工具判定
            else if (itemInHand.isDamageableItem() || itemInHand.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                slotToEquip = EquipmentSlot.MAINHAND;
            }

            // 4. 如果成功判定了欄位，開始執行換裝
            if (slotToEquip != null) {
                ItemStack currentEquipped = this.getItemBySlot(slotToEquip);

                // 先把守衛身上原本的裝備噴出來
                if (!currentEquipped.isEmpty()) {
                    this.spawnAtLocation(serverLevel, currentEquipped.copy());
                }

                // 幫守衛換上玩家手裡的東西
                ItemStack newEquip = itemInHand.copy();
                newEquip.setCount(1);
                this.setItemSlot(slotToEquip, newEquip);

                // 扣除玩家手上的物品數量
                if (!player.getAbilities().instabuild) {
                    itemInHand.shrink(1);
                }

                // 播放穿戴聲音與揮手
                serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                player.swing(hand, true);
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
        }

        // 🌟 切換模式：空手右鍵，切換巡邏 / 跟隨
        if (itemInHand.isEmpty() && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
            if (!this.level().isClientSide()) {
                boolean currentMode = this.isPatrolling();
                this.setPatrolling(!currentMode);
                
                String modeName = !currentMode ? "§a【跟隨模式】(形影不離)" : "§e【巡邏模式】(留守原地)";
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7[民兵守衛] 任務變更為： " + modeName));
                
                // 修正：使用 level().playSound 確保雙端同步音效
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), 
                    net.minecraft.sounds.SoundEvents.VILLAGER_YES, 
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
            } else {
                for (int i = 0; i < 5; i++) {
                    this.level().addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, 
                        this.getRandomX(0.5D), this.getRandomY() + 0.5D, this.getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
                }
            }
            // 修正：改回標準的 InteractionResult.SUCCESS
            return net.minecraft.world.InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, hand);
    }
    @Override
    public void aiStep() {
        super.aiStep();

        // 如果 AI Goal 結束或失去目標，確保在非客戶端安全放下盾牌
        if (!this.level().isClientSide()) {
            if (this.getTarget() == null || this.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                if (this.isUsingItem() && this.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND) {
                    this.stopUsingItem();
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            // 檢查手上拿的是不是弩
            if (this.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                // 如果 AI 正在讓它拉弦/換彈（isUsingItem）
                if (this.isUsingItem()) {
                    // 強制讓碰撞箱與狀態切換為蹲姿
                    if (this.getPose() != net.minecraft.world.entity.Pose.CROUCHING) {
                        this.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                        this.setShiftKeyDown(true);
                    }
                } else {
                    if (this.getPose() == net.minecraft.world.entity.Pose.CROUCHING) {
                        this.setPose(net.minecraft.world.entity.Pose.STANDING);
                        this.setShiftKeyDown(false);
                    }
                }
            } else {
                if (this.isShiftKeyDown()) {
                    this.setPose(net.minecraft.world.entity.Pose.STANDING);
                    this.setShiftKeyDown(false);
                }
            }
        }
    }

    // ================== 【 攻擊目標目標選擇AI 】 ==================
    public static class MilitiaAttackTargetGoal extends Goal {
        private final Mob mob;
        private final double range = 16.0D;

        public MilitiaAttackTargetGoal(Mob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
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
                    if (entity instanceof Zombie || entity instanceof net.minecraft.world.entity.monster.Ravager) {
                        return true;
                    }
                    if (entity instanceof net.minecraft.world.entity.monster.illager.AbstractIllager) {
                        return !(entity instanceof VillageMilitiaEntity);
                    }
                    return false;
                }
            );

            if (!enemies.isEmpty()) {
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
}