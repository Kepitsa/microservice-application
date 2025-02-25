package net.ohuje.CodeZero.ai;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.ohuje.CodeZero.config.ConfigManager;

import java.util.*;

public class HordeSpawner {

    private static final Random RANDOM = new Random();
    private static final int MIN_DISTANCE_BETWEEN_MOBS = 5;

    public static void spawnHorde(ServerWorld world, ServerPlayerEntity player, int groupSize, double angle) {
        BlockPos playerPos = player.getBlockPos();
        int surfaceSpawnCount = groupSize;
        int caveSpawnCount = 0;

        // Определяем, находится ли игрок под землёй
        int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, playerPos.getX(), playerPos.getZ());
        boolean isPlayerInCave = playerPos.getY() < surfaceY - 5;

        if (isPlayerInCave) {
            // Делим волну на две группы: часть на поверхности, часть в пещере
            surfaceSpawnCount = groupSize / 2;
            caveSpawnCount = groupSize - surfaceSpawnCount;
        }

        // Спавн мобов на поверхности
        if (surfaceSpawnCount > 0) {
            spawnGroup(world, player, surfaceSpawnCount,
                    ConfigManager.HORDE_SPAWN_DISTANCE_MIN,
                    ConfigManager.HORDE_SPAWN_DISTANCE_MAX,
                    angle, true);
        }

