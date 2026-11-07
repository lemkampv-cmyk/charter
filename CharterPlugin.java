package dev.charter;

import org.bukkit.plugin.java.JavaPlugin;

public final class CharterPlugin extends JavaPlugin {

    private ContractManager contracts;
    private PactEffects effects;
    private BlockManager blocks;
    private CharterManager charters;
    private CrestManager crest;
    private BoneflyManager boneflies;
    private MouldManager moulds;
    private DominanceManager dominance;
    private BellListener bell;
    private ChainSwordManager chainSword;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Items.init(this);
        ContractData.init(this);

        contracts = new ContractManager(this);
        effects = new PactEffects(this);
        blocks = new BlockManager(this);
        charters = new CharterManager(this);
        crest = new CrestManager(this);
        boneflies = new BoneflyManager(this);
        moulds = new MouldManager(this);
        dominance = new DominanceManager(this);
        bell = new BellListener(this);
        chainSword = new ChainSwordManager(this);

        // Reihenfolge: Bloecke vor Charters (Charters pruefen die Block-Registry)
        effects.start();
        blocks.start();
        charters.start();
        crest.start();
        contracts.start();
        chainSword.start();
        boneflies.start();
        moulds.start();
        dominance.start();
        bell.start();
        Recipes.register(this);

        CharterCommand cmd = new CharterCommand(this);
        getCommand("charter").setExecutor(cmd);
        getCommand("charter").setTabCompleter(cmd);
    }

    @Override
    public void onDisable() {
        if (boneflies != null) boneflies.shutdown();
        if (chainSword != null) chainSword.shutdown();
        if (blocks != null) blocks.save();
        if (charters != null) charters.save();
        if (moulds != null) moulds.save();
        if (dominance != null) dominance.save();
    }

    public ContractManager contracts() { return contracts; }
    public PactEffects effects() { return effects; }
    public BlockManager blocks() { return blocks; }
    public CharterManager charters() { return charters; }
    public BoneflyManager boneflies() { return boneflies; }
    public MouldManager moulds() { return moulds; }
    public DominanceManager dominance() { return dominance; }
}
