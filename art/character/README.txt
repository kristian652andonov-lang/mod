Girlfriend companion — character assets (GeckoLib 4.x, NeoForge 1.21.1)

mod/                      drop-in sources for your mod (see mod/README.md for setup)
  src/main/resources/assets/companion/
    geo/entity/           body + 3 outfit models (.geo.json, Bedrock format, open in Blockbench)
    textures/entity/girlfriend/   skin.png, everyday.png, beach.png, sleep.png (64x64)
    animations/entity/girlfriend.animation.json   all 15 GeckoLib animations:
      idle, walk, run, swim, sit, hug, carried, lap, pet_head, pet_cheek,
      lie_down, sleep, cuddle, stretch, wake_up
  src/main/java/com/example/companion/   outfit system, entity, renderer, outfit layer, payload
tools/build-assets.js     regenerates every model, texture, animation and Outfit.java
uploads/                  her original skin (the generator's source)
Character Viewer.html     3D preview + GLB/OBJ export. Serve the folder locally to open it, e.g.
                          "python -m http.server" then http://localhost:8000/Character%20Viewer.html

Rename "companion" / "com.example.companion" to your mod id and package.
