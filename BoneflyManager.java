package dev.charter;

import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Horse;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Boneflies: unsichtbares, flugfaehiges Reittier (Horse ohne Gravitation) mit
 * ItemDisplay-Modell. Gesteuert ueber WASD, Leertaste = steigen, Sprint = schneller.
 */
public final class BoneflyManager implements Listener {

    private static final class Bf {
        final Player owner;
        final Horse horse;
        final ItemDisplay model;
        final boolean ayl;
        int age;
        int idle;
        boolean flapUp;

        Bf(Player owner, Horse horse, ItemDisplay model, boolean ayl) {
            this.owner = owner;
            this.horse = horse;
            this.model = model;
            this.ayl = ayl;
        }
    }

    private final CharterPlugin pl;
    private final Map<UUID, Bf> byOwner = new HashMap<>();
    private final Map<UUID, Bf> byHorse = new HashMap<>();
    private final NamespacedKey horseKey;
    private final NamespacedKey lastKey;

    public BoneflyManager(CharterPlugin pl) {
        this.pl = pl;
        this.horseKey = new NamespacedKey(pl, "bonefly");
        this.lastKey = new NamespacedKey(pl, "last_bonefly");
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, pl);
        Bukkit.getScheduler().runTaskTimer(pl, this::tick, 1, 1);
    }

    public void shutdown() {
        for (Bf b : new ArrayList<>(byOwner.values())) despawn(b, false);
    }

    // ---------- Beschwoeren ----------

    public void summon(Player p, boolean ayl, Location at, boolean mount) {
        Bf old = byOwner.get(p.getUniqueId());
        if (old != null) despawn(old, false);

        Horse horse = at.getWorld().spawn(at, Horse.class, h -> {
            h.setTamed(true);
            h.setOwner(p);
            h.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            h.setInvisible(true);
            h.setSilent(true);
            h.setInvulnerable(true);
            h.setGravity(false);
            h.setCollidable(false);
            h.setPersistent(false);
            h.getPersistentDataContainer().set(horseKey, PersistentDataType.STRING, p.getUniqueId().toString());
        });
        ItemDisplay model = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(Items.display(ayl ? "aylythian_bonefly" : "bonefly"));
            d.setBillboard(Display.Billboard.FIXED);
            d.setPersistent(false);
            d.setTeleportDuration(2);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(2.2f, 2.2f, 2.2f), new AxisAngle4f()));
        });

        Bf bf = new Bf(p, horse, model, ayl);
        byOwner.put(p.getUniqueId(), bf);
        byHorse.put(horse.getUniqueId(), bf);
        p.getPersistentDataContainer().set(lastKey, PersistentDataType.STRING, ayl ? "ayl" : "bone");
        if (mount) horse.addPassenger(p);

        // Beschwoerungs-Animation
        Fx.spiralUp(pl, at, 1.0, 2.4, 25, ayl ? Particle.SOUL_FIRE_FLAME : Particle.ASH, null);
        at.getWorld().spawnParticle(Particle.SOUL, at.clone().add(0, 1, 0), 25, 0.5, 0.7, 0.5, 0.03);
        at.getWorld().playSound(at, Sound.ENTITY_SKELETON_HORSE_AMBIENT, 1f, ayl ? 1.4f : 0.8f);
        at.getWorld().playSound(at, Sound.BLOCK_BONE_BLOCK_PLACE, 1f, 0.7f);
    }

    /** Fuer den Caller Bell: letzte Variante zum Spieler rufen. */
    public boolean call(Player p) {
        String last = p.getPersistentDataContainer().get(lastKey, PersistentDataType.STRING);
        if (last == null) return false;
        Location at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2.5));
        summon(p, last.equals("ayl"), at, false);
        return true;
    }

    private void despawn(Bf b, boolean fx) {
        byOwner.remove(b.owner.getUniqueId());
        byHorse.remove(b.horse.getUniqueId());
        Location l = b.horse.getLocation();
        if (fx) {
            l.getWorld().spawnParticle(Particle.POOF, l.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
            l.getWorld().spawnParticle(Particle.SOUL, l.clone().add(0, 1, 0), 20, 0.5, 0.6, 0.5, 0.04);
            l.getWorld().playSound(l, Sound.ENTITY_SKELETON_HORSE_DEATH, 1f, 1.2f);
        }
        b.horse.eject();
        b.horse.remove();
        b.model.remove();
    }

    // ---------- Events ----------

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Items.Kind k = Items.kind(e.getItem());
        if (k != Items.Kind.BONEFLY && k != Items.Kind.AYLYTHIAN_BONEFLY) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Bf cur = byOwner.get(p.getUniqueId());
        if (cur != null) {
            despawn(cur, true);
            return;
        }
        Location at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(1.5));
        summon(p, k == Items.Kind.AYLYTHIAN_BONEFLY, at, true);
    }

    @EventHandler
    public void onHorseInteract(PlayerInteractEntityEvent e) {
        Bf b = byHorse.get(e.getRightClicked().getUniqueId());
        if (b == null) return;
        if (!b.owner.equals(e.getPlayer()) || e.getPlayer().isSneaking()) e.setCancelled(true);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        Bf b = byHorse.get(e.getDismounted().getUniqueId());
        if (b == null || !e.getEntity().equals(b.owner)) return;
        Bukkit.getScheduler().runTask(pl, () -> {
            if (byOwner.get(b.owner.getUniqueId()) == b) despawn(b, true);
        });
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (byHorse.containsKey(e.getEntity().getUniqueId())) e.setCancelled(true);
    }

    // ---------- Tick: Steuerung, Modell, Animation ----------

    private void tick() {
        int idleLimit = pl.getConfig().getInt("bonefly.idle-despawn-seconds", 60) * 20;
        for (Bf b : new ArrayList<>(byOwner.values())) {
            if (!b.owner.isOnline() || !b.horse.isValid()) {
                despawn(b, false);
                continue;
            }
            b.age++;
            boolean ridden = b.horse.getPassengers().contains(b.owner);
            if (ridden) {
                b.idle = 0;
                steer(b);
            } else if (++b.idle > idleLimit) {
                despawn(b, true);
                continue;
            } else {
                b.horse.setVelocity(b.horse.getVelocity().multiply(0.8));
            }
            syncModel(b);
            trail(b);
        }
    }

    private void steer(Bf b) {
        Player p = b.owner;
        Input in = p.getCurrentInput();
        double speed = pl.getConfig().getDouble("bonefly.speed", 0.85);
        if (in.isSprint()) speed *= pl.getConfig().getDouble("bonefly.sprint-multiplier", 1.6);
        double climb = pl.getConfig().getDouble("bonefly.climb", 0.5);

        Vector look = p.getLocation().getDirection();
        Vector fwd = look.clone().setY(0);
        if (fwd.lengthSquared() > 0) fwd.normalize();
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());

        double f = (in.isForward() ? 1 : 0) - (in.isBackward() ? 1 : 0);
        double s = (in.isRight() ? 1 : 0) - (in.isLeft() ? 1 : 0);

        Vector target = fwd.clone().multiply(f).add(right.multiply(s));
        if (target.lengthSquared() > 0) target.normalize().multiply(speed * 0.5);
        // Blickrichtung bestimmt die Hoehe beim Vorwaertsfliegen
        if (f > 0) target.setY(look.getY() * speed * 0.5);
        if (in.isJump()) target.setY(target.getY() + climb * 0.5);

        Vector vel = b.horse.getVelocity().multiply(0.75).add(target.multiply(0.25 * 2));
        b.horse.setVelocity(vel);
        b.horse.setRotation(p.getLocation().getYaw(), 0);
    }

    private void syncModel(Bf b) {
        Location l = b.horse.getLocation().add(0, 0.7, 0);
        l.setYaw(b.horse.getLocation().getYaw() + 180f);
        l.setPitch(0);
        b.model.teleport(l);

        if (b.age % 8 == 0) {
            b.flapUp = !b.flapUp;
            b.model.setInterpolationDelay(0);
            b.model.setInterpolationDuration(8);
            b.model.setTransformation(new Transformation(new Vector3f(),
                    new AxisAngle4f(b.flapUp ? 0.16f : -0.16f, 0, 0, 1),
                    new Vector3f(2.2f, 2.2f, 2.2f),
                    new AxisAngle4f()));
        }
    }

    private void trail(Bf b) {
        if (b.age % 2 != 0) return;
        Location l = b.horse.getLocation().add(0, 0.6, 0);
        if (b.ayl) {
            l.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, l, 2, 0.3, 0.2, 0.3, 0.01);
            l.getWorld().spawnParticle(Particle.DUST, l, 1, 0.3, 0.2, 0.3, 0, Fx.CYAN);
        } else {
            l.getWorld().spawnParticle(Particle.ASH, l, 3, 0.35, 0.25, 0.35, 0.01);
            l.getWorld().spawnParticle(Particle.SOUL, l, 1, 0.3, 0.2, 0.3, 0.01);
        }
    }
}
