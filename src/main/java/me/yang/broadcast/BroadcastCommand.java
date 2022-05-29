package me.yang.broadcast;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.ItemSlotArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.ItemStack;
import net.minecraft.network.MessageType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import java.math.RoundingMode;
import java.text.DecimalFormat;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class BroadcastCommand {
    private static final SimpleCommandExceptionType FAILED_ITEMLESS_FEEDBACK = new SimpleCommandExceptionType(new TranslatableText("commands.broadcast.failed.itemless"));
    private static final SimpleCommandExceptionType FAILED_ITEMLESS_HOLDING_FEEDBACK = new SimpleCommandExceptionType(new TranslatableText("commands.broadcast.failed.itemless.holding"));
    private static final DynamicCommandExceptionType FAILED_NO_SUCH_SLOT_FEEDBACK = new DynamicCommandExceptionType(slot -> new TranslatableText("commands.broadcast.failed.no_such_slot", slot));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralCommandNode<ServerCommandSource> literalCommandNode = dispatcher.register(literal("broadcast")
                .then(literal("coord").executes(context -> executeCoord(context.getSource(), context.getSource().getPosition(), context.getSource().getWorld().getRegistryKey()))
                        .then(argument("location", Vec3ArgumentType.vec3()).executes(context -> executeCoord(context.getSource(), Vec3ArgumentType.getVec3(context, "location"), context.getSource().getWorld().getRegistryKey()))
                                .then(argument("dimension", DimensionArgumentType.dimension()).executes(context -> executeCoord(context.getSource(), Vec3ArgumentType.getVec3(context, "location"), DimensionArgumentType.getDimensionArgument(context, "dimension").getRegistryKey()))
                                )
                        )
                )
                .then(literal("item").executes(context -> executeItem(context.getSource(), 98))
                        .then(argument("slot", ItemSlotArgumentType.itemSlot()).executes(context -> executeItem(context.getSource(), ItemSlotArgumentType.getItemSlot(context, "slot")))
                        )
                )
        );
        dispatcher.register(literal("brc")
                .redirect(literalCommandNode)
        );
    }

    private static int executeCoord(ServerCommandSource source, Vec3d pos, RegistryKey<World> dimension) {
        Entity entity = source.getEntity();
        Text text = new TranslatableText("chat.type.text", source.getDisplayName(), getCoordText(pos, dimension));
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
            throw FAILED_NO_SUCH_SLOT_FEEDBACK.create(slot);
        }
        ItemStack item = stackReference.get();
        if (item.isEmpty()) {
            throw slot == 98 ? FAILED_ITEMLESS_HOLDING_FEEDBACK.create() : FAILED_ITEMLESS_FEEDBACK.create();
        }
        Text text = new TranslatableText("chat.type.text", entity.getDisplayName(), getItemText(item));
        source.getServer().getPlayerManager().broadcast(text, MessageType.CHAT, entity.getUuid());

        return 1;
    }

    protected static Text getCoordText(Vec3d pos, RegistryKey<World> dimension) {
        DecimalFormat df = new DecimalFormat("0.000");
        df.setRoundingMode(RoundingMode.HALF_UP);
        Text dimensionText = switch (dimension.getValue().toString()) {
            case "minecraft:overworld" -> new TranslatableText("commands.broadcast.success.dimension.overworld");
            case "minecraft:the_nether" -> new TranslatableText("commands.broadcast.success.dimension.the_nether");
            case "minecraft:the_end" -> new TranslatableText("commands.broadcast.success.dimension.the_end");
            default -> new LiteralText(dimension.getValue().toString());
        };
        String commandLine = "/execute in " + dimension.getValue().toString() + " run tp " + df.format(pos.x) + ' ' + df.format(pos.y) + ' ' + df.format(pos.z);
        return new LiteralText("[").append(dimensionText).append(": " + df.format(pos.x) + ", " + df.format(pos.y) + ", " + df.format(pos.z) + "]").setStyle(Style.EMPTY
                .withColor(Formatting.GREEN)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TranslatableText("chat.coordinates.tooltip")))
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, commandLine))
        );
    }

    protected static Text getItemText(ItemStack item) {
        return item.getMaxCount() == 1 && item.getCount() == 1 ? item.toHoverableText() : new LiteralText("").append(item.toHoverableText()).append(" * " + item.getCount());
    }
}
