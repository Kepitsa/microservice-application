package net.ohuje.CodeZero.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.ohuje.CodeZero.config.ConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * Mixin для Зомби, чтобы с шансом стать "информатором",
 * иметь 4-секундное "x-ray" при потере видимости
 * и потом рассылать LKP окрестным мобам.
 */
@Mixin(ZombieEntity.class)
public abstract class ZombieInformatorMixin extends PathAwareEntity {

    @Unique
    private static final Random RAND = new Random();

    // 4-секундный вид: 80 тиков
    @Unique
    private static final int VISION_DELAY_TICKS = 80;

    // Таймер, когда мы потеряли реальную видимость
    @Unique
    private int lostSightTimer = 0;
    // Флаг "стали информатором"
    @Unique
    private boolean isInformator = false;
    // Последняя известная позиция игрока (обновляем, пока x-ray ещё действует)
    @Unique
    private Vec3d lastKnownPlayerPos = null;

    protected ZombieInformatorMixin(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onConstruct(EntityType<? extends ZombieEntity> type, World world, CallbackInfo ci) {
        // Проверка блеклиста
        String mobId = Registries.ENTITY_TYPE.getId(type).toString();
        if (ConfigManager.MOB_BLACKLIST.contains(mobId)) {
            // Этот зомби в блэклисте, ничего не делаем
            return;
        }

        // С шансом ConfigManager.INFORMATOR_CHANCE
        if (RAND.nextFloat() <= ConfigManager.INFORMATOR_CHANCE) {
            isInformator = true;
            //System.out.println("[ZombieInformatorMixin] This zombie is an INFORMATOR!");
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!isInformator) return;
        if (this.getTarget() == null) {
            //System.out.println("[Inf] No target, lostSightTimer="+lostSightTimer);
            return;
        }
        boolean see = this.canSee(this.getTarget());
        //System.out.println("[Inf] see="+see+" lostSightTimer="+lostSightTimer+
                //" target="+this.getTarget().getName().getString());

        if (!isInformator) {
            return; // Обычный зомби, ничего особого
        }

        // Проверяем, есть ли у нас цель (игрок)
        if (this.getTarget() == null) {
            // сбрасываем, если был какой-то таймер
            lostSightTimer = 0;
            lastKnownPlayerPos = null;
            return;
        }

        // Если реально видим игрока (line of sight)
        if (this.canSee(this.getTarget())) {
            // обнуляем таймер
            lostSightTimer = 0;
            // обновляем lastKnownPlayerPos "по-настоящему"
            lastKnownPlayerPos = this.getTarget().getPos();
        } else {
            // Реальной видимости нет
            if (lostSightTimer < VISION_DELAY_TICKS) {
                // x-ray продолжается
                lostSightTimer++;
                // "сквозь стены" продолжаем обновлять lastKnownPlayerPos
                // Допустим, считаем, что мы "видим" целиком передвижения игрока
                lastKnownPlayerPos = this.getTarget().getPos();
            } else {
                // x-ray истёк, теперь мы окончательно теряем видимость
                // если lastKnownPlayerPos != null, рассылаем
                if (lastKnownPlayerPos != null) {
                    broadcastLastKnownPos(lastKnownPlayerPos);
                }
                // сбрасываем цель, т.к. теперь не видим
                this.setTarget(null);
                lostSightTimer = 0;
                lastKnownPlayerPos = null;
            }
        }
    }

    /**
     * Рассылает координаты lastKnownPlayerPos всем враждебным мобам
     * в радиусе ConfigManager.INFORMATOR_BROADCAST_RADIUS
     */
    @Unique
    private void broadcastLastKnownPos(Vec3d pos) {
        float radius = ConfigManager.INFORMATOR_BROADCAST_RADIUS;
        //System.out.println("[ZombieInformatorMixin] Broadcasting LKP " + pos + " to mobs in radius " + radius);

        // Ищем всех HostileEntity (или MobEntity) вокруг
        // Можно искать через world.getOtherEntities(...)
        // Для простоты pseudo-логика:
        this.getWorld().getOtherEntities(null, this.getBoundingBox().expand(radius), (entity) -> {
            // Фильтр: нужно только HostileEntity (или PathAwareEntity)
            return entity instanceof PathAwareEntity;
        }).forEach(entity -> {
            // Если в блеклисте, игнорируем, но можно всё равно вызвать:
            // "setLastKnownPos()" у них может быть пуст.
            if (entity == this) {
                return; // не передаём самому себе
            }
            // Псевдо-метод, например "MobExtensions" - нужно реализовать
            if (entity instanceof PathAwareEntity pathMob) {
                // вызовем некий интерфейс: pathMob.setLastKnownPos(pos)
                // Но в реальном коде нужен или Mixin, или интерфейс-расширение
                //System.out.println("  -> " + entity.getName().getString() + " gets LKP");
                // ... pseudo-code ...
            }
        });
    }
}
