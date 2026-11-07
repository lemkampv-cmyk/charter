package dev.charter;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

/** Crafting-Rezepte der Charter-Items. */
public final class Recipes {

    private Recipes() {}

    private static RecipeChoice soul() {
        return new RecipeChoice.MaterialChoice(Tag.SOUL_FIRE_BASE_BLOCKS);
    }

    private static RecipeChoice exact(Items.Kind k) {
        return new RecipeChoice.ExactChoice(Items.create(k, 1));
    }

    private static ShapedRecipe shaped(Plugin pl, String id, Items.Kind result, String... shape) {
        ShapedRecipe r = new ShapedRecipe(new NamespacedKey(pl, id), Items.create(result, 1));
        r.shape(shape);
        return r;
    }

    public static void register(Plugin pl) {
        ShapedRecipe r;

        r = shaped(pl, "charter_stone", Items.Kind.CHARTER_STONE, "DWD", "DFD", "DWD");
        r.setIngredient('D', Material.DEEPSLATE_TILES);
        r.setIngredient('F', exact(Items.Kind.APEX_FOCUS));
        r.setIngredient('W', exact(Items.Kind.WAYSTONE));
        add(r);

        r = shaped(pl, "charter_vessel", Items.Kind.CHARTER_VESSEL, "QIQ", "TST", "QTQ");
        r.setIngredient('T', Material.CHISELED_QUARTZ_BLOCK);
        r.setIngredient('I', Material.IRON_INGOT);
        r.setIngredient('Q', Material.QUARTZ);
        r.setIngredient('S', Material.AMETHYST_SHARD);
        add(r);

        r = shaped(pl, "contract", Items.Kind.CONTRACT, "LWP", "LSP");
        r.setIngredient('P', Material.PAPER);
        r.setIngredient('W', Material.WITHER_SKELETON_SKULL);
        r.setIngredient('L', Material.LEATHER);
        r.setIngredient('S', soul());
        add(r);

        r = shaped(pl, "contract_lategame", Items.Kind.CONTRACT, "LWP", "LSP");
        r.setIngredient('P', Material.PAPER);
        r.setIngredient('W', Material.WITHER_ROSE);
        r.setIngredient('L', Material.LEATHER);
        r.setIngredient('S', soul());
        add(r);

        r = shaped(pl, "eternal_seal", Items.Kind.ETERNAL_PACT, "IPI", "TPT", "IWI");
        r.setIngredient('T', Material.DEEPSLATE_TILES);
        r.setIngredient('P', exact(Items.Kind.PACT_VESSEL));
        r.setIngredient('I', Material.IRON_INGOT);
        r.setIngredient('W', Material.NETHER_STAR);
        add(r);

        r = shaped(pl, "pact_press", Items.Kind.PACT_VESSEL, "ICI", "TST", "ITI");
        r.setIngredient('T', Material.DEEPSLATE_TILES);
        r.setIngredient('C', Material.CHISELED_DEEPSLATE);
        r.setIngredient('I', Material.IRON_INGOT);
        r.setIngredient('S', soul());
        add(r);

        r = shaped(pl, "soul_swapper", Items.Kind.SOUL_SWAPPER, "ISI", "BCB", "ISI");
        r.setIngredient('B', Material.IRON_BARS);
        r.setIngredient('I', Material.IRON_INGOT);
        r.setIngredient('S', soul());
        r.setIngredient('C', Material.COAL_BLOCK);
        add(r);

        r = shaped(pl, "waystone", Items.Kind.WAYSTONE, "DDD", "DED", "DDD");
        r.setIngredient('D', Material.DEEPSLATE_TILES);
        r.setIngredient('E', Material.ENDER_PEARL);
        add(r);
    }

    private static void add(ShapedRecipe r) {
        Bukkit.removeRecipe(r.getKey());
        Bukkit.addRecipe(r);
    }
}
