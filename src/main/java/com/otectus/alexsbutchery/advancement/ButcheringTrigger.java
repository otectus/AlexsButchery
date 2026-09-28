package com.otectus.alexsbutchery.advancement;

import com.google.gson.JsonObject;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.def.MobDef;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Criterion {@code alexsbutchery:butchering}: a player did one butchering action to one of our creatures. Every
 * field is optional, so packs can write their own advancements against it:
 * <pre>{"trigger": "alexsbutchery:butchering", "conditions": {"action": "cut_3", "mob": "void_worm", "floor": true}}</pre>
 */
public final class ButcheringTrigger extends SimpleCriterionTrigger<ButcheringTrigger.Instance> {
    public static final ResourceLocation ID = AlexsButchery.id("butchering");
    public static final ButcheringTrigger INSTANCE = new ButcheringTrigger();

    /** What the player did; the serialized name is the action's lower-case name. */
    public enum Action {
        KILL, HANG, BLEED, HEAD, SKIN, PLUCK, CUT_1, CUT_2, CUT_3, ACID;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Nullable
        static Action byId(@Nullable String id) {
            if (id == null) return null;
            for (Action a : values()) if (a.id().equals(id)) return a;
            throw new IllegalArgumentException("Unknown butchering action " + id);
        }
    }

    private ButcheringTrigger() {}

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
        Action action = Action.byId(GsonHelper.getAsString(json, "action", null));
        String mob = GsonHelper.getAsString(json, "mob", null);
        Boolean floor = json.has("floor") ? GsonHelper.getAsBoolean(json, "floor") : null;
        return new Instance(player, action, mob, floor);
    }

    public void trigger(ServerPlayer player, MobDef def, Action action) {
        trigger(player, instance -> instance.matches(def, action));
    }

    public static final class Instance extends AbstractCriterionTriggerInstance {
        @Nullable private final Action action;
        @Nullable private final String mob;
        @Nullable private final Boolean floor;

        public Instance(ContextAwarePredicate player, @Nullable Action action, @Nullable String mob, @Nullable Boolean floor) {
            super(ID, player);
            this.action = action;
            this.mob = mob;
            this.floor = floor;
        }

        public static Instance of(@Nullable Action action, @Nullable String mob, @Nullable Boolean floor) {
            return new Instance(ContextAwarePredicate.ANY, action, mob, floor);
        }

        boolean matches(MobDef def, Action done) {
            return (action == null || action == done) && (mob == null || mob.equals(def.id())) && (floor == null || floor == def.floor());
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            if (action != null) json.addProperty("action", action.id());
            if (mob != null) json.addProperty("mob", mob);
            if (floor != null) json.addProperty("floor", floor);
            return json;
        }
    }
}
