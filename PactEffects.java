package dev.charter;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Die zwei Effekte der Mod, nachgebildet ohne Client-Mod:
 * - Eternal Indenture: Saettigung bleibt 0, Wasser = Wither, ueberlebt "unblockbare" Todesursachen mit 1 HP.
 * - Soul Strain: erlittener Schaden wird verdoppelt.
 */
public final class PactEffects implements Listener {

    private record Strain(long expires, int amp) {}

    private static final Set<EntityDamageEvent.DamageCause> SAVEABLE = EnumSet.of(
            EntityDamageEvent.DamageCause.VOID, EntityDamageEvent.DamageCause.SUICIDE,
            EntityDamageEvent.DamageCause.STARVATION, EntityDamageEvent.DamageCause.DROWNING,
            EntityDamageEvent.DamageCause.WITHER, EntityDamageEvent.DamageCause.MAGIC,
            EntityDamageEvent.DamageCause.POISON, EntityDamageEvent.DamageCause.FALL);

    private final CharterPlugin pl;
    private final Map<UUID, Long> indenture = new HashMap<>();
    private final Map<UUID, Strain> strain = new HashMap<>();
    private int tick;

    public PactEffects(CharterPlugin pl) {
        this.pl = pl;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, pl);
        Bukkit.getScheduler().runTaskTimer(pl, this::run, 5, 5);
    }

    public void applyIndenture(Player p, int ticks) {
        indenture.put(p.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
    }

    public void applyStrain(Player p, int ticks, int amp) {
        strain.put(p.getUniqueId(), new Strain(System.currentTimeMillis() + ticks * 50L, Math.max(0, amp)));
    }

    public boolean hasIndenture(Player p) {
        Long t = indenture.get(p.getUniqueId());
        return t != null && t > System.currentTimeMillis();
    }

    public boolean hasStrain(Player p) {
        Strain s = strain.get(p.getUniqueId());
        return s != null && s.expires() > System.currentTimeMillis();
    }

    private void run() {
        tick += 5;
        long now = System.currentTimeMillis();
        indenture.values().removeIf(t -> t <= now);
        strain.values().removeIf(s -> s.expires() <= now);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (hasIndenture(p)) {
                p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                        p.getLocation().add((Math.random() - 0.5) * 0.3, 0.1, (Math.random() - 0.5) * 0.3),
                        1, 0, 0.02, 0, 0.02);
                if (tick % 20 == 0) {
                    p.setSaturation(0f);
                    if (p.isInWater()) {
                        boolean river = p.getLocation().getBlock().getBiome() == Biome.RIVER;
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, river ? 60 : 40, river ? 2 : 1, false, false));
                    }
                }
            }
            if (hasStrain(p) && tick % 20 == 0) {
                p.getWorld().spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.01);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && hasStrain(p)) {
            e.setDamage(e.getDamage() * 2.0);
        }
    }

    /** Eternal Indenture wirkt wie ein Totem gegen unblockbaren Schaden. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !hasIndenture(p)) return;
        if (!SAVEABLE.contains(e.getCause())) return;
        if (p.getHealth() - e.getFinalDamage() > 0) return;
        e.setCancelled(true);
        p.setHealth(1.0);
        PotionEffect weak = p.getPotionEffect(PotionEffectType.WEAKNESS);
        if (weak != null && weak.getAmplifier() > 1) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 300, 4));
            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 300, 4));
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 600, 1));
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 300, 2));
        p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Zustand bleibt bis zum Ablauf erhalten, damit Ausloggen keinen Effekt aufhebt.
    }
}
