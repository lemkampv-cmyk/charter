package dev.charter;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Mould of Souls: Waechter-Konstrukt, unterscheidet Freund (Besitzer + Vertraute) und Feind. */
public final class MouldManager implements Listener {

    private static final class Mould {
        final UUID id;
        final UUID owner;
        final Location loc;
        final Set<UUID> trusted = new HashSet<>();
        float spin;

        Mould(UUID id, UUID owner, Location loc) {
            this.id = id;
            this.owner = owner;
            this.loc = loc;
        }
    }

    private final CharterPlugin pl;
    private final File file;
    private final NamespacedKey idKey;
    private final Map<UUID, Mould> moulds = new LinkedHashMap<>();
    private int tick;

    public MouldManager(CharterPlugin pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "moulds.yml");
        this.idKey = new NamespacedKey(pl, "mould_id");
    }

    public void start() {
        load();
        Bukkit.getPluginManager().registerEvents(this, pl);
        Bukkit.getScheduler().runTaskTimer(pl, this::run, 20, 5);
    }

    // ---------- Platzieren / Einsammeln ----------

    @EventHandler
    public void onPlace(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (Items.kind(e.getItem()) != Items.Kind.SOUL_MOULD || e.getClickedBlock() == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Location loc = e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        Mould m = new Mould(UUID.randomUUID(), p.getUniqueId(), loc);
        moulds.put(m.id, m);
        spawnDisplay(m);
        spawnInteraction(m);
        e.getItem().setAmount(e.getItem().getAmount() - 1);
        save();

        // Erweckungs-Animation
        World w = loc.getWorld();
        Fx.spiralUp(pl, loc, 0.9, 2.2, 30, Particle.SOUL_FIRE_FLAME, null);
        Fx.ring(loc.clone().add(0, 0.1, 0), pl.getConfig().getDouble("mould.radius", 8.0), 80, Particle.SOUL, null);
        w.playSound(loc, Sound.BLOCK_BELL_RESONATE, 1.2f, 0.5f);
        w.playSound(loc, Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 1f, 0.5f);
        Msg.to(p, "Mould of Souls erweckt. Vertraue Freunde mit /charter trust <Spieler>.");
    }

    @EventHandler
    public void onInteractMould(PlayerInteractEntityEvent e) {
        String id = e.getRightClicked().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        Mould m = moulds.get(UUID.fromString(id));
        if (m == null) return;
        Player p = e.getPlayer();
        if (p.getUniqueId().equals(m.owner) && p.isSneaking()) {
            remove(m);
            p.getInventory().addItem(Items.create(Items.Kind.SOUL_MOULD, 1))
                    .values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
            Msg.to(p, "Mould eingesammelt.");
        } else if (p.getUniqueId().equals(m.owner)) {
            Msg.to(p, "Sneak + Rechtsklick zum Einsammeln.");
        } else {
            Msg.to(p, "Der Mould gehoert " + name(m.owner) + ".");
        }
    }

    private void remove(Mould m) {
        moulds.remove(m.id);
        for (Entity en : m.loc.getWorld().getNearbyEntities(m.loc.clone().add(0, 1, 0), 1.5, 2.5, 1.5)) {
            if (m.id.toString().equals(en.getPersistentDataContainer().get(idKey, PersistentDataType.STRING))) {
                en.remove();
            }
        }
        m.loc.getWorld().spawnParticle(Particle.POOF, m.loc.clone().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.05);
        m.loc.getWorld().playSound(m.loc, Sound.BLOCK_SOUL_SAND_BREAK, 1f, 0.6f);
        save();
    }

    // ---------- Vertrauen ----------

    public boolean trust(Player owner, OfflinePlayer target, boolean add) {
        Mould m = nearestOwned(owner);
        if (m == null) return false;
        if (add) m.trusted.add(target.getUniqueId());
        else m.trusted.remove(target.getUniqueId());
        save();
        return true;
    }

    private Mould nearestOwned(Player p) {
        Mould best = null;
        double bd = 16 * 16;
        for (Mould m : moulds.values()) {
            if (!m.owner.equals(p.getUniqueId()) || m.loc.getWorld() != p.getWorld()) continue;
            double d = m.loc.distanceSquared(p.getLocation());
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    private boolean friend(Mould m, Player p) {
        return m.owner.equals(p.getUniqueId()) || m.trusted.contains(p.getUniqueId());
    }

    private String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "unbekannt" : n;
    }

    // ---------- Entities ----------

    private void spawnDisplay(Mould m) {
        m.loc.getWorld().spawn(m.loc.clone().add(0, 1.1, 0), ItemDisplay.class, d -> {
            d.setItemStack(Items.display("soul_mould"));
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(0);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(1.7f, 1.7f, 1.7f), new AxisAngle4f()));
            d.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, m.id.toString());
        });
    }

    private void spawnInteraction(Mould m) {
        m.loc.getWorld().spawn(m.loc, Interaction.class, i -> {
            i.setInteractionWidth(0.9f);
            i.setInteractionHeight(2.1f);
            i.setResponsive(true);
            i.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, m.id.toString());
        });
    }

    private void ensureEntities(Mould m) {
        World w = m.loc.getWorld();
        int cx = m.loc.getBlockX() >> 4, cz = m.loc.getBlockZ() >> 4;
        if (!w.isChunkLoaded(cx, cz) || !w.getChunkAt(cx, cz, false).isEntitiesLoaded()) return;
        boolean hasD = false, hasI = false;
        for (Entity e : w.getNearbyEntities(m.loc.clone().add(0, 1, 0), 1.5, 2.5, 1.5)) {
            if (!m.id.toString().equals(e.getPersistentDataContainer().get(idKey, PersistentDataType.STRING))) continue;
            if (e instanceof ItemDisplay) hasD = true;
            if (e instanceof Interaction) hasI = true;
        }
        if (!hasD) spawnDisplay(m);
        if (!hasI) spawnInteraction(m);
    }

    // ---------- Waechter-Logik ----------

    private void run() {
        tick += 5;
        double r = pl.getConfig().getDouble("mould.radius", 8.0);
        double dmg = pl.getConfig().getDouble("mould.damage", 2.0);
        boolean monsters = pl.getConfig().getBoolean("mould.attack-monsters", true);

        for (Mould m : moulds.values()) {
            World w = m.loc.getWorld();
            if (w == null || !w.isChunkLoaded(m.loc.getBlockX() >> 4, m.loc.getBlockZ() >> 4)) continue;
            if (tick % 100 == 0) ensureEntities(m);

            // Idle-Animation: langsames Drehen + Seelenfunken
            if (tick % 40 == 0) {
                for (Entity e : w.getNearbyEntities(m.loc.clone().add(0, 1.1, 0), 1, 1, 1)) {
                    if (e instanceof ItemDisplay d
                            && m.id.toString().equals(d.getPersistentDataContainer().get(idKey, PersistentDataType.STRING))) {
                        m.spin += (float) Math.PI / 2f;
                        d.setInterpolationDelay(0);
                        d.setInterpolationDuration(40);
                        d.setTransformation(new Transformation(new Vector3f(0, 0.12f * (float) Math.sin(m.spin), 0),
                                new AxisAngle4f(m.spin, 0, 1, 0), new Vector3f(1.7f, 1.7f, 1.7f), new AxisAngle4f()));
                    }
                }
            }
            if (tick % 10 == 0) {
                w.spawnParticle(Particle.SOUL, m.loc.clone().add(0, 1.2, 0), 1, 0.4, 0.6, 0.4, 0.01);
            }

            if (tick % 20 != 0) continue;
            for (LivingEntity t : w.getNearbyLivingEntities(m.loc, r)) {
                if (t instanceof Player p) {
                    if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
                    if (friend(m, p)) {
                        if (tick % 80 == 0) {
                            Fx.beam(m.loc.clone().add(0, 1.2, 0), p.getLocation().add(0, 1, 0), Particle.END_ROD, null);
                        }
                        continue;
                    }
                    attack(m, p, dmg);
                } else if (monsters && t instanceof Monster) {
                    attack(m, t, dmg);
                }
            }
        }
    }

    private void attack(Mould m, LivingEntity t, double dmg) {
        Location from = m.loc.clone().add(0, 1.2, 0);
        Fx.beam(from, t.getLocation().add(0, 1, 0), Particle.SOUL_FIRE_FLAME, null);
        t.getWorld().spawnParticle(Particle.SCULK_SOUL, t.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.02);
        t.damage(dmg);
        t.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0));
        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_VEX_CHARGE, 0.8f, 0.8f);
    }

    // ---------- Persistenz ----------

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("moulds");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(key);
            World w = Bukkit.getWorld(c.getString("world", ""));
            if (w == null) continue;
            Mould m = new Mould(UUID.fromString(key), UUID.fromString(c.getString("owner")),
                    new Location(w, c.getDouble("x"), c.getDouble("y"), c.getDouble("z")));
            for (String t : c.getStringList("trusted")) m.trusted.add(UUID.fromString(t));
            moulds.put(m.id, m);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Mould m : moulds.values()) {
            String p = "moulds." + m.id;
            y.set(p + ".owner", m.owner.toString());
            y.set(p + ".world", m.loc.getWorld().getName());
            y.set(p + ".x", m.loc.getX());
            y.set(p + ".y", m.loc.getY());
            y.set(p + ".z", m.loc.getZ());
            y.set(p + ".trusted", m.trusted.stream().map(UUID::toString).toList());
        }
        try {
            pl.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            pl.getLogger().warning("moulds.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }
}
