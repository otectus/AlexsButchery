package com.otectus.alexsbutchery.def;

import com.otectus.alexsbutchery.AlexsButchery;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the mod knows about one Alex's Mobs creature: which blocks and items it gets, how it bleeds, which
 * butchering stages it has and what each stage drops. Registration, datagen, the kill handler and the cut machine
 * all read this and nothing else, so adding a mob is adding one entry to {@link MobDefs}.
 */
public final class MobDef {
    public static final String HEAD = "head";
    public static final String CUT_1 = "cut_1";
    public static final String CUT_2 = "cut_2";
    public static final String CUT_3 = "cut_3";

    private final String id;
    private final String displayName;
    private final ResourceLocation entity;
    private final BloodClass blood;
    private final boolean hookable;
    private final SkinStep skin;
    private final boolean head;
    private final MountSize mount;
    private final Weight weight;
    private final boolean organs;
    private final boolean bossTool;
    private final boolean skeleton;
    @Nullable
    private final String rug;
    private final Map<String, List<Drop>> drops;
    private final List<ResourceLocation> replacedDrops;

    private MobDef(Builder b) {
        this.id = b.id;
        this.displayName = b.displayName;
        this.entity = b.entity;
        this.blood = b.blood;
        this.hookable = b.hookable;
        this.skin = b.skin;
        this.head = b.head;
        this.mount = b.mount;
        this.weight = b.weight;
        this.organs = b.organs;
        this.bossTool = b.bossTool;
        this.skeleton = b.skeleton == null ? b.blood != BloodClass.NONE : b.skeleton;
        this.rug = b.rug;
        this.drops = Collections.unmodifiableMap(new LinkedHashMap<>(b.drops));
        this.replacedDrops = List.copyOf(b.replacedDrops);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public ResourceLocation entity() { return entity; }
    public BloodClass blood() { return blood; }
    public boolean hookable() { return hookable; }
    public SkinStep skin() { return skin; }
    public boolean hasHead() { return head; }
    public MountSize mount() { return mount; }
    public Weight weight() { return weight; }
    public boolean organs() { return organs; }
    public boolean bossTool() { return bossTool; }
    /** Acid dissolves a whole carcass of this mob into a skeleton block (animals with a real skeleton). */
    public boolean hasSkeleton() { return skeleton; }
    /** The skin item (our id path) a pelt rug of this mob is crafted from, or null when it has no rug. */
    @Nullable public String rug() { return rug; }
    public boolean hasRug() { return rug != null; }
    public Map<String, List<Drop>> drops() { return drops; }
    public List<ResourceLocation> replacedDrops() { return replacedDrops; }

    /** Bleeding carcasses hang, drain and become a drained carcass; the rest are cut up on the fresh block. */
    public boolean bleeds() { return blood != BloodClass.NONE; }
    /** Butchery's small carcasses: small fill and drip procedures, {@code butchery:small_carcass}. */
    public boolean small() { return blood == BloodClass.SMALL; }
    /** Placed in the world at death, never picked up onto a hook. */
    public boolean floor() { return !hookable; }

    public ResourceLocation carcassId() { return AlexsButchery.id(id + "_carcass"); }
    public ResourceLocation drainedId() { return AlexsButchery.id("drained_" + id + "_carcass"); }
    public ResourceLocation headId() { return AlexsButchery.id(id + "_head"); }
    public ResourceLocation mountId() { return AlexsButchery.id(id + "_head_mount"); }
    public ResourceLocation skeletonId() { return AlexsButchery.id(id + "_skeleton"); }
    public ResourceLocation rugId() { return AlexsButchery.id(id + "_rug"); }
    /** Loot table rolled by one butchering stage: {@code alexsbutchery:carcass/<mob>/<stage>}. */
    public ResourceLocation stageTable(String stage) { return AlexsButchery.id("carcass/" + id + "/" + stage); }

    @Override
    public String toString() {
        return "MobDef[" + id + "]";
    }

    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName);
    }

    public static final class Builder {
        private final String id;
        private final String displayName;
        private ResourceLocation entity;
        private BloodClass blood = BloodClass.REGULAR;
        private boolean hookable = true;
        private SkinStep skin = SkinStep.SKIN;
        private boolean head = true;
        private MountSize mount = MountSize.REGULAR;
        private Weight weight = Weight.LIGHT;
        private boolean organs = true;
        private boolean bossTool;
        private Boolean skeleton;
        private String rug;
        private final Map<String, List<Drop>> drops = new LinkedHashMap<>();
        private final List<ResourceLocation> replacedDrops = new ArrayList<>();

        private Builder(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
            this.entity = new ResourceLocation("alexsmobs", id);
        }

        public Builder entity(String entityId) { this.entity = new ResourceLocation(entityId); return this; }
        public Builder blood(BloodClass blood) { this.blood = blood; return this; }
        public Builder small() { this.blood = BloodClass.SMALL; this.mount = MountSize.SMALL; return this; }
        public Builder floor() { this.hookable = false; this.blood = BloodClass.LARGE; this.weight = Weight.HEAVY; this.mount = MountSize.LARGE; return this; }
        public Builder noBlood() { this.blood = BloodClass.NONE; this.skin = SkinStep.NONE; this.organs = false; return this; }
        public Builder skin(SkinStep skin) { this.skin = skin; return this; }
        public Builder pluck() { this.skin = SkinStep.PLUCK; return this; }
        public Builder skinless() { this.skin = SkinStep.NONE; return this; }
        public Builder headless() { this.head = false; return this; }
        public Builder mount(MountSize mount) { this.mount = mount; return this; }
        public Builder weight(Weight weight) { this.weight = weight; return this; }
        public Builder heavy() { this.weight = Weight.HEAVY; return this; }
        public Builder noOrgans() { this.organs = false; return this; }
        public Builder bossTool() { this.bossTool = true; return this; }
        /** Bleeding mobs have skeletons by default, bloodless ones do not; these override that. */
        public Builder skeleton() { this.skeleton = true; return this; }
        public Builder noSkeleton() { this.skeleton = false; return this; }
        /** A pelt rug crafted from three of this skin item (our id path) and the mob's head. */
        public Builder rug(String skinItem) { this.rug = skinItem; return this; }

        /** Drops of one stage: {@code head}, {@code skin}, {@code pluck}, {@code cut_1}, {@code cut_2}, {@code cut_3}. */
        public Builder drops(String stage, Drop... entries) {
            drops.computeIfAbsent(stage, k -> new ArrayList<>()).addAll(List.of(entries));
            return this;
        }

        /** Items the mob normally drops that the carcass yields instead; removed from the death drops. */
        public Builder replaces(String... items) {
            for (String item : items) replacedDrops.add(new ResourceLocation(item));
            return this;
        }

        public MobDef build() {
            if (head) {
                ResourceLocation headItem = new ResourceLocation(AlexsButchery.MOD_ID, id + "_head");
                List<Drop> headDrops = drops.computeIfAbsent(HEAD, k -> new ArrayList<>());
                if (headDrops.stream().noneMatch(d -> d.item().equals(headItem))) headDrops.add(0, new Drop(headItem, 1, 1, 1F));
            }
            if (rug != null && !head) throw new IllegalStateException(id + ": a rug needs the mob's head");
            return new MobDef(this);
        }
    }
}
