package net.ohuje.CodeZero.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.ohuje.CodeZero.ai.HordeManager;
import net.ohuje.CodeZero.config.ConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.nbt.NbtCompound;

@Mixin(Entity.class)
public abstract class EntityRemoveMixin {

    @Inject(method = "remove", at = @At("HEAD"))
    private void onEntityRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof HostileEntity hostileEntity) {
            NbtCompound nbtData = new NbtCompound();
            hostileEntity.writeNbt(nbtData);

            boolean isWaveMob = nbtData.contains("waveMob") && nbtData.getBoolean("waveMob");

            // Проверяем, если это естественный моб во время судной ночи
            if (!isWaveMob && HordeManager.isJudgmentNight()) {
                HordeManager.decrementNaturalHostileMobCount();
                //System.out.println("[CodeZero] Естественный моб удалён через миксин. Текущий счётчик: " + HordeManager.getNaturalHostileMobCount());
            }
        }
    }
}


