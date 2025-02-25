package net.ohuje.CodeZero.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;


@Environment(EnvType.CLIENT)
public class CodeZeroClient implements ClientModInitializer {
    // Должен совпадать с HordeManager.JUDGMENT_NIGHT_PACKET_ID
    public static final Identifier JUDGMENT_NIGHT_PACKET_ID =
            new Identifier("codezero", "judgment_night");

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // Сбрасываем "кровавую ночь" на клиенте
            BloodMoonRenderer.setBloodMoonActive(false);
        });
        // Регистрируем рендер "кровавой ночи"
        BloodMoonRenderer.init();

        // Регистрируем приёмник пакета
        ClientPlayNetworking.registerGlobalReceiver(JUDGMENT_NIGHT_PACKET_ID, (client, handler, buf, responseSender) -> {
            boolean active = buf.readBoolean();
            client.execute(() -> {
                // Вот тут
                BloodMoonRenderer.setBloodMoonActive(active);
            });

        });

    }

}
