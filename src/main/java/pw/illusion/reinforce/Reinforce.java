package pw.illusion.reinforce;

import com.google.gson.Gson;
import com.meowj.langutils.lang.LanguageHelper;
import lombok.Getter;
import lombok.SneakyThrows;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import pw.illusion.reinforce.api.TypeJudge;
import pw.illusion.reinforce.config.Config;
import pw.illusion.reinforce.config.Modifier;
import pw.illusion.reinforce.hook.VaultHook;
import pw.illusion.reinforce.support.Vanilla;
import pw.illusion.reinforce.util.ArmorType;
import pw.illusion.reinforce.util.ArmorUtil;
import pw.illusion.reinforce.util.PriceUtil;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;


public final class Reinforce extends JavaPlugin {
    public static boolean debug = true;
    public List<TypeJudge> typeJudgeList = new ArrayList<>();
    private static final Gson gson = new Gson();
    private static Random random;
    @Getter
    private ScriptEngine scriptEngine;
    @Getter
    private VaultHook vaultHook;

    public static Reinforce getInst() {
        return Reinforce.getPlugin(Reinforce.class);
    }

    @SneakyThrows
    @Override
    public void onEnable() {
        random = new Random();//tried to keep rng refresh...
        if (!getServer().getPluginManager().isPluginEnabled("Vault")) {
            Log.warn("Vault not found.");
            this.setEnabled(false);
            return;
        }
        Log.info("Initializing Configuration..");
        if (!loadConfig()) return;
        if (Config.inst.firstRun) {
            Log.info("Set your config first XD");
            Config.inst.firstRun = false;
            Config.inst.saveConfig();
            this.setEnabled(false);
            return;
        }
        Log.info("Initializing ScriptEngine..");
        ScriptEngineManager scm = new ScriptEngineManager();
        scriptEngine = scm.getEngineByName("JavaScript");
        Log.info("Initializing Judges..");
        loadDefaultJudges();
        Log.info("Loading VaultHook");
        vaultHook = setupEconomy().orElseThrow(VaultNotFoundE::new);

    }

    @Override
    public void onDisable() {
        //Config.inst.saveConfig();
        // Plugin shutdown logic
    }

    /**
     * Clear item modifiers
     *
     * @param item item to be clean
     * @return cleaned item
     */
    public ItemStack resetModifier(ItemStack item) {
        if (!isValidItem(item)) return item;
        Log.debug("resetModifier() call");
        ItemMeta itemMeta = item.getItemMeta();
        if (!itemMeta.hasDisplayName()) return item;
        //clear lore
        String a = "";
        if (itemMeta.hasLore()) {
            Iterator<String> iter = itemMeta.getLore().iterator();
            boolean flag = false;
            List<String> newLore = new ArrayList<>();
            while (iter.hasNext()) {
                String ele = iter.next();
                if (ele.equals(Config.inst.loreHeader)) {
                    flag = true;
                }
                if (debug && flag) Log.debug(ele);
                if (flag && ele.startsWith(Config.inst.lang.loreModTitle.replaceAll("%s", ""))) {
                    a = ele.replaceAll(Config.inst.lang.loreModTitle.replaceAll("%s", ""), "").replaceAll(ChatColor.WHITE + "", "");
                    Log.debug("a:" + a);
                }
                if (!flag) newLore.add(ele);
                if (ele.equals(Config.inst.loreFooter)) flag = false;
            }
            itemMeta.setLore(newLore);
            Log.debug("resetModifier.setDisplayname " + itemMeta.getDisplayName() + " || " + a);
            ChatColor color = itemMeta.hasEnchants() ? ChatColor.AQUA : ChatColor.WHITE;
            String[] arr = itemMeta.getDisplayName().split(" ");
            StringBuilder builder = new StringBuilder();
            for (String s : arr) {
                if (!s.startsWith(a)) {
                    builder.append(s).append(' ');
                }
            }
            itemMeta.setDisplayName(color + builder.toString().trim());
        } else {
            return item;
        }

        ItemStack itemStack = item.clone();
        if (!itemStack.setItemMeta(itemMeta)) Log.warn("FAILED TO SET ITEMMETA # ResetModifier");
        Log.debug("Modified: " + itemStack.getItemMeta().getDisplayName());
        return itemStack;
    }

    /**
     * Give buff to item
     * Do nothing(return original item) when selectRandomModifier return null or unknown armortype.
     *
     * @param item   items that ready to be modified
     * @param player who?
     * @return modified item or original item.
     */
    public ItemStack randModifier(ItemStack item, Player player) {
        if (ArmorUtil.typeOf(item) == ArmorType.UNRECOGNIZED) return item;
        ItemStack result = item;
        Optional<Modifier> mod = selectRandomModifier(item, player);
        Log.debug("Selected..");
        if (mod.isPresent()) {
            Log.debug("Applying..");
            result = applyModifier(resetModifier(item), mod.get(), player);
        }
        return result;
    }

