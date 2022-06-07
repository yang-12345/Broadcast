package me.yang.broadcast;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;

public class Broadcast implements ModInitializer {
    public static final String MODID = "broadcast";

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> BroadcastCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> BroadcastMessageCommand.register(dispatcher));
    }
}
