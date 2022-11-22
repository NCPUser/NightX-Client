
package net.aspw.nightx.features.module.modules.world

import net.aspw.nightx.NightX
import net.aspw.nightx.event.EventTarget
import net.aspw.nightx.event.UpdateEvent
import net.aspw.nightx.features.module.Module
import net.aspw.nightx.features.module.ModuleCategory
import net.aspw.nightx.features.module.ModuleInfo
import net.aspw.nightx.features.module.modules.player.AutoTool
import net.aspw.nightx.utils.RotationUtils
import net.aspw.nightx.utils.block.BlockUtils.getCenterDistance
import net.aspw.nightx.utils.block.BlockUtils.searchBlocks
import net.aspw.nightx.utils.timer.TickTimer
import net.aspw.nightx.value.BoolValue
import net.aspw.nightx.value.FloatValue
import net.aspw.nightx.value.IntegerValue
import net.aspw.nightx.value.ListValue
import net.minecraft.block.Block
import net.minecraft.block.BlockLiquid
import net.minecraft.init.Blocks
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.Vec3
import kotlin.math.roundToInt

@ModuleInfo(name = "Nuker", category = ModuleCategory.WORLD)
class Nuker : Module() {

    /**
     * OPTIONS
     */

    private val radiusValue = FloatValue("Radius", 4.2F, 1F, 6F)
    private val throughWallsValue = BoolValue("ThroughWalls", true)
    private val priorityValue = ListValue("Priority", arrayOf("Distance", "Hardness"), "Distance")
    private val rotationsValue = BoolValue("Rotations", true)
    private val layerValue = BoolValue("Layer", false)
    private val hitDelayValue = IntegerValue("HitDelay", 4, 0, 20)
    private val nukeValue = IntegerValue("Nuke", 1, 1, 20)
    private val nukeDelay = IntegerValue("NukeDelay", 1, 1, 20)

    /**
     * VALUES
     */

    private val attackedBlocks = arrayListOf<BlockPos>()
    private var currentBlock: BlockPos? = null
    private var blockHitDelay = 0

    private var nukeTimer = TickTimer()
    private var nuke = 0

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        // Block hit delay
        if (blockHitDelay > 0 && !NightX.moduleManager[FastBreak::class.java]!!.state) {
            blockHitDelay--
            return
        }

        // Reset bps
        nukeTimer.update()
        if (nukeTimer.hasTimePassed(nukeDelay.get())) {
            nuke = 0
            nukeTimer.reset()
        }

        // Clear blocks
        attackedBlocks.clear()

        val thePlayer = mc.thePlayer!!

