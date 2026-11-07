package dev.charter;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Registry fuer alle Charter-Bloecke. Die Bloecke sind Vanilla-Bloecke, die ueber eine Liste
 * (blocks.yml) als Charter Stone, Waystone, Pact Vessel usw. erkannt werden.
 */
public final class BlockManager implements Listener {

    public enum BlockType {
        CHARTER_STONE(Material.LODESTONE, Items.Kind.CHARTER_STONE),
        WAYSTONE(Material.CHISELED_DEEPSLATE, Items.Kind.WAYSTONE),
        PACT_VESSEL(Material.LECTERN, Items.Kind.PACT_VESSEL),
        CHARTER_VESSEL(Material.CHISELED_QUARTZ_BLOCK, Items.Kind.CHARTER_VESSEL),
        SWAPPER(Material.POLISHED_BASALT, Items.Kind.SOUL_SWAPPER),
        BROKEN_CHARTER_STONE(Material.DEEPSLATE, null),
        BROKEN_WAYSTONE(Material.OBSIDIAN, null);

        public final Material material;
        public final Items.Kind item;

        BlockType(Material material, Items.Kind item) {
            this.material = material;
            this.item = item;
        }

        static BlockType ofKind(Items.Kind k) {
            if (k == null) return null;
            for (BlockType t : values()) if (t.item == k) return t;
            return null;
        }
    }

    private static final class Entry {
        final BlockType type;
        boolean lit;
        ItemStack contract;
        boolean powered;

        Entry(BlockType type) {
            this.type = type;
        }
    }

    private static final double RANDOM_TICK_CHANCE = 20 * 3 / 4096.0;

    private final CharterPlugin pl;
    private final File file;
    private final NamespacedKey displayKey;
    private final Map<String, Entry> map = new LinkedHashMap<>();
    private int tick;

