package fr.jmproduction.pocketdoor.portal.api;

import fr.jmproduction.pocketdoor.portal.Portal;

/**
 * Faithful port of qouteall.imm_ptl.core.api.PortalAPI (Immersive Portals 1.19.2, v2.3.2).
 * The narrow subset Pocket Door uses.
 */
public final class PortalAPI {
    private PortalAPI() {}

    public static void spawnServerEntity(Portal portal) {
        Portal.spawnServerEntity(portal);
    }

    public static void setPortalTag(Portal portal, String tag) { portal.portalTag = tag; }

    /** Called by client-side renderers before drawing through a portal. No-op here. */
    public static void beforeRender(Portal portal) {
        // intentionally empty; the bridge already enforces the right state.
    }

    /** Called by client-side renderers after drawing through a portal. No-op here. */
    public static void afterRender(Portal portal) {
        // intentionally empty.
    }

    /** Called from the client entrypoint on init. Reserved for future use. */
    public static void onClientInit() {
        // intentionally empty; portal runtime state is created on first tick.
    }
}
