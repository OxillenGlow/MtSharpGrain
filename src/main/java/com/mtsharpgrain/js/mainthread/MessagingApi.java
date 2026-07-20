package com.mtsharpgrain.js.mainthread;

import org.graalvm.polyglot.HostAccess;

/**
 * Intermod communication, exposed to JS as the {@code Mod} global:
 *   Mod.send(data)
 *
 * Broadcasts `data` (a string — JSON.stringify it yourself for structured
 * payloads) to every OTHER pack, synchronously, on the render/main thread.
 * Delivery is done by JSModifier.deliverMessage(), which calls that pack's
 * own top-level `onReceive(data, fromModName)` JS function if it defined
 * one — mods that don't care about messages simply never define it.
 *
 * Disabled packs are excluded on both ends: their tick callbacks don't run
 * (so nothing calls Mod.send() from a disabled pack in the first place),
 * and ModPackManager.broadcast() skips disabled packs as recipients too.
 */
public class MessagingApi {

    private final String packName;
    private final ModPackManager modPackManager;

    public MessagingApi(String packName, ModPackManager modPackManager) {
        this.packName = packName;
        this.modPackManager = modPackManager;
    }

    @HostAccess.Export
    public void send(String data) {
        modPackManager.broadcast(packName, data);
    }
}
