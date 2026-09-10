package com.zszl.zszlScriptMod.gui.modern.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

/** Shared data helpers for the native modern NBT pages. */
final class ModernNbtSupport {

    private ModernNbtSupport() {
    }

    static final class Node {
        final String path;
        final String key;
        final NBTBase tag;
        final NBTTagCompound parent;
        final int depth;

        Node(String path, String key, NBTBase tag, NBTTagCompound parent, int depth) {
            this.path = path;
            this.key = key;
            this.tag = tag;
            this.parent = parent;
            this.depth = depth;
        }

        boolean isCompound() {
            return tag instanceof NBTTagCompound;
        }
    }

    static List<Node> flatten(NBTTagCompound root, Set<String> collapsedPaths) {
        if (root == null) {
            return Collections.emptyList();
        }
        List<Node> result = new ArrayList<>();
        appendChildren(root, "", 0, collapsedPaths == null ? Collections.<String>emptySet() : collapsedPaths, result);
        return result;
    }

    private static void appendChildren(NBTTagCompound parent, String parentPath, int depth, Set<String> collapsedPaths,
            List<Node> result) {
        List<String> keys = new ArrayList<>(parent.getKeySet());
        Collections.sort(keys);
        for (String key : keys) {
            NBTBase tag = parent.getTag(key);
            if (tag == null) {
                continue;
            }
            String path = parentPath.isEmpty() ? key : parentPath + "/" + key;
            Node node = new Node(path, key, tag, parent, depth);
            result.add(node);
            if (tag instanceof NBTTagCompound && !collapsedPaths.contains(path)) {
                appendChildren((NBTTagCompound) tag, path, depth + 1, collapsedPaths, result);
            }
        }
    }

    static ItemStack copyMainHand(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = minecraft.player.getHeldItemMainhand();
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    static ItemStack copyStack(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    static NBTTagCompound copyTag(ItemStack stack) {
        return stack != null && stack.hasTagCompound() ? stack.getTagCompound().copy() : null;
    }

    static String tagToString(NBTBase tag) {
        if (tag == null) {
            return "";
        }
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            StringBuilder builder = new StringBuilder("{");
            List<String> keys = new ArrayList<>(compound.getKeySet());
            Collections.sort(keys);
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                String key = keys.get(i);
                builder.append('"').append(key).append('"').append(':')
                        .append(tagToString(compound.getTag(key)));
            }
            return builder.append('}').toString();
        }
        if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < list.tagCount(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(tagToString(list.get(i)));
            }
            return builder.append(']').toString();
        }
        return tag.toString();
    }

    static String typeName(NBTBase tag) {
        if (tag == null) {
            return "gui.modern.nbts.u001";
        }
        switch (tag.getId()) {
        case 1: return "Byte";
        case 2: return "Short";
        case 3: return "Int";
        case 4: return "Long";
        case 5: return "Float";
        case 6: return "Double";
        case 7: return "Byte Array";
        case 8: return "String";
        case 9: return "List";
        case 10: return "Compound";
        case 11: return "Int Array";
        default: return "gui.modern.nbts.u001";
        }
    }

    static String summary(NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            return tr("gui.modern.nbts.fmt.compound", String.valueOf(((NBTTagCompound) tag).getKeySet().size()));
        }
        if (tag instanceof NBTTagList) {
            return tr("gui.modern.nbts.fmt.list", String.valueOf(((NBTTagList) tag).tagCount()));
        }
        return typeName(tag) + " · " + safe(tag == null ? "" : tag.toString());
    }

    static String stringValue(NBTBase tag) {
        if (tag instanceof NBTTagString) {
            return ((NBTTagString) tag).getString();
        }
        return tag == null ? "" : tag.toString();
    }

    static String safe(String value) {
        return value == null ? "" : value;
    }
    private static String tr(String key) {
        return ModernFormI18n.tr(key);
    }

    private static String tr(String key, Object... args) {
        return ModernFormI18n.tr(key, args);
    }

}
