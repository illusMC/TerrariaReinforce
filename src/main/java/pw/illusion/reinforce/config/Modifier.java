package pw.illusion.reinforce.config;

import pw.illusion.reinforce.util.ArmorType;

import java.util.ArrayList;
import java.util.List;

public class Modifier {
    public String displayName;
    public double probability;
    public String permission;
    public double price;
    public List<ArmorType> armorType;
    public List<String> lores = new ArrayList<>();

    public static boolean isValid(Modifier modifier) {
        return (modifier.armorType != null && !modifier.displayName.isEmpty() && modifier.lores.size() != 0);
    }
}
