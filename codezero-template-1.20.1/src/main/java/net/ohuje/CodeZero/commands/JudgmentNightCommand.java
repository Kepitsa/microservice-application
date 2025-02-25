package net.ohuje.CodeZero.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.ohuje.CodeZero.ai.HordeManager;
import net.ohuje.CodeZero.ai.NightDataState;

import static net.minecraft.server.command.CommandManager.literal;

public class JudgmentNightCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("judgmentnight")
                        .requires(source -> source.hasPermissionLevel(2)) // только операторы
                        .then(
                                literal("reset")
                                        .executes(ctx -> {
                                            ServerCommandSource source = ctx.getSource();
                                            ServerWorld overworld = source.getServer().getOverworld();

                                            // Ваш код сброса
                                            NightDataState state = NightDataState.get(overworld);
                                            state.nightCounter = 0;
                                            state.lastJudgmentNight = 0;
                                            state.markDirty();

                                            // Принудительно завершаем судную ночь
                                            HordeManager.forceStopJudgmentNight(overworld);

                                            // Отправляем сообщение исполнителю
                                            // Вариант A: через Supplier<Text>
                                            source.sendFeedback(
                                                    () -> Text.literal("Счётчики судной ночи сброшены."),
                                                    true
                                            );

                                            // Или Вариант B: sendMessage (не попадает в лог)
                                            // source.sendMessage(Text.literal("Счётчики судной ночи сброшены."));

                                            return Command.SINGLE_SUCCESS;
                                        })
                        )
        );

    }
}