        if (!mc.playerController.isInCreativeMode) {
            // Default nuker

            val validBlocks = searchBlocks(radiusValue.get().roundToInt() + 1)
                .filter { (pos, block) ->
                    if (getCenterDistance(pos) <= radiusValue.get() && validBlock(block)) {
                        if (layerValue.get() && pos.y < thePlayer.posY) { // Layer: Break all blocks above you
                            return@filter false
                        }

                        if (!throughWallsValue.get()) { // ThroughWalls: Just break blocks in your sight
                            // Raytrace player eyes to block position (through walls check)
                            val eyesPos = Vec3(
                                thePlayer.posX, thePlayer.entityBoundingBox.minY +
                                        thePlayer.eyeHeight, thePlayer.posZ
                            )
                            val blockVec = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
                            val rayTrace = mc.theWorld!!.rayTraceBlocks(
                                eyesPos, blockVec,
                                false, true, false
                            )

                            // Check if block is visible
                            rayTrace != null && rayTrace.blockPos == pos
                        } else true // Done
                    } else false // Bad block
                }.toMutableMap()

            do {
                val (blockPos, block) = when (priorityValue.get()) {
                    "Distance" -> validBlocks.minByOrNull { (pos, block) ->
                        val distance = getCenterDistance(pos)
                        val safePos = BlockPos(thePlayer.posX, thePlayer.posY - 1, thePlayer.posZ)

                        if (pos.x == safePos.x && safePos.y <= pos.y && pos.z == safePos.z)
                            Double.MAX_VALUE - distance // Last block
                        else
                            distance
                    }

                    "Hardness" -> validBlocks.maxByOrNull { (pos, block) ->
                        val hardness = block.getPlayerRelativeBlockHardness(thePlayer, mc.theWorld!!, pos).toDouble()

                        val safePos = BlockPos(thePlayer.posX, thePlayer.posY - 1, thePlayer.posZ)
                        if (pos.x == safePos.x && safePos.y <= pos.y && pos.z == safePos.z)
                            Double.MIN_VALUE + hardness // Last block
                        else
                            hardness
                    }

                    else -> return // what? why?
                } ?: return // well I guess there is no block to break :(

                // Reset current damage in case of block switch
                if (blockPos != currentBlock)
                    currentDamage = 0F

                // Change head rotations to next block
                if (rotationsValue.get()) {
                    val rotation = RotationUtils.faceBlock(blockPos) ?: return // In case of a mistake. Prevent flag.
                    RotationUtils.setTargetRotation(rotation.rotation)
                }

                // Set next target block
                currentBlock = blockPos
                attackedBlocks.add(blockPos)

                // Call auto tool
                val autoTool = NightX.moduleManager.getModule(AutoTool::class.java) as AutoTool
                if (autoTool.state)
                    autoTool.switchSlot(blockPos)

                // Start block breaking
                if (currentDamage == 0F) {
                    mc.netHandler.addToSendQueue(
                        C07PacketPlayerDigging(
                            C07PacketPlayerDigging.Action.START_DESTROY_BLOCK,
                            blockPos, EnumFacing.DOWN
                        )
                    )

                    // End block break if able to break instant
                    if (block.getPlayerRelativeBlockHardness(thePlayer, mc.theWorld!!, blockPos) >= 1F) {
                        currentDamage = 0F
                        thePlayer.swingItem()
                        mc.playerController.onPlayerDestroyBlock(blockPos, EnumFacing.DOWN)
                        blockHitDelay = hitDelayValue.get()
                        validBlocks -= blockPos
                        nuke++
                        continue // Next break
                    }
                }

                // Break block
                thePlayer.swingItem()
                currentDamage += block.getPlayerRelativeBlockHardness(thePlayer, mc.theWorld!!, blockPos)
                mc.theWorld!!.sendBlockBreakProgress(thePlayer.entityId, blockPos, (currentDamage * 10F).toInt() - 1)

                // End of breaking block
                if (currentDamage >= 1F) {
                    mc.netHandler.addToSendQueue(
                        C07PacketPlayerDigging(
                            C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK,
                            blockPos,
                            EnumFacing.DOWN
                        )
                    )
                    mc.playerController.onPlayerDestroyBlock(blockPos, EnumFacing.DOWN)
                    blockHitDelay = hitDelayValue.get()
                    currentDamage = 0F
                }
                return // Break out
            } while (nuke < nukeValue.get())
        } else {
            // Fast creative mode nuker (CreativeStorm option)

            // Unable to break with swords in creative mode
            if (thePlayer.heldItem?.item is ItemSword)
                return

            // Search for new blocks to break
            searchBlocks(radiusValue.get().roundToInt() + 1)
                .filter { (pos, block) ->
                    if (getCenterDistance(pos) <= radiusValue.get() && validBlock(block)) {
                        if (layerValue.get() && pos.y < thePlayer.posY) { // Layer: Break all blocks above you
                            return@filter false
                        }

                        if (!throughWallsValue.get()) { // ThroughWalls: Just break blocks in your sight
                            // Raytrace player eyes to block position (through walls check)
                            val eyesPos = Vec3(
                                thePlayer.posX, thePlayer.entityBoundingBox.minY +
                                        thePlayer.eyeHeight, thePlayer.posZ
                            )
                            val blockVec = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
                            val rayTrace = mc.theWorld!!.rayTraceBlocks(
                                eyesPos, blockVec,
                                false, true, false
                            )

                            // Check if block is visible
                            rayTrace != null && rayTrace.blockPos == pos
                        } else true // Done
                    } else false // Bad block
                }
                .forEach { (pos, _) ->
                    // Instant break block
                    mc.netHandler.addToSendQueue(
                        C07PacketPlayerDigging(
                            C07PacketPlayerDigging.Action.START_DESTROY_BLOCK,
                            pos, EnumFacing.DOWN
                        )
                    )
                    thePlayer.swingItem()
                    mc.netHandler.addToSendQueue(
                        C07PacketPlayerDigging(
                            C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK,
                            pos, EnumFacing.DOWN
                        )
                    )
                    attackedBlocks.add(pos)
                }
        }
    }

    /**
     * Check if [block] is a valid block to break
     */
    private fun validBlock(block: Block) = block != Blocks.air && block !is BlockLiquid

    companion object {
        var currentDamage = 0F
    }
}