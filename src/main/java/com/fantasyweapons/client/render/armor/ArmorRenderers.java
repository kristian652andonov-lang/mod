package com.fantasyweapons.client.render.armor;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.armor.FantasyArmorItem;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import com.fantasyweapons.client.render.LeanGeoRenderer;
import com.fantasyweapons.client.render.LeanGlowLayer;

import java.util.List;

/** GeckoLib renderers for the legendary armor: the worn set and each piece's inventory icon. */
public final class ArmorRenderers {
    private ArmorRenderers() {
    }

    /** The set's model, texture and animations ({@code geo/armor/<set>_armor}). */
    static final class Model extends GeoModel<FantasyArmorItem> {
        private final ResourceLocation geo, texture, animation;

        Model(ArmorSet set) {
            geo = FantasyWeapons.id("geo/armor/" + set.asset() + ".geo.json");
            texture = FantasyWeapons.id("textures/armor/" + set.asset() + ".png");
            animation = FantasyWeapons.id("animations/armor/" + set.asset() + ".animation.json");
        }

        @Override
        public ResourceLocation getModelResource(FantasyArmorItem item) {
            return geo;
        }

        @Override
        public ResourceLocation getTextureResource(FantasyArmorItem item) {
            return texture;
        }

        @Override
        public ResourceLocation getAnimationResource(FantasyArmorItem item) {
            return animation;
        }
    }

    /** Eclipse Reaper's armor turns black and purple while its wearer holds the Eclipse Reaper in its dark form. */
    static boolean darkMode(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        ItemStack held = living.getMainHandItem();
        if (!(held.getItem() instanceof FantasyWeaponItem weapon) || !weapon.definition().id().equals("eclipse_reaper")) return false;
        var form = weapon.definition().form(FantasyWeaponItem.data(held));
        return form != null && form.id().contains("dark");
    }

    public static final class Worn extends GeoArmorRenderer<FantasyArmorItem> implements LeanGeoRenderer<FantasyArmorItem> {
        private final ArmorSet set;
        private final ResourceLocation dark;

        public Worn(ArmorSet set) {
            super(new Model(set));
            this.set = set;
            this.dark = FantasyWeapons.id("textures/armor/" + set.asset() + "_dark.png");
            addRenderLayer(new LeanGlowLayer<>(this));
        }

        @Override
        public ResourceLocation getTextureLocation(FantasyArmorItem item) {
            if (set == ArmorSet.ECLIPSE_REAPER && darkMode(getCurrentEntity())) return dark;
            return super.getTextureLocation(item);
        }

        /** The one arm drawn while rendering for a first-person arm (see {@link FirstPersonArmor}), else null. */
        private HumanoidArm onlyArm;

        /** Draws only one arm of the chestplate (prepared with {@code prepForRender}): a first-person sleeve. */
        void renderArm(HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, float partialTick, int light) {
            onlyArm = arm;
            try {
                defaultRender(poseStack, getAnimatable(), buffer, null, null, 0, partialTick, light);
            } finally {
                onlyArm = null;
            }
        }

        @Override
        protected void applyBoneVisibilityBySlot(EquipmentSlot slot) {
            super.applyBoneVisibilityBySlot(slot);
            if (onlyArm == null) return;
            setAllBonesVisible(false);
            setBoneVisible(onlyArm == HumanoidArm.LEFT ? leftArm : rightArm, true);
        }
    }

    /** A piece in the inventory: the set's model with only that piece's bones showing. */
    public static final class Icon extends GeoItemRenderer<FantasyArmorItem> implements LeanGeoRenderer<FantasyArmorItem> {
        private static final List<String> ALL = List.of("armorHead", "armorBody", "armorRightArm", "armorLeftArm", "armorRightLeg", "armorLeftLeg",
                "armorRightBoot", "armorLeftBoot");
        private final List<String> shown;

        public Icon(ArmorSet set, ArmorItem.Type type) {
            super(new Model(set));
            this.shown = ArmorSet.bones(type);
            addRenderLayer(new LeanGlowLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, FantasyArmorItem item, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                              boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            for (String bone : ALL) model.getBone(bone).ifPresent(b -> b.setHidden(!shown.contains(bone)));
            super.preRender(poseStack, item, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        }

        @Override
        public RenderType getRenderType(FantasyArmorItem item, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
            return RenderType.entityCutoutNoCull(texture);
        }
    }
}
