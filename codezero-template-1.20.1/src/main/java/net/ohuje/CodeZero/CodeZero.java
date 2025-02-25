package net.ohuje.CodeZero;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BedBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.ohuje.CodeZero.ai.HordeManager;
import net.ohuje.CodeZero.commands.JudgmentNightCommand;
import net.ohuje.CodeZero.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.util.ActionResult;


public class CodeZero implements ModInitializer {
	public static final String MOD_ID = "codezero";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static int tickCounter = 0; // Счётчик тиков
	private static final int CHECK_INTERVAL = 20; // Проверяем каждые 20 тиков (1 секунда)

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			// Регистрируем нашу команду
			JudgmentNightCommand.register(dispatcher);
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			// Срабатывает, когда игрок пытается кликнуть (ПКМ) по блоку
			if (world.isClient()) {
				return ActionResult.PASS;
			}

			var blockPos = hitResult.getBlockPos();
			var state = world.getBlockState(blockPos);
			if (state.getBlock() instanceof BedBlock) {
				// Это кровать
				if (HordeManager.isJudgmentNight()) {
					// Судная Ночь => запрещаем спать
					player.sendMessage(Text.literal("Вы слышите свою кровь, она течёт по венам, уснуть не получается."), true);
					return ActionResult.FAIL;
				}
			}

			return ActionResult.PASS;
		});


		LOGGER.info("CodeZero mod has been initialized!");
		ConfigManager.loadConfig();

		// Регистрируем событие, вызывающееся при тике конкретного мира
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (world.getRegistryKey().equals(World.OVERWORLD)) {
				tickCounter++;
				if (tickCounter >= CHECK_INTERVAL) {
					HordeManager.onDayChange((ServerWorld) world);
					tickCounter = 0; // Сбрасываем счётчик
				}
			}
		});

		// (Не обязательно, но если хотите) подписываемся на загрузку мира
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("Server has started, initializing states...");

			ServerWorld overworld = server.getOverworld();
			if (overworld != null) {
				// Просто для примера: читаем NightDataState,
				// чтобы заодно залогировать текущее состояние счётчиков
				var state = net.ohuje.CodeZero.ai.NightDataState.get(overworld);
				LOGGER.info("[CodeZero] NightDataState loaded: nightCounter={}, lastJudgmentNight={}",
						state.nightCounter, state.lastJudgmentNight);
			}
		});
		}
	}
