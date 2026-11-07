package dev.charter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * Schwert, das toedliche Treffer in Ketten verwandelt: das Opfer bleibt auf einem halben Herz,
 * ist festgekettet und unverwundbar. Ein weiterer Treffer loest die Vollstreckungs-Animation aus
 * (Lichtstrahl, gelber Burst, lila Wirbel, Meldung).
 */
public final class ChainSwordManager implements Listener {

    private static final Particle.DustOptions YELLOW = new Particle.DustOptions(Color.fromRGB(255, 235, 60), 1.8f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.6f);
    private static final Particle.DustOptions PURPLE = new Particle.DustOptions(Color.fromRGB(170, 70, 230), 1.4f);
    private static final Particle.DustOptions IRON = new Particle.DustOptions(Color.fromRGB(150, 150, 160), 1.1f);

    private static final class Chained {
        final UUID attacker;
        final List<ItemDisplay> links = new ArrayList<>();
        final long since = System.currentTimeMillis();
        boolean finishing;
        int age;

        Chained(UUID attacker) {
            this.attacker = attacker;
        }
    }

    private final CharterPlugin pl;
    private final Map<UUID, Chained> chained = new HashMap<>();
    private final Set<UUID> repurposed = new HashSet<>();

    public ChainSwordManager(CharterPlugin pl) {
        this.pl = pl;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, pl);
        Bukkit.getScheduler().runTaskTimer(pl, this::tick, 2, 2);
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(chained.keySet())) {
            Player p = Bukkit.getPlayer(id);
            release(id, p);
        }
    }

    public boolean isChained(Player p) {
        return chained.containsKey(p.getUniqueId());
    }

    // ---------- Treffer ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (victim.getGameMode() == GameMode.CREATIVE || victim.getGameMode() == GameMode.SPECTATOR) return;
        Player attacker = e.getDamager() instanceof Player a ? a : null;
        boolean sword = attacker != null
                && Items.kind(attacker.getInventory().getItemInMainHand()) == Items.Kind.CHAIN_SWORD;

        Chained ch = chained.get(victim.getUniqueId());
        if (ch != null) {
            e.setCancelled(true);
            if (sword && !ch.finishing) finish(victim, attacker);
            return;
        }
        if (!sword) return;

        e.setDamage(e.getDamage() * pl.getConfig().getDouble("chain-sword.damage-multiplier", 1.3));
        if (victim.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            chain(victim, attacker);
        }
    }

    /** Alles andere Schaden/Heilung am angeketteten Spieler wird unterbunden. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnyDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && chained.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onRegain(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p && chained.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    // ---------- Festketten ----------

    private void chain(Player victim, Player attacker) {
        Chained ch = new Chained(attacker.getUniqueId());
        chained.put(victim.getUniqueId(), ch);

        victim.setHealth(1.0); // halbes Herz
        victim.eject();
        victim.leaveVehicle();
        victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 128, false, false, false));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 6, false, false, false));
        victim.sendActionBar(Component.text("Du wurdest in Ketten gelegt.", NamedTextColor.GRAY));

        Location base = victim.getLocation();
        World w = base.getWorld();
        for (int i = 0; i < 4; i++) {
            ItemDisplay d = w.spawn(base.clone().add(0, 3, 0), ItemDisplay.class, disp -> {
                disp.setItemStack(Items.display("chain_link"));
                disp.setBillboard(Display.Billboard.FIXED);
                disp.setPersistent(false);
                disp.setTeleportDuration(2);
                disp.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(1.0f, 2.2f, 1.0f), new AxisAngle4f()));
            });
            ch.links.add(d);
        }
        // Ketten fallen von oben herab und schliessen sich
        w.playSound(base, Sound.BLOCK_ANVIL_PLACE, 1f, 0.6f);
        w.playSound(base, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1f, 0.5f);
        w.playSound(base, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.2f, 0.5f);
        Fx.ring(base.clone().add(0, 0.1, 0), 0.9, 30, Particle.DUST, IRON);
        w.spawnParticle(Particle.CRIT, base.clone().add(0, 1, 0), 25, 0.4, 0.7, 0.4, 0.2);
        w.spawnParticle(Particle.ENCHANTED_HIT, base.clone().add(0, 1, 0), 20, 0.5, 0.8, 0.5, 0.1);
    }

    private void tick() {
        long timeout = pl.getConfig().getLong("chain-sword.chain-timeout-seconds", 0) * 1000L;
        for (Map.Entry<UUID, Chained> en : new ArrayList<>(chained.entrySet())) {
            Player p = Bukkit.getPlayer(en.getKey());
            Chained ch = en.getValue();
            if (p == null || !p.isOnline() || p.isDead()) {
                release(en.getKey(), p);
                continue;
            }
            if (ch.finishing) continue;
            if (timeout > 0 && System.currentTimeMillis() - ch.since > timeout) {
                release(en.getKey(), p);
                Msg.to(p, "Die Ketten loesen sich.");
                continue;
            }
            ch.age += 2;
            p.setHealth(1.0);

            Location l = p.getLocation();
            double spin = ch.age * 0.06;
            int n = ch.links.size();
            for (int i = 0; i < n; i++) {
                double a = spin + i * (2 * Math.PI / n);
                Location t = l.clone().add(Math.cos(a) * 0.6, 1.0 + Math.sin(ch.age * 0.15 + i) * 0.05, Math.sin(a) * 0.6);
                t.setYaw((float) Math.toDegrees(a) + 90f);
                ch.links.get(i).teleport(t);
            }
            if (ch.age % 10 == 0) {
                l.getWorld().spawnParticle(Particle.DUST, l.clone().add(0, 1, 0), 6, 0.5, 0.8, 0.5, 0, IRON);
            }
        }
    }

    private void release(UUID id, Player p) {
        Chained ch = chained.remove(id);
        if (ch == null) return;
        for (ItemDisplay d : ch.links) d.remove();
        if (p != null) {
            p.removePotionEffect(PotionEffectType.JUMP_BOOST);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }

    // ---------- Festhalten ----------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!chained.containsKey(e.getPlayer().getUniqueId())) return;
        Location f = e.getFrom(), t = e.getTo();
        if (t == null) return;
        if (f.getX() != t.getX() || f.getY() != t.getY() || f.getZ() != t.getZ()) {
            Location n = f.clone();
            n.setYaw(t.getYaw());
            n.setPitch(t.getPitch());
            e.setTo(n);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (chained.containsKey(e.getPlayer().getUniqueId())
                && e.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            e.setCancelled(true);
        }
    }

    /** Ausloggen aus den Ketten gilt nicht: Vollstreckung sofort. */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Chained ch = chained.get(p.getUniqueId());
        if (ch == null) return;
        release(p.getUniqueId(), p);
        repurposed.add(p.getUniqueId());
        p.setHealth(0);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        release(p.getUniqueId(), p);
        if (repurposed.remove(p.getUniqueId())) {
            e.deathMessage(Component.text(p.getName() + "'s existence was repurposed", NamedTextColor.LIGHT_PURPLE));
        }
    }

    // ---------- Vollstreckung ----------

    private void finish(Player victim, Player attacker) {
        Chained ch = chained.get(victim.getUniqueId());
        if (ch == null || ch.finishing) return;
        ch.finishing = true;
        for (ItemDisplay d : ch.links) d.remove();
        ch.links.clear();
        World w = victim.getWorld();
        Location base = victim.getLocation();
        double height = 40;
        w.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.7f);
        w.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 2f, 0.5f);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!victim.isOnline() || victim.isDead()) {
                    release(victim.getUniqueId(), victim);
                    cancel();
                    return;
                }
                Location b = victim.getLocation();

                // 1) Lichtstrahl: breit oben, schmal unten
                if (t <= 45) {
                    for (double h = 0; h <= height; h += 2) {
                        double r = 0.15 + 1.6 * (h / height);
                        for (int k = 0; k < 3; k++) {
                            double a = t * 0.4 + k * (2 * Math.PI / 3);
                            w.spawnParticle(Particle.DUST, b.getX() + Math.cos(a) * r, b.getY() + h,
                                    b.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, (h % 4 == 0) ? WHITE : YELLOW);
                        }
                    }
                    w.spawnParticle(Particle.END_ROD, b.clone().add(0, 1, 0), 2, 0.15, 0.6, 0.15, 0.02);
                }

                // 2) Gelber Burst (Kugel, expandierend)
                if (t >= 30 && t <= 38) {
                    double r = 0.6 + (t - 30) * 0.45;
                    int pts = 90;
                    double ga = Math.PI * (3 - Math.sqrt(5));
                    for (int i = 0; i < pts; i++) {
                        double y = 1 - (i / (double) (pts - 1)) * 2;
                        double rr = Math.sqrt(1 - y * y);
                        double a = ga * i;
                        w.spawnParticle(Particle.DUST, b.getX() + Math.cos(a) * rr * r, b.getY() + 1 + y * r,
                                b.getZ() + Math.sin(a) * rr * r, 1, 0, 0, 0, 0, YELLOW);
                    }
                    if (t == 30) {
                        w.playSound(b, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.4f);
                        w.playSound(b, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.6f);
                    }
                }

                // 3) Lila Wirbel um das Opfer
                if (t >= 30) {
                    for (int s = 0; s < 3; s++) {
                        double a = t * 0.5 + s * (2 * Math.PI / 3);
                        double y = ((t - 30) % 20) / 10.0;
                        w.spawnParticle(Particle.DUST, b.getX() + Math.cos(a) * 0.9, b.getY() + y,
                                b.getZ() + Math.sin(a) * 0.9, 1, 0, 0, 0, 0, PURPLE);
                    }
                    w.spawnParticle(Particle.WITCH, b.clone().add(0, 1, 0), 3, 0.4, 0.7, 0.4, 0.02);
                }

                // 4) Meldung
                if (t == 40) {
                    Bukkit.broadcast(Component.text(victim.getName() + "'s existence was repurposed",
                            NamedTextColor.LIGHT_PURPLE));
                }

                // 5) Abschluss
                if (t >= 60) {
                    finishAction(victim, attacker);
                    cancel();
                    return;
                }
                t++;
            }
        }.runTaskTimer(pl, 0, 1);
    }

    private void finishAction(Player victim, Player attacker) {
        String action = pl.getConfig().getString("chain-sword.finish-action", "kill").toLowerCase();
        release(victim.getUniqueId(), victim);
        switch (action) {
            case "ban" -> {
                victim.ban("Deine Existenz wurde umfunktioniert.", (Date) null, "Charter");
                victim.kick(Component.text("Deine Existenz wurde umfunktioniert.", NamedTextColor.LIGHT_PURPLE));
            }
            case "kick" -> victim.kick(Component.text("Deine Existenz wurde umfunktioniert.", NamedTextColor.LIGHT_PURPLE));
            default -> {
                repurposed.add(victim.getUniqueId());
                victim.setHealth(0);
            }
        }
    }
}
