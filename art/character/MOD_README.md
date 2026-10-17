# Girlfriend companion — character assets

GeckoLib 4.7.x · NeoForge 1.21.1 · 64×64 textures, 1 texel per model pixel. Her face, eyes, hair and pink hoodie are taken from her existing skin.

## Files

```
src/main/resources/assets/companion/
  geo/entity/girlfriend.geo.json            body: skeleton + skin, hair, joint fillers
  geo/entity/girlfriend_everyday.geo.json   her pink hoodie from the skin (hood up), panties underneath
  geo/entity/girlfriend_beach.geo.json      gingham triangle top, tie-side bottoms
  geo/entity/girlfriend_sleep.geo.json      bra + panties (same panties as under the hoodie)
  textures/entity/girlfriend/{skin,everyday,beach,sleep}.png
  animations/entity/girlfriend.animation.json
src/main/java/com/example/companion/
  outfit/Outfit.java          outfit table (generated — edit tools/build-assets.js)
  outfit/Wardrobe.java        server-side outfit state, auto sleepwear, NBT
  entity/GirlfriendEntity.java reference entity: synced outfit, sleep hooks, animation picker
  client/GirlfriendModel.java  body GeoModel + procedural cloth/hair rules
  client/GirlfriendRenderer.java hides covered body parts, side-lying sleep rotation
  client/OutfitLayer.java      renders the outfit on the body's skeleton
  network/SetOutfitPayload.java manual outfit choice from the client
```

Rename `companion` / `com.example.companion` to your mod id and package.

## Rig

Every geo file carries the same 18 skeleton bones with the same pivots (Bedrock space: px, feet at y 0, facing −Z):
`root › body › waist › chest › {bust, head, hair_front, hair_back, right_arm › right_forearm, left_arm › left_forearm}`, `waist › {skirt_front, skirt_back}`, `body › {right_leg › right_shin, left_leg › left_shin}`.

- Torso bends at the hips (`waist`) and mid-back (`chest`); elbows and knees bend. Slim 3-px arms, standard 32-px height, a 0.5-px waist inset each side and a small full-width bust (8×4×1 wedge tilted 16°, about 1 px deep at the bottom) on the chest bone, identical under every outfit. Every outfit covers it with its own wedge shell (the hoodie samples the same rows of her skin as the chest behind it), so fabric follows its shape.
- Joint fillers (inset −0.1 px) sit inside the neighbouring part at rest and fill the notch when a joint bends.
- Body parts that outfits can hide are leaf bones: `chest_skin`, `bust`, `waist_skin`, `*_upper_arm`, `*_lower_arm`, `*_thigh`, `*_calf`, `hair_back`.
- `skirt_front/back` are cloth bones for future skirts and dresses (no current outfit uses them). `GirlfriendModel` drives them from the thighs every frame so a hem never cuts through her legs.

## How outfit switching works

1. The entity stores one synced string (`DATA_OUTFIT`). Changing it is the whole switch: position, AI, animation controller and relationship data are untouched.
2. `OutfitLayer` fetches the outfit's baked model, copies every same-named bone transform from the body, and re-renders it with the outfit's texture.
3. `GirlfriendRenderer#preRender` hides the body bones listed in `Outfit.hiddenBodyBones` (under the hoodie: chest, bust, arms and her separate hair pieces, since the hoodie layer carries her original hood and hair overlay), so nothing can poke through.
4. `Outfit.hairFrontPush` moves the front hair strands out over thick fabric.

Adding an outfit: add an entry to `OUTFITS` in `tools/build-assets.js`, regenerate, ship the new geo + png.

## Integration

```java
// registry
public static final DeferredHolder<EntityType<?>, EntityType<GirlfriendEntity>> GIRLFRIEND = ENTITIES.register("girlfriend",
        () -> EntityType.Builder.of(GirlfriendEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).build("girlfriend"));

// mod bus
@SubscribeEvent static void attributes(EntityAttributeCreationEvent e) { e.put(GIRLFRIEND.get(), GirlfriendEntity.createAttributes().build()); }
@SubscribeEvent static void payloads(RegisterPayloadHandlersEvent e) {
    e.registrar("1").playToServer(SetOutfitPayload.TYPE, SetOutfitPayload.STREAM_CODEC, SetOutfitPayload::handle);
}
// client mod bus
@SubscribeEvent static void renderers(EntityRenderersEvent.RegisterRenderers e) { e.registerEntityRenderer(GIRLFRIEND.get(), GirlfriendRenderer::new); }
```

- Manual choice (server): `her.chooseOutfit(Outfit.BEACH)`; from a client screen: `PacketDistributor.sendToServer(new SetOutfitPayload(her.getId(), "beach"))`.
- Bedtime: vanilla `startSleeping`/`stopSleeping` call `Wardrobe#onBedtime`/`#onWake`. A custom bed routine calls them directly. A manual pick while she's in bed is kept after she wakes. `wardrobe().setAutoSleepwear(false)` turns the automatic switch off.

## Animations

`idle`, `walk`, `run`, `swim`, `sit`, `hug`, `carried`, `lap` (on the player's lap, leaning back into them), `pet_head`, `pet_cheek`, `sleep`, `cuddle` loop; `lie_down` holds its last frame; `stretch`, `wake_up` play once. Set the activity string (`setActivity("lap")`, `"pet_head"`, `"hug"` etc.) from your AI; the picker in `GirlfriendEntity` maps it to a clip. All clips are skeleton-level and work under every outfit.

`sleep`/`cuddle` lay her on her right side facing model −Z with her head toward model +X; the renderer skips vanilla's lie-on-back tilt and turns her to the bed's head. If she sits off-centre on your bed, adjust `body` position in those clips.

## Regenerating

Run `tools/build-assets.js` (project viewer) to rebuild every geo, texture, animation file and `Outfit.java` from one table. Open `Character Viewer.html` to inspect each outfit and animation and to export GLB/OBJ.

Not yet compiled against your project. The GeckoLib calls target the 4.x API (`reRender`, `GeoRenderLayer#render`); GeckoLib 5 (MC 1.21.5+) renamed them.
