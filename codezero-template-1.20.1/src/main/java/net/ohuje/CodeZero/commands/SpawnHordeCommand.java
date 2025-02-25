package net.ohuje.CodeZero.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.ohuje.CodeZero.ai.HordeSpawner;
import net.ohuje.CodeZero.config.ConfigManager;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class SpawnHordeCommand {

    private static final Random RANDOM = new Random();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("spawnhorde")
                .requires(source -> source.hasPermissionLevel(2))
                //.executes(context -> spawnHorde(context.getSource(), ConfigManager.HORDE_GROUP_SIZE))
                .then(CommandManager.argument("groupSize", IntegerArgumentType.integer(1))
                        .executes(context -> spawnHorde(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "groupSize")
                        ))
                )
        );
    }

    private static int spawnHorde(ServerCommandSource source, int groupSize) {
        ServerWorld world = source.getWorld();

        if (!world.getRegistryKey().equals(ServerWorld.OVERWORLD)) {
            source.sendError(Text.literal("Hordes can only be spawned in the Overworld!"));
            return 0;
        }

        if (source.getEntity() != null && source.getEntity().isPlayer()) {
            // Генерируем случайное число мобов, если groupSize == 0
            int finalGroupSize = groupSize > 0 ? groupSize : ThreadLocalRandom.current().nextInt(
                    ConfigManager.HORDE_WAVE_MIN_MOBS,
                    ConfigManager.HORDE_WAVE_MAX_MOBS + 1
            );

            double hordeSpawnAngle = RANDOM.nextDouble() * 2 * Math.PI; // Генерация случайного угла

            HordeSpawner.spawnHorde(
                    world,
                    source.getPlayer(),
                    finalGroupSize,
                    hordeSpawnAngle // Передаём угол
            );

            source.sendFeedback(() -> Text.literal("Horde spawned with " + finalGroupSize + " mobs!"), true);
        } else {
            source.sendError(Text.literal("This command must be executed by a player!"));
        }

        return 1;
    }

}
