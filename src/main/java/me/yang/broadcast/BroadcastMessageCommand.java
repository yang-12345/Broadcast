package me.yang.broadcast;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.ItemSlotArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;

import static me.yang.broadcast.BroadcastCommand.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class BroadcastMessageCommand {
    private static final SimpleCommandExceptionType FAILED_ITEMLESS_FEEDBACK = new SimpleCommandExceptionType(new TranslatableText("commands.broadcast.failed.itemless"));
    private static final SimpleCommandExceptionType FAILED_ITEMLESS_HOLDING_FEEDBACK = new SimpleCommandExceptionType(new TranslatableText("commands.broadcast.failed.itemless.holding"));
    private static final DynamicCommandExceptionType FAILED_NO_SUCH_SLOT_FEEDBACK = new DynamicCommandExceptionType(slot -> new TranslatableText("commands.broadcast.failed.no_such_slot", slot));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralCommandNode<ServerCommandSource> literalCommandNode = dispatcher.register(literal("broadcastmsg")
                .then(argument("targets", EntityArgumentType.players())
                        .then(literal("coord").executes(context -> executeCoord(context.getSource(), EntityArgumentType.getPlayers(context, "targets"), context.getSource().getPosition(), context.getSource().getWorld(), false))
                                .then(argument("location", Vec3ArgumentType.vec3(false)).executes(context -> executeCoord(context.getSource(), EntityArgumentType.getPlayers(context, "targets"), Vec3ArgumentType.getVec3(context, "location"), context.getSource().getWorld(), false))
                                        .then(argument("dimension", DimensionArgumentType.dimension()).executes(context -> executeCoord(context.getSource(), EntityArgumentType.getPlayers(context, "targets"), Vec3ArgumentType.getVec3(context, "location"), DimensionArgumentType.getDimensionArgument(context, "dimension"), false))
                                                .then(argument("scaled", BoolArgumentType.bool()).executes(context -> executeCoord(context.getSource(), EntityArgumentType.getPlayers(context, "targets"), Vec3ArgumentType.getVec3(context, "location"), DimensionArgumentType.getDimensionArgument(context, "dimension"), BoolArgumentType.getBool(context, "scaled")))
                                                )
                                        )
                                )
                        )
                        .then(literal("item").executes(context -> executeItem(context.getSource(), EntityArgumentType.getPlayers(context, "targets"), 98))
                                .then(argument("slot", ItemSlotArgumentType.itemSlot()).executes(context -> executeItem(context.getSource(), EntityArgumentType.getPlayers(context, "targets"), ItemSlotArgumentType.getItemSlot(context, "slot")))
                                )
                        )
                        .then(literal("status").executes(context -> executeStatus(context.getSource(), EntityArgumentType.getPlayers(context, "targets")))
                        )
                )
        );
        dispatcher.register(literal("brcmsg")
                .redirect(literalCommandNode)
        );
    }

    private static int executeCoord(ServerCommandSource source, Collection<ServerPlayerEntity> targets, Vec3d pos, World dimension, boolean scaled) {
        Text text = getCoordText(source.getWorld(), pos, dimension, scaled);

        return sendMessage(source, targets, text);
    }

    private static int executeItem(ServerCommandSource source, Collection<ServerPlayerEntity> targets, int slot) throws CommandSyntaxException {
        Entity entity = source.getEntityOrThrow();
        StackReference stackReference = entity.getStackReference(slot);
        if (stackReference == StackReference.EMPTY) {
            throw FAILED_NO_SUCH_SLOT_FEEDBACK.create(slot);
        }
        ItemStack item = stackReference.get();
        if (item.isEmpty()) {
            throw slot == 98 ? FAILED_ITEMLESS_HOLDING_FEEDBACK.create() : FAILED_ITEMLESS_FEEDBACK.create();
        }
        Text text = getItemText(item);

        return sendMessage(source, targets, text);
    }

    private static int executeStatus(ServerCommandSource source, Collection<ServerPlayerEntity> targets) throws CommandSyntaxException {
        Entity entity = source.getEntity();
        if (!(entity instanceof LivingEntity livingEntity))
            throw ServerCommandSource.REQUIRES_PLAYER_EXCEPTION.create();
        Text text = getStatusText(livingEntity);

        return sendMessage(source, targets, text);
    }

    private static int sendMessage(ServerCommandSource source, Collection<ServerPlayerEntity> targets, Text message) {
        UUID uUID = source.getEntity() == null ? Util.NIL_UUID : source.getEntity().getUuid();
        Entity entity = source.getEntity();
        Consumer<Text> consumer;
        if (entity instanceof ServerPlayerEntity serverPlayerEntity) {
            consumer = (playerName) -> serverPlayerEntity.sendSystemMessage((new TranslatableText("commands.message.display.outgoing", playerName, message)).formatted(Formatting.GRAY, Formatting.ITALIC), serverPlayerEntity.getUuid());
        } else {
            consumer = (playerName) -> source.sendFeedback(new TranslatableText("commands.message.display.outgoing", playerName, message).formatted(Formatting.GRAY, Formatting.ITALIC), false);
        }

        for (ServerPlayerEntity serverPlayerEntity2 : targets) {
            consumer.accept(serverPlayerEntity2.getDisplayName());
            serverPlayerEntity2.sendSystemMessage(new TranslatableText("commands.message.display.incoming", source.getDisplayName(), message).formatted(Formatting.GRAY, Formatting.ITALIC), uUID);
        }

        return targets.size();
    }
}
