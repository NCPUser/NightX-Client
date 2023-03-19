package net.aspw.client.features.module.modules.client

import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.value.BoolValue
import net.aspw.client.value.ListValue

@ModuleInfo(name = "CustomModel", spacedName = "Custom Model", category = ModuleCategory.CLIENT)
class CustomModel : Module() {
    val mode = ListValue("Mode", arrayOf("Rabbit", "Freddy"), "Rabbit")
    var onlySelf = BoolValue("OnlySelf", false)
}