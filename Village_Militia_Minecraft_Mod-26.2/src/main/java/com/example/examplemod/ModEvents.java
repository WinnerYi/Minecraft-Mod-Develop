package com.example.examplemod;

import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

@EventBusSubscriber(modid = ExampleMod.MODID)
public class ModEvents {

    @SubscribeEvent
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
            ModEntities.VILLAGE_MILITIA.get(),
        
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            VillageMilitiaEntity::checkMilitiaSpawnRules, // 完美指向你先前修正好、支援 EntitySpawnReason 的方法
            RegisterSpawnPlacementsEvent.Operation.OR
        );
    }
}