package com.otectus.alexsbutchery.compat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.butcher.OfficialAddonGuard;
import com.otectus.alexsbutchery.compat.profile.CompatProfile;
import com.otectus.alexsbutchery.compat.profile.CountRange;
import com.otectus.alexsbutchery.compat.profile.GroupSpec;
import com.otectus.alexsbutchery.compat.profile.ProviderSelection;
import com.otectus.alexsbutchery.config.ServerConfig;
import com.otectus.alexsbutchery.def.Drop;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Operator diagnostics, read only: {@code /alexsbutchery compat status}, {@code validate} and {@code explain <creature>}.
 * They print the server's resolved rules (installed providers, chosen provider per product and stage, native
 * fallbacks, rejected profiles and why, settings, recipe conflicts, death drops removed or kept) without rolling loot or
 * changing anything.
 */
@Mod.EventBusSubscriber(modid = AlexsButchery.MOD_ID)
public final class CompatCommands {
    private static final int MAX_LINES = 40;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(AlexsButchery.MOD_ID).requires(source -> source.hasPermission(2))
                .then(Commands.literal("compat")
                        .then(Commands.literal("status").executes(CompatCommands::status))
                        .then(Commands.literal("validate").executes(CompatCommands::validate))
                        .then(Commands.literal("explain").then(Commands.argument("creature", ResourceLocationArgument.id())
                                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(MobDefs.all().stream().map(MobDef::entity), b))
                                .executes(CompatCommands::explain)))));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CompatState state = CompatState.get(context.getSource().getServer());
        if (state == null) return fail(context, tr("none"));
        List<Component> lines = new ArrayList<>();
        lines.add(header(tr("status.title")));
        for (CompatState.ProviderStatus p : state.providers().values()) {
            MutableComponent text = !p.installed() ? tr("provider.absent", ProviderNames.name(p.provider()), p.provider())
                    : p.enabled() ? tr("provider.active", ProviderNames.name(p.provider()), p.provider(), p.version(), p.activeProfiles())
                    : tr("provider.disabled", ProviderNames.name(p.provider()), p.provider(), p.version());
            lines.add(text.withStyle(p.installed() && p.enabled() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        lines.add(tr("settings", state.policy().describe() + ", replaceMobDrops=" + ServerConfig.REPLACE_MOB_DROPS.get()).withStyle(ChatFormatting.GRAY));
        lines.add(tr("files", state.profiles().profiles().size(), state.profiles().selections().size(), state.profiles().disabled().size(),
                packs(state.profiles().sources().values())).withStyle(ChatFormatting.GRAY));
        for (RecipePolicy.Outcome o : state.recipes().outcomes()) {
            if (o.status() == RecipePolicy.Status.NOT_PRESENT) continue;
            lines.add((o.status() == RecipePolicy.Status.RESOLVED ? tr("conflict.owned", o.conflict(), o.owner()) : tr("conflict.reported", o.conflict(), o.detail()))
                    .withStyle(o.status() == RecipePolicy.Status.RESOLVED ? ChatFormatting.GRAY : ChatFormatting.YELLOW));
        }
        if (state.degraded()) lines.add(tr("degraded").withStyle(ChatFormatting.RED));
        lines.add(tr("counts", state.count(CompatState.Severity.ERROR), state.count(CompatState.Severity.WARNING), state.view().digest())
                .withStyle(ChatFormatting.GRAY));
        return send(context, lines);
    }

    private static int validate(CommandContext<CommandSourceStack> context) {
        CompatState state = CompatState.get(context.getSource().getServer());
        if (state == null) return fail(context, tr("none"));
        List<Component> lines = new ArrayList<>();
        lines.add(header(tr("validate.title")));
        List<CompatState.Diagnostic> problems = state.diagnostics().stream().filter(d -> d.severity() != CompatState.Severity.INFO).toList();
        if (problems.isEmpty()) lines.add(tr("validate.ok").withStyle(ChatFormatting.GREEN));
        for (CompatState.Diagnostic d : problems) {
            lines.add(tr("diagnostic", d.severity().name(), d.source(), d.message())
                    .withStyle(d.severity() == CompatState.Severity.ERROR ? ChatFormatting.RED : ChatFormatting.YELLOW));
        }
        return send(context, lines);
    }

    private static int explain(CommandContext<CommandSourceStack> context) {
        CompatState state = CompatState.get(context.getSource().getServer());
        if (state == null) return fail(context, tr("none"));
        ResourceLocation id = ResourceLocationArgument.getId(context, "creature");
        MobDef def = MobDefs.byEntityId(id);
        if (def == null && id.getNamespace().equals("minecraft")) def = MobDefs.byId(id.getPath());
        if (def == null) return fail(context, tr("unknown", id.toString()));
        List<Component> lines = new ArrayList<>();
        lines.add(header(Component.literal(def.displayName() + " (" + def.entity() + ")")));
        lines.add(tr("explain.providers", state.providers().values().stream().map(p -> p.provider() + " " + (p.installed() ? p.version()
                + (p.enabled() ? "" : " (disabled)") : "-")).collect(Collectors.joining(", "))).withStyle(ChatFormatting.GRAY));
        lines.add(tr("settings", state.policy().describe()).withStyle(ChatFormatting.GRAY));
        if (ServerConfig.isDisabled(def)) lines.add(tr("explain.disabled_mob", def.id()).withStyle(ChatFormatting.YELLOW));
        if (OfficialAddonGuard.covers(def)) lines.add(tr("explain.deferred", OfficialAddonGuard.OFFICIAL_MOD_ID).withStyle(ChatFormatting.YELLOW));
        for (String group : def.groups()) {
            for (String stage : def.stagesOf(group)) {
                CompatState.Resolution r = state.resolution(def.entity(), group, stage);
                lines.add(tr("explain.rule", group, stage, chain(r, stage)).withStyle(ChatFormatting.WHITE));
                lines.add(tr("explain.fallback", def.groupDrops(stage, group).stream().map(CompatCommands::drop).collect(Collectors.joining(", ")))
                        .withStyle(ChatFormatting.GRAY));
                for (CompatState.Rejection rejection : r.rejected())
                    lines.add(tr("explain.rejected", rejection.profile().toString(), rejection.provider(), rejection.reason())
                            .withStyle(rejection.severity() == CompatState.Severity.ERROR ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
            }
        }
        List<String> sources = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CompatProfile> e : state.profiles().profiles().entrySet())
            if (e.getValue().entity().equals(def.entity())) sources.add(e.getKey() + " [" + state.profiles().sources().getOrDefault(e.getKey(), "?") + "]");
        for (Map.Entry<ResourceLocation, ProviderSelection> e : state.profiles().selections().entrySet())
            if (e.getValue().entity().equals(def.entity())) sources.add(e.getKey() + " (selection " + e.getValue().providerPriority() + ")");
        lines.add(tr("explain.profiles", sources.isEmpty() ? "-" : String.join(", ", sources)).withStyle(ChatFormatting.GRAY));
        if (ServerConfig.REPLACE_MOB_DROPS.get()) {
            lines.add(tr("explain.removes", items(state.suppression(def.entity()))).withStyle(ChatFormatting.GRAY));
            if (!state.packGlobalRemovals().isEmpty()) lines.add(tr("explain.removes_global", items(state.packGlobalRemovals())).withStyle(ChatFormatting.GRAY));
            lines.add(tr("explain.kept").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(tr("explain.no_replace").withStyle(ChatFormatting.YELLOW));
        }
        for (RecipePolicy.Outcome o : state.recipes().outcomes()) {
            if (o.status() == RecipePolicy.Status.NOT_PRESENT) continue;
            lines.add((o.status() == RecipePolicy.Status.RESOLVED ? tr("explain.conflict", o.conflict(), o.owner(), o.removed().toString(), o.alternates().toString())
                    : tr("conflict.reported", o.conflict(), o.detail())).withStyle(ChatFormatting.GRAY));
        }
        return send(context, lines);
    }

    private static String chain(CompatState.Resolution r, String stage) {
        List<String> links = new ArrayList<>();
        for (CompatState.Choice c : r.chain()) {
            if (!(c instanceof CompatState.Provided p)) {
                links.add("native");
                break;
            }
            String items = p.item() != null ? key(p.item()) : p.spec().variants().nbt() + " " + p.variantItems().entrySet().stream()
                    .map(e -> e.getKey() + "=" + key(e.getValue())).collect(Collectors.joining(", ", "{", "}"));
            String count = p.spec().countMode() == GroupSpec.CountMode.NATIVE_GROUP ? "native counts" : range(p.spec().counts().get(stage));
            links.add(p.provider() + ": " + items + " x " + count);
        }
        return String.join(" > ", links);
    }

    private static String range(CountRange r) {
        if (r == null) return "?";
        String count = r.min() == r.max() ? Integer.toString(r.min()) : r.min() + "-" + r.max();
        return r.chance() < 1F ? count + " (" + Math.round(r.chance() * 100) + "%)" : count;
    }

    private static String drop(Drop d) {
        String count = d.min() == d.max() ? Integer.toString(d.min()) : d.min() + "-" + d.max();
        return d.item() + " x " + count + (d.chance() < 1F ? " (" + Math.round(d.chance() * 100) + "%)" : "");
    }

    private static String items(java.util.Collection<Item> items) {
        return items.isEmpty() ? "nothing" : items.stream().map(CompatCommands::key).collect(Collectors.joining(", "));
    }

    private static String key(Item item) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(item));
    }

    private static String packs(java.util.Collection<String> packs) {
        List<String> distinct = packs.stream().distinct().sorted().toList();
        return distinct.isEmpty() ? "no pack" : String.join(", ", distinct);
    }

    /** {@code commands.alexsbutchery.compat.<key>}; data such as ids and diagnostic reasons are its arguments. */
    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("commands.alexsbutchery.compat." + key, args);
    }

    private static MutableComponent header(MutableComponent text) {
        return text.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    private static int send(CommandContext<CommandSourceStack> context, List<Component> lines) {
        int shown = 0;
        for (Component line : lines) {
            if (shown++ >= MAX_LINES) {
                int rest = lines.size() - MAX_LINES;
                context.getSource().sendSuccess(() -> tr("more", rest).withStyle(ChatFormatting.GRAY), false);
                break;
            }
            context.getSource().sendSuccess(() -> line, false);
        }
        return lines.size();
    }

    private static int fail(CommandContext<CommandSourceStack> context, MutableComponent message) {
        context.getSource().sendFailure(message);
        return 0;
    }

    private CompatCommands() {}
}
