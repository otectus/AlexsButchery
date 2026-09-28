package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.SkinStep;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Butchery's cut numbering as a state machine. A carcass is worked through a fixed list of actions (head off,
 * skin or pluck, three cuts); each action, once done, leaves the block in a specific {@code blockstate}. Mobs
 * without a head or a skin simply skip those actions, and the numbers stay Butchery's so its hint overlays fit.
 */
public final class Stages {

    public enum Action {
        HEAD, SKIN, CUT_1, CUT_2, CUT_3;

        public boolean needsKnife() {
            return this == SKIN;
        }

        public int cutIndex() {
            return switch (this) {
                case CUT_1 -> 1;
                case CUT_2 -> 2;
                case CUT_3 -> 3;
                default -> 0;
            };
        }

        public String table(MobDef def) {
            return switch (this) {
                case HEAD -> MobDef.HEAD;
                case SKIN -> def.skin().table();
                case CUT_1 -> MobDef.CUT_1;
                case CUT_2 -> MobDef.CUT_2;
                case CUT_3 -> MobDef.CUT_3;
            };
        }
    }

    /** The block is removed once this action is done. */
    public static final int REMOVED = -1;

    public record Step(Action action, int doneState) {}

    public static List<Action> actions(MobDef def) {
        List<Action> actions = new ArrayList<>(5);
        if (def.hasHead()) actions.add(Action.HEAD);
        if (def.bleeds() && def.skin() != SkinStep.NONE) actions.add(Action.SKIN);
        actions.add(Action.CUT_1);
        actions.add(Action.CUT_2);
        actions.add(Action.CUT_3);
        return actions;
    }

    /** Whether the given state lies on the hanging path of the block kind. */
    public static boolean hanging(boolean freshBlock, int state) {
        if (freshBlock) return state == 1 || state >= 7;
        return state >= 1 && state <= 5;
    }

    /** The state an action leaves the block in, on the given path. */
    public static int doneState(Action action, boolean freshBlock, boolean hanging) {
        if (action == Action.CUT_3) return REMOVED;
        if (freshBlock) {
            // Skinless carcasses: ground 4, 5, 6; hanging 7, 8, 9.
            int base = hanging ? 6 : 3;
            return switch (action) {
                case HEAD -> base + 1;
                case CUT_1 -> base + 2;
                case CUT_2 -> base + 3;
                default -> throw new IllegalArgumentException(action + " on a fresh block");
            };
        }
        int base = hanging ? 1 : 5;
        return switch (action) {
            case HEAD -> base + 1;
            case SKIN -> base + 2;
            case CUT_1 -> base + 3;
            case CUT_2 -> base + 4;
            default -> throw new IllegalArgumentException(action.toString());
        };
    }

    /** Actions already performed on a block in this state. */
    public static Set<Action> done(MobDef def, boolean freshBlock, int state) {
        Set<Action> done = EnumSet.noneOf(Action.class);
        boolean hanging = hanging(freshBlock, state);
        int start = hanging ? 1 : 0;
        if (state == start) return done;
        for (Action action : actions(def)) {
            int ds = doneState(action, freshBlock, hanging);
            if (ds != REMOVED && ds <= state) done.add(action);
        }
        return done;
    }

    /** The next action for a block in this state, or null when nothing applies. */
    @Nullable
    public static Step next(MobDef def, boolean freshBlock, int state) {
        boolean hanging = hanging(freshBlock, state);
        int start = hanging ? 1 : 0;
        for (Action action : actions(def)) {
            int ds = doneState(action, freshBlock, hanging);
            if (ds == REMOVED || state == start || ds > state) return new Step(action, ds);
        }
        return null;
    }

    private Stages() {}
}
