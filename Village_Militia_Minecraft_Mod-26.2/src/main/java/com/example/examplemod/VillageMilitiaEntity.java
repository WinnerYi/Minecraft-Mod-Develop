package com.example.examplemod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
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


public class VillageMilitiaEntity extends Pillager {
    public static boolean checkMilitiaSpawnRules(
        EntityType<? extends Mob> type, 
        net.minecraft.world.level.ServerLevelAccessor level, 
        net.minecraft.world.entity.EntitySpawnReason spawnReason, 
        net.minecraft.core.BlockPos pos, 
        net.minecraft.util.RandomSource random
    ) {
        //  什麼都不檢查，只要觸發判定就無條件放行！
        return false; 
    }

    @Override
    public float getWalkTargetValue(net.minecraft.core.BlockPos pos, net.minecraft.world.level.LevelReader level) {
        // 核心修正：強行回傳 10.0F（只要是正常方塊都算高分放行），徹底打破原版 Pillager 寫死的 0.0F 限制！
        return 10.0F;
    }

    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_PATROLLING = 
        net.minecraft.network.syncher.SynchedEntityData.defineId(VillageMilitiaEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    public VillageMilitiaEntity(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
        this.getNavigation().setCanOpenDoors(true);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 0.2F); 
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.LAVA, 0.2F);
        this.getNavigation().setRequiredPathLength(48.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Pillager.createAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.4D)
            .add(Attributes.ATTACK_DAMAGE, 1.0D)
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
        this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, true));
        // 換彈期間的斜後退蹲下走位 AI（優先級最高）
       this.targetSelector.addGoal(1, new HurtByTargetGoal(this, VillageMilitiaEntity.class).setAlertOthers());
        this.goalSelector.addGoal(1, new com.example.examplemod.ai.MilitiaCrossbowRetreatGoal(this));
       // this.goalSelector.addGoal(1, new RangedCrossbowAttackGoal<>(this, 1.0D, 8.0F));
        this.targetSelector.addGoal(1, new MilitiaAttackTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Zombie.class, true)); 
        this.goalSelector.addGoal(2, new com.example.examplemod.ai.MilitiaSwordAndShieldAttackGoal(this));
         
        
        // 跟隨玩家邏輯
        // this.goalSelector.addGoal(3, new Goal() {
        //     @Override
        //     public boolean canUse() {
        //         if (!VillageMilitiaEntity.this.isPatrolling()) return false;
        //         Player closestPlayer = VillageMilitiaEntity.this.level().getNearestPlayer(VillageMilitiaEntity.this, 16.0D);
        //         return closestPlayer != null && VillageMilitiaEntity.this.distanceToSqr(closestPlayer) > 16.0D;
        //     }

        //     @Override
        //     public void tick() {
        //         Player closestPlayer = VillageMilitiaEntity.this.level().getNearestPlayer(VillageMilitiaEntity.this, 16.0D);
        //         if (closestPlayer != null) {
        //             VillageMilitiaEntity.this.getNavigation().moveTo(closestPlayer, 1.2D);
        //         }
        //     }
        // });
        this.goalSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal(this, 0.6D, false, 4, () -> false));
        this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0D));
        
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.6D));

        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        
    }

    @Override
    public void setTarget(@javax.annotation.Nullable net.minecraft.world.entity.LivingEntity target) {
        if (target instanceof VillageMilitiaEntity || target instanceof IronGolem || target instanceof SnowGolem || (target instanceof Wolf wolf &&  wolf.isTame())) {
            return; 
        }
        super.setTarget(target); // 其他怪物（殭屍等）正常鎖定
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayerSq) {
        return false; 
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

        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, SpawnGroupData spawnData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, spawnData);
        if (this.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem() instanceof net.minecraft.world.item.BannerItem) {
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
            this.setPatrolLeader(false);
        }

        return result;
    }

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

    //右鍵智慧換裝與互動機制 
    @Override
    public net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {

        ItemStack itemInHand = player.getItemInHand(hand);
        
        // 確保在伺服器端處理，且玩家手上拿著東西
        if (player.isShiftKeyDown() && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel && !itemInHand.isEmpty()) {
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
            // 武器與工具判定
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

        if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
        
        // 
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
            
            // 如果民兵目前正騎在馬上（有坐騎）
            if (this.isPassenger()) {
                if (!this.level().isClientSide()) {
                    this.stopRiding();
                    
                    // 可以加上一些視覺或聽覺回饋，讓互動更有感（例如村民生氣或滿意的聲音，這裡用揮手示意）
                    this.level().broadcastEntityEvent(this, (byte)4); 
                }
               
                
            }
          }
        }

        //  切換模式：手拿綠寶石右鍵（且限制主手），切換巡邏 / 跟隨
        // if (itemInHand.is(net.minecraft.world.item.Items.EMERALD) && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
            
        //     if (!this.level().isClientSide()) {
        //         // 1. 執行模式切換
        //         boolean currentMode = this.isPatrolling();
        //         this.setPatrolling(!currentMode);
                
        //         // 這裡我幫你把文字提示改得更符合「收費雇用」的感覺
        //         String modeName = !currentMode ? "§a【跟隨模式】(形影不離)" : "§e【巡邏模式】(留守原地)";
        //         player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7[民兵守衛] 感謝您的綠寶石！任務變更為： " + modeName));
                
        //      
        //         if (!player.getAbilities().instabuild) {
        //             itemInHand.shrink(1);
        //         }
                
        //         // 3. 播放村民高興的聲音
        //         this.level().playSound(null, this.getX(), this.getY(), this.getZ(), 
        //             net.minecraft.sounds.SoundEvents.VILLAGER_YES, 
        //             net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
        //     } else {
        //         // 4. 客戶端噴出快樂村民綠粒子
        //         for (int i = 0; i < 7; i++) {
        //             this.level().addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, 
        //                 this.getRandomX(0.5D), this.getRandomY() + 0.5D, this.getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
        //         }
        //     }
            
        //     return net.minecraft.world.InteractionResult.SUCCESS;
        // }

        return super.mobInteract(player, hand);
    }

    private int lastHorseCheckTick = 0;
   @Override
    public void aiStep() {
        super.aiStep();
        

       
        if (!this.level().isClientSide()) {
            
            // 1. 【原版收盾邏輯】如果 AI Goal 結束或失去目標，確保安全放下盾牌
            if (this.getTarget() == null || this.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                if (this.isUsingItem() && this.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND) {
                    this.stopUsingItem();
                }
            }

            // 【重裝戰馬騎乘與卸載邏輯】
            if (!this.isPassenger()) {
                // =================【 上馬邏輯 】=================
                //  每 20 ticks (1秒) 檢查一次周圍
                if (this.tickCount - this.lastHorseCheckTick >= 35) {
                    this.lastHorseCheckTick = this.tickCount;

                    net.minecraft.world.phys.AABB checkArea = this.getBoundingBox().inflate(2.0D);
                    java.util.List<net.minecraft.world.entity.animal.equine.Horse> nearbyHorses = 
                        this.level().getEntitiesOfClass(net.minecraft.world.entity.animal.equine.Horse.class, checkArea);

                    for (net.minecraft.world.entity.animal.equine.Horse horse : nearbyHorses) {
                        // 方案 C 核心判定：必須同時有馬鞍 (SADDLE) 和馬鎧 (BODY)
                        boolean hasSaddle = !horse.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.SADDLE).isEmpty();
                        
                        
                        // 用 horse.getPassengers().isEmpty() 代替舊版的 hasNoPassengers()
                        boolean hasNoPassengers = horse.getPassengers().isEmpty();

                        if (horse.isAlive() && hasNoPassengers && hasSaddle  && !horse.isLeashed()) {
                            this.startRiding(horse);
                            break;
                        }
                    }
                }
            } else {
                // =================【 下馬邏輯 】=================
                
                if (this.getVehicle() instanceof net.minecraft.world.entity.animal.equine.Horse horse) {
                    if (this.tickCount % 20 == 0) {
                        if (!horse.isAlive()) {
                            
                            this.stopRiding();
                        }
                    }
                }
            }

            // 3. 【原版回血邏輯】每 100 ticks 治癒 (半顆心)
            if (this.tickCount % 100 == 0) {
                if (this.getHealth() < this.getMaxHealth()) {
                    this.heal(1.0F);
                    
                    // ((net.minecraft.server.level.ServerLevel)this.level()).sendParticles(
                    //     net.minecraft.core.particles.ParticleTypes.HEART, 
                    //     this.getX(), this.getY() + 2.0D, this.getZ(), 
                    //     3, 0.2D, 0.2D, 0.2D, 0.01D
                    // );
                }
            }

            // 4. 【原版逃生邏輯】每 20 ticks 在水或岩漿中尋找陸地導航
            if (this.tickCount % 20 == 0 && (this.isInWater() || this.isInLava()) && this.getTarget() == null && this.getNavigation().isDone()) {
                net.minecraft.world.phys.Vec3 landPos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(this, 10, 7);
                
                if (landPos != null) {
                    // 如果在岩漿裡，逃命速度加成到 1.5D！在水裡則維持 1.0D
                    double speed = this.isInLava() ? 1.5D : 1.0D;
                    this.getNavigation().moveTo(landPos.x, landPos.y, landPos.z, speed);
                }
            }
       
        }
    }
    
    @Override
    public void rideTick() {
        super.rideTick();
        
        
        if (this.getVehicle() instanceof net.minecraft.world.entity.animal.equine.Horse) {
            // 把民兵目前的 Y 軸座標，直接往下移動 0.25 格，強行修正浮空！
            this.setPos(this.getX(), this.getY() - 0.60D, this.getZ());
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

    @Override
    protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel serverLevel, net.minecraft.world.damagesource.DamageSource damageSource, boolean hitByPlayer) {
        super.dropCustomDeathLoot(serverLevel, damageSource, hitByPlayer);

        // 遍歷所有裝備欄位，並無條件 100% 噴在地上
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack itemStack = this.getItemBySlot(slot);
            if (!itemStack.isEmpty()) {
                this.spawnAtLocation(serverLevel, itemStack);
                this.setItemSlot(slot, ItemStack.EMPTY); // 清空防止重複刷物
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
