package net.ohuje.CodeZero.ai;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.ohuje.CodeZero.config.ConfigManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * HordeManager:
 * 1) Сохраняет счётчик ночей в NightDataState (dat-файл),
 * 2) Управляет "судной ночью" (Blood Moon),
 * 3) Останавливает ванильную музыку и воспроизводит "judgment_night.ogg" (5+ минут),
 * 4) Рассылает клиентам пакет, чтобы включить красный оверлей.
 */
public class HordeManager {
    // ---------- Пакет для "красной ночи" -----------
    public static final Identifier JUDGMENT_NIGHT_PACKET_ID = new Identifier("codezero", "judgment_night");

    // Звуки (должны быть прописаны в sounds.json без "type" и "repeat")
    private static final SoundEvent WARNING_SOUND =
            SoundEvent.of(new Identifier("codezero", "warning_message"));

    private static final SoundEvent JUDGMENT_SOUND =
            SoundEvent.of(new Identifier("codezero", "judgment_night"));

    // Флаг: идёт ли сейчас судная ночь
    private static boolean isJudgmentNight = false;

    // Для предупреждений
    private static boolean warningSent = false;
    private static boolean secondWarningSent = false;

    // Случайный интервал между судными ночами
    private static int nextJudgmentNightInterval = ThreadLocalRandom.current()
            .nextInt(ConfigManager.JUDGMENT_NIGHT_MIN, ConfigManager.JUDGMENT_NIGHT_MAX + 1);

    // Планирование волн
    private static long[] waveTimes;
    private static boolean wavesScheduled = false;
    private static int currentWaveIndex = 0;
    private static double hordeSpawnAngle;

    // Игроки
    private static int musicStopTimer = 0; // счётчик для сброса музыки
    private static final Set<ServerPlayerEntity> ACTIVE_PLAYERS = new HashSet<>();

    // Планировщик для отложенных задач (второе предупреждение и т.п.)
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();


    // Счётчики мобов (просто для примера)
    private static int currentHostileMobCount = 0;
    private static int naturalHostileMobCount = 0;

    /**
     * Вызывается каждый игровой "день" (или раз в N тиков) для проверки времени, волн и т.д.
     */
    public static void onDayChange(ServerWorld world) {

        NightDataState state = NightDataState.get(world); // (dat-файл)
        long currentTime = world.getTimeOfDay() % 24000;

        // 1) Утреннее предупреждение
        if (currentTime >= 6000 && currentTime < 13000 && !warningSent) {
            checkForUpcomingJudgmentNight(world, state);
        }
        // 2) Второе предупреждение
        if (warningSent && !secondWarningSent && currentTime >= 6100 && currentTime < 13000) {
            sendSecondWarning(world, state);
        }

        // 3) Ночью (13000..23000) планируем волны
        if (currentTime >= 13000 && currentTime <= 23000) {
            if (!wavesScheduled) {
                incrementNightCounter(world, state); // +1 ночь, проверяем судную ночь
                if (isJudgmentNight) {
                    //System.out.println("[CodeZero] Наступила судная ночь!");
                    sendJudgmentNightWarning(world);
                    startJudgmentNightMusic(world); // Останавливаем ванильную музыку, запускаем наш трек
                }
                scheduleNightWaves(currentTime);
                wavesScheduled = true;
                hordeSpawnAngle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
                //System.out.println("[CodeZero] Орда придёт с угла: " + Math.toDegrees(hordeSpawnAngle));
            }

            // Запуск волн при достижении waveTimes[i]
            while (currentWaveIndex < waveTimes.length && currentTime >= waveTimes[currentWaveIndex]) {
                System.out.println("[CodeZero] Запускаем волну №" + (currentWaveIndex + 1));
                spawnWave(world);
                currentWaveIndex++;
            }
        } else {
            // День/другое время - сбрасываем волн
            resetWaves();
        }

        // 4) Завершение судной ночи после 23000
        if (currentTime > 23000 && isJudgmentNight) {
            endJudgmentNight(world, state);
        }
        if (isJudgmentNight) {
            // Каждые 400 тиков (примерно 20 секунд)
            musicStopTimer += 20; // раз "onDayChange" вызывается каждые 20 тиков
            if (musicStopTimer >= 300) {
                stopVanillaMusic(world);
                musicStopTimer = 0;
            }
        } else {
            // Если судная ночь закончилась — сбрасываем счётчик
            musicStopTimer = 0;
        }
    }

