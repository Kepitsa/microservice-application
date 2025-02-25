package net.ohuje.CodeZero.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.ohuje.CodeZero.ai.HordeManager;
import net.ohuje.CodeZero.ai.UnyieldingTargetGoal; // наш класс
import net.ohuje.CodeZero.config.ConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(ServerWorld.class)
public abstract class MobSpawnMixin {

    @Unique
    private static final UUID JUDGMENT_SPEED_BOOST_ID = UUID.fromString("d47d9651-91d3-4d51-86a9-f9a813262e66");

    @Inject(method = "spawnEntity", at = @At("HEAD"))
    private void onMobSpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof HostileEntity hostileEntity)) {
            return;
        }
        ServerWorld world = (ServerWorld) hostileEntity.getWorld();

        // Читаем NBT (waveMob?)
        NbtCompound nbt = new NbtCompound();
        hostileEntity.writeNbt(nbt);
        boolean isWaveMob = nbt.contains("waveMob");
        boolean isJudgment = HordeManager.isJudgmentNight();

        // Волновой или Судный:
        if (isWaveMob || isJudgment) {
            // (Если нужно различать waveMob от судных - можно сделать if/else)

            if (isWaveMob) {
                // Волновой => setPersistent
                hostileEntity.setPersistent();
            }

            if (isJudgment && !isWaveMob) {
                // (Допустим, это обычный "natural" моб, но судная ночь)
                HordeManager.incrementNaturalHostileMobCount();
                // Пример: +30% speed
                var speedAttr = hostileEntity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
                if (speedAttr != null && speedAttr.getModifier(JUDGMENT_SPEED_BOOST_ID) == null) {
                    EntityAttributeModifier speedBoost = new EntityAttributeModifier(
                            JUDGMENT_SPEED_BOOST_ID,
                            "JudgmentNightSpeedBoost",
                            0.3,
                            EntityAttributeModifier.Operation.MULTIPLY_TOTAL
                    );
                    speedAttr.addPersistentModifier(speedBoost);
                }
            }

            // Подключаем UnyieldingTargetGoal (ignore LOS)
            // Нужно через accessor (т.к. targetSelector - protected)
            if (hostileEntity instanceof MobEntityAccessor accessor) {
                // Добавляем с приоритетом 0, чтобы «перебить» стандартный TargetGoal
                accessor.getTargetSelector().add(0, new UnyieldingTargetGoal(hostileEntity));
            }

            // Пробуем агрить на ближайшего игрока (необязательно)
            PlayerEntity closestPlayer = world.getClosestPlayer(hostileEntity,
                    ConfigManager.JUDGMENT_AGGRO_DISTANCE);
            if (closestPlayer != null) {
                hostileEntity.setTarget(closestPlayer);
            }
        }
    }
}
