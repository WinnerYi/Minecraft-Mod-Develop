package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.VillageMilitiaEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

// 🌟 1. 將類別泛型中的第二個參數換成我們自訂的 MyRenderState
public class VillageMilitiaRenderer extends HumanoidMobRenderer<VillageMilitiaEntity, VillageMilitiaRenderer.MyRenderState, HumanoidModel<VillageMilitiaRenderer.MyRenderState>> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        ExampleMod.MODID,
        "textures/entity/village_self_defender.png"
    );

    public VillageMilitiaRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    // 🌟 2. 建立自訂的渲染狀態儲存槽（RenderState）
    public static class MyRenderState extends HumanoidRenderState {
        public boolean isHoldingCrossbow = false;
        public boolean isChargingCrossbow = false;
        public boolean isAggressive = false;
    }

    // 🌟 3. 告訴遊戲我們要使用自訂的狀態槽
    @Override
    public MyRenderState createRenderState() {
        return new MyRenderState();
    }

    // 🌟 4. 【核心關鍵】在資料提取階段，將 Entity 的真實狀態拷貝進 RenderState
    @Override
    public void extractRenderState(VillageMilitiaEntity entity, MyRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        
        // 讀取主手物品
        ItemStack mainHand = entity.getMainHandItem();
        state.isHoldingCrossbow = mainHand.getItem() instanceof CrossbowItem;
        
        // 讀取實體本身被 AI 控制的蓄力與敵對狀態
        state.isChargingCrossbow = entity.isChargingCrossbow();
        state.isAggressive = entity.isAggressive() || entity.getTarget() != null;

        // 🌟 5. 根據拷貝出來的狀態，直接強行改寫人類模型的雙手姿勢（ArmPose）！
        // 🌟 重新校正：蓄力完畢後，雙手必須「同時」保持 CROSSBOW_HOLD 姿勢
        if (state.isHoldingCrossbow) {
            if (state.isChargingCrossbow) {
                // 1. 正在拉弦蓄力階段
                state.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_CHARGE;
                state.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            } else if (state.isAggressive) {
                // 2. 蓄力完成了，或者是正在瞄準目標準備發射
                // 💡 關鍵：雙手都必須無條件填入 CROSSBOW_HOLD，缺一不可！
                state.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
                state.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
            } else {
                // 3. 平常沒戰鬥、單純把弩拿在手上的巡邏姿勢（可選：看你想讓牠垂手還是端弩）
                // 如果想讓牠平常沒事也端著弩，就維持 CROSSBOW_HOLD；想放鬆就改回 EMPTY
                state.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
                state.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        } else if (mainHand.is(Items.IRON_SWORD) && state.isAggressive) {
            // 拿鐵劍戰鬥
            state.rightArmPose = HumanoidModel.ArmPose.ITEM;
        }
    }

    @Override
    public Identifier getTextureLocation(MyRenderState state) {
        return TEXTURE;
    }

    
}