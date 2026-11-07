package dev.charter;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Charters: Der Charter Stone erzeugt eine 65er-Wuerfelzone, Waystones erweitern sie um 33er-Wuerfel.
 * Nur Mitglieder duerfen in der Zone bauen/interagieren, Mitglieder bekommen dort Resistance.
 */
public final class CharterManager implements Listener {

    public static final class Area {
        final String world;
        final double cx, cy, cz, half;

        Area(String world, double cx, double cy, double cz, double half) {
            this.world = world;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.half = half;
        }

        boolean contains(Location l) {
            return l.getWorld() != null && l.getWorld().getName().equals(world)
                    && Math.abs(l.getX() - cx) <= half && Math.abs(l.getY() - cy) <= half
                    && Math.abs(l.getZ() - cz) <= half;
        }

        boolean centeredOn(Block b) {
            return b.getWorld().getName().equals(world) && b.getX() + 0.5 == cx
                    && b.getY() + 0.5 == cy && b.getZ() + 0.5 == cz;
        }

        Block centerBlock() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : w.getBlockAt((int) Math.floor(cx), (int) Math.floor(cy), (int) Math.floor(cz));
        }
    }

    public static final class Charter {
        public final UUID owner;
        public final String world;
        public final int sx, sy, sz;
        public final Set<UUID> members = new LinkedHashSet<>();
        public final List<Area> areas = new ArrayList<>();
        public long uses;

        Charter(UUID owner, String world, int sx, int sy, int sz) {
            this.owner = owner;
            this.world = world;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
        }

        Block stoneBlock() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : w.getBlockAt(sx, sy, sz);
        }

        public Area main() {
            return areas.get(0);
        }
    }

    private static final double MAIN_HALF = 32.5;
    private static final double WAYSTONE_HALF = 16.5;

    private final CharterPlugin pl;
    private final File file;
    private final List<Charter> charters = new ArrayList<>();
    private int tick;

    public CharterManager(CharterPlugin pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "charters.yml");
    }

    public void start() {
        load();
        Bukkit.getPluginManager().registerEvents(this, pl);
        Bukkit.getScheduler().runTaskTimer(pl, this::run, 20, 20);
    }

    // ---------- Abfragen ----------

    public Charter at(Location l) {
        Charter found = null;
        for (Charter c : charters) {
            for (Area a : c.areas) if (a.contains(l)) found = c;
        }
        return found;
    }

    public Charter at(Block b) {
        return at(b.getLocation().add(0.5, 0.5, 0.5));
    }

    /** Charter, dessen Hauptzone (um den Charter Stone) den Block enthaelt. */
    public Charter mainAt(Block b) {
        Location l = b.getLocation().add(0.5, 0.5, 0.5);
        for (Charter c : charters) if (c.main().contains(l)) return c;
        return null;
    }

    public Charter ownedBy(UUID id) {
        for (Charter c : charters) if (c.owner.equals(id)) return c;
        return null;
    }

    public boolean isOwner(UUID id) {
        return ownedBy(id) != null;
    }

    public boolean isInAnyCharter(UUID id) {
        for (Charter c : charters) if (c.members.contains(id)) return true;
        return false;
    }

    private boolean bypass(Player p) {
        return p.hasPermission("charter.bypass");
    }

    // ---------- Anlegen / Waystones ----------

    public boolean createCharter(Player p, Block stone) {
        if (at(stone) != null) {
            Msg.to(p, "Hier wirkt bereits ein Charter.");
            return false;
        }
        if (isOwner(p.getUniqueId())) {
            Msg.to(p, "Du besitzt bereits einen Charter.");
            return false;
        }
        if (isInAnyCharter(p.getUniqueId())) {
            Msg.to(p, "Du bist bereits Mitglied eines Charters.");
            return false;
        }
        Charter c = new Charter(p.getUniqueId(), stone.getWorld().getName(), stone.getX(), stone.getY(), stone.getZ());
        c.members.add(p.getUniqueId());
        c.areas.add(new Area(c.world, stone.getX() + 0.5, stone.getY() + 0.5, stone.getZ() + 0.5, MAIN_HALF));
        charters.add(c);
        save();

        Location loc = stone.getLocation().add(0.5, 0.5, 0.5);
        stone.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.6f);
        Fx.ring(loc.clone().add(0, -0.4, 0), 3, 50, Particle.DUST, Fx.CYAN);
        Fx.spiralUp(pl, loc, 1.2, 5, 40, Particle.SOUL_FIRE_FLAME, null);
        Msg.to(p, "Dein Charter ist errichtet.");
        return true;
    }

    /** @return true, wenn der Waystone in einem Charter steht und die Zone erweitert. */
    public boolean addWaystone(Block b) {
        Charter c = at(b);
        if (c == null) return false;
        c.areas.add(new Area(c.world, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, WAYSTONE_HALF));
        save();
        Location loc = b.getLocation().add(0.5, 0.5, 0.5);
        b.getWorld().playSound(loc, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.7f);
        Fx.ring(loc, 2, 30, Particle.DUST, Fx.CYAN);
        return true;
    }

    public void removeWaystoneArea(Block b) {
        boolean changed = false;
        for (Charter c : charters) {
            changed |= c.areas.removeIf(a -> a != c.main() && a.centeredOn(b));
        }
        if (changed) save();
    }

    // ---------- Tick ----------

    private void run() {
        tick++;
        long perSecond = pl.getConfig().getLong("charter.uses-per-second", 20);
        int resist = pl.getConfig().getInt("charter.resistance-ticks", 1000);
        for (Charter c : new ArrayList<>(charters)) {
            World w = Bukkit.getWorld(c.world);
            if (w == null) continue;
            Block stone = c.stoneBlock();
            if (pl.blocks().type(stone) != BlockManager.BlockType.CHARTER_STONE) {
                die(c);
                continue;
            }
            c.uses += perSecond;

            if (w.isChunkLoaded(c.sx >> 4, c.sz >> 4)) {
                Block up = stone.getRelative(0, 1, 0);
                if (!up.getType().isAir() && up.getType() != Material.WATER) up.breakNaturally();
                w.spawnParticle(Particle.SOUL, stone.getLocation().add(0.5, 1.5, 0.5), 2, 0.3, 0.6, 0.3, 0.01);
            }
            for (UUID id : c.members) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                if (c.main().contains(p.getLocation()) || at(p.getLocation()) == c) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, resist, 0, false, true, true));
                }
                if (c.uses > 10000) {
                    pl.effects().applyStrain(p, 1000, (int) (c.uses / 10000) - 1);
                }
            }
        }
        if (tick % 60 == 0) save();
    }

    /** Der Stone ist weg: Waystones brechen, Mitglieder erblinden, der Charter erlischt. */
    private void die(Charter c) {
        for (Area a : new ArrayList<>(c.areas)) {
            Block b = a.centerBlock();
            if (b == null) continue;
            BlockManager.BlockType t = pl.blocks().type(b);
            if (t == BlockManager.BlockType.WAYSTONE) pl.blocks().convert(b, BlockManager.BlockType.BROKEN_WAYSTONE);
        }
        Block stone = c.stoneBlock();
        if (stone != null && pl.blocks().type(stone) == null) {
            pl.blocks().convert(stone, BlockManager.BlockType.BROKEN_CHARTER_STONE);
        }
        for (UUID id : c.members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 5));
                Msg.to(p, "Dein Charter ist erloschen.");
            }
        }
        charters.remove(c);
        save();
    }

    // ---------- Schutz ----------

    private boolean denied(Player p, Block b) {
        if (bypass(p)) return false;
        Charter c = at(b);
        return c != null && !c.members.contains(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (denied(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            Msg.to(e.getPlayer(), "Dieses Gebiet gehoert einem Charter.");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (denied(e.getPlayer(), e.getBlockPlaced())) {
            e.setCancelled(true);
            Msg.to(e.getPlayer(), "Dieses Gebiet gehoert einem Charter.");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (denied(e.getPlayer(), e.getClickedBlock())) {
            e.setCancelled(true);
        }
    }

    /** Mitglieder koennen den Besitzer nicht verletzen. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Charter c = ownedBy(victim.getUniqueId());
        if (c == null) return;
        Entity d = e.getDamager();
        Player attacker = d instanceof Player p ? p : (d instanceof Projectile pr && pr.getShooter() instanceof Player p ? p : null);
        if (attacker != null && c.members.contains(attacker.getUniqueId())) e.setCancelled(true);
    }

    /** Tod in der Zone: stirbt der Besitzer, bricht der Stone; stirbt ein Mitglied, bricht ein Waystone. */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Charter c = at(p.getLocation());
        if (c == null) return;
        boolean owner = c.owner.equals(p.getUniqueId());
        if (!owner && !c.members.contains(p.getUniqueId())) return;

        for (Area a : new ArrayList<>(c.areas)) {
            if (!a.contains(p.getLocation())) continue;
            Block b = a.centerBlock();
            if (b == null) continue;
            BlockManager.BlockType t = pl.blocks().type(b);
            if (t == BlockManager.BlockType.CHARTER_STONE && owner) {
                pl.blocks().breakCustom(b, false);
            } else if (t == BlockManager.BlockType.WAYSTONE) {
                c.uses += owner ? 1000 : 250;
                c.areas.remove(a);
                pl.blocks().breakCustom(b, true);
                pl.blocks().convert(b, BlockManager.BlockType.BROKEN_WAYSTONE);
            }
        }
        save();
    }

    // ---------- Persistenz ----------

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("charters");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(key);
            Charter ch = new Charter(UUID.fromString(key), c.getString("world", "world"),
                    c.getInt("x"), c.getInt("y"), c.getInt("z"));
            ch.uses = c.getLong("uses");
            for (String m : c.getStringList("members")) ch.members.add(UUID.fromString(m));
            for (String a : c.getStringList("areas")) {
                String[] p = a.split(",");
                ch.areas.add(new Area(ch.world, Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                        Double.parseDouble(p[2]), Double.parseDouble(p[3])));
            }
            if (ch.areas.isEmpty()) continue;
            charters.add(ch);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Charter c : charters) {
            String p = "charters." + c.owner;
            y.set(p + ".world", c.world);
            y.set(p + ".x", c.sx);
            y.set(p + ".y", c.sy);
            y.set(p + ".z", c.sz);
            y.set(p + ".uses", c.uses);
            y.set(p + ".members", c.members.stream().map(UUID::toString).toList());
            y.set(p + ".areas", c.areas.stream()
                    .map(a -> a.cx + "," + a.cy + "," + a.cz + "," + a.half).toList());
        }
        try {
            pl.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            pl.getLogger().warning("charters.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    public List<Charter> all() {
        return charters;
    }
}
