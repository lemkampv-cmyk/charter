package dev.charter;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Merchant's Crest: mit Vertrag in der anderen Hand zum Verschuldeten teleportieren (und zurueck),
 * dazu passiv Leben vom Verschuldeten saugen. Eternal Pact: haelt jeden Verschuldeten
 * aus den Vertraegen im Inventar unter Eternal Indenture.
 */
public final class CrestManager implements Listener {

    private final CharterPlugin pl;
    private final NamespacedKey tpKey;
    private final NamespacedKey posKey;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private int tick;

    public CrestManager(CharterPlugin pl) {
        this.pl = pl;
        this.tpKey = new NamespacedKey(pl, "teleported");
        this.posKey = new NamespacedKey(pl, "prev_pos");
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, pl);
        Bukkit.getScheduler().runTaskTimer(pl, this::run, 10, 10);
    }

    // ---------- Teleport ----------

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        EquipmentSlot hand = e.getHand();
        if (hand == null) return;
        ItemStack crest = p.getInventory().getItem(hand);
        if (Items.kind(crest) != Items.Kind.MERCHANT_CREST) return;
        ItemStack other = p.getInventory().getItem(hand == EquipmentSlot.HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND);
        if (!ContractData.isBound(other)) return;
        e.setCancelled(true);

        long now = System.currentTimeMillis();
        Long until = cooldown.get(p.getUniqueId());
        if (until != null && until > now) return;
        cooldown.put(p.getUniqueId(), now + 2000);

        UUID id = ContractData.indebted(other);
        Player debtor = id == null ? null : Bukkit.getPlayer(id);
        if (debtor == null || debtor.getWorld() != p.getWorld()) {
            Msg.to(p, "Der Verschuldete ist nicht erreichbar.");
            return;
        }

        PersistentDataContainer pdc = crest.getItemMeta().getPersistentDataContainer();
        boolean away = Boolean.TRUE.equals(pdc.get(tpKey, PersistentDataType.BOOLEAN));
        World w = p.getWorld();
        if (!away) {
            Location l = p.getLocation();
            String packed = l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ()
                    + ";" + l.getYaw() + ";" + l.getPitch();
            crest.editMeta(m -> {
                m.getPersistentDataContainer().set(tpKey, PersistentDataType.BOOLEAN, true);
                m.getPersistentDataContainer().set(posKey, PersistentDataType.STRING, packed);
            });
            p.getInventory().setItem(hand, crest);
            fx(p.getLocation());
            p.teleport(debtor.getLocation());
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0, false, false));
            fx(p.getLocation());
        } else {
            String packed = pdc.get(posKey, PersistentDataType.STRING);
            if (packed != null) {
                String[] s = packed.split(";");
                World pw = Bukkit.getWorld(s[0]);
                if (pw != null) {
                    fx(p.getLocation());
                    p.teleport(new Location(pw, Double.parseDouble(s[1]), Double.parseDouble(s[2]),
                            Double.parseDouble(s[3]), Float.parseFloat(s[4]), Float.parseFloat(s[5])));
                    fx(p.getLocation());
                }
            }
            crest.editMeta(m -> {
                m.getPersistentDataContainer().set(tpKey, PersistentDataType.BOOLEAN, false);
                m.getPersistentDataContainer().remove(posKey);
            });
            p.getInventory().setItem(hand, crest);
            debtor.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false));
        }
        w.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
    }

    private void fx(Location l) {
        l.getWorld().spawnParticle(Particle.SOUL, l.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.05);
        l.getWorld().spawnParticle(Particle.DUST, l.clone().add(0, 1, 0), 15, 0.4, 0.8, 0.4, 0, Fx.GOLD);
    }

    // ---------- Passives ----------

    private void run() {
        tick += 10;
        boolean leech = pl.getConfig().getBoolean("crest.leech", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean crest = false, seal = false;
            for (ItemStack s : p.getInventory().getStorageContents()) {
                Items.Kind k = Items.kind(s);
                if (k == Items.Kind.MERCHANT_CREST) crest = true;
                if (k == Items.Kind.ETERNAL_PACT) seal = true;
            }
            if (!crest && !seal) continue;

            for (ItemStack s : p.getInventory().getStorageContents()) {
                if (!ContractData.isBound(s)) continue;
                UUID id = ContractData.indebted(s);
                Player debtor = id == null ? null : Bukkit.getPlayer(id);
                if (debtor == null || debtor.equals(p)) continue;

                if (seal && tick % 20 == 0) {
                    pl.effects().applyIndenture(debtor, 1600);
                }
                if (crest && leech && debtor.getWorld() == p.getWorld()
                        && debtor.getLocation().distanceSquared(p.getLocation()) <= 16 * 3
                        && p.getHealth() < p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                        && debtor.getHealth() > 1) {
                    p.setHealth(Math.min(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(), p.getHealth() + 1));
                    debtor.damage(1.0);
                    Fx.beam(debtor.getLocation().add(0, 1, 0), p.getLocation().add(0, 1, 0), Particle.DUST, Fx.RED);
                }
            }
        }
    }

    // ---------- Beute ----------

    @EventHandler
    public void onLoot(LootGenerateEvent e) {
        if (!"minecraft:chests/bastion_treasure".equals(e.getLootTable().getKey().toString())) return;
        double chance = pl.getConfig().getDouble("crest.bastion-chance", 0.25);
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            e.getLoot().add(Items.create(Items.Kind.MERCHANT_CREST, 1));
        }
    }
}
