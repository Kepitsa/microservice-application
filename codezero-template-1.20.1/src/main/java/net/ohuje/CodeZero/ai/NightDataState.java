package net.ohuje.CodeZero.ai;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;
import net.minecraft.server.world.ServerWorld;

public class NightDataState extends PersistentState {
    public int nightCounter = 0;
    public int lastJudgmentNight = 0;

    public static NightDataState fromNbt(NbtCompound nbt) {
        NightDataState state = new NightDataState();
        state.nightCounter = nbt.getInt("nightCounter");
        state.lastJudgmentNight = nbt.getInt("lastJudgmentNight");
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putInt("nightCounter", this.nightCounter);
        nbt.putInt("lastJudgmentNight", this.lastJudgmentNight);
        return nbt;
    }

    public static NightDataState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                NightDataState::fromNbt,
                NightDataState::new,
                "codezero_night_data"
        );
    }
}
