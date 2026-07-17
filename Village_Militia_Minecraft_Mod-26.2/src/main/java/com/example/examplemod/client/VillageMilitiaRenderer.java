package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.VillageMilitiaEntity;
import com.example.examplemod.client.VillageMilitiaRenderer.MyRenderState;

import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

public class VillageMilitiaRenderer extends HumanoidMobRenderer<VillageMilitiaEntity, VillageMilitiaRenderer.MyRenderState, HumanoidModel<VillageMilitiaRenderer.MyRenderState>> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        ExampleMod.MODID,
        "textures/entity/village_millita.png"
    );

    public VillageMilitiaRenderer(EntityRendererProvider.Context context) {
      // 呼叫父類建構子
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);

        // 1. 使用官方定義的 PLAYER_ARMOR (這已經包含 helmet, chestplate, leggings, boots 的註冊)
        // 2. 透過 bake 方法將這些位置轉換為可用的模型集
        var armorModelSet = net.minecraft.client.renderer.entity.ArmorModelSet.bake(
            ModelLayers.PLAYER_ARMOR,
            context.getModelSet(),
            part -> new HumanoidModel<MyRenderState>(part)
        );

        // 3. 顯式指定泛型參數 <MyRenderState, HumanoidModel<MyRenderState>, HumanoidModel<MyRenderState>>
        this.addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<
            MyRenderState, 
            HumanoidModel<MyRenderState>, 
            HumanoidModel<MyRenderState>
        >(
            this,
            armorModelSet,
            context.getEquipmentRenderer()
        ));
    }

    public static class MyRenderState extends HumanoidRenderState {
        public boolean isHoldingCrossbow = false;
        public boolean isChargingCrossbow = false;
        public boolean isAggressive = false;
    }

    @Override
    public MyRenderState createRenderState() {
        return new MyRenderState();
    }

    @Override
    public void extractRenderState(VillageMilitiaEntity entity, MyRenderState state, float partialTick) {
        // 1. 先讓父類別提取基本狀態
        super.extractRenderState(entity, state, partialTick);
        
        // 🌟 修正點 1：強制覆蓋父類別（Pillager）的蹲下判定！
        // 這樣你在 Entity 裡面設定的 Pose.CROUCHING 或是 isShiftKeyDown() 才會真正生效！
        state.isCrouching = entity.isShiftKeyDown() || entity.getPose() == net.minecraft.world.entity.Pose.CROUCHING;

        // 讀取主手物品
        ItemStack mainHand = entity.getMainHandItem();
        state.isHoldingCrossbow = mainHand.getItem() instanceof CrossbowItem;
        
        // 讀取實體本身被 AI 控制的蓄力與敵對狀態
        state.isChargingCrossbow = entity.isChargingCrossbow();
        state.isAggressive = entity.isAggressive() || entity.getTarget() != null;

        // 雙手姿勢（ArmPose）核心邏輯
        if (state.isHoldingCrossbow) {
            
                // 核心關鍵：先確保蓄力時間有傳進去（這段一定要在前面）
            if (entity.isUsingItem() && entity.getUseItem().getItem() instanceof CrossbowItem) {
                state.isUsingItem = true;
                state.useItemHand = entity.getUsedItemHand();
                state.ticksUsingItem = (float) entity.getTicksUsingItem();
                state.maxCrossbowChargeDuration = (float) CrossbowItem.getChargeDuration(entity.getUseItem(), entity);
            }
            boolean isCharged = CrossbowItem.isCharged(mainHand);
            // 🌟 正確的優先級分層：
            if (state.isChargingCrossbow || entity.isUsingItem()) {
                // 只要正在拉弦，絕對優先播放拉弦動畫！！！
                state.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_CHARGE;
                state.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            } else {
                // 舉弩還是放鬆
                if (isCharged ||state.isAggressive || entity.getTarget() != null) {
                    state.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
                    state.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
                    state.attackTime = 0.0F;
                } else {
                    state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
                    state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
                }
            }
        } else {
            if ((mainHand.is(Items.IRON_SWORD) || mainHand.is(Items.IRON_SPEAR)) && state.isAggressive) {
                state.rightArmPose = HumanoidModel.ArmPose.ITEM;
            } else {
                state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
            }

            // 2. 處理副手（左臂）的盾牌格擋邏輯
            if (state.isUsingItem && state.useItemHand == net.minecraft.world.InteractionHand.OFF_HAND) {
                // 進入戰鬥舉盾，強行將左手改為 BLOCK 姿勢！
                state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
            } else {
                // 平常沒舉盾，左手放鬆
                state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            }
        }
    }
   
   
    @Override
    public Identifier getTextureLocation(MyRenderState state) {
        return TEXTURE;
    }
}