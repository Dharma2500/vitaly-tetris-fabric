package ru.vitaly_sokolov.tetris;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public final class TetrisClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("tetris").executes(context -> {
                var client = context.getSource().getClient();
                client.execute(() -> client.setScreen(new TetrisScreen()));
                return 1;
            }))
        );
    }
}
