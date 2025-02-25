package net.ohuje.CodeZero.mixin;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.SpawnHelper;
import net.ohuje.CodeZero.ai.HordeManager;
import net.ohuje.CodeZero.config.ConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnHelper.Info.class)
public abstract class SpawnLimitMixin {

    @Inject(method = "isBelowCap", at = @At("HEAD"), cancellable = true)
    private void increaseMobCap(SpawnGroup group, ChunkPos chunkPos, CallbackInfoReturnable<Boolean> cir) {
        if (HordeManager.isJudgmentNight() && group == SpawnGroup.MONSTER) {
            int currentMobs = HordeManager.getNaturalHostileMobCount();
            int maxMobs = ConfigManager.MAX_JUDGMENT_MOBS;

            if (currentMobs < maxMobs) {
                cir.setReturnValue(true); // Разрешаем спавн, если мобов меньше лимита
                //System.out.println("[CodeZero] Судная ночь: Спавн разрешён. Текущий счётчик: " + currentMobs);
            } else {
                cir.setReturnValue(false); // Блокируем спавн, если лимит превышен
                //System.out.println("[CodeZero] Судная ночь: Достигнут лимит (" + maxMobs + "). Спавн мобов остановлен.");
            }
        }
    }
}


