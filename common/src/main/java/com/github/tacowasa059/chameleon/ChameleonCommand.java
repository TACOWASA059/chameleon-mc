package com.github.tacowasa059.chameleon;

import com.github.tacowasa059.chameleon.net.ChameleonNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * {@code /chameleon} -- OP (level 2) command to view/change the config intervals at
 * runtime (also persists the {@code config/chameleon.properties} file). Registered
 * per loader (Forge RegisterCommandsEvent / Fabric CommandRegistrationCallback).
 *
 * <ul>
 *   <li>{@code /chameleon} -- show the current values</li>
 *   <li>{@code /chameleon saveinterval <ticks>} -- set the server save interval</li>
 *   <li>{@code /chameleon sendinterval <ticks>} -- set the client send interval</li>
 *   <li>{@code /chameleon reload} -- re-read the config file</li>
 * </ul>
 */
public final class ChameleonCommand {

    private ChameleonCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chameleon")
                .requires(src -> src.hasPermission(2))
                .executes(ChameleonCommand::show)
                .then(Commands.literal("saveinterval")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 72000))
                                .executes(ctx -> {
                                    ChameleonConfig.setSaveInterval(IntegerArgumentType.getInteger(ctx, "ticks"));
                                    return report(ctx, "saveIntervalTicks set to " + ChameleonConfig.saveIntervalTicks);
                                })))
                .then(Commands.literal("sendinterval")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 72000))
                                .executes(ctx -> {
                                    ChameleonConfig.setSendInterval(IntegerArgumentType.getInteger(ctx, "ticks"));
                                    // Push it to every connected client so they actually use it.
                                    ChameleonNetwork.broadcastConfig(ctx.getSource().getServer());
                                    return report(ctx, "sendIntervalTicks set to " + ChameleonConfig.sendIntervalTicks
                                            + " (pushed to all clients)");
                                })))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            ChameleonConfig.reload();
                            // Re-push config (send interval + allowed poses) to all clients.
                            ChameleonNetwork.broadcastConfig(ctx.getSource().getServer());
                            ChameleonNetwork.clearDisallowedPoses(ctx.getSource().getServer());
                            return report(ctx, "Chameleon config reloaded (saveIntervalTicks="
                                    + ChameleonConfig.saveIntervalTicks + ", allowedPoses pushed)");
                        }))
                .then(Commands.literal("pose")
                        .then(Commands.argument("pose", StringArgumentType.word())
                                .suggests((c, builder) -> {
                                    for (ChameleonPose p : ChameleonPose.VALUES) {
                                        if (p.selectable()) {
                                            builder.suggest(p.name().toLowerCase(Locale.ROOT));
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("allowed", BoolArgumentType.bool())
                                        .executes(ChameleonCommand::setPose)))));
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Chameleon: sendIntervalTicks=" + ChameleonConfig.sendIntervalTicks
                        + ", saveIntervalTicks=" + ChameleonConfig.saveIntervalTicks
                        + ", allowedPoses=[" + ChameleonConfig.allowedPosesString() + "]"), false);
        return 1;
    }

    private static int setPose(CommandContext<CommandSourceStack> ctx) {
        ChameleonPose p = ChameleonPose.byName(StringArgumentType.getString(ctx, "pose"));
        if (p == null || !p.selectable()) {
            ctx.getSource().sendFailure(Component.literal("Unknown pose (use crouch / crawl / sit / lie)"));
            return 0;
        }
        boolean allowed = BoolArgumentType.getBool(ctx, "allowed");
        ChameleonConfig.setPoseAllowed(p, allowed);
        ChameleonNetwork.broadcastConfig(ctx.getSource().getServer());
        if (!allowed) {
            ChameleonNetwork.clearDisallowedPoses(ctx.getSource().getServer()); // reset anyone using it
        }
        return report(ctx, "pose " + p.name().toLowerCase(Locale.ROOT) + " allowed=" + allowed
                + " (now: [" + ChameleonConfig.allowedPosesString() + "])");
    }

    private static int report(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }
}