        // Спавн мобов в пещере
        if (caveSpawnCount > 0) {
            spawnGroup(world, player, caveSpawnCount,
                    ConfigManager.HORDE_CAVE_SPAWN_DISTANCE_MIN,
                    ConfigManager.HORDE_CAVE_SPAWN_DISTANCE_MAX,
                    angle, false);
        }
    }

    private static void spawnGroup(ServerWorld world, ServerPlayerEntity player,
                                   int groupSize, int minDistance, int maxDistance,
                                   double baseAngle, boolean isSurface) {
        BlockPos playerPos = player.getBlockPos();
        Set<BlockPos> usedPositions = new HashSet<>();

        // Флаг, чтобы минимум один зомби был в группе
        boolean hasSpawnedZombie = false;

        for (int i = 0; i < groupSize; i++) {
            BlockPos validSpawnPos = null;
            int attempts = 0;

            // Ищем подходящую позицию
            while (attempts < (isSurface ? 20 : 50)) {
                double angle = baseAngle + (RANDOM.nextDouble() - 0.5) * Math.toRadians(30); // +/- 30 градусов
                int distance = RANDOM.nextInt(maxDistance - minDistance + 1) + minDistance;

                // Дополнительное случайное смещение
                int offsetX = RANDOM.nextInt(5) - 2;
                int offsetZ = RANDOM.nextInt(5) - 2;

                int x = (int) (playerPos.getX() + distance * Math.cos(angle)) + offsetX;
                int z = (int) (playerPos.getZ() + distance * Math.sin(angle)) + offsetZ;
                int y;

                if (isSurface) {
                    // На поверхности
                    y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                } else {
                    // В пещере: ± 5 блоков по вертикали от уровня игрока
                    y = playerPos.getY() + RANDOM.nextInt(11) - 5;
                }

                BlockPos potentialPos = new BlockPos(x, y, z);

                // Проверяем освещение и воду
                BlockState blockBelow = world.getBlockState(potentialPos.down());
                boolean isDarkEnough = world.getLightLevel(potentialPos) <= 8;
                if (blockBelow.getBlock() == Blocks.WATER || !isDarkEnough) {
                    attempts++;
                    continue;
                }

                // Проверяем, не слишком ли близко к другим мобам
                if (usedPositions.stream().noneMatch(pos -> pos.isWithinDistance(potentialPos, MIN_DISTANCE_BETWEEN_MOBS))
                        && world.isAir(potentialPos) && world.isAir(potentialPos.up())) {
                    validSpawnPos = potentialPos;
                    usedPositions.add(validSpawnPos);
                    break;
                }
                attempts++;
            }

            if (validSpawnPos != null) {
                // Определяем тип моба. Гарантируем хотя бы одного зомби.
                EntityType<? extends HostileEntity> mobType;

                // Если это последний моб в группе, а зомби ещё не было,
                // то обязательно спавним зомби
                if (!hasSpawnedZombie && i == groupSize - 1) {
                    mobType = EntityType.ZOMBIE;
                } else {
                    mobType = pickWaveMobType();
                }

                // Если выбрали зомби, отмечаем флаг
                if (mobType == EntityType.ZOMBIE) {
                    hasSpawnedZombie = true;
                }

                // Пытаемся создать моба
                HostileEntity waveMob = mobType.create(world);
                if (waveMob != null) {
                    // Позиция и поворот
                    waveMob.refreshPositionAndAngles(validSpawnPos,
                            RANDOM.nextFloat() * 360.0F, 0.0F);
                    waveMob.setPersistent();
                    // 1) (Опционально) Включить свечение для отладки:
                    // waveMob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 999999, 0));

                    // 2) Увеличиваем скорость бега
                    //    Например, базовое +20%, иногда +40%
                    double speedBonus = 0.4; // +20% к скорости
                    if (RANDOM.nextFloat() < 0.3f) {
                        speedBonus = 0.6; // 30% шанс получить +40%
                    }
                    addAttributeModifier(waveMob, EntityAttributes.GENERIC_MOVEMENT_SPEED,
                            speedBonus, "WaveSpeedBoost");

                    // 3) Усиление атаки
                    //    70% шанс: +2.0 к урону
                    if (RANDOM.nextFloat() < 0.4f) {
                        addAttributeModifier(waveMob, EntityAttributes.GENERIC_ATTACK_DAMAGE,
                                2.0, "WaveDamageBoost");
                    }
                    // Агрим на игрока
                    waveMob.setTarget(player);

                    // Помечаем моба как "waveMob", чтобы отличить от естественных
                    NbtCompound nbtData = new NbtCompound();
                    waveMob.writeNbt(nbtData);
                    nbtData.putBoolean("waveMob", true);
                    waveMob.readNbt(nbtData);

                    // Спавним
                    world.spawnEntity(waveMob);

                    // Увеличиваем счётчик
                    HordeManager.incrementHostileMobCount();
                }
            }
        }
    }

    /**
     * Возвращает случайный тип моба для волны.
     * Учитываем 4 варианта: Зомби, Скелет, Крипер, Паук.
     * Но основной выбор — случайный,
     * т.к. гарантированно хотя бы один зомби мы обеспечиваем через hasSpawnedZombie.
     */
    private static EntityType<? extends HostileEntity> pickWaveMobType() {
        float rand = RANDOM.nextFloat();
        if (rand < 0.25f) {
            return EntityType.ZOMBIE;
        } else if (rand < 0.50f) {
            return EntityType.SKELETON;
        } else if (rand < 0.75f) {
            return EntityType.CREEPER;
        } else {
            return EntityType.SPIDER;
        }
    }

    /**
     * Утилитный метод: добавляет постоянный модификатор атрибута
     * (например, скорость, урон, здоровье).
     */
    private static void addAttributeModifier(HostileEntity mob,
                                             net.minecraft.entity.attribute.EntityAttribute attribute,
                                             double amount,
                                             String name) {
        // Используем MULTIPLY_TOTAL для скорости,
        // но для урона/здоровья можно ADDITION.
        // Ниже — логика:
        //  - Если attribute==MOVEMENT_SPEED => MULTIPLY_TOTAL (увеличивает на +%)
        //  - Иначе => ADDITION (прибавляем абсолютное значение)

        var instance = mob.getAttributeInstance(attribute);
        if (instance == null) return; // бывает, если нет такого атрибута у сущности

        // Ждём уникальный UUID, чтобы не дублировать
        UUID modifierId = UUID.nameUUIDFromBytes((name + attribute.getTranslationKey()).getBytes());

        // Удаляем старый, если есть
        var old = instance.getModifier(modifierId);
        if (old != null) {
            instance.removeModifier(old);
        }

        // Выбираем тип операции
        EntityAttributeModifier.Operation op =
                (attribute == EntityAttributes.GENERIC_MOVEMENT_SPEED)
                        ? EntityAttributeModifier.Operation.MULTIPLY_TOTAL // +20% и т.д.
                        : EntityAttributeModifier.Operation.ADDITION;      // +2.0 и т.д.

        var modifier = new EntityAttributeModifier(modifierId, name, amount, op);
        instance.addPersistentModifier(modifier);
    }
}
