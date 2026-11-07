package dev.charter;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CharterCommand implements TabExecutor {

    private final CharterPlugin pl;

    public CharterCommand(CharterPlugin pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            Msg.to(s, "/charter give <Spieler> <Item> [Anzahl] | charter | trust <Spieler> | untrust <Spieler>"
                    + " | dominance <list|cancel> | reload");
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "give" -> {
                if (!admin(s)) return true;
                if (a.length < 3) {
                    Msg.to(s, "/charter give <Spieler> <Item> [Anzahl]");
                    return true;
                }
                Player t = Bukkit.getPlayer(a[1]);
                Items.Kind k = Items.Kind.byId(a[2]);
                if (t == null || k == null) {
                    Msg.to(s, "Spieler oder Item unbekannt.");
                    return true;
                }
                int amount = a.length > 3 ? parse(a[3], 1) : 1;
                ItemStack it = Items.create(k, amount);
                t.getInventory().addItem(it).values().forEach(r -> t.getWorld().dropItemNaturally(t.getLocation(), r));
                Msg.to(s, amount + "x " + k.name + " an " + t.getName() + " gegeben.");
            }
            case "charter" -> {
                if (!(s instanceof Player p)) return true;
                CharterManager.Charter c = pl.charters().at(p.getLocation());
                if (c == null) {
                    Msg.to(s, "Hier wirkt kein Charter.");
                } else {
                    Msg.to(s, "Charter von " + name(c.owner) + ": " + c.members.size() + " Mitglieder, "
                            + c.areas.size() + " Zone(n), Uses: " + c.uses);
                }
            }
            case "trust", "untrust" -> {
                if (!(s instanceof Player p) || a.length < 2) {
                    Msg.to(s, "/charter trust <Spieler>");
                    return true;
                }
                OfflinePlayer t = Bukkit.getOfflinePlayer(a[1]);
                boolean add = a[0].equalsIgnoreCase("trust");
                if (!pl.moulds().trust(p, t, add)) Msg.to(s, "Kein eigener Mould im Umkreis von 16 Bloecken.");
                else Msg.to(s, a[1] + (add ? " wird jetzt als Freund erkannt." : " ist kein Freund mehr."));
            }
            case "dominance" -> {
                if (!admin(s)) return true;
                if (a.length < 2) {
                    Msg.to(s, "/charter dominance list | cancel <Id>  (Item: /charter give <Spieler> divine_dominance)");
                    return true;
                }
                switch (a[1].toLowerCase()) {
                    case "cancel" -> Msg.to(s, a.length > 2 && pl.dominance().cancel(a[2])
                            ? "Dominance beendet, niemand wurde gebannt." : "Zone nicht gefunden.");
                    case "list" -> pl.dominance().zones().forEach(z ->
                            Msg.to(s, z.shortId() + " @ " + z.world + " (" + z.bx + ", " + z.bz + ") r=" + (int) z.radius
                                    + ", gebunden: " + z.bound.size()));
                    default -> Msg.to(s, "Unbekannt.");
                }
            }
            case "reload" -> {
                if (!admin(s)) return true;
                pl.reloadConfig();
                Msg.to(s, "Config neu geladen.");
            }
            default -> Msg.to(s, "Unbekannter Unterbefehl.");
        }
        return true;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("charter.admin")) return true;
        Msg.to(s, "Dafuer fehlt dir die Berechtigung.");
        return false;
    }

    private String name(java.util.UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "unbekannt" : n;
    }

    private int parse(String v, int def) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("give", "charter", "trust", "untrust", "dominance", "reload"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            for (Items.Kind k : Items.Kind.values()) out.add(k.id);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("dominance")) {
            out.addAll(List.of("list", "cancel"));
        }
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
