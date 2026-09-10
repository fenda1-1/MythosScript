package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.core.ModernTabDescriptor;

/** Packet command registrations consumed by the modern shell. */
public final class PacketModernRoutes {
    private PacketModernRoutes() { }

    public static List<ModernTabDescriptor> routes() {
        List<ModernTabDescriptor> routes = new ArrayList<ModernTabDescriptor>();
        routes.add(new ModernTabDescriptor(PacketWorkbenchTab.COMMAND, "gui.modern.pktroute.u001",
                (minecraft, context) -> new PacketWorkbenchTab(minecraft, context)));
        routes.add(child(PacketWorkbenchTab.VIEWER, "gui.modern.pktview.u004"));
        routes.add(child(PacketWorkbenchTab.FILTER, "gui.modern.pktflt.u001"));
        routes.add(child(PacketWorkbenchTab.FIELD_RULES, "gui.modern.pktfield.u001"));
        routes.add(child(PacketWorkbenchTab.INTERCEPT, "gui.modern.pktint.u001"));
        routes.add(child(PacketWorkbenchTab.CAPTURED_IDS, "gui.modern.pktid.u001"));
        routes.add(child(PacketWorkbenchTab.ID_GENERATOR, "gui.modern.pktgen.u001"));
        routes.add(child(PacketWorkbenchTab.SNAPSHOTS, "gui.modern.pktsnap.u001"));
        routes.add(child(PacketWorkbenchTab.ID_RECORDS, "gui.modern.pktrec.u001"));
        routes.add(child(PacketWorkbenchTab.SEQUENCES, "gui.modern.pktseqm.u001"));
        routes.add(child(PacketWorkbenchTab.SEQUENCE_EDITOR, "gui.modern.pktseqe.u001"));
        return Collections.unmodifiableList(routes);
    }

    private static ModernTabDescriptor child(String command, String title) {
        return new ModernTabDescriptor(command, title,
                (minecraft, context) -> new PacketWorkbenchTab(minecraft, context), PacketWorkbenchTab.COMMAND);
    }
}
