package net.ohuje.CodeZero.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;
import net.ohuje.CodeZero.ai.UnyieldingTargetGoal;
import net.ohuje.CodeZero.config.ConfigManager;
import net.ohuje.CodeZero.ai.NoLimitBuildGoal;
import net.ohuje.CodeZero.ai.GoToLKPGoal;
import net.ohuje.CodeZero.ai.HordeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HostileEntity.class)
public abstract class HostileEntityMixin extends PathAwareEntity {

	protected HostileEntityMixin(EntityType<? extends PathAwareEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V",
			at = @At("TAIL"))
	private void onConstruct(EntityType<? extends HostileEntity> entityType, World world, CallbackInfo ci) {
		// Исключаем пиглинов
		if (entityType == EntityType.ZOMBIFIED_PIGLIN || entityType == EntityType.PIGLIN) {
			return;
		}

		// Исключаем blacklist
		String mobId = Registries.ENTITY_TYPE.getId(entityType).toString();
		if (ConfigManager.MOB_BLACKLIST.contains(mobId)) {
			return;
		}

		HostileEntity self = (HostileEntity) (Object) this;
		MobEntityAccessor accessor = (MobEntityAccessor) (Object) this;

		// Добавляем цели для строительства и преследования
		accessor.getGoalSelector().add(6, new NoLimitBuildGoal(self));
		accessor.getGoalSelector().add(7, new GoToLKPGoal(self));

		// Определяем: волновый ли моб (waveMob) или судная ночь
		// Но у нас нет NBT тут при конструировании.
		// => Либо вы поставите NBT чуть позже (MobSpawnMixin),
		// => Либо мы проверим isJudgmentNight().

		// Если сейчас isJudgmentNight():
		if (HordeManager.isJudgmentNight()) {
			// Удалим обычный ActiveTargetGoal (если он уже есть)
			// И добавим наш "UnyieldingTargetGoal"
			accessor.getTargetSelector().add(0, new UnyieldingTargetGoal(self));
		}
	}

}
