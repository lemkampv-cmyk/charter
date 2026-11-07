package dev.charter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** Alle Charter-Items. Optik kommt ueber das Resource Pack (item_model Komponente). */
public final class Items {

    private static NamespacedKey KEY;

    public enum Kind {
        CONTRACT("contract", "Contract", NamedTextColor.DARK_RED,
                "Bindet einen Spieler als Verschuldeten.",
                "Rechtsklick: an dich binden. Rechtsklick auf einen Spieler: er muss gegenzeichnen."),
        BONEFLY("bonefly", "Bonefly", NamedTextColor.WHITE,
                "Skelettartiges Reittier.", "Rechtsklick: rufen / entlassen."),
        AYLYTHIAN_BONEFLY("aylythian_bonefly", "Aylythian Bonefly", NamedTextColor.AQUA,
                "Aylythische Variante des Boneflys.", "Rechtsklick: rufen / entlassen."),
        SOUL_MOULD("soul_mould", "Mould of Souls", NamedTextColor.DARK_AQUA,
                "Waechter-Konstrukt fuer ein Gebiet.", "Rechtsklick auf einen Block: platzieren."),
        CALLER_BELL("caller_bell", "Caller Bell", NamedTextColor.GOLD,
                "Ruft dein Bonefly zu dir."),
        BENEFACTOR_MARK("benefactor_mark", "Mark of the Benefactor", NamedTextColor.LIGHT_PURPLE,
                "Erlaubt, die Divine Dominance zu verlassen."),
        MERCHANT_CREST("merchant_crest", "Merchant's Crest", NamedTextColor.GOLD,
                "Mit einem Vertrag in der anderen Hand: Teleport zum Verschuldeten und zurueck.",
                "Im Inventar: saugt Leben vom nahen Verschuldeten."),
        ETERNAL_PACT("eternal_seal", "Eternal Pact", NamedTextColor.AQUA,
                "Im Inventar: Verschuldete deiner Vertraege stehen unter Eternal Indenture."),
        APEX_FOCUS("apex_focus", "Apex Focus", NamedTextColor.DARK_AQUA,
                "Zutat fuer den Charter Stone."),
        CHARTER_STONE("charter_stone", "Charter Stone", NamedTextColor.AQUA,
                "Errichtet einen Charter (65x65x65)."),
        CHARTER_VESSEL("charter_vessel", "Charter Vessel", NamedTextColor.WHITE,
                "Senkt die Abnutzung (Uses) deines Charters."),
        PACT_VESSEL("pact_press", "Pact Vessel", NamedTextColor.DARK_GRAY,
                "Nimmt einen Contract auf und macht den Verschuldeten zum Mitglied."),
        WAYSTONE("waystone", "Waystone", NamedTextColor.DARK_AQUA,
                "Erweitert einen Charter um eine 33er-Zone."),
        SOUL_SWAPPER("swapper", "Soul Sand Swapper", NamedTextColor.GRAY,
                "Mit Redstone: tauscht Bloecke, bei zwei Pact Vessels auch die Verschuldeten."),
        CHAIN_SWORD("chain_sword", "Sword of Repurposing", NamedTextColor.LIGHT_PURPLE,
                "Toedliche Treffer ketten das Opfer bei einem halben Herz fest.",
                "Ein weiterer Treffer vollstreckt."),
        DOMINANCE_BLOCK("divine_dominance", "Divine Dominance", NamedTextColor.GOLD,
                "Rechtsklick auf einen Block: erzeugt eine schrumpfende Border.",
                "Wer beim Schliessen noch drin ist, wird gebannt.");

        public final String id;
        public final String name;
        public final TextColor color;
        public final String[] lore;

        Kind(String id, String name, TextColor color, String... lore) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.lore = lore;
        }

        public static Kind byId(String id) {
            for (Kind k : values()) if (k.id.equalsIgnoreCase(id)) return k;
            return null;
        }
    }

    private Items() {}

    public static void init(Plugin plugin) {
        KEY = new NamespacedKey(plugin, "kind");
    }

    private static Component plain(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /** Basis-Material: Block-Items nutzen den passenden Vanilla-Block, damit sie platzierbar sind. */
    public static Material baseMaterial(Kind kind) {
        return switch (kind) {
            case CHAIN_SWORD -> Material.NETHERITE_SWORD;
            case CHARTER_STONE -> Material.LODESTONE;
            case WAYSTONE -> Material.CHISELED_DEEPSLATE;
            case PACT_VESSEL -> Material.LECTERN;
            case CHARTER_VESSEL -> Material.CHISELED_QUARTZ_BLOCK;
            case SOUL_SWAPPER -> Material.POLISHED_BASALT;
            default -> Material.PAPER;
        };
    }

    public static ItemStack create(Kind kind, int amount) {
        ItemStack it = new ItemStack(baseMaterial(kind), amount);
        it.editMeta(m -> {
            if (kind == Kind.CHAIN_SWORD) m.setUnbreakable(true);
            m.itemName(Component.text(kind.name, kind.color));
            List<Component> lore = new ArrayList<>();
            for (String l : kind.lore) lore.add(plain(l, NamedTextColor.GRAY));
            m.lore(lore);
            m.setItemModel(NamespacedKey.fromString("charter:" + kind.id));
            m.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, kind.id);
        });
        return it;
    }

    /** Reines Anzeige-Item fuer Display-Entities (z. B. roter Stoff). */
    public static ItemStack display(String model) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.editMeta(m -> m.setItemModel(NamespacedKey.fromString("charter:" + model)));
        return it;
    }

    public static Kind kind(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String id = stack.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        return id == null ? null : Kind.byId(id);
    }
}
