package net.ohuje.CodeZero.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.ohuje.CodeZero.extensions.ILastKnownPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(HostileEntity.class)
public abstract class HostileEntityLkpMixin implements ILastKnownPos {

    @Unique
    private Vec3d theirLKP = null;

    @Override
    public Vec3d getLastKnownPos() {
        return theirLKP;
    }

    @Override
    public void setLastKnownPos(Vec3d pos) {
        this.theirLKP = pos;
    }

    // Конструктор
    // (Инъекция, если нужно, но обычно mixin требует соответствовать суперклассу)
}
