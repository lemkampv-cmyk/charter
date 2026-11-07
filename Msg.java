package dev.charter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public final class Msg {
    private Msg() {}

    public static void to(CommandSender target, String text) {
        target.sendMessage(Component.text("[Charter] ", NamedTextColor.DARK_RED)
                .append(Component.text(text, NamedTextColor.GRAY)));
    }
}
