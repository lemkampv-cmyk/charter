package dev.charter;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Caller Bell. Die genaue Funktion im Original ist unbekannt - hier: ruft das
 * zuletzt benutzte Bonefly des Spielers herbei (Annahme, per Code leicht aenderbar).
 */
public final class BellListener implements Listener {

    private final CharterPlugin pl;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public BellListener(CharterPlugin pl) {
        this.pl = pl;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, pl);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (Items.kind(e.getItem()) != Items.Kind.CALLER_BELL) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        long cd = pl.getConfig().getLong("caller-bell.cooldown-seconds", 8) * 1000L;
        Long last = cooldown.get(p.getUniqueId());
        if (last != null && now - last < cd) {
            Msg.to(p, "Die Glocke klingt noch nach.");
            return;
        }
        cooldown.put(p.getUniqueId(), now);

        // Klang-Animation: sich ausbreitende Ringe + Noten
        Location c = p.getLocation().add(0, 0.2, 0);
        p.getWorld().playSound(c, Sound.BLOCK_BELL_USE, 1.4f, 0.7f);
        for (int i = 0; i < 4; i++) {
            final double r = 1 + i * 1.5;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                Fx.ring(c, r, (int) (r * 14), Particle.DUST, Fx.GOLD);
                p.getWorld().spawnParticle(Particle.NOTE, c.clone().add(0, 1.6, 0), 2, r * 0.3, 0.3, r * 0.3, 1);
            }, i * 4L);
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!pl.boneflies().call(p)) Msg.to(p, "Niemand antwortet - du hast noch kein Bonefly gerufen.");
        }, 10);
    }
}
