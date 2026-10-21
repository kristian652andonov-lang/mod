package com.terrahaul.worldgen.ruins;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.LakeType;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.water.FallPlan;
import com.terrahaul.worldgen.water.FallPlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Signposts and what they say. A junction gets a fingerpost: a post with one hanging sign per
 * trail branch, each board pointing along its branch like a finger. A trail end gets a board on a
 * post facing the walkers who arrive. Along long trails stand direction posts, with an arm each
 * way, and milestones: a dressed stone with a plate facing the path. Posts follow the country: a
 * stripped log, a log in its bark, a stone pillar in the uplands, a bamboo pole in the jungle.
 * <p>
 * The text is real. Each arm names the destination that lies best in its direction, with its
 * distance: a lake from the lake sites, a volcano, a waterfall, a stone arch, the name of an
 * abandoned homestead (a broken house), a chapel, a watchtower, a well or a ford (other trail
 * ruins), or what a few rays along the branch run into first (a river, the coast, a canyon,
 * mountains, a marsh). Generic words are translatable, with an English fallback; names and
 * distances are literal.
 */
public final class SignpostPlanner {
    /** One sign: the direction its arm points from the post, its height above the ground and its text. */
    public record Board(int dir, int dy, RuinPlan.SignSpec text) {
    }

    private record Dest(String label, double x, double z, double weight) {
    }

    private SignpostPlanner() {
    }

    /** The signs of a post, worked out once and cached in the site. */
    public static Board[] boards(RuinSites sites, Signpost s) {
        Board[] b = s.boards;
        if (b == null) {
            b = switch (s.kind) {
                case Signpost.JUNCTION, Signpost.WAYPOST -> junctionBoards(sites, s);
                case Signpost.MILESTONE -> milestoneBoards(sites, s);
                default -> endBoards(sites, s);
            };
            s.boards = b;
        }
        return b;
    }

    /** The materials of a post: a bamboo pole is bamboo whatever the wood around. */
    private static RuinMaterials postMaterials(Signpost s) {
        RuinMaterials m = s.materials;
        return s.style == Signpost.BAMBOO_POST ? new RuinMaterials(m.stone, RuinMaterials.BAMBOO, m.roofWood, m.roofCover, m.leaves) : m;
    }

