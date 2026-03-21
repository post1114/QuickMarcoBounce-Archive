package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.player.Gapple
import net.ccbluex.liquidbounce.utils.inventory.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.getEnchantmentLevel
import net.ccbluex.liquidbounce.utils.inventory.attackDamage
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.isSelected
import net.ccbluex.liquidbounce.utils.extensions.isClientFriend
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemSword
import net.minecraft.item.ItemStack
import net.minecraft.init.Items

/**
 * ArmorBreaker
 * 基于 Southside 的 ArmorBreaker 逻辑重写
 * 在攻击时自动切换到最佳破甲武器
 */
object ArmorBreaker : Module("ArmorBreaker", Category.COMBAT, subjective = true) {

    private val switchDelay by int("SwitchDelay", 2, 1..20)
    private val spoof by boolean("SpoofItem", false)
    private val spoofTicks by int("SpoofTicks", 10, 1..20) { spoof }
    
    private val timer = MSTimer()

    private var previousSlot = -1
    private var currentWeaponSlot = -1

    val onAttack = handler<AttackEvent> { event ->
        val player = mc.thePlayer ?: return@handler
        val target = event.targetEntity as? EntityLivingBase ?: return@handler
        
        if (Gapple.state && Gapple.eating) {
            return@handler
        }
        
        // 检查当前武器是否已经是破甲武器
        val currentWeapon = player.heldItem
        if (isArmorBreakerWeapon(currentWeapon)) {
            return@handler
        }
        
        // 寻找最佳破甲武器
        val bestSlot = getBestArmorBreakerSlot()
        if (bestSlot == -1) {
            return@handler
        }
        
        val currentSlot = player.inventory.currentItem
        val targetSlot = if (bestSlot < 9) bestSlot else bestSlot - 36
        
        if (currentSlot != targetSlot && timer.hasTimePassed(switchDelay * 50L)) {
            previousSlot = currentSlot
            currentWeaponSlot = targetSlot
            
            SilentHotbar.selectSlotSilently(this, targetSlot, spoofTicks, true, !spoof, spoof)
            
            if (!spoof) {
                player.inventory.currentItem = targetSlot
                SilentHotbar.resetSlot(this)
            }
            
            timer.reset()
        }
    }

    val onTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler
        
        if (Gapple.state && Gapple.eating) {
            return@handler
        }
        
        // 如果没有目标，处理击退武器逻辑
        if (getTargets().isEmpty()) {
            handleKnockbackWeapon()
            return@handler
        }
        
        // 动态武器排序逻辑
        if (!shouldSortWeapons()) {
            return@handler
        }
        
        val currentWeapon = player.heldItem
        
        // 如果当前不是破甲武器，尝试切换
        if (!isArmorBreakerWeapon(currentWeapon)) {
            val bestSlot = getBestArmorBreakerSlot()
            if (bestSlot != -1 && timer.hasTimePassed(switchDelay * 50L)) {
                val targetSlot = if (bestSlot < 9) bestSlot else bestSlot - 36
                if (player.inventory.currentItem != targetSlot) {
                    SilentHotbar.selectSlotSilently(this, targetSlot, spoofTicks, true, !spoof, spoof)
                    
                    if (!spoof) {
                        player.inventory.currentItem = targetSlot
                        SilentHotbar.resetSlot(this)
                    }
                    
                    timer.reset()
                }
            }
        }
    }

    /**
     * 判断是否为破甲武器（斧头或带锋利附魔的剑）
     */
    private fun isArmorBreakerWeapon(stack: ItemStack?): Boolean {
        if (stack == null) return false
        return isSharpAxe(stack) || 
               (stack.item is ItemSword && stack.getEnchantmentLevel(Enchantment.sharpness) > 0)
    }

    /**
     * 判断是否为锋利的斧头
     */
    private fun isSharpAxe(stack: ItemStack): Boolean {
        if (stack.item !is ItemAxe) return false
        return stack.getEnchantmentLevel(Enchantment.sharpness) > 0
    }

    /**
     * 获取最佳破甲武器槽位
     * @return 最佳武器槽位，如果没有则返回 -1
     */
    private fun getBestArmorBreakerSlot(): Int {
        val player = mc.thePlayer ?: return -1
        
        var bestSlot = -1
        var bestDamage = 0f
        
        // 搜索整个物品栏 (0-44)
        for (i in 0..44) {
            val stack = player.inventory.getStackInSlot(i) ?: continue
            
            // 跳过非武器物品
            if (stack.item !is ItemSword && !isSharpAxe(stack)) {
                continue
            }
            
            val damage = getDamageScore(stack)
            if (damage > bestDamage) {
                bestDamage = damage
                bestSlot = i
            }
        }
        
        return bestSlot
    }

    /**
     * 获取武器伤害分数
     */
    private fun getDamageScore(stack: ItemStack): Float {
        return stack.attackDamage.toFloat()
    }

    /**
     * 处理击退武器切换
     * 无目标时切换到有击退附魔的低级武器
     */
    private fun handleKnockbackWeapon() {
        val player = mc.thePlayer ?: return
        
        val currentWeapon = player.heldItem
        if (currentWeapon != null && currentWeapon.item is ItemSword) {
            val knockbackLevel = currentWeapon.getEnchantmentLevel(Enchantment.knockback)
            if (knockbackLevel == 0 && timer.hasTimePassed(switchDelay * 50L)) {
                // 寻找有击退附魔的武器
                for (i in 0..44) {
                    val stack = player.inventory.getStackInSlot(i)
                    if (stack == currentWeapon) continue
                    
                    if (stack.item is ItemSword) {
                        val sword = stack.item as ItemSword
                        // 检查是否为木质剑且有击退附魔
                        if (sword == Items.wooden_sword && 
                            stack.getEnchantmentLevel(Enchantment.knockback) > 0) {
                            val targetSlot = if (i < 9) i else i - 36
                            
                            SilentHotbar.selectSlotSilently(this, targetSlot, spoofTicks, true, !spoof, spoof)
                            
                            if (!spoof) {
                                player.inventory.currentItem = targetSlot
                                SilentHotbar.resetSlot(this)
                            }
                            
                            timer.reset()
                            return
                        }
                    }
                }
            }
        }
    }

    /**
     * 判断是否应该进行武器排序
     */
    private fun shouldSortWeapons(): Boolean {
        // 始终启用武器排序
        return true
    }

    /**
     * 获取当前 KillAura 的目标列表
     */
    private fun getTargets(): List<EntityLivingBase> {
        val player = mc.thePlayer ?: return emptyList()
        val targets = mutableListOf<EntityLivingBase>()
        
        for (entity in mc.theWorld.loadedEntityList) {
            if (entity !is EntityLivingBase) continue
            if (entity == player || entity.isDead) continue
            if (entity is EntityPlayer && entity.isClientFriend()) continue
            
            if (isSelected(entity, true)) {
                targets.add(entity)
            }
        }
        
        return targets
    }

    /**
     * 重置状态
     */
    override fun onDisable() {
        SilentHotbar.resetSlot(this)
        previousSlot = -1
        currentWeaponSlot = -1
        timer.reset()
    }

    override fun onEnable() {
        previousSlot = -1
        currentWeaponSlot = -1
        timer.reset()
    }
}
