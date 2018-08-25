package think.rpgitems.commands;

import org.bukkit.Effect;
import think.rpgitems.power.Power;
import think.rpgitems.power.TriggerType;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum Preset {
    NONE,
    POTION_EFFECT_TYPE,
    TRIGGERS,
    VISUAL_EFFECT,
    ;

    @SuppressWarnings("unchecked")
    public List<String> get(Class<? extends Power> cls) {
        switch (this) {
            case POTION_EFFECT_TYPE:
                return Arrays.asList("SPEED",
                        "SLOW",
                        "FAST_DIGGING",
                        "SLOW_DIGGING",
                        "INCREASE_DAMAGE",
                        "HEAL",
                        "HARM",
                        "JUMP",
                        "CONFUSION",
                        "REGENERATION",
                        "DAMAGE_RESISTANCE",
                        "FIRE_RESISTANCE",
                        "WATER_BREATHING",
                        "INVISIBILITY",
                        "BLINDNESS",
                        "NIGHT_VISION",
                        "HUNGER",
                        "WEAKNESS",
                        "POISON",
                        "WITHER",
                        "HEALTH_BOOST",
                        "ABSORPTION",
                        "SATURATION",
                        "GLOWING",
                        "LEVITATION",
                        "LUCK",
                        "UNLUCK",
                        "SLOW_FALLING",
                        "CONDUIT_POWER",
                        "DOLPHINS_GRACE");
            case TRIGGERS:
                return Power.getTriggerTypes(cls).stream().map(TriggerType::name).collect(Collectors.toList());
            case VISUAL_EFFECT:
                return Arrays.stream(Effect.values()).filter(effect -> effect.getType() == Effect.Type.VISUAL).map(Enum::name).collect(Collectors.toList());
            case NONE:
            default:
                throw new IllegalStateException();
        }
    }
}