package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.player.Gapple
import net.ccbluex.liquidbounce.utils.inventory.SilentHotbar
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.isSelected
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemSword
import net.minecraft.item.ItemStack
import net.minecraft.init.Items

/**
 * ArmorBreaker
 * Created by post1114
 */

object ArmorBreaker : Module("ArmorBreaker", Category.COMBAT, subjective = true) {

    private val switchDelay by int("SwitchDelay", 2, 1..20)
    private val minDamageIncrease by float("MinDamageIncrease", 0.1f, 0.01f..1f)
    private val spoof by boolean("SpoofItem", false)
    private val spoofTicks by int("SpoofTicks", 10, 1..20) { spoof }

    private var lastSwitchTick = 0
    private var currentWeaponSlot = -1

    val onTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler
        val world = mc.theWorld ?: return@handler

        if (Gapple.state && Gapple.eating) {
            return@handler
        }

        val target = findTarget()

        if (target == null) {
            val knockbackWoodSwordSlot = findKnockbackWoodSword()

            if (knockbackWoodSwordSlot != -1 && player.inventory.currentItem != knockbackWoodSwordSlot) {
                SilentHotbar.selectSlotSilently(this, knockbackWoodSwordSlot, spoofTicks, true, !spoof, spoof)

                if (!spoof) {
                    player.inventory.currentItem = knockbackWoodSwordSlot
                    SilentHotbar.resetSlot(this)
                }
            }

            currentWeaponSlot = knockbackWoodSwordSlot
            lastSwitchTick = mc.thePlayer.ticksExisted
            return@handler
        }

        if (mc.thePlayer.ticksExisted - lastSwitchTick < switchDelay) {
            return@handler
        }

        val currentWeapon = player.heldItem
        val currentDamage = currentWeapon?.attackDamage ?: 0f

        val bestWeaponSlot = findBestWeapon(currentDamage, target)

        if (bestWeaponSlot != -1 && bestWeaponSlot != player.inventory.currentItem) {
            SilentHotbar.selectSlotSilently(this, bestWeaponSlot, spoofTicks, true, !spoof, spoof)

            if (!spoof) {
                player.inventory.currentItem = bestWeaponSlot
                SilentHotbar.resetSlot(this)
            }

            currentWeaponSlot = bestWeaponSlot
            lastSwitchTick = mc.thePlayer.ticksExisted
        }
    }

    private fun findTarget(): EntityLivingBase? {
        val player = mc.thePlayer ?: return null

        var nearestTarget: EntityLivingBase? = null
        var nearestDistance = Double.MAX_VALUE

        for (entity in mc.theWorld.loadedEntityList) {
            if (entity !is EntityLivingBase) continue
            if (entity == player || entity.isDead) continue
            if (entity is EntityPlayer && entity.isClientFriend()) continue

            val distance = player.getDistanceToEntity(entity)

            if (distance < nearestDistance && isSelected(entity, true)) {
                nearestTarget = entity
                nearestDistance = distance
            }
        }

        return nearestTarget
    }

    private fun findKnockbackWoodSword(): Int {
        val player = mc.thePlayer ?: return -1

        var bestSlot = -1
        var bestKnockbackLevel = 0

        for (i in 0..8) {
            val stack = player.inventory.getStackInSlot(i) ?: continue

            if (isKnockbackWoodSword(stack)) {
                val knockbackLevel = stack.getEnchantmentLevel(Enchantment.knockback)

                if (knockbackLevel > bestKnockbackLevel) {
                    bestSlot = i
                    bestKnockbackLevel = knockbackLevel
                }
            }
        }

        return bestSlot
    }

    private fun findBestWeapon(currentDamage: Float, target: EntityLivingBase): Int {
        val player = mc.thePlayer ?: return -1

        var bestSlot = -1
        var minPositiveIncrease = Float.MAX_VALUE

        for (i in 0..8) {
            val stack = player.inventory.getStackInSlot(i) ?: continue

            if (!isSwordOrSharpAxe(stack)) continue

            if (isGodAxe(stack)) continue

            val weaponDamage = stack.attackDamage

            val damageIncrease = weaponDamage - currentDamage

            if (damageIncrease > 0 && damageIncrease < minPositiveIncrease) {
                bestSlot = i
                minPositiveIncrease = damageIncrease
            }
        }

        return bestSlot
    }

    private fun isSwordOrSharpAxe(stack: ItemStack): Boolean {
        val item = stack.item

        if (item is ItemSword) return true

        if (item is ItemAxe) {
            val sharpnessLevel = stack.getEnchantmentLevel(Enchantment.sharpness)
            return sharpnessLevel > 0
        }

        return false
    }

    private fun isKnockbackWoodSword(stack: ItemStack): Boolean {
        if (stack.item != Items.wooden_sword) return false

        val knockbackLevel = stack.getEnchantmentLevel(Enchantment.knockback)

        return knockbackLevel > 0
    }

    private fun isGodAxe(stack: ItemStack): Boolean {
        if (stack.item !is ItemAxe) return false

        val sharpnessLevel = stack.getEnchantmentLevel(Enchantment.sharpness)
        val efficiencyLevel = stack.getEnchantmentLevel(Enchantment.efficiency)
        val unbreakingLevel = stack.getEnchantmentLevel(Enchantment.unbreaking)

        return sharpnessLevel >= 5 && efficiencyLevel >= 5 && unbreakingLevel >= 3
    }

    override fun onDisable() {
        SilentHotbar.resetSlot(this)
        currentWeaponSlot = -1
    }
}