    /** The post as a grouped plan around its column; heights are relative to the ground there. */
    public static RuinPlan plan(RuinSites sites, Signpost s) {
        Board[] boards = boards(sites, s);
        RuinPlan p = new RuinPlan(RuinPlan.GROUPED, s.x - 2, -1, s.z - 2, 5, 8, 5, 0, postMaterials(s), false);
        p.anchorX = s.x;
        p.anchorZ = s.z;
        long h = s.seed;
        boolean mossy = s.wetness > 0.45;
        int weather = mossy ? RuinCell.MOSSY : SeedMixer.unit(SeedMixer.mix(h + 5)) < 0.4 ? RuinCell.CRACKED : RuinCell.PLAIN;
        p.set(s.x, 0, s.z, RuinCell.withWeathering(RuinCell.of(RuinPart.FOUNDATION), mossy ? RuinCell.MOSSY : RuinCell.PLAIN));
        if (s.kind == Signpost.MILESTONE) {
            // a dressed stone with a plate on the side facing the path
            p.set(s.x, 1, s.z, RuinCell.withWeathering(RuinCell.of(RuinPart.QUOIN), weather));
            double r = SeedMixer.unit(SeedMixer.mix(h + 1));
            p.set(s.x, 2, s.z, r < 0.5 ? RuinCell.withWeathering(RuinCell.of(RuinPart.MASONRY_SLAB), weather)
                : RuinCell.withWeathering(RuinCell.of(RuinPart.MASONRY_WALL), weather));
            int px = s.x + RuinCell.DX[s.toTrail];
            int pz = s.z + RuinCell.DZ[s.toTrail];
            p.set(px, 1, pz, RuinCell.of(RuinPart.WALL_SIGN, s.toTrail));
            p.extras.add(new RuinPlan.Extra(px, 1, pz, false, null, 0L, boards[0].text));
        } else if (s.kind == Signpost.JUNCTION || s.kind == Signpost.WAYPOST) {
            int post = switch (s.style) {
                case Signpost.BARK_POST, Signpost.BAMBOO_POST -> RuinCell.of(RuinPart.LOG);
                case Signpost.STONE_POST -> RuinCell.withWeathering(RuinCell.of(RuinPart.MASONRY), weather);
                default -> RuinCell.of(RuinPart.STRIPPED_LOG);
            };
            for (int y = 1; y <= 3; y++) {
                p.set(s.x, y, s.z, post);
            }
            int cap;
            if (s.style == Signpost.STONE_POST) {
                cap = RuinCell.withWeathering(RuinCell.of(RuinPart.MASONRY_SLAB), weather);
            } else {
                cap = SeedMixer.unit(SeedMixer.mix(h + 1)) < 0.5 ? RuinCell.of(RuinPart.FENCE) : RuinCell.of(RuinPart.PLANK_SLAB);
            }
            p.set(s.x, 4, s.z, cap);
            for (Board b : boards) {
                int ax = s.x + RuinCell.DX[b.dir];
                int az = s.z + RuinCell.DZ[b.dir];
                p.set(ax, b.dy, az, RuinCell.of(RuinPart.WALL_HANGING_SIGN, RuinCell.clockwise(b.dir)));
                p.extras.add(new RuinPlan.Extra(ax, b.dy, az, false, null, 0L, b.text));
            }
            // ivy up a wet post, a web under an arm
            if (s.wetness > 0.6 && SeedMixer.unit(SeedMixer.mix(h + 2)) < 0.35) {
                int face = (int) (SeedMixer.unit(SeedMixer.mix(h + 3)) * 4);
                boolean free = true;
                for (Board b : boards) {
                    free &= b.dir != face;
                }
                if (free) {
                    int vx = s.x + RuinCell.DX[face];
                    int vz = s.z + RuinCell.DZ[face];
                    int top = 1 + (int) (SeedMixer.unit(SeedMixer.mix(h + 4)) * 3);
                    for (int y = 1; y <= top; y++) {
                        p.set(vx, y, vz, RuinCell.withAux(RuinCell.of(RuinPart.VINE), 1 << RuinCell.opposite(face)) | RuinCell.SOFT);
                    }
                }
            }
        } else {
            p.set(s.x, 1, s.z, s.style == Signpost.STONE_POST ? RuinCell.withWeathering(RuinCell.of(RuinPart.MASONRY_WALL), weather)
                : RuinCell.of(RuinPart.FENCE));
            double ax = Math.cos(s.arms[0]);
            double az = Math.sin(s.arms[0]);
            int rotation = (int) Math.floor(Math.toDegrees(Math.atan2(-ax, az)) * 16.0 / 360.0 + 0.5) & 15;
            p.set(s.x, 2, s.z, RuinCell.withAux(RuinCell.of(RuinPart.STANDING_SIGN), rotation) | RuinCell.FLAG);
            p.extras.add(new RuinPlan.Extra(s.x, 2, s.z, false, null, 0L, boards[0].text));
        }
        if (mossy) {
            for (int d = 0; d < 4; d++) {
                if (SeedMixer.unit(SeedMixer.mix(h + 10 + d)) < 0.35) {
                    p.decals.add(new RuinPlan.Decal(s.x + RuinCell.DX[d], s.z + RuinCell.DZ[d], 1, RuinCell.of(RuinPart.MOSS_CARPET)));
                }
            }
        }
        return p;
    }

    // =========================================================================================
    // Text
    // =========================================================================================

