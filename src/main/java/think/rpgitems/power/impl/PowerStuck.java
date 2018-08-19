/*
 *  This file is part of RPG Items.
 *
 *  RPG Items is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  RPG Items is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with RPG Items.  If not, see <http://www.gnu.org/licenses/>.
 */
package think.rpgitems.power.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import think.rpgitems.I18n;
import think.rpgitems.RPGItems;
import think.rpgitems.commands.Property;
import think.rpgitems.power.PowerHit;
import think.rpgitems.power.PowerResult;
import think.rpgitems.power.PowerRightClick;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static think.rpgitems.utils.PowerUtils.*;

/**
 * Power Stuck.
 * <p>
 * The stuck power will make the hit target stuck with a chance of 1/{@link #chance}.
 * </p>
 */
public class PowerStuck extends BasePower implements PowerHit, PowerRightClick {
    /**
     * Chance of triggering this power
     */
    public int chance = 3;

    /**
     * Cost of this power (hit)
     */
    public int cost = 0;

    /**
     * Cost of this power (right click)
     */
    public int costAoe = 0;

    /**
     * Cost of this power (right click per entity)
     */
    public int costPerEntity = 0;

    /**
     * Range of this power
     */
    public int range = 10;

    /**
     * Maximum view angle
     */
    public double facing = 30;

    /**
     * Duration of this power in tick
     */
    @Property(order = 1)
    public int duration = 100;

    /**
     * Cooldown time of this power
     */
    @Property(order = 0)
    public long cooldown = 200;

    /**
     * Whether allow aoe
     */
    public boolean allowAoe = false;

    /**
     * Whether allow hit
     */
    public boolean allowHit = true;


    private Random random = new Random();

    private Consumer<EntityTeleportEvent> tpl;

    private Consumer<PlayerTeleportEvent> pml;

    private Cache<UUID, Long> stucked = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).concurrencyLevel(2).build();

    @Override
    public PowerResult<Double> hit(Player player, ItemStack stack, LivingEntity entity, double damage, EntityDamageByEntityEvent event) {
        if (!allowHit) return PowerResult.noop();
        if (!checkCooldown(this, player, cooldown, true)) return PowerResult.cd();
        if (random.nextInt(chance) == 0) {
            if (!getItem().consumeDurability(stack, cost)) return PowerResult.cost();
            stucked.put(entity.getUniqueId(), System.currentTimeMillis());
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, duration, 10), true);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, duration, 128), true);
            return PowerResult.ok(damage);
        }
        return PowerResult.noop();
    }

    @Override
    public PowerResult<Void> rightClick(Player player, ItemStack stack, Block clicked, PlayerInteractEvent event) {
        if (!allowAoe) return PowerResult.noop();
        if (!checkCooldown(this, player, cooldown, true)) return PowerResult.cd();
        if (!getItem().consumeDurability(stack, costAoe)) return PowerResult.cost();
        List<LivingEntity> entities = getLivingEntitiesInCone(getNearestLivingEntities(this, player.getEyeLocation(), player, range, 0), player.getLocation().toVector(), facing, player.getLocation().getDirection());
        entities.forEach(entity -> {
                    if (!getItem().consumeDurability(stack, costPerEntity)) return;
                    stucked.put(entity.getUniqueId(), System.currentTimeMillis());
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, duration, 10), true);
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, duration, 128), true);
                }
        );
        return PowerResult.ok();
    }

    @Override
    public String displayText() {
        return I18n.format("power.stuck", (int) ((1d / (double) chance) * 100d), duration, (double) cooldown / 20d);
    }

    @Override
    public String getName() {
        return "stuck";
    }

    @Override
    public void init(ConfigurationSection s) {
        facing = s.getDouble("facing", 30);
        chance = s.getInt("chance", 3);
        cost = s.getInt("cost", s.getInt("consumption", 0));
        cooldown = s.getLong("cooldown", 200);
        duration = s.getInt("duration", 100);
        costAoe = s.getInt("costAOE", 0);
        range = s.getInt("range", 10);
        allowHit = s.getBoolean("allowHit", true);
        allowAoe = s.getBoolean("allowAoe", false);
        tpl = e -> {
            try {
                if (stucked.get(e.getEntity().getUniqueId(), () -> Long.MIN_VALUE) >= (System.currentTimeMillis() - duration * 50)) {
                    e.setCancelled(true);
                }
            } catch (ExecutionException ex) {
                ex.printStackTrace();
            }
        };
        pml = e -> {
            try {
                if (stucked.get(e.getPlayer().getUniqueId(), () -> Long.MIN_VALUE) >= (System.currentTimeMillis() - duration * 50)) {
                    if (e.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) {
                        e.getPlayer().sendMessage(I18n.format("message.stuck"));
                        e.setCancelled(true);
                    }
                }
            } catch (ExecutionException ex) {
                ex.printStackTrace();
            }
        };
        RPGItems.listener.addEventListener(EntityTeleportEvent.class, tpl).addEventListener(PlayerTeleportEvent.class, pml);
    }

    @Override
    public void save(ConfigurationSection s) {
        s.set("chance", chance);
        s.set("cost", cost);
        s.set("duration", duration);
        s.set("cooldown", cooldown);
        s.set("range", range);
        s.set("facing", facing);
        s.set("costAoe", costAoe);
        s.set("allowHit", allowHit);
        s.set("allowAoe", allowAoe);
    }

    @Override
    protected void finalize() {
        RPGItems.listener.removeEventListener(EntityTeleportEvent.class, tpl).removeEventListener(PlayerTeleportEvent.class, pml);
    }
}