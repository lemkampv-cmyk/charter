package dev.charter;

import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Divine Dominance: Ein platzierter Block erzeugt eine quadratische Border, die sich
 * stetig verkleinert. Nur Traeger der Mark of the Benefactor kommen heraus. Schliesst sich die
 * Border, werden alle noch eingeschlossenen Spieler gebannt.
 */
public final class DominanceManager implements Listener {

    public static final class Zone {
        final UUID id;
        final String world;
        final int bx, by, bz;
        double radius;
        final double shrinkPerSecond;
        final Set<UUID> bound = new HashSet<>();
        final Set<UUID> viewers = new HashSet<>();
        WorldBorder border;

        Zone(UUID id, String world, int bx, int by, int bz, double radius, double shrink) {
            this.id = id;
            this.world = world;
            this.bx = bx;
            this.by = by;
            this.bz = bz;
            this.radius = radius;
            this.shrinkPerSecond = shrink;
        }

        double cx() { return bx + 0.5; }
        double cz() { return bz + 0.5; }

        boolean contains(Location l) {
            if (l.getWorld() == null || !l.getWorld().getName().equals(world)) return false;
            return Math.abs(l.getX() - cx()) <= radius && Math.abs(l.getZ() - cz()) <= radius;
        }

        String shortId() { return id.toString().substring(0, 8); }
    }

    private final CharterPlugin pl;
    private final File file;
    private final Map<UUID, Zone> zones = new LinkedHashMap<>();
    private final Map<UUID, Long> msgCooldown = new HashMap<>();
    private int saveCounter;