    private static Board[] junctionBoards(RuinSites sites, Signpost s) {
        List<Dest> fixed = destinations(sites, s);
        int n = s.arms.length;
        boolean[] usedDir = new boolean[4];
        List<Dest> usedDest = new ArrayList<>();
        List<Board> out = new ArrayList<>();
        // strongest arms first: they keep their cardinal direction if two compete
        for (int i = 0; i < n && out.size() < 3; i++) {
            double a = s.arms[i];
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            int dir = RuinCell.dirOf(dx, dz);
            if (usedDir[dir]) {
                // try the second-nearest cardinal if the branch is not too far from it
                int alt = Math.abs(dx) >= Math.abs(dz) ? RuinCell.dirOf(0, dz) : RuinCell.dirOf(dx, 0);
                double dot = RuinCell.DX[alt] * dx + RuinCell.DZ[alt] * dz;
                if (usedDir[alt] || dot < 0.2) {
                    continue;
                }
                dir = alt;
            }
            double weather = SeedMixer.unit(SeedMixer.mix(s.seed + 31L * i));
            if (weather < 0.2 && i > 0) {
                // this arm has rotted off
                continue;
            }
            List<Dest> cands = new ArrayList<>(fixed);
            cands.addAll(rays(sites, s, a));
            Dest best = null;
            double bestScore = Double.MAX_VALUE;
            for (Dest d : cands) {
                double bx = d.x - s.nodeX;
                double bz = d.z - s.nodeZ;
                double dist = Math.hypot(bx, bz);
                if (dist < 60) {
                    continue;
                }
                double cos = (bx * dx + bz * dz) / dist;
                if (cos < Math.cos(Math.toRadians(45))) {
                    continue;
                }
                boolean taken = false;
                for (Dest u : usedDest) {
                    taken |= u.label.equals(d.label);
                }
                double score = dist * d.weight * (1.6 - 0.6 * cos);
                if (!taken && score < bestScore) {
                    bestScore = score;
                    best = d;
                }
            }
            String label;
            String dist;
            if (best != null) {
                usedDest.add(best);
                label = best.label;
                dist = distance(Math.hypot(best.x - s.nodeX, best.z - s.nodeZ));
            } else if (out.isEmpty() && i == n - 1) {
                label = COMPASS[dir];
                dist = "";
            } else {
                continue;
            }
            usedDir[dir] = true;
            boolean blank = weather > 0.9;
            String[] front = blank ? empty() : new String[]{"", label, dist.isEmpty() ? "" : dist + " →", ""};
            String[] back = blank ? empty() : new String[]{"", label, dist.isEmpty() ? "" : "← " + dist, ""};
            boolean glow = SeedMixer.unit(SeedMixer.mix(s.seed + 97L * i)) < 0.1;
            out.add(new Board(dir, out.size() % 2 == 0 ? 3 : 2, new RuinPlan.SignSpec(front, back, s.materials.darkWood(), glow)));
        }
        if (out.isEmpty()) {
            int dir = RuinCell.dirOf(Math.cos(s.arms[0]), Math.sin(s.arms[0]));
            String[] t = {"", COMPASS[dir], "", ""};
            out.add(new Board(dir, 3, new RuinPlan.SignSpec(t, t.clone(), s.materials.darkWood(), false)));
        }
        return out.toArray(new Board[0]);
    }

    /**
     * The plate of a milestone: the destination that lies best along the trail in either
     * direction, its distance and an arrow pointing the way, as walkers see it from the path.
     */
    private static Board[] milestoneBoards(RuinSites sites, Signpost s) {
        List<Dest> fixed = destinations(sites, s);
        Dest best = null;
        double bestScore = Double.MAX_VALUE;
        int bestArm = 0;
        for (int i = 0; i < s.arms.length; i++) {
            double dx = Math.cos(s.arms[i]);
            double dz = Math.sin(s.arms[i]);
            List<Dest> cands = new ArrayList<>(fixed);
            cands.addAll(rays(sites, s, s.arms[i]));
            for (Dest d : cands) {
                double bx = d.x - s.nodeX;
                double bz = d.z - s.nodeZ;
                double dist = Math.hypot(bx, bz);
                if (dist < 80) {
                    continue;
                }
                double cos = (bx * dx + bz * dz) / dist;
                if (cos < Math.cos(Math.toRadians(40))) {
                    continue;
                }
                double score = dist * d.weight * (1.6 - 0.6 * cos);
                if (score < bestScore) {
                    bestScore = score;
                    best = d;
                    bestArm = i;
                }
            }
        }
        String[] front;
        double r = SeedMixer.unit(SeedMixer.mix(s.seed + 7));
        if (best == null || r < 0.12) {
            // worn smooth: only the cut of the old figures remains
            front = r < 0.06 || best == null ? empty() : new String[]{"", "", distance(Math.hypot(best.x - s.nodeX, best.z - s.nodeZ)), ""};
        } else {
            // the arrow points the way as seen by someone standing on the path, facing the stone
            double ax = Math.cos(s.arms[bestArm]);
            double az = Math.sin(s.arms[bestArm]);
            int face = RuinCell.opposite(s.toTrail);
            int right = RuinCell.clockwise(face);
            double side = ax * RuinCell.DX[right] + az * RuinCell.DZ[right];
            String dist = distance(Math.hypot(best.x - s.nodeX, best.z - s.nodeZ));
            front = new String[]{"", best.label, side >= 0 ? dist + " →" : "← " + dist, ""};
        }
        return new Board[]{new Board(s.toTrail, 1, new RuinPlan.SignSpec(front, empty(), s.materials.darkWood(), false))};
    }

