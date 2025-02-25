package net.ohuje.CodeZero.extensions;

import net.minecraft.util.math.Vec3d;

public interface ILastKnownPos {
    Vec3d getLastKnownPos();
    void setLastKnownPos(Vec3d pos);
}
