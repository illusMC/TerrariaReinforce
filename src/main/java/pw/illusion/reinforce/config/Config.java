package pw.illusion.reinforce.config;

import cc.sfclub.util.common.JsonConfig;
import pw.illusion.reinforce.Reinforce;

import java.util.ArrayList;
import java.util.List;

public class Config extends JsonConfig {
    public static Config inst;
    public List<Modifier> modifiers = new ArrayList<>();

    public Config() {
        super(Reinforce.getPlugin(Reinforce.class).getDataFolder().toString());
    }
}