    private static Board[] endBoards(RuinSites sites, Signpost s) {
        double r = SeedMixer.unit(SeedMixer.mix(s.seed + 5));
        // what lies ahead, beyond the end of the path?
        double ax = -Math.cos(s.arms[0]);
        double az = -Math.sin(s.arms[0]);
        String[] front;
        Dest ahead = null;
        double aheadDist = Double.MAX_VALUE;
        for (Dest d : destinations(sites, s)) {
            double bx = d.x - s.nodeX;
            double bz = d.z - s.nodeZ;
            double dist = Math.hypot(bx, bz);
            if (dist >= 60 && (bx * ax + bz * az) / dist > Math.cos(Math.toRadians(40)) && dist * d.weight < aheadDist) {
                aheadDist = dist * d.weight;
                ahead = d;
            }
        }
        if (r < 0.2) {
            front = empty();
        } else if (ahead != null && r < 0.75) {
            front = new String[]{"@path_ends|Path ends", "", ahead.label, "↑ " + distance(Math.hypot(ahead.x - s.nodeX, ahead.z - s.nodeZ))};
        } else if (r < 0.85) {
            front = new String[]{"", "@path_closed_1|Path", "@path_closed_2|closed", ""};
        } else if (r < 0.92) {
            front = new String[]{"", "@turn_back|Turn back", "", ""};
        } else {
            front = new String[]{"", "@path_ends|Path ends", "", ""};
        }
        return new Board[]{new Board(RuinCell.NORTH, 2, new RuinPlan.SignSpec(front, empty(), s.materials.darkWood(), false))};
    }

    private static final String[] COMPASS = {"@north|North", "@east|East", "@south|South", "@west|West"};

    private static String[] empty() {
        return new String[]{"", "", "", ""};
    }

    /** "350 m" below a kilometre (rounded to 10), "1.4 km" from there. */
    static String distance(double blocks) {
        if (blocks < 1000) {
            return Math.max(10, (int) Math.round(blocks / 10.0) * 10) + " m";
        }
        return String.format(Locale.ROOT, "%.1f km", blocks / 1000.0);
    }

