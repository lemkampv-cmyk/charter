package dev.charter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Ein Contract ist an genau einen Verschuldeten (Spieler-UUID) gebunden. */
public final class ContractData {

    private static NamespacedKey UUID_KEY;
    private static NamespacedKey NAME_KEY;

    private ContractData() {}

    public static void init(Plugin pl) {
        UUID_KEY = new NamespacedKey(pl, "indebted_uuid");
        NAME_KEY = new NamespacedKey(pl, "indebted_name");
    }

    public static boolean isContract(ItemStack s) {
        return Items.kind(s) == Items.Kind.CONTRACT;
    }

    public static boolean isBound(ItemStack s) {
        if (!isContract(s)) return false;
        return s.getItemMeta().getPersistentDataContainer().has(UUID_KEY, PersistentDataType.STRING);
    }

    public static UUID indebted(ItemStack s) {
        if (!isBound(s)) return null;
        try {
            return UUID.fromString(s.getItemMeta().getPersistentDataContainer().get(UUID_KEY, PersistentDataType.STRING));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String indebtedName(ItemStack s) {
        if (!isBound(s)) return "";
        String n = s.getItemMeta().getPersistentDataContainer().get(NAME_KEY, PersistentDataType.STRING);
        return n == null ? "" : n;
    }

    public static void bind(ItemStack s, Player debtor) {
        s.editMeta(m -> {
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            pdc.set(UUID_KEY, PersistentDataType.STRING, debtor.getUniqueId().toString());
            pdc.set(NAME_KEY, PersistentDataType.STRING, debtor.getName());
            List<Component> lore = new ArrayList<>();
            if (m.lore() != null) lore.addAll(m.lore());
            lore.add(Component.text("Verschuldet: " + debtor.getName(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
        });
    }
}
