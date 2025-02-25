package net.ohuje.CodeZero.ai;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.ohuje.CodeZero.config.ConfigManager;

import java.util.*;

/**
 * Три фазы:
 *   JUMP -> WAIT_LAND -> PLACE
 * Моб начинает строить, даже если не "видит" игрока.
 */
public class NoLimitBuildGoal extends Goal {

    private static final double MAX_START_DISTANCE = 40.0;
    private static final double CANCEL_BUILD_DISTANCE = 2 * MAX_START_DISTANCE;
    private static final double MIN_HORIZONTAL_APPROACH = 3.0;
    private static final double CLIFF_THRESHOLD = 2.0;
    private static final double MAX_HORIZONTAL_FOR_CLIFF = 8.0;
    private static final double MIN_VERTICAL_DIFF = 3.0;
    private static final int WALL_HEIGHT = 2;

    // Кулдаун между постановками блоков
    private static final int PLACE_COOLDOWN = 15;

    // Машинный автомат
    private enum BuildState {
        JUMP,       // задали velocity, ждём отрыва
        WAIT_LAND,  // ждём, пока моб действительно "слетел" и снова приземлится
        PLACE       // ставим блок
    }

    private final HostileEntity mob;
    private boolean building = false;

    private BuildState state = BuildState.JUMP;
    private final List<Block> blockPool;
    private int blocksLeft;
    private final Random random = new Random();

    private int placeCooldown = 0;

    // Доп. флаг для WAIT_LAND
    private boolean inAir = false;

    public NoLimitBuildGoal(HostileEntity mob) {
        this.mob = mob;
        this.setControls(EnumSet.of(Control.JUMP));

        // Количество блоков (из конфига)
        int minB = ConfigManager.BLOCKS_MIN;
        int maxB = ConfigManager.BLOCKS_MAX;
        if (maxB < minB) {
            maxB = minB;
        }
        this.blocksLeft = minB + random.nextInt(maxB - minB + 1);

        // "Инвентарь" блоков
        this.blockPool = Arrays.asList(
                Blocks.DIRT,
                Blocks.COBBLESTONE,
                Blocks.OAK_PLANKS,
                Blocks.SAND
        );
    }

    @Override
    public boolean canStart() {
        // 1) Чёрный список
        String mobId = Registries.ENTITY_TYPE.getId(mob.getType()).toString();
        if (ConfigManager.MOB_BLACKLIST.contains(mobId)) {
            return false;
        }
        // 2) Пиглины / зомби-пиглины
        if (mob.getType() == EntityType.ZOMBIFIED_PIGLIN || mob.getType() == EntityType.PIGLIN) {
            return false;
        }
        // 3) Есть ли блоки
        if (blocksLeft <= 0) {
            return false;
        }
        // 4) Цель - игрок
        if (!(mob.getTarget() instanceof PlayerEntity player)) {
            return false;
        }

        // 5) (УБРАЛИ) Проверку canSee(player)
        // 6) Дистанция
        double dist = mob.distanceTo(mob.getTarget());
        if (dist > MAX_START_DISTANCE) {
            return false;
        }
        // 7) Моб на земле
        if (!mob.isOnGround()) {
            return false;
        }

        // (УБРАЛИ) проверку пути (path != null && !path.isFinished())

        // 9) Высота
        double diffY = mob.getTarget().getY() - mob.getY();
        boolean isCliff = (diffY >= CLIFF_THRESHOLD);
        boolean isPlayerHigher = (diffY >= MIN_VERTICAL_DIFF);

        // 10) Есть ли "стена" перед мобом
        boolean isWall = checkWallInFront(WALL_HEIGHT);

        // 11) Горизонтальная дистанция
        double dx = mob.getTarget().getX() - mob.getX();
        double dz = mob.getTarget().getZ() - mob.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Если игрок выше на 2+ блока => maxHoriz=8, иначе =3
        double maxHorzAllowed = isCliff ? MAX_HORIZONTAL_FOR_CLIFF : MIN_HORIZONTAL_APPROACH;
        if (horizontalDist > maxHorzAllowed) {
            return false;
        }

        // 12) Если нет "значительной высоты" и нет стены — не строим
        if (!isPlayerHigher && !isWall) {
            return false;
        }

        return true;
    }

    @Override
    public boolean shouldContinue() {
        return building && blocksLeft > 0;
    }

    @Override
    public void start() {
        building = true;
        state = BuildState.JUMP;
        placeCooldown = 0;
        inAir = false;
    }

    @Override
    public void stop() {
        building = false;
    }

    @Override
    public void tick() {
        if (!building) return;
        if (blocksLeft <= 0) {
            building = false;
            return;
        }

        if (!(mob.getTarget() instanceof PlayerEntity player)) {
            building = false;
            return;
        }

        double dist = mob.distanceTo(player);
        if (dist > CANCEL_BUILD_DISTANCE) {
            building = false;
            return;
        }

        // Проверка: "игрок явно выше" или есть стена
        double diffY = player.getY() - mob.getY();
        boolean isWall = checkWallInFront(WALL_HEIGHT);
        if (diffY < MIN_VERTICAL_DIFF && !isWall) {
            building = false;
            return;
        }

        // Кулдаун
        if (placeCooldown > 0) {
            placeCooldown--;
        }

        switch (state) {
            case JUMP -> handleJump();
            case WAIT_LAND -> handleWaitLand();
            case PLACE -> handlePlace();
        }
    }

    private void handleJump() {
        // Если моб на земле, придаём импульс
        if (mob.isOnGround()) {
            mob.setVelocity(mob.getVelocity().x, 0.48, mob.getVelocity().z);
            mob.velocityModified = true;
        }
        // Переходим в WAIT_LAND
        state = BuildState.WAIT_LAND;
        inAir = false;
    }

    private void handleWaitLand() {
        // Ждём, пока реально оторвётся
        if (!inAir && !mob.isOnGround()) {
            inAir = true;
        }
        // Когда снова приземлился -> PLACE
        if (inAir && mob.isOnGround()) {
            state = BuildState.PLACE;
        }
    }

    private void handlePlace() {
        if (placeCooldown > 0) {
            return;
        }

        World world = mob.getWorld();
        BlockPos mobPos = mob.getBlockPos();

        BlockPos placePos = null;
        if (world.isAir(mobPos)) {
            placePos = mobPos;
        } else {
            BlockPos upPos = mobPos.up();
            if (world.isAir(upPos)) {
                placePos = upPos;
            }
        }

        if (placePos == null) {
            building = false;
            return;
        }

        // Ставим блок
        Block blockToPlace = blockPool.get(random.nextInt(blockPool.size()));
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.setBlockState(placePos, blockToPlace.getDefaultState());
        }

        blocksLeft--;
        placeCooldown = PLACE_COOLDOWN;

        // Возврат к JUMP
        state = BuildState.JUMP;
        inAir = false;
    }

    private boolean checkWallInFront(int wallH) {
        Direction facing = mob.getMovementDirection();
        BlockPos basePos = mob.getBlockPos();
        World w = mob.getWorld();
        for (int i = 0; i < wallH; i++) {
            BlockPos checkPos = basePos.offset(facing).up(i);
            if (w.isAir(checkPos)) {
                return false;
            }
        }
        return true;
    }
}