    /** Landmarks around the post that exist for certain: lakes, volcanoes, waterfalls, formations, homesteads. */
    private static List<Dest> destinations(RuinSites sites, Signpost s) {
        TerrainModel m = sites.model;
        List<Dest> out = new ArrayList<>();
        int x = s.nodeX;
        int z = s.nodeZ;
        TerrainModel.LakeSite lake = m.nearestLake(x, z, 1200, false);
        if (lake != null && lake.active) {
            String label = switch (lake.type) {
                case LakeType.MOUNTAIN -> "@tarn|Tarn";
                case LakeType.MARSH -> "@marsh|Marsh";
                case LakeType.GLACIAL -> "@glacier_lake|Ice Lake";
                case LakeType.CRATER -> "@volcano|Volcano";
                default -> "@lake|Lake";
            };
            out.add(new Dest(label, lake.x, lake.z, 0.8));
        }
        TerrainModel.LakeSite great = m.nearestLake(x, z, 2500, true);
        if (great != null && great.active) {
            out.add(new Dest("@great_lake|Great Lake", great.x, great.z, 0.75));
        }
        TerrainModel.VolcanoSite v = m.nearestVolcano(x, z, 2500);
        if (v != null) {
            out.add(new Dest("@volcano|Volcano", v.x, v.z, 0.5));
        }
        FallPlanner falls = FallPlanner.of(m);
        int wc = FallPlanner.FALL_CELL;
        for (int cx = Math.floorDiv(x, wc) - 2; cx <= Math.floorDiv(x, wc) + 2; cx++) {
            for (int cz = Math.floorDiv(z, wc) - 2; cz <= Math.floorDiv(z, wc) + 2; cz++) {
                FallPlan w = falls.fall(cx, cz);
                if (w != null) {
                    out.add(new Dest("@waterfall|Waterfall", w.lipX, w.lipZ, 0.7));
                }
            }
        }
        RuinSites.WorldFilter filter = sites.filter();
        if (filter == null || filter.formations()) {
            int fc = TerrainModel.FORMATION_CELL;
            for (int cx = Math.floorDiv(x, fc) - 1; cx <= Math.floorDiv(x, fc) + 1; cx++) {
                for (int cz = Math.floorDiv(z, fc) - 1; cz <= Math.floorDiv(z, fc) + 1; cz++) {
                    TerrainModel.FormationSite f = m.formationSite(cx, cz);
                    if (f != null) {
                        String label = switch (f.type) {
                            case TerrainModel.ARCH -> "@stone_arch|Stone Arch";
                            case TerrainModel.SPIRE -> "@rock_spire|Rock Spire";
                            case TerrainModel.HOODOO -> "@hoodoos|Hoodoos";
                            default -> "@sea_stacks|Sea Stacks";
                        };
                        out.add(new Dest(label, f.x, f.z, 0.8));
                    }
                }
            }
        }
        int tc = RuinSites.TRAIL_CELL;
        for (int cx = Math.floorDiv(x, tc) - 1; cx <= Math.floorDiv(x, tc) + 1; cx++) {
            for (int cz = Math.floorDiv(z, tc) - 1; cz <= Math.floorDiv(z, tc) + 1; cz++) {
                for (TrailRuin r : sites.trailCell(cx, cz).ruins()) {
                    String label = switch (r.kind) {
                        case TrailRuin.HOUSE -> r.name;
                        case TrailRuin.CHAPEL -> "@chapel|Chapel";
                        case TrailRuin.WATCHTOWER -> "@watchtower|Watchtower";
                        case TrailRuin.WELL -> "@well|Well";
                        case TrailRuin.BRIDGE -> "@ford|Ford";
                        default -> null;
                    };
                    if (label != null && sites.allowed(r)) {
                        out.add(new Dest(label, r.x, r.z, r.kind == TrailRuin.HOUSE ? 0.6 : r.kind == TrailRuin.CHAPEL ? 0.55 : 0.7));
                    }
                }
            }
        }
        return out;
    }

    /** What three rays along a branch run into first: a river, the coast, a canyon, mountains or a marsh. */
    private static List<Dest> rays(RuinSites sites, Signpost s, double angle) {
        TerrainModel m = sites.model;
        List<Dest> out = new ArrayList<>();
        ColumnSample c = new ColumnSample();
        for (int k = -1; k <= 1; k++) {
            double a = angle + Math.toRadians(20) * k;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            for (int step = 1; step <= 22; step++) {
                double dist = step * 48.0;
                int x = (int) Math.round(s.nodeX + dx * dist);
                int z = (int) Math.round(s.nodeZ + dz * dist);
                m.sampleInto(x, z, c);
                String label = null;
                double weight = 1.0;
                if (c.river > 0.2 && c.riverWidth >= 50 && !c.dryRiver) {
                    label = "@great_river|Great River";
                    weight = 0.9;
                } else if (c.river > 0.3 && c.riverWidth >= 3 && !c.dryRiver) {
                    label = "@river|River";
                } else if (c.isSea(sites.seaLevel) && c.continentalness < -0.19) {
                    label = "@coast|Coast";
                } else if (c.canyon > 0.5) {
                    label = "@canyon|Canyon";
                    weight = 0.9;
                } else if (c.mountain > 0.6 && c.peakness > 0.5) {
                    label = "@mountains|Mountains";
                    weight = 1.1;
                } else if (c.swamp > 0.75) {
                    label = "@marsh|Marsh";
                }
                if (label != null) {
                    out.add(new Dest(label, x, z, weight));
                    break;
                }
            }
        }
        return out;
    }
}
