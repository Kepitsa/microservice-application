package net.ohuje.CodeZero.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.ohuje.CodeZero.mixin.MobEntityAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(EntityType<? extends ZombieEntity> entityType, World world, CallbackInfo info) {
        ZombieEntity zombie = (ZombieEntity) (Object) this;

        // Исключаем зомбифицированных пиглинов и пиглинов
        if (entityType.equals(EntityType.ZOMBIFIED_PIGLIN) || entityType.equals(EntityType.PIGLIN)) {
            return;
        }

        // Получаем доступ к goalSelector и targetSelector через Accessor
        GoalSelector goalSelector = ((MobEntityAccessor) zombie).getGoalSelector();
        GoalSelector targetSelector = ((MobEntityAccessor) zombie).getTargetSelector();

        // Удаляем стандартные цели и добавляем кастомные
        goalSelector.add(2, new MeleeAttackGoal(zombie, 1.0, false));
        targetSelector.add(1, new ActiveTargetGoal<>(zombie, ServerPlayerEntity.class, true));
    }
}


