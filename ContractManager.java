package dev.charter;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Contract-Item.
 * - Rechtsklick (Luft/Block): bindet den Vertrag an den Benutzer selbst.
 * - Rechtsklick auf einen Spieler: bietet ihm den Vertrag an. Bestaetigt er per Rechtsklick auf
 *   den Anbieter, wird der Vertrag an IHN gebunden (Verschuldeter) und bleibt beim Anbieter.
 */
public final class ContractManager implements Listener {

    private record Pending(UUID proposer, long expires) {}

    private final CharterPlugin pl;
    private final Map<UUID, Pending> pending = new HashMap<>();   // Schluessel = Ziel

    public ContractManager(CharterPlugin pl) {
        this.pl = pl;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, pl);
    }

    public void save() {}

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (!ContractData.isContract(it) || ContractData.isBound(it)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        ContractData.bind(it, p);
        p.getInventory().setItemInMainHand(it);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.7f);
        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0, Fx.RED);
        Msg.to(p, "Der Vertrag ist jetzt an dich gebunden.");
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Player other)) return;
        Player p = e.getPlayer();

        // Gegenzeichnen: der Angesprochene rechtsklickt den Anbieter
        Pending pe = pending.get(p.getUniqueId());
        if (pe != null && pe.proposer().equals(other.getUniqueId())) {
            e.setCancelled(true);
            pending.remove(p.getUniqueId());
            if (System.currentTimeMillis() > pe.expires()) {
                Msg.to(p, "Das Angebot ist abgelaufen.");
                return;
            }
            ItemStack held = other.getInventory().getItemInMainHand();
            if (!ContractData.isContract(held) || ContractData.isBound(held)) {
                Msg.to(p, other.getName() + " hat keinen freien Vertrag mehr in der Hand.");
                return;
            }
            if (!closeEnough(p, other)) {
                Msg.to(p, "Ihr seid zu weit voneinander entfernt.");
                return;
            }
            ContractData.bind(held, p);
            other.getInventory().setItemInMainHand(held);
            Msg.to(p, "Du hast unterzeichnet: Du bist " + other.getName() + " verpflichtet.");
            Msg.to(other, p.getName() + " hat unterzeichnet. Der Vertrag liegt in deiner Hand.");
            animateSigning(other, p);
            return;
        }

        // Anbieten
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!ContractData.isContract(hand) || ContractData.isBound(hand)) return;
        e.setCancelled(true);
        if (!closeEnough(p, other)) {
            Msg.to(p, "Ihr seid zu weit voneinander entfernt.");
            return;
        }
        long ms = pl.getConfig().getLong("contract.timeout-seconds", 30) * 1000L;
        pending.put(other.getUniqueId(), new Pending(p.getUniqueId(), System.currentTimeMillis() + ms));
        Msg.to(p, "Vertrag angeboten. " + other.getName() + " muss dich jetzt rechtsklicken.");
        Msg.to(other, p.getName() + " bietet dir einen Vertrag an. Rechtsklicke " + p.getName()
                + ", um zu unterzeichnen. Danach bist du ihm verschuldet.");
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
        other.playSound(other.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
        for (Player x : new Player[]{p, other}) {
            x.getWorld().spawnParticle(Particle.DUST, x.getLocation().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0, Fx.RED);
        }
    }

    private boolean closeEnough(Player a, Player b) {
        double max = pl.getConfig().getDouble("contract.max-distance", 5.0);
        return a.getWorld() == b.getWorld() && a.getLocation().distanceSquared(b.getLocation()) <= max * max;
    }

    /** Roter Stoff erscheint zwischen den Parteien, Helix-Partikel, Glocke. */
    private void animateSigning(Player a, Player b) {
        Location mid = a.getLocation().add(b.getLocation()).multiply(0.5).add(0, 1.4, 0);
        World w = mid.getWorld();

        ItemDisplay cloth = w.spawn(mid, ItemDisplay.class, d -> {
            d.setItemStack(Items.display("red_cloth"));
            d.setBillboard(Display.Billboard.FIXED);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.01f, 0.01f, 0.01f), new AxisAngle4f()));
        });
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            cloth.setInterpolationDelay(0);
            cloth.setInterpolationDuration(25);
            cloth.setTransformation(new Transformation(new Vector3f(),
                    new AxisAngle4f((float) Math.PI / 3f, 0, 1, 0),
                    new Vector3f(0.9f, 0.03f, 0.9f), new AxisAngle4f()));
        }, 2);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            cloth.setInterpolationDelay(0);
            cloth.setInterpolationDuration(20);
            cloth.setTransformation(new Transformation(new Vector3f(0, 0.6f, 0),
                    new AxisAngle4f((float) Math.PI, 0, 1, 0),
                    new Vector3f(0.01f, 0.01f, 0.01f), new AxisAngle4f()));
        }, 60);
        Bukkit.getScheduler().runTaskLater(pl, cloth::remove, 85);

        Fx.spiralUp(pl, a.getLocation(), 0.8, 2.2, 40, Particle.DUST, Fx.RED);
        Fx.spiralUp(pl, b.getLocation(), 0.8, 2.2, 40, Particle.DUST, Fx.RED);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            Fx.ring(mid, 1.2, 40, Particle.DUST, Fx.RED);
            w.spawnParticle(Particle.ENCHANT, mid, 60, 0.8, 0.6, 0.8, 0.5);
            w.spawnParticle(Particle.CRIMSON_SPORE, mid, 40, 1, 0.8, 1, 0);
            w.playSound(mid, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.2f, 0.7f);
            w.playSound(mid, Sound.BLOCK_BELL_RESONATE, 1f, 0.6f);
        }, 40);
        w.playSound(mid, Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.6f);
    }
}
