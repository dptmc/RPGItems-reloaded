package think.rpgitems.power.impl;

import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import think.rpgitems.I18n;
import think.rpgitems.commands.Deserializer;
import think.rpgitems.commands.PowerMeta;
import think.rpgitems.commands.Property;
import think.rpgitems.commands.Serializer;
import think.rpgitems.power.PowerResult;
import think.rpgitems.power.PowerTick;
import think.rpgitems.utils.PotionEffectUtils;

import static java.lang.Double.min;
import static think.rpgitems.utils.PowerUtils.checkCooldownByString;

/**
 * Power potiontick.
 * <p>
 * The potiontick power will give the welder {@link #effect}
 * level {@link #amplifier} while held/worn
 * </p>
 */
@SuppressWarnings("WeakerAccess")
@PowerMeta(immutableTrigger = true)
public class PowerPotionTick extends BasePower implements PowerTick {

    /**
     * Type of potion effect
     */
    @Deserializer(PotionEffectUtils.class)
    @Serializer(PotionEffectUtils.class)
    @Property(order = 1, required = true)
    public PotionEffectType effect = PotionEffectType.SPEED;
    /**
     * Amplifier of potion effect
     */
    @Property(order = 0)
    public int amplifier = 1;
    /**
     * Cost of this power
     */
    public int cost = 0;
    /**
     * Interval of this power
     */
    @Property(order = 2)
    public int interval = 0;
    /**
     * Duration of this power
     */
    @Property(order = 3)
    public int duration = 60;
    /**
     * Whether to remove the effect instead of adding it.
     */
    @Property
    public boolean clear = false;

    @Override
    public PowerResult<Void> tick(Player player, ItemStack stack) {
        if (!checkCooldownByString(player, getItem(), "potiontick." + effect.getName(), interval, false)) return PowerResult.cd();
        if (!getItem().consumeDurability(stack, cost)) return PowerResult.cost();
        double health = player.getHealth();
        boolean hasEffect = false;
        for (PotionEffect potionEffect : player.getActivePotionEffects()) {
            if (potionEffect.getType().equals(effect)) {
                hasEffect = true;
                if (clear) {
                    player.removePotionEffect(effect);
                } else if (potionEffect.getDuration() <= 5 || potionEffect.getAmplifier() < amplifier)
                    player.addPotionEffect(new PotionEffect(effect, duration, amplifier, true), true);
                break;
            }
        }
        if (!hasEffect && !clear) {
            player.addPotionEffect(new PotionEffect(effect, duration, amplifier, true), true);
        }
        if (effect.equals(PotionEffectType.HEALTH_BOOST) && health > 0) {
            health = min(health, player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            player.setHealth(health);
        }
        return PowerResult.ok();
    }

    @Override
    public String getName() {
        return "potiontick";
    }

    @Override
    public String displayText() {
        return clear ?
                       I18n.format("power.potiontick.clear", effect.getName().toLowerCase().replaceAll("_", " "))
                       : I18n.format("power.potiontick.display", effect.getName().toLowerCase().replaceAll("_", " "), amplifier + 1);
    }
}