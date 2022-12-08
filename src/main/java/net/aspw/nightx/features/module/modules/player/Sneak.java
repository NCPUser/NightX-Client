package net.aspw.nightx.features.module.modules.player;

import net.aspw.nightx.event.EventState;
import net.aspw.nightx.event.EventTarget;
import net.aspw.nightx.event.MotionEvent;
import net.aspw.nightx.features.module.Module;
import net.aspw.nightx.features.module.ModuleCategory;
import net.aspw.nightx.features.module.ModuleInfo;
import net.aspw.nightx.utils.MovementUtils;
import net.aspw.nightx.value.BoolValue;
import net.aspw.nightx.value.ListValue;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.network.play.client.C0BPacketEntityAction;

@ModuleInfo(name = "Sneak", category = ModuleCategory.PLAYER)
public class Sneak extends Module {

    public final ListValue modeValue = new ListValue("Mode", new String[]{"Normal", "Legit", "Vanilla", "Switch", "AAC3.6.4"}, "Normal");
    public final BoolValue stopMoveValue = new BoolValue("StopMove", false);

    private boolean sneaked;

    @Override
    public void onEnable() {
        if (mc.thePlayer == null)
            return;

        if ("vanilla".equalsIgnoreCase(modeValue.get())) {
            mc.getNetHandler().addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
        }
    }

    @EventTarget
    public void onMotion(final MotionEvent event) {
        if (stopMoveValue.get() && MovementUtils.isMoving()) {
            if (sneaked) {
                onDisable();
                sneaked = false;
            }
            return;
        }

        sneaked = true;

        switch (modeValue.get().toLowerCase()) {
            case "legit":
                mc.gameSettings.keyBindSneak.pressed = true;
                break;
            case "switch":
                switch (event.getEventState()) {
                    case PRE:
                        if (!MovementUtils.isMoving())
                            return;

                        mc.getNetHandler().addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
                        mc.getNetHandler().addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
                        break;
                    case POST:
                        mc.getNetHandler().addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
                        mc.getNetHandler().addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
                        break;
                }
                break;
            case "normal":
                if (event.getEventState() == EventState.PRE)
                    break;

                mc.getNetHandler().addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
                break;

            case "aac3.6.4":
                mc.gameSettings.keyBindSneak.pressed = true;
                if (mc.thePlayer.onGround) {
                    MovementUtils.strafe(MovementUtils.getSpeed() * 1.251F);
                } else {
                    MovementUtils.strafe(MovementUtils.getSpeed() * 1.03F);
                }
                break;
        }
    }

    @Override
    public void onDisable() {
        if (mc.thePlayer == null)
            return;

        switch (modeValue.get().toLowerCase()) {
            case "legit":
            case "vanilla":
            case "switch":
            case "aac3.6.4":
                if (!GameSettings.isKeyDown(mc.gameSettings.keyBindSneak))
                    mc.gameSettings.keyBindSneak.pressed = false;
                break;
            case "normal":
                mc.getNetHandler().addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
                break;
        }
        super.onDisable();
    }
}