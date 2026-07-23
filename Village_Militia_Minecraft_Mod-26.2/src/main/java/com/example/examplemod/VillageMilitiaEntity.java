package com.example.examplemod;
import java.util.function.Predicate;

import com.example.examplemod.ai.MilitiaBowRetreatGoal;
import com.example.examplemod.ai.MilitiaAttackTargetGoal;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;


import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;


public class VillageMilitiaEntity extends PathfinderMob implements CrossbowAttackMob, InventoryCarrier{
    private final SimpleContainer inventory = new SimpleContainer(5);
    private int celebrateTicks = 0;
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
    protected void registerGoals() {
        
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, true));
        // 換彈期間的斜後退蹲下走位 AI（優先級最高）
       this.targetSelector.addGoal(1, new HurtByTargetGoal(this, VillageMilitiaEntity.class).setAlertOthers());
       
       // this.goalSelector.addGoal(1, new RangedCrossbowAttackGoal<>(this, 1.0D, 8.0F));
        this.targetSelector.addGoal(1, new MilitiaAttackTargetGoal(this));
        this.goalSelector.addGoal(2, new com.example.examplemod.ai.MilitiaCrossbowRetreatGoal(this));
        this.goalSelector.addGoal(2, new com.example.examplemod.ai.MilitiaSwordAndShieldAttackGoal(this));
        this.goalSelector.addGoal(1, new MilitiaBowRetreatGoal(this));
       
        this.goalSelector.addGoal(4, new MoveThroughVillageGoal(this, 0.5D, false, 4, () -> false));
        this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0D));
        
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.5D));

        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> IS_CHARGING_CROSSBOW = 
        net.minecraft.network.syncher.SynchedEntityData.defineId(VillageMilitiaEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> IS_CELEBRATING =
        SynchedEntityData.defineId(VillageMilitiaEntity.class, EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_CHARGING_CROSSBOW, false);
        builder.define(IS_CELEBRATING, false);
    }

    
    
    public VillageMilitiaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.getNavigation().setCanOpenDoors(true);
        this.getNavigation().setRequiredPathLength(48.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Pillager.createAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.5D)
            .add(Attributes.ATTACK_DAMAGE, 1.0D)
            .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public boolean isHoldingBowAndCharging() {
        return this.isUsingItem() && this.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem;
    }

    

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        // 呼叫 CrossbowAttackMob 預設的弩箭發射邏輯 (1.6F 為射擊速度/力量)
        this.performCrossbowAttack(this, 1.6F);
    }

    public boolean isChargingCrossbow() {
        return this.entityData.get(IS_CHARGING_CROSSBOW);
    }

    @Override
    public void setChargingCrossbow(boolean isCharging) {
        // this.entityData.set(IS_CHARGING_CROSSBOW, isCharging);
    }

     @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

   
    @Override
    public ItemStack getProjectile(ItemStack weapon) {
        if (weapon.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem weaponItem) {

            return new ItemStack(net.minecraft.world.item.Items.ARROW);
        }
        
        return ItemStack.EMPTY;
    }


    @Override
    public SimpleContainer getInventory() {
        return this.inventory;
    }
    
    public boolean isCelebrating() {
        return this.entityData.get(IS_CELEBRATING);
    }

    public void setCelebrating(boolean celebrating) {
        this.entityData.set(IS_CELEBRATING, celebrating);
    }
   
    private boolean isHeroNearby() {
        Player nearestPlayer = this.level().getNearestPlayer(this, 3.0D);
        return nearestPlayer != null && nearestPlayer.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
    }

   

   
    @Override
    public void setTarget(@javax.annotation.Nullable net.minecraft.world.entity.LivingEntity target) {
        if (target instanceof VillageMilitiaEntity ) {
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
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, SpawnGroupData spawnData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, spawnData);
        if (this.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem() instanceof net.minecraft.world.item.BannerItem) {
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
            
        }
        this.populateDefaultEquipmentSlots(level.getRandom(), difficulty);

        return result;
    }
   

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() { return net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT; }
    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource damageSource) { return net.minecraft.sounds.SoundEvents.VILLAGER_HURT; }
    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() { return net.minecraft.sounds.SoundEvents.VILLAGER_DEATH; }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // 呼叫父類別執行真正的扣血與受傷處理
        boolean wasHurt = super.hurtServer(level, source, amount);

        // 如果成功造成傷害，且攻擊者是玩家（近戰或遠程）
        if (wasHurt) {
            if (source.getEntity() instanceof Player || source.getDirectEntity() instanceof Player) {
                level.sendParticles(
                    ParticleTypes.ANGRY_VILLAGER,
                    this.getX(), 
                    this.getEyeY() + 0.5D, // 頭頂位置
                    this.getZ(),
                    5,                     // 數量
                    0.25D, 0.25D, 0.25D,   // 擴散範圍
                    0.02D                  // 飄散速度
                );
            }
        }

        return wasHurt;
    }
    // ================== 【 右鍵智慧換裝與互動機制 】 ==================
    @Override
    public net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {

        ItemStack itemInHand = player.getItemInHand(hand);
        if (this.getTarget() != null && this.getTarget().isAlive()) {
             return InteractionResult.PASS;
        }
        
        // 確保在伺服器端處理，且玩家手上拿著東西
        if (player.isShiftKeyDown() && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {

            if (itemInHand.is(net.minecraft.world.item.Items.STICK)) {
                boolean dropAny = false;

                // 遍歷所有裝備欄位 (MAINHAND, OFFHAND, FEET, LEGS, CHEST, HEAD)
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    ItemStack currentEquipped = this.getItemBySlot(slot);

                    if (!currentEquipped.isEmpty()) {
                        // 將裝備噴在地上
                        this.spawnAtLocation(serverLevel, currentEquipped.copy());
                        // 清空該欄位
                        this.setItemSlot(slot, ItemStack.EMPTY);
                        dropAny = true;
                    }
                }

                // 如果真的有卸下任何裝備，播放聲音與揮手手勢
                if (dropAny) {
                    serverLevel.playSound(
                        null, 
                        this.getX(), this.getY(), this.getZ(), 
                        net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC.value(), 
                        net.minecraft.sounds.SoundSource.NEUTRAL, 
                        1.0F, 1.0F
                    );
                    player.swing(hand, true);
                    return net.minecraft.world.InteractionResult.SUCCESS;
                }
            } 
            // =========================================================
            //  一般物品 + Shift + 右鍵：單件裝備替換邏輯
            // =========================================================
            else if (!itemInHand.isEmpty()) {
                EquipmentSlot slotToEquip = null;

                // 盾牌預設放副手
                if (itemInHand.getItem() instanceof net.minecraft.world.item.ShieldItem) {
                    slotToEquip = EquipmentSlot.OFFHAND;
                } 
                // 1.21 新版防具/裝備判定：透過 DataComponents 獲取 Equippable 組件
                else if (itemInHand.has(net.minecraft.core.component.DataComponents.EQUIPPABLE)) {
                    var equippable = itemInHand.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
                    if (equippable != null) {
                        slotToEquip = equippable.slot();
                    }
                }
                // 武器與工具判定
                else if (itemInHand.isDamageableItem() || itemInHand.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                    slotToEquip = EquipmentSlot.MAINHAND;
                }

                // 如果成功判定了欄位，開始執行換裝
                if (slotToEquip != null) {
                    ItemStack currentEquipped = this.getItemBySlot(slotToEquip);

                    // 先把守衛身上原本該欄位的裝備噴出來
                    if (!currentEquipped.isEmpty()) {
                        this.spawnAtLocation(serverLevel, currentEquipped.copy());
                    }

                    // 幫守衛換上玩家手裡的東西
                    ItemStack newEquip = itemInHand.copy();
                    newEquip.setCount(1);
                    this.setItemSlot(slotToEquip, newEquip);

                    // 扣除玩家手上的物品數量（非創造模式）
                    if (!player.getAbilities().instabuild) {
                        itemInHand.shrink(1);
                    }

                    // 播放穿戴聲音與揮手
                    serverLevel.playSound(
                        null, 
                        this.getX(), this.getY(), this.getZ(), 
                        net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON, 
                        net.minecraft.sounds.SoundSource.NEUTRAL, 
                        1.0F, 1.0F
                    );
                    player.swing(hand, true);
                    return net.minecraft.world.InteractionResult.SUCCESS;
                }
            }
        }



        if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
        
        
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

       
        return super.mobInteract(player, hand);
    }

    
    
    private int lastHorseCheckTick = 0;
   @Override
    public void aiStep() {
        super.aiStep();
                                /// important
        if (this.level().isClientSide() && this.swinging) {
            this.swingTime++; // 強制讓 -1 變成 0, 1, 2, 3, 4, 5...
            
            // 原版揮手預設持續 6 個 tick
            if (this.swingTime >= this.getCurrentSwingDuration()) {
                this.swingTime = 0;
                this.swinging = false;
            }
        }
        

        if (!this.level().isClientSide()) {
            
            // 1. 【原版收盾邏輯】如果 AI Goal 結束或失去目標，確保安全放下盾牌
            if (this.getTarget() == null || this.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                if (this.isUsingItem() && this.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND) {
                    this.stopUsingItem();
                }
            }

            // 2. 🌟【重裝戰馬騎乘與卸載邏輯】
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
                            // 📯 民兵立刻跳下馬！
                            this.stopRiding();
                        }
                    }
                }
            }

            // 3. 【原版回血邏輯】每 100 ticks 治癒 (半顆心)
            if (this.tickCount % 100 == 0) {
                if (this.getHealth() < this.getMaxHealth()) {
                    this.heal(1.0F);
                }
            }

            // 4. 【原版逃生邏輯】每 20 ticks 在水或岩漿中尋找陸地導航
            if (this.tickCount % 20 == 0 && (this.isInWater() || this.isInLava()) && this.getTarget() == null && this.getNavigation().isDone()) {
                net.minecraft.world.phys.Vec3 landPos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(this, 10, 7);
                
                if (landPos != null) {
                    // 如果在岩漿裡，逃命速度加成到 1.5D！在水裡則維持 1.0D
                    double speed = this.isInLava() ? 1.4D : 1.0D;
                    this.getNavigation().moveTo(landPos.x, landPos.y, landPos.z, speed);
                }
            }
       
        }
    }
    
    @Override
    public void rideTick() {
        super.rideTick();
        if (this.getVehicle() instanceof net.minecraft.world.entity.animal.equine.Horse) {
            //  強行干預：把民兵目前的 Y 軸座標，直接往下移動 0.25 格，強行修正浮空！
            this.setPos(this.getX(), this.getY() - 0.60D, this.getZ());
        }
    }

   
    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) {
            if (this.level().isClientSide()) {
                
                this.stopUsingItem();      // 1. 強制解開舉盾/使用物品
                this.swinging = true;      // 2. 強制設為正在揮手
                this.swingTime = 0;       // 3. 強制將計時器歸位到 -1 (準備進入 0)
                this.swingingArm = net.minecraft.world.InteractionHand.MAIN_HAND; // 4. 指定右手
            }
        } else if (id == 5) {
            if (this.level().isClientSide()) {
                this.stopUsingItem();
                this.swinging = true;
                this.swingTime = -1;
                this.swingingArm = net.minecraft.world.InteractionHand.OFF_HAND;
            }
        } else {
            super.handleEntityEvent(id);
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


        if (!this.level().isClientSide()) {
    // 1. 慶祝計時器倒數
            if (isCelebrating()) {
                this.celebrateTicks--;
                if (this.celebrateTicks <= 0) {
                    setCelebrating(false);
                }
            } 
            // 2. 沒在慶祝時，才去檢查是否要觸發新的慶祝
            else if (this.tickCount % 40 == 0 && isHeroNearby()) {
                // 機率改為 1F (100%) 或你想要的數值
                if (this.random.nextFloat() < 1F) { 
                    setCelebrating(true);
                    this.celebrateTicks = 50; 
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



}