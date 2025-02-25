package net.ohuje.CodeZero.ai;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.ohuje.CodeZero.extensions.ILastKnownPos;

import java.util.EnumSet;

public class GoToLKPGoal extends Goal {
    private final HostileEntity mob;
    private Path pathToLkp = null;

    public GoToLKPGoal(HostileEntity mob) {
        this.mob = mob;
        // Управляем движением / прыжками
        this.setControls(EnumSet.of(Control.MOVE, Control.JUMP));
    }

    @Override
    public boolean canStart() {
        // Если моб видит реального игрока, не начинаем
        if (mob.getTarget() instanceof PlayerEntity) {
            return false;
        }
        // Проверяем, есть ли у нас LKP
        if (!(mob instanceof ILastKnownPos lkpMob)) {
            return false;
        }
        Vec3d lkp = lkpMob.getLastKnownPos();
        return lkp != null;
    }

    @Override
    public boolean shouldContinue() {
        // Пока есть LKP и нет реального игрока
        if (mob.getTarget() instanceof PlayerEntity) {
            return false;
        }
        if (!(mob instanceof ILastKnownPos lkpMob)) {
            return false;
        }
        return lkpMob.getLastKnownPos() != null;
    }

    @Override
    public void start() {
        if (!(mob instanceof ILastKnownPos lkpMob)) {
            return;
        }
        Vec3d lkp = lkpMob.getLastKnownPos();
        if (lkp != null) {
            // Строим путь
            // Проверка, correct import:
            // import net.minecraft.entity.ai.pathing.Path;
            pathToLkp = mob.getNavigation().findPathTo(lkp.x, lkp.y, lkp.z, 1);
            if (pathToLkp != null) {
                mob.getNavigation().startMovingAlong(pathToLkp, 1.0);
            }
        }
    }

    @Override
    public void stop() {
        if (mob.getNavigation() != null) {
            mob.getNavigation().stop();
        }
        pathToLkp = null;
    }

    @Override
    public void tick() {
        if (!(mob instanceof ILastKnownPos lkpMob)) {
            return;
        }
        Vec3d lkp = lkpMob.getLastKnownPos();
        if (lkp == null) {
            return;
        }
        // Если в процессе увидели реального игрока
        if (mob.getTarget() instanceof PlayerEntity) {
            return;
        }

        // Дистанция до LKP
        double distSq = mob.squaredDistanceTo(lkp.x, lkp.y, lkp.z);
        if (distSq < 4.0) {
            // Дошли, сбрасываем
            lkpMob.setLastKnownPos(null);
            return;
        }

        // Если навигация окончена (isIdle) или путь null,
        // можно опять пробовать findPathTo
        if (pathToLkp == null || mob.getNavigation().isIdle()) {
            pathToLkp = mob.getNavigation().findPathTo(lkp.x, lkp.y, lkp.z, 1);
            if (pathToLkp != null) {
                mob.getNavigation().startMovingAlong(pathToLkp, 1.0);
            } else {
                // Здесь можно вызвать "строительство" вручную
                // или оставить как есть.
            }
        }
    }
}