    public DominanceManager(CharterPlugin pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "dominance.yml");
    }

    public void start() {
        load();
        Bukkit.getPluginManager().registerEvents(this, pl);
        Bukkit.getScheduler().runTaskTimer(pl, this::tick, 20, 20);
    }

    public Collection<Zone> zones() { return zones.values(); }

    public static boolean hasMark(Player p) {
        for (ItemStack s : p.getInventory().getContents()) {
            if (Items.kind(s) == Items.Kind.BENEFACTOR_MARK) return true;
        }
        return false;
    }

    private boolean exempt(Player p) {
        return hasMark(p) || p.hasPermission("charter.bypass");
    }

    // ---------- Platzieren ----------

    @EventHandler
    public void onPlace(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (Items.kind(e.getItem()) != Items.Kind.DOMINANCE_BLOCK || e.getClickedBlock() == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Block b = e.getClickedBlock().getRelative(e.getBlockFace());
        if (!b.getType().isAir()) return;
        for (Zone z : zones.values()) {
            if (z.world.equals(b.getWorld().getName())) {
                Msg.to(p, "In dieser Welt wirkt bereits eine Divine Dominance.");
                return;
            }
        }
        Material mat = Material.matchMaterial(pl.getConfig().getString("dominance.block-material", "CRYING_OBSIDIAN"));
        b.setType(mat == null ? Material.CRYING_OBSIDIAN : mat);
        e.getItem().setAmount(e.getItem().getAmount() - 1);

        double start = pl.getConfig().getDouble("dominance.start-radius", 60);
        double shrink = pl.getConfig().getDouble("dominance.shrink-per-second", 0.25);
        Zone z = new Zone(UUID.randomUUID(), b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), start, shrink);
        zones.put(z.id, z);
        save();

        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        World w = b.getWorld();
        w.strikeLightningEffect(c);
        w.playSound(c, Sound.ENTITY_WITHER_SPAWN, 2f, 0.5f);
        Fx.ring(c, 3, 60, Particle.DUST, Fx.GOLD);
        Fx.spiralUp(pl, c, 1.5, 6, 40, Particle.END_ROD, null);
        Bukkit.broadcast(Component.text("Eine Divine Dominance erhebt sich. Ihre Grenzen schliessen sich.", NamedTextColor.GOLD));
    }

    // ---------- Block-Schutz ----------

    private Zone zoneOfBlock(Block b) {
        for (Zone z : zones.values()) {
            if (z.world.equals(b.getWorld().getName()) && z.bx == b.getX() && z.by == b.getY() && z.bz == b.getZ()) return z;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Zone z = zoneOfBlock(e.getBlock());
        if (z == null) return;
        if (e.getPlayer().hasPermission("charter.admin")) {
            end(z, false);
            Msg.to(e.getPlayer(), "Divine Dominance beendet (keine Banns).");
        } else {
            e.setCancelled(true);
            Msg.to(e.getPlayer(), "Der Block der Dominance ist unzerstoerbar.");
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> zoneOfBlock(b) != null);
    }

    // ---------- Tick: Schrumpfen, Binden, Border ----------

    private void tick() {
        for (Zone z : new ArrayList<>(zones.values())) {
            World w = Bukkit.getWorld(z.world);
            if (w == null) continue;
            if (z.border == null) {
                z.border = Bukkit.createWorldBorder();
                z.border.setCenter(z.cx(), z.cz());
                z.border.setSize(z.radius * 2);
                z.border.setWarningDistance(20);
            }
            z.radius = Math.max(0, z.radius - z.shrinkPerSecond);
            z.border.setSize(Math.max(1, z.radius * 2), TimeUnit.SECONDS, 1);

            double bounce = pl.getConfig().getDouble("dominance.bound-damage", 2.0);
            Set<UUID> online = new HashSet<>();
            for (Player p : w.getPlayers()) {
                online.add(p.getUniqueId());
                boolean ex = exempt(p);
                if (!ex && z.contains(p.getLocation())) z.bound.add(p.getUniqueId());
                if (ex) z.bound.remove(p.getUniqueId());

                boolean isBound = z.bound.contains(p.getUniqueId());
                if (isBound && z.viewers.add(p.getUniqueId())) p.setWorldBorder(z.border);
                if (!isBound && z.viewers.remove(p.getUniqueId())) p.setWorldBorder(null);

                // Border drueckt gebundene Spieler zurueck nach innen
                if (isBound && !z.contains(p.getLocation())) {
                    Vector in = new Vector(z.cx() - p.getX(), 0, z.cz() - p.getZ());
                    if (in.lengthSquared() > 0) p.setVelocity(in.normalize().multiply(0.6).setY(0.2));
                    p.damage(bounce);
                    p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 10, 0.4, 0.6, 0.4, 0, Fx.RED);
                }
                if (isBound) warn(p, z);
            }
            // Ausgeloggte Spieler bleiben gebunden (Logout entkommt dem Bann nicht)

            // Atmosphaere am Block
            Location c = new Location(w, z.cx(), z.by + 0.5, z.cz());
            if (w.isChunkLoaded(z.bx >> 4, z.bz >> 4)) {
                w.spawnParticle(Particle.ENCHANT, c, 20, 0.8, 1.2, 0.8, 0.6);
                w.spawnParticle(Particle.END_ROD, c.clone().add(0, 1, 0), 3, 0.3, 0.8, 0.3, 0.02);
            }

            if (z.radius <= 0.5) close(z);
        }
        if (!zones.isEmpty() && ++saveCounter % 10 == 0) save();
    }

    private void warn(Player p, Zone z) {
        double secs = z.shrinkPerSecond > 0 ? z.radius / z.shrinkPerSecond : 9999;
        if (secs <= 60 && (int) secs % 10 == 0 || secs <= 10) {
            p.sendActionBar(Component.text("Die Dominance schliesst sich in " + (int) secs + "s", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, secs <= 10 ? 1.5f : 0.6f);
        }
    }

    // ---------- Schliessen ----------

    private void close(Zone z) {
        World w = Bukkit.getWorld(z.world);
        Location c = new Location(w, z.cx(), z.by + 0.5, z.cz());
        boolean ban = pl.getConfig().getBoolean("dominance.ban", true);
        long hours = pl.getConfig().getLong("dominance.ban-hours", 0);
        String reason = pl.getConfig().getString("dominance.ban-reason", "Von der Divine Dominance verschlungen.");
        Date until = hours > 0 ? new Date(System.currentTimeMillis() + hours * 3_600_000L) : null;

        for (UUID id : new ArrayList<>(z.bound)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.getWorld().spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
                p.getWorld().strikeLightningEffect(p.getLocation());
                if (ban) p.ban(reason, until, "Charter");
                p.kick(Component.text(reason, NamedTextColor.DARK_RED));
            } else if (ban) {
                Bukkit.getBanList(org.bukkit.BanList.Type.PROFILE)
                        .addBan(Bukkit.createProfile(id), reason, until, "Charter");
            }
        }
        if (w != null) {
            w.playSound(c, Sound.ENTITY_WITHER_DEATH, 2f, 0.6f);
            Fx.ring(c, 2, 50, Particle.SOUL_FIRE_FLAME, null);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 1);
        }
        Bukkit.broadcast(Component.text("Die Divine Dominance hat sich geschlossen.", NamedTextColor.DARK_RED));
        end(z, true);
    }

    /** Raeumt die Zone auf; Block entfernen, Borders zuruecksetzen. */
    public void end(Zone z, boolean closed) {
        for (UUID id : z.viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setWorldBorder(null);
        }
        World w = Bukkit.getWorld(z.world);
        if (w != null) w.getBlockAt(z.bx, z.by, z.bz).setType(Material.AIR);
        zones.remove(z.id);
        save();
    }

    public boolean cancel(String shortId) {
        for (Zone z : new ArrayList<>(zones.values())) {
            if (z.shortId().equalsIgnoreCase(shortId)) {
                end(z, false);
                return true;
            }
        }
        return false;
    }

    // ---------- Ausbruch verhindern ----------

    private boolean blocked(Player p, Location from, Location to) {
        if (to == null || exempt(p)) return false;
        for (Zone z : zones.values()) {
            if (z.bound.contains(p.getUniqueId()) && !z.contains(to)) return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location f = e.getFrom(), t = e.getTo();
        if (t == null || (f.getBlockX() == t.getBlockX() && f.getBlockZ() == t.getBlockZ())) return;
        if (!blocked(e.getPlayer(), f, t)) return;
        e.setCancelled(true);
        wall(e.getPlayer(), f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (blocked(e.getPlayer(), e.getFrom(), e.getTo())) {
            e.setCancelled(true);
            wall(e.getPlayer(), e.getFrom());
        }
    }

    @EventHandler
    public void onVehicle(VehicleMoveEvent e) {
        for (Entity pass : e.getVehicle().getPassengers()) {
            if (pass instanceof Player p && blocked(p, e.getFrom(), e.getTo())) {
                e.getVehicle().teleport(e.getFrom(), TeleportFlag.EntityState.RETAIN_PASSENGERS);
                e.getVehicle().setVelocity(new Vector());
                wall(p, e.getFrom());
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        for (Zone z : zones.values()) z.viewers.remove(e.getPlayer().getUniqueId());
    }

    private void wall(Player p, Location at) {
        p.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 18, 0.7, 0.9, 0.7, 0, Fx.GOLD);
        long now = System.currentTimeMillis();
        Long last = msgCooldown.get(p.getUniqueId());
        if (last == null || now - last > 3000) {
            msgCooldown.put(p.getUniqueId(), now);
            Msg.to(p, "Die Divine Dominance haelt dich fest. Nur die Mark of the Benefactor oeffnet den Weg.");
        }
    }

    // ---------- Persistenz ----------

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("zones");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(key);
            Zone z = new Zone(UUID.fromString(key), c.getString("world", "world"), c.getInt("x"), c.getInt("y"),
                    c.getInt("z"), c.getDouble("radius"), c.getDouble("shrink"));
            for (String b : c.getStringList("bound")) z.bound.add(UUID.fromString(b));
            zones.put(z.id, z);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Zone z : zones.values()) {
            String p = "zones." + z.id;
            y.set(p + ".world", z.world);
            y.set(p + ".x", z.bx);
            y.set(p + ".y", z.by);
            y.set(p + ".z", z.bz);
            y.set(p + ".radius", z.radius);
            y.set(p + ".shrink", z.shrinkPerSecond);
            y.set(p + ".bound", z.bound.stream().map(UUID::toString).toList());
        }
        try {
            pl.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            pl.getLogger().warning("dominance.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }
}
