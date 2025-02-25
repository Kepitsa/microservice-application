package net.ohuje.CodeZero.ai;

import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Цель агро, которая игнорирует линию видимости (Line of Sight),
 * и не сбрасывается, когда моб «теряет» игрока из виду.
 */
public class UnyieldingTargetGoal extends ActiveTargetGoal<PlayerEntity> {

    public UnyieldingTargetGoal(HostileEntity mob) {
        // Последний параметр в super(...) = "checkVisibility".
        // Ставим false => НЕ проверять LOS (игнорировать видимость).
        super(mob, PlayerEntity.class, false);
    }


    /**
     * По умолчанию ActiveTargetGoal может сбрасывать цель при отсутствии LOS,
     * но мы включили ignoreLineOfSight = true, значит
     * super-методы не будут делать проверку видимости.
     */

    @Override
    public boolean canStart() {
        // Можно дополнительно проверять расстояние и т.д.
        // Но обычно super.canStart() уже учитывает базовые параметры:
        return super.canStart();
    }

    @Override
    public boolean shouldContinue() {
        // По умолчанию ActiveTargetGoal сбрасывает цель,
        // если пропала видимость или цель умерла/слишком далеко.
        // Мы хотим игнорировать видимость, значит relyOn our "ignoreLineOfSight = true".
        // Но если цель ушла слишком далеко/умерла, всё равно сбросим.
        return super.shouldContinue();
    }
}