    /**
     * apply mod to item
     * it doesn't clear modifier for item.
     *
     * @param itemStack item
     * @return modded item
     */
    public ItemStack applyModifier(ItemStack itemStack, Modifier mod, Player player) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta.hasLore()) {
            itemMeta.getLore().add(Config.inst.loreHeader);
            itemMeta.getLore().add(String.format(Config.inst.lang.loreModTitle, mod.displayName));
            itemMeta.getLore().addAll(mod.lores);
            itemMeta.getLore().add(Config.inst.loreFooter);
        } else {
            List<String> newLores = new ArrayList<>();
            newLores.add(Config.inst.loreHeader);
            newLores.add(String.format(Config.inst.lang.loreModTitle, mod.displayName));
            newLores.addAll(mod.lores);
            newLores.add(Config.inst.loreFooter);
            itemMeta.setLore(newLores);
        }
        if (itemMeta.hasDisplayName()) {
            itemMeta.setDisplayName(mod.displayName + " " + itemMeta.getDisplayName());
        } else {
            if (itemMeta.hasEnchants()) {
                itemMeta.setDisplayName(mod.displayName + " " + ChatColor.AQUA + LanguageHelper.getItemDisplayName(itemStack, player));
            } else {
                itemMeta.setDisplayName(mod.displayName + " " + LanguageHelper.getItemDisplayName(itemStack, player));
            }
        }
        ItemStack item = itemStack.clone();
        item.setItemMeta(itemMeta);
        return item;
    }

    /**
     * Roll a random modifier.
     *
     * @return modifier
     */
    private Optional<Modifier> selectRandomModifier(ItemStack item, Player player) {
        AtomicReference<Modifier> result = new AtomicReference<>();

        int maxProbability = 0;
        Map<Modifier, Integer> modifierRates = new HashMap<>();
        for (Modifier modifier : Config.inst.modifiers) {
            if (!modifier.armorType.contains(ArmorUtil.typeOf(item)) && !modifier.armorType.contains(ArmorType.ANY)) {
                continue;
            }

            if (!player.hasPermission(modifier.permission)) {
                continue;
            }

            modifierRates.put(modifier, maxProbability);
            maxProbability += modifier.probability;
        }
        int rand = random.nextInt(maxProbability);

        modifierRates.forEach((k, v) -> {
            if (rand >= v && rand < k.probability) {
                result.set(k);
            }
        });

        return Optional.ofNullable(result.get());
    }

    /**
     * Check if item modified by TR
     *
     * @return isModified
     */
    public boolean isValidItem(ItemStack itemStack) {
        return (itemStack.getItemMeta().hasLore() && itemStack.getItemMeta().getLore().contains(Config.inst.loreHeader) && itemStack.getItemMeta().getLore().contains(Config.inst.loreFooter));
    }

    private void loadDefaultJudges() {
        registerTypeJudge(new Vanilla());
    }

    public void registerTypeJudge(TypeJudge judge) {
        typeJudgeList.add(judge);
        Log.info("[!] New TypeJudge Registered: " + ChatColor.AQUA + judge.name());
    }

    private Optional<VaultHook> setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return Optional.empty();
        }
        Economy econ = rsp.getProvider();
        return Optional.of(new VaultHook(econ));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!sender.hasPermission("trf.use")) {
                sender.sendMessage(Config.inst.lang.perm_denied);
                return true;
            }
            if (sender instanceof Player) {
                Player player = (Player) sender;
                ItemStack itemInHand = player.getEquipment().getItemInMainHand();
                if (Session.sessionMap.containsKey(player.getUniqueId())) {
                    Session session = Session.sessionMap.get(player.getUniqueId());
                    Session.sessionMap.remove(player.getUniqueId());
                    if (itemInHand.hashCode() != session.targetedItemHash) {
                        player.sendMessage(Config.inst.lang.dont_move_your_sword_away);
                        return true;
                    }
                    if (!vaultHook.getEcon().has(player, session.price)) {
                        player.sendMessage(Config.inst.lang.money_not_enough);
                        return true;
                    }
                    vaultHook.getEcon().withdrawPlayer(player, session.price);
                    ItemStack itemReinforced = randModifier(itemInHand, player);
                    if (itemReinforced == itemInHand) { //pointer compare for check is it really reinforced.
                        player.sendMessage(Config.inst.lang.failed);
                        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 5, 5);
                    } else {
                        player.sendMessage(String.format(Config.inst.lang.succeed, itemReinforced.getItemMeta().getDisplayName()));
                        player.getEquipment().setItemInMainHand(itemReinforced);
                        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_HIT, 5, 10);
                    }
                    return true;
                } else {
                    //new session
                    if (ArmorUtil.typeOf(itemInHand) == ArmorType.UNRECOGNIZED) {
                        player.sendMessage(Config.inst.lang.unrecognized_item);
                        return true;
                    }
                    if (itemInHand.getAmount() != 1) {
                        player.sendMessage(Config.inst.lang.stack_of_items_are_denied);
                        return true;
                    }
                    //Start
                    boolean discount = DiscountCard.canPlayerDiscount(player);
                    Session sess = new Session(itemInHand.hashCode(), Math.floor(PriceUtil.calcPrice(itemInHand, discount)));
                    Session.sessionMap.put(player.getUniqueId(), sess);
                    String name;
                    if (itemInHand.getItemMeta().hasDisplayName()) {
                        name = itemInHand.getItemMeta().getDisplayName();
                    } else {
                        name = LanguageHelper.getItemDisplayName(itemInHand, player);
                    }
                    player.sendMessage(String.format(Config.inst.lang.ensure_with_price, sess.price, name) + (discount ? " " + Config.inst.lang.has_discount_card_suffix.replaceAll("%perc%", String.valueOf(Math.floor((1 - Config.inst.discountPercent) * 100))) : ""));
                    return true;
                }
            } else {
                sender.sendMessage("Not a player.");
            }
        }
        if (sender instanceof Player) {
            if (args[0].equals("reload")) {
                if (!sender.hasPermission("trf.reload")) {
                    sender.sendMessage(Config.inst.lang.perm_denied);
                    return true;
                }
                loadConfig();
                sender.sendMessage("Reloaded.");
                return true;
            }
            if (args[0].equals("clear")) {
                if (!sender.hasPermission("trf.clear")) {
                    sender.sendMessage(Config.inst.lang.perm_denied);
                    return true;
                }
                Player player = (Player) sender;
                player.getEquipment().setItemInMainHand(resetModifier(player.getEquipment().getItemInMainHand()));
                return true;
            }
            if (args[0].equals("getcard")) {
                if (!sender.hasPermission("trf.getcard")) {
                    sender.sendMessage(Config.inst.lang.perm_denied);
                    return true;
                }
                Player player = (Player) sender;
                player.getInventory().addItem(DiscountCard.allocate());
                return true;
            }
            if (args[0].equals("modifiers")) {
                if (!sender.hasPermission("trf.modifiers")) {
                    sender.sendMessage(Config.inst.lang.perm_denied);
                    return true;
                }
                Config.inst.modifiers.forEach(e -> sender.sendMessage(e.displayName));
                return true;
            }
        } else {
            sender.sendMessage("Not a player.");
        }
        return false;
    }

    @SneakyThrows
    private boolean loadConfig() {
        File modsDir = new File(getDataFolder(), "mods"); //scan singleton modifier.json
        getDataFolder().mkdir();
        modsDir.mkdir();
        Config.inst = (Config) new Config().saveDefaultOrLoad();
        /* Checking Configuration*/
        if (Config.inst.loreHeader == null
                || Config.inst.loreHeader.isEmpty()
                || Config.inst.loreFooter == null
                || Config.inst.loreFooter.isEmpty()) {
            Log.warn("LoreHeader or LoreFooter can't be null!");
            this.setEnabled(false);
            return false;
        }
        if (Config.inst.loreFooter.equals(Config.inst.loreHeader)) {
            Log.warn("LoreHeader is equals to LoreFooter");
            this.setEnabled(false);
            return false;
        }
        for (File file : modsDir.listFiles()) {
            Log.debug("Loading File: " + file.getName());
            Modifier mod = gson.fromJson(new BufferedReader(new FileReader(file)), Modifier.class);
            if (Modifier.isValid(mod)) {
                Log.info("Loading " + ChatColor.AQUA + mod.displayName + " (from " + file.getName() + ")");
                Config.inst.modifiers.add(mod);
            }
        }
        /*Formatting colors*/
        Config.inst.loreFooter = ChatColor.translateAlternateColorCodes('&', Config.inst.loreFooter);
        Config.inst.loreHeader = ChatColor.translateAlternateColorCodes('&', Config.inst.loreHeader);
        Config.inst.modifiers.forEach(e -> {
            Log.info("Loaded: " + ChatColor.stripColor(e.displayName));
            e.displayName = ChatColor.translateAlternateColorCodes('&', e.displayName);
            e.lores.forEach(s -> s = ChatColor.translateAlternateColorCodes('&', s));
        });
        for (Field field : Config.Lang.class.getFields()) {
            field.set(Config.inst.lang, ((String) field.get(Config.inst.lang)).replaceAll("&", String.valueOf(ChatColor.COLOR_CHAR)));
        }
        return true;
    }
}
