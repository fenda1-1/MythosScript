/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.zszl.zszlScriptMod.shadowbaritone.command.defaults;

import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.Command;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.argument.IArgConsumer;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.ForAxis;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.ForBlockOptionalMeta;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.ForEnumFacing;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.datatypes.RelativeBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandInvalidStateException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.exception.CommandInvalidTypeException;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.helpers.TabCompleteHelper;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.RenderEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.listener.AbstractGameEventListener;
import com.zszl.zszlScriptMod.shadowbaritone.api.schematic.CompositeSchematic;
import com.zszl.zszlScriptMod.shadowbaritone.api.schematic.FillSchematic;
import com.zszl.zszlScriptMod.shadowbaritone.api.schematic.ISchematic;
import com.zszl.zszlScriptMod.shadowbaritone.api.schematic.MaskSchematic;
import com.zszl.zszlScriptMod.shadowbaritone.api.schematic.ReplaceSchematic;
import com.zszl.zszlScriptMod.shadowbaritone.api.schematic.ShellSchematic;
import com.zszl.zszlScriptMod.shadowbaritone.api.schematic.WallsSchematic;
import com.zszl.zszlScriptMod.shadowbaritone.api.schematic.mask.shape.CylinderMask;
import com.zszl.zszlScriptMod.shadowbaritone.api.schematic.mask.shape.SphereMask;
import com.zszl.zszlScriptMod.shadowbaritone.api.selection.ISelection;
import com.zszl.zszlScriptMod.shadowbaritone.api.selection.ISelectionManager;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BlockOptionalMeta;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BlockOptionalMetaLookup;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.ShadowBaritoneI18n;
import com.zszl.zszlScriptMod.shadowbaritone.utils.BlockStateInterface;
import com.zszl.zszlScriptMod.shadowbaritone.utils.IRenderer;
import com.zszl.zszlScriptMod.shadowbaritone.utils.schematic.StaticSchematic;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3i;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class SelCommand extends Command {

    private ISelectionManager manager = baritone.getSelectionManager();
    private BetterBlockPos pos1 = null;
    private ISchematic clipboard = null;
    private Vec3i clipboardOffset = null;

    public SelCommand(IBaritone baritone) {
        super(baritone, "sel", "selection", "s");
        baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
            @Override
            public void onRenderPass(RenderEvent event) {
                if (!Baritone.settings().renderSelectionCorners.value || pos1 == null) {
                    return;
                }
                Color color = Baritone.settings().colorSelectionPos1.value;
                float opacity = Baritone.settings().selectionOpacity.value;
                float lineWidth = Baritone.settings().selectionLineWidth.value;
                boolean ignoreDepth = Baritone.settings().renderSelectionIgnoreDepth.value;
                IRenderer.startLines(color, opacity, lineWidth, ignoreDepth);
                IRenderer.emitAABB(new AxisAlignedBB(pos1, pos1.add(1, 1, 1)));
                IRenderer.endLines(ignoreDepth);
            }
        });
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Action action = Action.getByName(args.getString());
        if (action == null) {
            throw new CommandInvalidTypeException(
                    args.consumed(),
                    ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.sel.error.action_type"));
        }
        if (action == Action.POS1 || action == Action.POS2) {
            if (action == Action.POS2 && pos1 == null) {
                throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.error.set_pos1_first"));
            }
            BetterBlockPos playerPos = ctx.viewerPos();
            BetterBlockPos pos = args.hasAny() ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos) : playerPos;
            args.requireMax(0);
            if (action == Action.POS1) {
                pos1 = pos;
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.status.pos1_set"));
            } else {
                manager.addSelection(pos1, pos);
                pos1 = null;
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.status.selection_added"));
            }
        } else if (action == Action.CLEAR) {
            args.requireMax(0);
            pos1 = null;
            logDirect(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.sel.status.removed",
                    manager.removeAllSelections().length));
        } else if (action == Action.UNDO) {
            args.requireMax(0);
            if (pos1 != null) {
                pos1 = null;
                logDirect(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.status.undid_pos1"));
            } else {
                ISelection[] selections = manager.getSelections();
                if (selections.length < 1) {
                    throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.sel.error.nothing_to_undo"));
                } else {
                    pos1 = manager.removeSelection(selections[selections.length - 1]).pos1();
                    logDirect(ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.sel.status.undid_pos2"));
                }
            }
        } else if (action.isFillAction()) {
            BlockOptionalMeta type = action == Action.CLEARAREA
                    ? new BlockOptionalMeta(Blocks.AIR)
                    : args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);

            final BlockOptionalMetaLookup replaces;
            final EnumFacing.Axis alignment;
            if (action == Action.REPLACE) {
                args.requireMin(1);
                List<BlockOptionalMeta> replacesList = new ArrayList<>();
                replacesList.add(type);
                while (args.has(2)) {
                    replacesList.add(args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE));
                }
                type = args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
                replaces = new BlockOptionalMetaLookup(replacesList.toArray(new BlockOptionalMeta[0]));
                alignment = null;
            } else if (action == Action.CYLINDER || action == Action.HCYLINDER) {
                args.requireMax(1);
                alignment = args.hasAny() ? args.getDatatypeFor(ForAxis.INSTANCE) : EnumFacing.Axis.Y;
                replaces = null;
            } else {
                args.requireMax(0);
                replaces = null;
                alignment = null;
            }
            ISelection[] selections = manager.getSelections();
            if (selections.length == 0) {
                throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.error.no_selections"));
            }
            BetterBlockPos origin = selections[0].min();
            CompositeSchematic composite = new CompositeSchematic(0, 0, 0);
            for (ISelection selection : selections) {
                BetterBlockPos min = selection.min();
                origin = new BetterBlockPos(
                        Math.min(origin.x, min.x),
                        Math.min(origin.y, min.y),
                        Math.min(origin.z, min.z));
            }
            for (ISelection selection : selections) {
                Vec3i size = selection.size();
                BetterBlockPos min = selection.min();

                UnaryOperator<ISchematic> create = fill -> {
                    final int w = fill.widthX();
                    final int h = fill.heightY();
                    final int l = fill.lengthZ();

                    switch (action) {
                        case WALLS:
                            return new WallsSchematic(fill);
                        case SHELL:
                            return new ShellSchematic(fill);
                        case REPLACE:
                            return new ReplaceSchematic(fill, replaces);
                        case SPHERE:
                            return MaskSchematic.create(fill, new SphereMask(w, h, l, true).compute());
                        case HSPHERE:
                            return MaskSchematic.create(fill, new SphereMask(w, h, l, false).compute());
                        case CYLINDER:
                            return MaskSchematic.create(fill, new CylinderMask(w, h, l, true, alignment).compute());
                        case HCYLINDER:
                            return MaskSchematic.create(fill, new CylinderMask(w, h, l, false, alignment).compute());
                        default:
                            return fill;
                    }
                };

                ISchematic schematic = create.apply(new FillSchematic(size.getX(), size.getY(), size.getZ(), type));
                composite.put(schematic, min.x - origin.x, min.y - origin.y, min.z - origin.z);
            }
            baritone.getBuilderProcess().build(
                    ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.sel.value.fill_name"),
                    composite,
                    origin);
            logDirect(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.sel.status.filling"));
        } else if (action == Action.COPY) {
            BetterBlockPos playerPos = ctx.viewerPos();
            BetterBlockPos pos = args.hasAny() ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos) : playerPos;
            args.requireMax(0);
            ISelection[] selections = manager.getSelections();
            if (selections.length < 1) {
                throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.error.no_selections"));
            }
            BlockStateInterface bsi = new BlockStateInterface(ctx);
            BetterBlockPos origin = selections[0].min();
            CompositeSchematic composite = new CompositeSchematic(0, 0, 0);
            for (ISelection selection : selections) {
                BetterBlockPos min = selection.min();
                origin = new BetterBlockPos(
                        Math.min(origin.x, min.x),
                        Math.min(origin.y, min.y),
                        Math.min(origin.z, min.z));
            }
            for (ISelection selection : selections) {
                Vec3i size = selection.size();
                BetterBlockPos min = selection.min();
                IBlockState[][][] blockstates = new IBlockState[size.getX()][size.getZ()][size.getY()];
                for (int x = 0; x < size.getX(); x++) {
                    for (int y = 0; y < size.getY(); y++) {
                        for (int z = 0; z < size.getZ(); z++) {
                            blockstates[x][z][y] = bsi.get0(min.x + x, min.y + y, min.z + z);
                        }
                    }
                }
                ISchematic schematic = new StaticSchematic() {
                    {
                        states = blockstates;
                        x = size.getX();
                        y = size.getY();
                        z = size.getZ();
                    }
                };
                composite.put(schematic, min.x - origin.x, min.y - origin.y, min.z - origin.z);
            }
            clipboard = composite;
            clipboardOffset = origin.subtract(pos);
            logDirect(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.sel.status.copied"));
        } else if (action == Action.PASTE) {
            BetterBlockPos playerPos = ctx.viewerPos();
            BetterBlockPos pos = args.hasAny() ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos) : playerPos;
            args.requireMax(0);
            if (clipboard == null) {
                throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.error.copy_first"));
            }
            baritone.getBuilderProcess().build(
                    ShadowBaritoneI18n.trKey(
                            "shadowbaritone.command.sel.value.fill_name"),
                    clipboard,
                    pos.add(clipboardOffset));
            logDirect(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.sel.status.building"));
        } else if (action == Action.EXPAND || action == Action.CONTRACT || action == Action.SHIFT) {
            args.requireExactly(3);
            TransformTarget transformTarget = TransformTarget.getByName(args.getString());
            if (transformTarget == null) {
                throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.error.invalid_transform_type"));
            }
            EnumFacing direction = args.getDatatypeFor(ForEnumFacing.INSTANCE);
            int blocks = args.getAs(Integer.class);
            ISelection[] selections = manager.getSelections();
            if (selections.length < 1) {
                throw new CommandInvalidStateException(ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.error.no_selections_found"));
            }
            selections = transformTarget.transform(selections);
            for (ISelection selection : selections) {
                if (action == Action.EXPAND) {
                    manager.expand(selection, direction, blocks);
                } else if (action == Action.CONTRACT) {
                    manager.contract(selection, direction, blocks);
                } else {
                    manager.shift(selection, direction, blocks);
                }
            }
            logDirect(ShadowBaritoneI18n.trKey(
                    "shadowbaritone.command.sel.status.transformed",
                    selections.length));
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                    .append(Action.getAllNames())
                    .filterPrefix(args.getString())
                    .sortAlphabetically()
                    .stream();
        } else {
            Action action = Action.getByName(args.getString());
            if (action != null) {
                if (action == Action.POS1 || action == Action.POS2) {
                    if (args.hasAtMost(3)) {
                        return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
                    }
                } else if (action.isFillAction()) {
                    if (args.hasExactlyOne() || action == Action.REPLACE) {
                        while (args.has(2)) {
                            args.get();
                        }
                        return args.tabCompleteDatatype(ForBlockOptionalMeta.INSTANCE);
                    } else if (args.hasExactly(2) && (action == Action.CYLINDER || action == Action.HCYLINDER)) {
                        args.get();
                        return args.tabCompleteDatatype(ForAxis.INSTANCE);
                    }
                } else if (action == Action.EXPAND || action == Action.CONTRACT || action == Action.SHIFT) {
                    if (args.hasExactlyOne()) {
                        return new TabCompleteHelper()
                                .append(TransformTarget.getAllNames())
                                .filterPrefix(args.getString())
                                .sortAlphabetically()
                                .stream();
                    } else {
                        TransformTarget target = TransformTarget.getByName(args.getString());
                        if (target != null && args.hasExactlyOne()) {
                            return args.tabCompleteDatatype(ForEnumFacing.INSTANCE);
                        }
                    }
                }
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return ShadowBaritoneI18n.trKey(
                "shadowbaritone.command.sel.short_desc");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.1"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.2"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.3"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.usage"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.pos1_current"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.pos1_relative"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.pos2_current"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.pos2_relative"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.clear"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.undo"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.set"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.walls"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.shell"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.sphere"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.hsphere"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.cylinder"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.hcylinder"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.cleararea"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.replace"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.copy"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.paste"),
                "",
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.expand"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.contract"),
                ShadowBaritoneI18n.trKey(
                        "shadowbaritone.command.sel.long_desc.example.shift"));
    }

    enum Action {
        POS1("pos1", "p1", "1"),
        POS2("pos2", "p2", "2"),
        CLEAR("clear", "c"),
        UNDO("undo", "u"),
        SET("set", "fill", "s", "f"),
        WALLS("walls", "w"),
        SHELL("shell", "shl"),
        SPHERE("sphere", "sph"),
        HSPHERE("hsphere", "hsph"),
        CYLINDER("cylinder", "cyl"),
        HCYLINDER("hcylinder", "hcyl"),
        CLEARAREA("cleararea", "ca"),
        REPLACE("replace", "r"),
        EXPAND("expand", "ex"),
        COPY("copy", "cp"),
        PASTE("paste", "p"),
        CONTRACT("contract", "ct"),
        SHIFT("shift", "sh");

        private final String[] names;

        Action(String... names) {
            this.names = names;
        }

        public static Action getByName(String name) {
            for (Action action : Action.values()) {
                for (String alias : action.names) {
                    if (alias.equalsIgnoreCase(name)) {
                        return action;
                    }
                }
            }
            return null;
        }

        public static String[] getAllNames() {
            Set<String> names = new HashSet<>();
            for (Action action : Action.values()) {
                names.addAll(Arrays.asList(action.names));
            }
            return names.toArray(new String[0]);
        }

        public final boolean isFillAction() {
            return this == SET
                    || this == WALLS
                    || this == SHELL
                    || this == SPHERE
                    || this == HSPHERE
                    || this == CYLINDER
                    || this == HCYLINDER
                    || this == CLEARAREA
                    || this == REPLACE;
        }
    }

    enum TransformTarget {
        ALL(sels -> sels, "all", "a"),
        NEWEST(sels -> new ISelection[]{sels[sels.length - 1]}, "newest", "n"),
        OLDEST(sels -> new ISelection[]{sels[0]}, "oldest", "o");

        private final Function<ISelection[], ISelection[]> transform;
        private final String[] names;

        TransformTarget(Function<ISelection[], ISelection[]> transform, String... names) {
            this.transform = transform;
            this.names = names;
        }

        public ISelection[] transform(ISelection[] selections) {
            return transform.apply(selections);
        }

        public static TransformTarget getByName(String name) {
            for (TransformTarget target : TransformTarget.values()) {
                for (String alias : target.names) {
                    if (alias.equalsIgnoreCase(name)) {
                        return target;
                    }
                }
            }
            return null;
        }

        public static String[] getAllNames() {
            Set<String> names = new HashSet<>();
            for (TransformTarget target : TransformTarget.values()) {
                names.addAll(Arrays.asList(target.names));
            }
            return names.toArray(new String[0]);
        }
    }
}