    private static void stopVanillaMusic(ServerWorld world) {
        // Пакет: остановить "все" треки в категории MUSIC
        StopSoundS2CPacket stopMusic = new StopSoundS2CPacket(null, SoundCategory.MUSIC);
        for (ServerPlayerEntity player : world.getPlayers()) {
            player.networkHandler.sendPacket(stopMusic);
        }
        // System.out.println("[CodeZero] Снова остановили ванильную музыку...");
    }

    // --------------------------------------------------------------------
    // Увеличиваем счётчик ночей, проверяем судную ночь
    // --------------------------------------------------------------------
    private static void incrementNightCounter(ServerWorld world, NightDataState state) {
        state.nightCounter++;
        boolean canHaveJudgmentNight = (state.nightCounter - state.lastJudgmentNight) >= ConfigManager.JUDGMENT_NIGHT_MIN;

        isJudgmentNight = canHaveJudgmentNight && (state.nightCounter % nextJudgmentNightInterval == 0);
        if (isJudgmentNight) {
            state.lastJudgmentNight = state.nightCounter;
            nextJudgmentNightInterval = ThreadLocalRandom.current()
                    .nextInt(ConfigManager.JUDGMENT_NIGHT_MIN, ConfigManager.JUDGMENT_NIGHT_MAX + 1);

            // Говорим клиентам: "включить красный фильтр"
            broadcastJudgmentNightState(world, true);

            //System.out.println("[CodeZero] Ночь #" + state.nightCounter + " стала судной");
        } else {
            //System.out.println("[CodeZero] Обычная ночь #" + state.nightCounter);
        }
        state.markDirty();
    }

    private static void endJudgmentNight(ServerWorld world, NightDataState state) {
        isJudgmentNight = false;
        warningSent = false;
        secondWarningSent = false;

        // Отключаем красный фильтр
        broadcastJudgmentNightState(world, false);

        // Останавливаем трек "judgment_night"
        stopJudgmentNightMusic(world);

        state.markDirty();
        System.out.println("[CodeZero] Судная ночь окончена.");
    }

    // --------------------------------------------------------------------
    // Предупреждения
    // --------------------------------------------------------------------
    private static void checkForUpcomingJudgmentNight(ServerWorld world, NightDataState state) {
        if (isNextNightJudgment(state) && !warningSent) {
            world.getPlayers().forEach(player -> {
                player.sendMessage(
                        Text.literal("Сегодня тени слишком неподвижны. Они ждут заката...")
                                .setStyle(Style.EMPTY.withColor(Formatting.DARK_RED)),
                        false
                );
            });
            warningSent = true;
        }
    }

    private static boolean isNextNightJudgment(NightDataState state) {
        return ((state.nightCounter + 1) % nextJudgmentNightInterval == 0);
    }

    private static void sendSecondWarning(ServerWorld world, NightDataState state) {
        if (isNextNightJudgment(state) && !secondWarningSent) {
            world.getPlayers().forEach(player -> {
                player.sendMessage(
                        Text.literal("Вы больше не можете прятаться")
                                .setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY)),
                        false
                );
                // Останавливаем ванильную музыку
                StopSoundS2CPacket stopMusic = new StopSoundS2CPacket(null, SoundCategory.MUSIC);
                player.networkHandler.sendPacket(stopMusic);

                // Короткий "warning_message.ogg"
                player.playSound(WARNING_SOUND, SoundCategory.MASTER, 1.0F, 1.0F);
            });

            // Через 20 секунд возвращаем (какую-нибудь) музыку
            scheduler.schedule(() -> world.getServer().execute(() -> {
                world.getPlayers().forEach(player -> {
                    player.playSound(SoundEvents.MUSIC_CREATIVE.value(), SoundCategory.MUSIC, 1.0F, 1.0F);
                });
                //System.out.println("[CodeZero] Через 20с после второго предупреждения вернули creative-музыку.");
            }), 20, TimeUnit.SECONDS);

