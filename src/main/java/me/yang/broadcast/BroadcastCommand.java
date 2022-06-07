package me.yang.broadcast;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.ItemSlotArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.ItemStack;
import net.minecraft.network.MessageType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;

import java.math.RoundingMode;
import java.text.DecimalFormat;

import static net.minecraft.server.command.CommandManager.*;

public class BroadcastCommand {
    private static final SimpleCommandExceptionType ITEMLESS_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.broadcast.failed.itemless"));
    private static final SimpleCommandExceptionType ITEMLESS_HOLDING_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.broadcast.failed.itemless.holding"));
    private static final DynamicCommandExceptionType NO_SUCH_SLOT_EXCEPTION = new DynamicCommandExceptionType(slot -> new TranslatableText("commands.broadcast.failed.no_such_slot", slot));
    private static final SimpleCommandExceptionType REQUIRES_LIVING_ENTITY_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("permissions.requires.living_entity"));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralCommandNode<ServerCommandSource> literalCommandNode = dispatcher.register(literal("broadcast")
                .then(literal("coord").executes(context -> executeCoord(context.getSource(), context.getSource().getPosition(), context.getSource().getWorld(), false))
                        .then(argument("location", Vec3ArgumentType.vec3(false)).executes(context -> executeCoord(context.getSource(), Vec3ArgumentType.getVec3(context, "location"), context.getSource().getWorld(), false))
                                .then(argument("dimension", DimensionArgumentType.dimension()).executes(context -> executeCoord(context.getSource(), Vec3ArgumentType.getVec3(context, "location"), DimensionArgumentType.getDimensionArgument(context, "dimension"), false))
                                        .then(argument("scaled", BoolArgumentType.bool()).executes(context -> executeCoord(context.getSource(), Vec3ArgumentType.getVec3(context, "location"), DimensionArgumentType.getDimensionArgument(context, "dimension"), BoolArgumentType.getBool(context, "scaled")))
                                        )
                                )
                        )
                )
                .then(literal("item").executes(context -> executeItem(context.getSource(), 98))
                        .then(argument("slot", ItemSlotArgumentType.itemSlot()).executes(context -> executeItem(context.getSource(), ItemSlotArgumentType.getItemSlot(context, "slot")))
                        )
                )
                .then(literal("status").executes(context -> executeStatus(context.getSource()))
                )
        );
        dispatcher.register(literal("brc")
                .redirect(literalCommandNode)
        );
        dispatcher.register(literal("bhere").executes(context -> executeCoord(context.getSource(), context.getSource().getPosition(), context.getSource().getWorld(), false))
        );
    }

    private static int executeCoord(ServerCommandSource source, Vec3d pos, World dimension, boolean scaled) {
        Entity entity = source.getEntity();
        Text text = new TranslatableText("chat.type.text", source.getDisplayName(), getCoordText(source.getWorld(), pos, dimension, scaled));
        if (entity != null) {
            source.getServer().getPlayerManager().broadcast(text, MessageType.CHAT, entity.getUuid());
        } else {
            source.getServer().getPlayerManager().broadcast(text, MessageType.SYSTEM, Util.NIL_UUID);
        }

        return 1;
    }

    private static int executeItem(ServerCommandSource source, int slot) throws CommandSyntaxException {
        Entity entity = source.getEntityOrThrow();
        StackReference stackReference = entity.getStackReference(slot);
        if (stackReference == StackReference.EMPTY) {
            throw NO_SUCH_SLOT_EXCEPTION.create(slot);
        }
        ItemStack item = stackReference.get();
        if (item.isEmpty()) {
            throw slot == 98 ? ITEMLESS_HOLDING_EXCEPTION.create() : ITEMLESS_EXCEPTION.create();
        }
        Text text = new TranslatableText("chat.type.text", entity.getDisplayName(), getItemText(item));
        source.getServer().getPlayerManager().broadcast(text, MessageType.CHAT, entity.getUuid());

        return 1;
    }

    private static int executeStatus(ServerCommandSource source) throws CommandSyntaxException {
        Entity entity = source.getEntity();
        if (!(entity instanceof LivingEntity livingEntity)) {
            throw REQUIRES_LIVING_ENTITY_EXCEPTION.create();
        }
        Text text = new TranslatableText("chat.type.text", entity.getDisplayName(), getStatusText(livingEntity));
        source.getServer().getPlayerManager().broadcast(text, MessageType.CHAT, entity.getUuid());

        return 1;
    }

    protected static Text getCoordText(World sourceWorld, Vec3d pos, World targetWorld, boolean scaled) {
        DecimalFormat df = new DecimalFormat("0.000");
        df.setRoundingMode(RoundingMode.HALF_UP);
        String dimensionId = targetWorld.getRegistryKey().getValue().toString();
        Text dimensionText = switch (dimensionId) {
            case "minecraft:overworld" -> new TranslatableText("commands.broadcast.success.coord.dimension.overworld");
            case "minecraft:the_nether" -> new TranslatableText("commands.broadcast.success.coord.dimension.the_nether");
            case "minecraft:the_end" -> new TranslatableText("commands.broadcast.success.coord.dimension.the_end");
            default -> new LiteralText(dimensionId);
        };
        if (scaled) {
            double scale = DimensionType.getCoordinateScaleFactor(sourceWorld.getDimension(), targetWorld.getDimension());
            pos = pos.multiply(scale, 1, scale);
        }
        String suggestCommandLine = "/execute in " + dimensionId + " run tp @s " + df.format(pos.x) + ' ' + df.format(pos.y) + ' ' + df.format(pos.z);
        return new TranslatableText("commands.broadcast.success.coord", dimensionText, df.format(pos.x), df.format(pos.y), df.format(pos.z)).setStyle(Style.EMPTY
                .withColor(Formatting.GREEN)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TranslatableText("chat.coordinates.tooltip")))
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestCommandLine)));
    }

    protected static Text getItemText(ItemStack item) {
        MutableText itemHoverableText = (MutableText) item.toHoverableText();
        return (item.getMaxCount() == 1 && item.getCount() == 1 ?
                new TranslatableText("commands.broadcast.success.item.unstackable", itemHoverableText) :
                new TranslatableText("commands.broadcast.success.item.stackable",
                        itemHoverableText,
                        new LiteralText(String.valueOf(item.getCount())))
        ).formatted(Formatting.GREEN);
    }

    protected static Text getStatusText(LivingEntity entity) {
        DecimalFormat df = new DecimalFormat("0.00");
        df.setRoundingMode(RoundingMode.HALF_UP);
        MutableText text;
        if (entity instanceof PlayerEntity playerEntity) {
            text = new TranslatableText("commands.broadcast.success.status", df.format(playerEntity.getHealth()), playerEntity.getHungerManager().getFoodLevel());
        } else {
            text = new TranslatableText("commands.broadcast.success.status.only_health", df.format(entity.getHealth()));
        }
        return text.formatted(Formatting.GREEN);
    }
}