    public BlockManager(CharterPlugin pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "blocks.yml");
        this.displayKey = new NamespacedKey(pl, "vessel_display");
    }

    public void start() {
        load();
        Bukkit.getPluginManager().registerEvents(this, pl);
        Bukkit.getScheduler().runTaskTimer(pl, this::run, 20, 20);
        Bukkit.getScheduler().runTaskTimer(pl, this::swapTick, 10, 4);
    }

    // ---------- Registry ----------

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    public BlockType type(Block b) {
        if (b == null) return null;
        Entry e = map.get(key(b));
        return e == null ? null : e.type;
    }

    private Entry entry(Block b) {
        return b == null ? null : map.get(key(b));
    }

    /** Setzt den Block auf den Typ (Material + Registry), z. B. zerbrochene Varianten. */
    public void convert(Block b, BlockType t) {
        Entry old = map.remove(key(b));
        if (old != null) removeDisplay(b);
        b.setType(t.material);
        map.put(key(b), new Entry(t));
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        b.getWorld().spawnParticle(Particle.LARGE_SMOKE, c, 25, 0.4, 0.5, 0.4, 0.03);
        b.getWorld().playSound(c, Sound.BLOCK_DEEPSLATE_BREAK, 1f, 0.6f);
        save();
    }

    /** Entfernt einen registrierten Block (Luft), optional mit Item-Drop. */
    public void breakCustom(Block b, boolean drop) {
        Entry en = map.remove(key(b));
        if (en == null) return;
        removeDisplay(b);
        if (drop) dropFor(b, en.type);
        b.setType(Material.AIR);
        save();
    }

    private void dropFor(Block b, BlockType t) {
        Items.Kind k = switch (t) {
            case BROKEN_WAYSTONE -> Items.Kind.WAYSTONE;
            case BROKEN_CHARTER_STONE -> Items.Kind.APEX_FOCUS;
            default -> t.item;
        };
        if (k != null) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), Items.create(k, 1));
        }
    }

    // ---------- Platzieren / Abbauen ----------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        BlockType t = BlockType.ofKind(Items.kind(e.getItemInHand()));
        if (t == null) return;
        Block b = e.getBlockPlaced();
        Entry en = new Entry(t);

        if (t == BlockType.CHARTER_STONE) {
            if (!pl.charters().createCharter(e.getPlayer(), b)) {
                e.setCancelled(true);
                return;
            }
        } else if (t == BlockType.WAYSTONE) {
            en.lit = pl.charters().addWaystone(b);
        }
        map.put(key(b), en);
        save();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Entry en = entry(b);
        if (en == null) return;
        Player p = e.getPlayer();

        if (en.type == BlockType.CHARTER_STONE && !p.hasPermission("charter.bypass")) {
            e.setCancelled(true);
            Msg.to(p, "Der Charter Stone ist unzerstoerbar.");
            return;
        }
        e.setDropItems(false);
        map.remove(key(b));
        removeDisplay(b);
        boolean creative = p.getGameMode() == GameMode.CREATIVE;

        switch (en.type) {
            case WAYSTONE -> {
                pl.charters().removeWaystoneArea(b);
                if (!creative) dropFor(b, en.type);
            }
            case PACT_VESSEL -> {
                // Bricht man das Gefaess mit Vertrag, stirbt der Verschuldete.
                if (en.contract != null && ContractData.isBound(en.contract)) {
                    UUID id = ContractData.indebted(en.contract);
                    Player victim = id == null ? null : Bukkit.getPlayer(id);
                    CharterManager.Charter c = pl.charters().mainAt(b);
                    if (c != null && id != null) c.members.removeIf(m -> m.equals(id) && !m.equals(c.owner));
                    if (victim != null) {
                        victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
                        victim.setHealth(0);
                    }
                }
                if (!creative) dropFor(b, en.type);
            }
            default -> {
                if (!creative) dropFor(b, en.type);
            }
        }
        save();
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        explosion(e.blockList());
    }

    @EventHandler
    public void onExplode(BlockExplodeEvent e) {
        explosion(e.blockList());
    }

    private void explosion(List<Block> blocks) {
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            Entry en = entry(b);
            if (en == null) continue;
            if (en.type == BlockType.CHARTER_STONE) {
                it.remove();
                continue;
            }
            if (en.type == BlockType.WAYSTONE) pl.charters().removeWaystoneArea(b);
            it.remove();
            breakCustom(b, true);
        }
    }

    // ---------- Pact Vessel ----------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        Entry en = entry(b);
        if (en == null || en.type != BlockType.PACT_VESSEL) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        CharterManager.Charter c = pl.charters().mainAt(b);

        if (en.contract == null && ContractData.isBound(hand)) {
            UUID debtor = ContractData.indebted(hand);
            if (c != null && debtor != null && !debtor.equals(c.owner)) {
                c.members.add(debtor);
                pl.charters().save();
            }
            ItemStack stored = hand.clone();
            stored.setAmount(1);
            en.contract = stored;
            en.lit = true;
            hand.setAmount(hand.getAmount() - 1);
            p.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
            Location loc = b.getLocation().add(0.5, 0.5, 0.5);
            b.getWorld().playSound(loc, Sound.ITEM_FLINTANDSTEEL_USE, 1f, 1f);
            b.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 0.8, 0), 20, 0.2, 0.3, 0.2, 0.02);
            ensureDisplay(b, en);
            save();
        } else if (en.contract != null && hand.getType().isAir()) {
            UUID debtor = ContractData.indebted(en.contract);
            if (c != null && debtor != null) {
                c.members.removeIf(m -> m.equals(debtor) && !m.equals(c.owner));
                pl.charters().save();
            }
            b.getWorld().dropItemNaturally(p.getLocation().add(0, 0.5, 0), en.contract);
            en.contract = null;
            en.lit = false;
            removeDisplay(b);
            b.getWorld().playSound(b.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.2f);
            save();
        }
    }

    private ItemDisplay findDisplay(Block b) {
        World w = b.getWorld();
        if (!w.isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) return null;
        for (Entity e : w.getNearbyEntities(b.getLocation().add(0.5, 1.2, 0.5), 1, 1.5, 1)) {
            if (e instanceof ItemDisplay d
                    && key(b).equals(d.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING))) {
                return d;
            }
        }
        return null;
    }

    private void ensureDisplay(Block b, Entry en) {
        if (en.type != BlockType.PACT_VESSEL || en.contract == null) return;
        if (!b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) return;
        if (!b.getChunk().isEntitiesLoaded()) return;
        ItemDisplay d = findDisplay(b);
        if (d != null) {
            d.setItemStack(en.contract);
            return;
        }
        b.getWorld().spawn(b.getLocation().add(0.5, 1.2, 0.5), ItemDisplay.class, disp -> {
            disp.setItemStack(en.contract);
            disp.setBillboard(Display.Billboard.CENTER);
            disp.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.55f, 0.55f, 0.55f), new AxisAngle4f()));
            disp.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, key(b));
        });
    }

    private void removeDisplay(Block b) {
        ItemDisplay d = findDisplay(b);
        if (d != null) d.remove();
    }

    // ---------- Laufende Logik ----------

    private void run() {
        tick += 20;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (Map.Entry<String, Entry> me : new ArrayList<>(map.entrySet())) {
            String[] k = me.getKey().split(";");
            World w = Bukkit.getWorld(k[0]);
            if (w == null) continue;
            int x = Integer.parseInt(k[1]), y = Integer.parseInt(k[2]), z = Integer.parseInt(k[3]);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block b = w.getBlockAt(x, y, z);
            Entry en = me.getValue();

            // Registry bereinigen, wenn der Block von aussen entfernt wurde
            if (b.getType() != en.type.material) {
                map.remove(me.getKey());
                if (en.type == BlockType.WAYSTONE) pl.charters().removeWaystoneArea(b);
                continue;
            }
            Location c = b.getLocation().add(0.5, 0.5, 0.5);

            switch (en.type) {
                case PACT_VESSEL -> {
                    if (tick % 100 == 0) ensureDisplay(b, en);
                    if (en.lit && en.contract != null) {
                        w.spawnParticle(Particle.SOUL_FIRE_FLAME, c.clone().add(0, 0.9, 0), 2, 0.15, 0.2, 0.15, 0.01);
                        if (soulSource(b.getRelative(0, -2, 0).getType())) {
                            UUID id = ContractData.indebted(en.contract);
                            Player p = id == null ? null : Bukkit.getPlayer(id);
                            if (p != null) pl.effects().applyStrain(p, 16000, 0);
                        }
                    }
                }
                case CHARTER_VESSEL -> {
                    if (en.lit) w.spawnParticle(Particle.END_ROD, c.clone().add(0, 0.9, 0), 2, 0.15, 0.3, 0.15, 0.01);
                    CharterManager.Charter ch = pl.charters().at(b);
                    if (ch != null && ch.uses > 0 && rnd.nextDouble() < RANDOM_TICK_CHANCE) {
                        ch.uses = Math.max(0, ch.uses - 1000);
                        if (rnd.nextInt(420) == 2) {
                            if (en.lit) {
                                map.remove(me.getKey());
                                b.setType(Material.AIR);
                                w.spawnParticle(Particle.POOF, c, 20, 0.3, 0.4, 0.3, 0.05);
                                continue;
                            }
                            en.lit = true;
                        }
                    }
                }
                case WAYSTONE -> {
                    if (en.lit) w.spawnParticle(Particle.ENCHANT, c.clone().add(0, 0.8, 0), 6, 0.3, 0.5, 0.3, 0.3);
                }
                case BROKEN_CHARTER_STONE -> {
                    w.spawnParticle(Particle.LARGE_SMOKE, c.clone().add(0, 0.8, 0), 3, 0.3, 0.5, 0.3, 0.01);
                    if (pl.getConfig().getBoolean("charter.broken-stone-effects", true)
                            && rnd.nextDouble() < RANDOM_TICK_CHANCE) {
                        for (LivingEntity le : w.getNearbyLivingEntities(c, 10)) {
                            if (le instanceof Monster) {
                                le.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 640, 2));
                                le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20, 1));
                            } else if (le instanceof Player) {
                                le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 640, 2));
                                le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 640, 5));
                                le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 120, 1));
                            }
                        }
                    }
                }
                case BROKEN_WAYSTONE -> w.spawnParticle(Particle.SMOKE, c.clone().add(0, 0.8, 0), 2, 0.3, 0.4, 0.3, 0.01);
                default -> {}
            }
        }
        if (tick % 1200 == 0) save();
    }

    private static boolean soulSource(Material m) {
        return m == Material.SOUL_FIRE || m == Material.SOUL_LANTERN || m == Material.SOUL_CAMPFIRE
                || m == Material.SOUL_TORCH || m == Material.SOUL_WALL_TORCH;
    }

    // ---------- Soul Sand Swapper ----------

    private void swapTick() {
        for (Map.Entry<String, Entry> me : new ArrayList<>(map.entrySet())) {
            Entry en = me.getValue();
            if (en.type != BlockType.SWAPPER) continue;
            String[] k = me.getKey().split(";");
            World w = Bukkit.getWorld(k[0]);
            int x = Integer.parseInt(k[1]), z = Integer.parseInt(k[3]);
            if (w == null || !w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block b = w.getBlockAt(x, Integer.parseInt(k[2]), z);
            if (b.getType() != BlockType.SWAPPER.material) continue;
            boolean pow = b.isBlockIndirectlyPowered();
            if (pow && !en.powered) swap(b);
            en.powered = pow;
        }
    }

    private void swap(Block s) {
        if (!(s.getBlockData() instanceof Orientable o)) return;
        BlockFace f = switch (o.getAxis()) {
            case X -> BlockFace.EAST;
            case Y -> BlockFace.UP;
            case Z -> BlockFace.SOUTH;
        };
        Block b1 = s.getRelative(f);
        Block b2 = s.getRelative(f.getOppositeFace());
        Entry e1 = entry(b1), e2 = entry(b2);

        // 1) Gewoehnliche Bloecke tauschen den Platz
        if (e1 == null && e2 == null && !(b1.getState() instanceof TileState) && !(b2.getState() instanceof TileState)) {
            BlockData d1 = b1.getBlockData(), d2 = b2.getBlockData();
            b1.setBlockData(d2);
            b2.setBlockData(d1);
        }
        // 2) Zwei beschriebene Pact Vessels: Vertraege und die Verschuldeten tauschen sich
        if (e1 != null && e2 != null && e1.type == BlockType.PACT_VESSEL && e2.type == BlockType.PACT_VESSEL
                && e1.lit && e2.lit && e1.contract != null && e2.contract != null) {
            UUID u1 = ContractData.indebted(e1.contract), u2 = ContractData.indebted(e2.contract);
            ItemStack c1 = e1.contract, c2 = e2.contract;
            e1.contract = c2;
            e2.contract = c1;
            ensureDisplay(b1, e1);
            ensureDisplay(b2, e2);
            Player p1 = u1 == null ? null : Bukkit.getPlayer(u1);
            Player p2 = u2 == null ? null : Bukkit.getPlayer(u2);
            if (p1 != null && p2 != null && p1 != p2 && p1.getWorld() == p2.getWorld()) swapPlayers(p1, p2);
            save();
        }
        s.getWorld().playSound(s.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_PISTON_EXTEND, 1f, 0.6f);
    }

    private record Snap(ItemStack[] contents, double health, Collection<PotionEffect> effects, Location loc) {}

    private Snap take(Player p) {
        return new Snap(p.getInventory().getContents().clone(), p.getHealth(),
                new ArrayList<>(p.getActivePotionEffects()), p.getLocation());
    }

    private void apply(Player p, Snap s) {
        p.getInventory().setContents(s.contents());
        for (PotionEffect pe : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(pe.getType());
        for (PotionEffect pe : s.effects()) p.addPotionEffect(pe);
        double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.max(0.5, Math.min(max, s.health())));
        p.teleport(s.loc());
    }

    /** Koerpertausch: Inventar, Leben, Effekte und Position wechseln. */
    private void swapPlayers(Player a, Player b) {
        Snap sa = take(a), sb = take(b);
        a.getWorld().spawnParticle(Particle.SOUL, a.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
        b.getWorld().spawnParticle(Particle.SOUL, b.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
        apply(a, sb);
        apply(b, sa);
        a.getWorld().playSound(a.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        b.getWorld().playSound(b.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
    }

    // ---------- Persistenz ----------

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> m : y.getMapList("blocks")) {
            try {
                BlockType t = BlockType.valueOf(String.valueOf(m.get("type")));
                Entry en = new Entry(t);
                en.lit = Boolean.parseBoolean(String.valueOf(m.get("lit")));
                Object c = m.get("contract");
                if (c != null) en.contract = ItemStack.deserializeBytes(Base64.getDecoder().decode(String.valueOf(c)));
                map.put(String.valueOf(m.get("key")), en);
            } catch (Exception ex) {
                pl.getLogger().warning("Ungueltiger Eintrag in blocks.yml uebersprungen: " + ex.getMessage());
            }
        }
    }

    public void save() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Entry> me : map.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", me.getKey());
            m.put("type", me.getValue().type.name());
            m.put("lit", me.getValue().lit);
            if (me.getValue().contract != null) {
                m.put("contract", Base64.getEncoder().encodeToString(me.getValue().contract.serializeAsBytes()));
            }
            list.add(m);
        }
        YamlConfiguration y = new YamlConfiguration();
        y.set("blocks", list);
        try {
            pl.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            pl.getLogger().warning("blocks.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }
}