            secondWarningSent = true;
        }
    }

    private static void sendJudgmentNightWarning(ServerWorld world) {
        world.getPlayers().forEach(player ->
                player.sendMessage(Text.of("Вы не слышите их шагов… но они уже здесь."), false)
        );
    }

    // --------------------------------------------------------------------
    // Музыка судной ночи
    // --------------------------------------------------------------------
    private static void startJudgmentNightMusic(ServerWorld world) {
        StopSoundS2CPacket stopMusic = new StopSoundS2CPacket(null, SoundCategory.AMBIENT);

        for (ServerPlayerEntity player : world.getPlayers()) {
            // Останавливаем ванильную музыку
            player.networkHandler.sendPacket(stopMusic);

            // Запускаем "judgment_night" (5+ минут)
            player.playSound(JUDGMENT_SOUND, SoundCategory.AMBIENT, 2.0F, 1.0F);
        }
        //System.out.println("[CodeZero] Остановили музыку, включили judgment_night.");
    }

    private static void stopJudgmentNightMusic(ServerWorld world) {
        StopSoundS2CPacket stopJudgmentMusic =
                new StopSoundS2CPacket(new Identifier("codezero", "judgment_night"), SoundCategory.AMBIENT);

        for (ServerPlayerEntity player : world.getPlayers()) {
            player.networkHandler.sendPacket(stopJudgmentMusic);
        }
        //System.out.println("[CodeZero] Остановили judgment_night, ванильная музыка вернётся сама.");
    }

    // --------------------------------------------------------------------
    // Волны мобов
    // --------------------------------------------------------------------
    private static void scheduleNightWaves(long currentTime) {
        int nightStart = 13000;
        int nightEnd = 23000;

        int waveCount = isJudgmentNight ? ConfigManager.JUDGMENT_WAVE_COUNT : ConfigManager.HORDE_WAVE_COUNT;
        int totalNightDuration = nightEnd - nightStart;
        int minInterval = Math.max(100, totalNightDuration / (waveCount * 2));
        int maxInterval = Math.max(200, totalNightDuration / waveCount);

        waveTimes = new long[waveCount];
        waveTimes[0] = currentTime + ThreadLocalRandom.current().nextInt(minInterval, maxInterval);

        for (int i = 1; i < waveCount; i++) {
            waveTimes[i] = waveTimes[i - 1] + ThreadLocalRandom.current().nextInt(minInterval, maxInterval);
            if (waveTimes[i] > nightEnd) {
                waveTimes[i] = nightEnd;
            }
        }
        System.out.println("[CodeZero] Запланированы волны: " + Arrays.toString(waveTimes));
    }

    private static void resetWaves() {
        wavesScheduled = false;
        currentWaveIndex = 0;
    }

    private static void spawnWave(ServerWorld world) {
        ACTIVE_PLAYERS.clear();
        ACTIVE_PLAYERS.addAll(world.getPlayers());

        for (ServerPlayerEntity player : ACTIVE_PLAYERS) {
            double waveSpawnAngle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            int randomGroupSize = isJudgmentNight
                    ? ThreadLocalRandom.current().nextInt(ConfigManager.JUDGMENT_WAVE_MIN_MOBS, ConfigManager.JUDGMENT_WAVE_MAX_MOBS + 1)
                    : ThreadLocalRandom.current().nextInt(ConfigManager.HORDE_WAVE_MIN_MOBS, ConfigManager.HORDE_WAVE_MAX_MOBS + 1);

            // Ваш способ спавна
            HordeSpawner.spawnHorde(world, player, randomGroupSize, waveSpawnAngle);

            //System.out.println("[CodeZero] Волна заспавнена! " + randomGroupSize + " мобов.");
        }
    }
    public static void forceStopJudgmentNight(ServerWorld world) {
        // Принудительно сбрасываем флаги
        isJudgmentNight = false;
        warningSent = false;
        secondWarningSent = false;

        // Останавливаем trек "judgment_night" (см. ваш код)
        stopJudgmentNightMusic(world);

        // Рассылаем пакет игрокам, чтобы убрать красный фильтр
        broadcastJudgmentNightState(world, false);

        System.out.println("[CodeZero] Судная ночь принудительно остановлена (forceStop).");
    }


    // --------------------------------------------------------------------
    // Пакет для красного оверлея
    // --------------------------------------------------------------------
    private static void broadcastJudgmentNightState(ServerWorld world, boolean active) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active);

        for (ServerPlayerEntity player : world.getPlayers()) {
            ServerPlayNetworking.send(player, JUDGMENT_NIGHT_PACKET_ID, buf);
        }
    }

    // --------------------------------------------------------------------
    // Методы для счётчиков мобов (пример)
    // --------------------------------------------------------------------
    public static int getCurrentHostileMobCount() {
        return currentHostileMobCount;
    }

    public static void incrementHostileMobCount() {
        currentHostileMobCount++;
    }

    public static void decrementHostileMobCount() {
        if (currentHostileMobCount > 0) {
            currentHostileMobCount--;
        }
    }

    public static int getNaturalHostileMobCount() {
        return naturalHostileMobCount;
    }

    public static void incrementNaturalHostileMobCount() {
        naturalHostileMobCount++;
    }

    public static void decrementNaturalHostileMobCount() {
        if (naturalHostileMobCount > 0) {
            naturalHostileMobCount--;
        }
    }

    public static boolean isJudgmentNight() {
        return isJudgmentNight;
    }
}
