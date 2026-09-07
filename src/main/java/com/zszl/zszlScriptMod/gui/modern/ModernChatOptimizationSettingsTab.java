package com.zszl.zszlScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.List;

import com.zszl.zszlScriptMod.GlobalEventListener;
import com.zszl.zszlScriptMod.config.ChatOptimizationConfig;
import com.zszl.zszlScriptMod.config.ChatOptimizationConfig.ImageQuality;
import com.zszl.zszlScriptMod.config.ChatOptimizationConfig.TimedMessageMode;
import com.zszl.zszlScriptMod.gui.packet.PacketInterceptConfig;
import com.zszl.zszlScriptMod.utils.TextureManagerHelper;

import net.minecraft.client.Minecraft;

/**
 * Complete modern embedded editor for the chat optimization profile settings.
 * Lists use an explicit {@code \n} separator so every legacy list remains
 * editable inside the single-line modern form control.
 */
public final class ModernChatOptimizationSettingsTab {

    private static final String MESSAGE_SEPARATOR = "\\n";

    private ModernChatOptimizationSettingsTab() {
    }

    public static ModernSettingsTab create() {
        return ModernFormSettingsTab.builder("gui.modern.chatopt.title", "gui.modern.chatopt.subtitle",
                "gui.modern.chatopt.header_tip", adapter())
                .section("gui.modern.chatopt.section.messages", "gui.modern.chatopt.section.messages.tip")
                .toggle("gui.modern.chatopt.smart_copy", "gui.modern.chatopt.smart_copy.tip", bool("enableSmartCopy"))
                .toggle("gui.modern.chatopt.copy_format", "gui.modern.chatopt.copy_format.tip", bool("copyWithFormatting"))
                .toggle("gui.modern.chatopt.anti_spam", "gui.modern.chatopt.anti_spam.tip", bool("enableAntiSpam"))
                .integer("gui.modern.chatopt.anti_spam_threshold", "gui.modern.chatopt.anti_spam_threshold.tip",
                        integer("antiSpamThresholdSeconds"), 1, 3600)
                .toggle("gui.modern.chatopt.scroll_bottom", "gui.modern.chatopt.scroll_bottom.tip", bool("antiSpamScrollToBottom"))
                .toggle("gui.modern.chatopt.timestamp", "gui.modern.chatopt.timestamp.tip", bool("enableTimestamp"))
                .toggle("gui.modern.chatopt.smooth", "gui.modern.chatopt.smooth.tip", bool("smooth"))
                .section("gui.modern.chatopt.section.timed", "gui.modern.chatopt.section.timed.tip")
                .toggle("gui.modern.chatopt.timed_enable", "gui.modern.chatopt.timed_enable.tip", bool("enableTimedMessage"))
                .choice("gui.modern.chatopt.timed_mode", "gui.modern.chatopt.timed_mode.tip", choiceTimedMode(),
                        ModernFormSettingsTab.options(
                                ModernFormSettingsTab.option(TimedMessageMode.SEQUENTIAL, "gui.modern.chatopt.timed.sequential"),
                                ModernFormSettingsTab.option(TimedMessageMode.RANDOM, "gui.modern.chatopt.timed.random")))
                .integer("gui.modern.chatopt.timed_interval", "gui.modern.chatopt.timed_interval.tip", integer("timedMessageIntervalSeconds"), 1, 86400)
                .text("gui.modern.chatopt.timed_list", "gui.modern.chatopt.timed_list.tip",
                        text("timedMessages"), "gui.modern.chatopt.timed_list.placeholder", 2048)
                .section("gui.modern.chatopt.section.filters", "gui.modern.chatopt.section.filters.tip")
                .toggle("gui.modern.chatopt.blacklist_enable", "gui.modern.chatopt.blacklist_enable.tip", bool("enableBlacklist"))
                .text("gui.modern.chatopt.blacklist", "gui.modern.chatopt.filter_list.tip", text("blacklist"), "gui.modern.chatopt.blacklist.placeholder", 2048)
                .toggle("gui.modern.chatopt.whitelist_enable", "gui.modern.chatopt.whitelist_enable.tip", bool("enableWhitelist"))
                .text("gui.modern.chatopt.whitelist", "gui.modern.chatopt.filter_list.tip", text("whitelist"), "gui.modern.chatopt.whitelist.placeholder", 2048)
                .toggle("gui.modern.chatopt.regex", "gui.modern.chatopt.regex.tip", bool("regexFilter"))
                .section("gui.modern.chatopt.section.look", "gui.modern.chatopt.section.look.tip")
                .integer("gui.modern.chatopt.bg_alpha", "gui.modern.chatopt.bg_alpha.tip", integer("backgroundTransparencyPercent"), 0, 100)
                .text("gui.modern.chatopt.bg_path", "gui.modern.chatopt.bg_path.tip",
                        text("backgroundImagePath"), "gui.modern.chatopt.optional", 512)
                .choice("gui.modern.chatopt.quality", "gui.modern.chatopt.quality.tip", choiceImageQuality(),
                        ModernFormSettingsTab.options(
                                ModernFormSettingsTab.option(ImageQuality.ORIGINAL, "gui.modern.chatopt.quality.original"),
                                ModernFormSettingsTab.option(ImageQuality.HIGH, "gui.modern.chatopt.quality.high"),
                                ModernFormSettingsTab.option(ImageQuality.MEDIUM, "gui.modern.chatopt.quality.medium"),
                                ModernFormSettingsTab.option(ImageQuality.LOW, "gui.modern.chatopt.quality.low")))
                .integer("gui.modern.chatopt.bg_scale", "gui.modern.chatopt.bg_scale.tip", integer("backgroundImageScale"), 10, 300)
                .integer("gui.modern.chatopt.crop_x", "gui.modern.chatopt.crop_x.tip", integer("backgroundCropX"), 0, 10000)
                .integer("gui.modern.chatopt.crop_y", "gui.modern.chatopt.crop_y.tip", integer("backgroundCropY"), 0, 10000)
                .decimal("gui.modern.chatopt.chat_scale", "gui.modern.chatopt.chat_scale.tip", decimal("chatScale"), 0.0F, 1.0F)
                .decimal("gui.modern.chatopt.chat_width", "gui.modern.chatopt.chat_width.tip", decimal("chatWidth"), 0.0F, 1.0F)
                .integer("gui.modern.chatopt.offset_x", "gui.modern.chatopt.offset_x.tip",
                        integer("xOffset"), -10000, 10000)
                .integer("gui.modern.chatopt.offset_y", "gui.modern.chatopt.offset_y.tip",
                        integer("yOffset"), -10000, 10000)
                .action("gui.modern.chatopt.reset_preview", "gui.modern.chatopt.reset_preview.tip", "gui.modern.chatopt.reset_preview.button",
                        ModernFormSettingsTab.ActionStyle.SECONDARY, "gui.modern.chatopt.reset_preview.status", new ModernFormSettingsTab.FormAction<State>() {
                            @Override
                            public void execute(ModernFormSettingsTab<State> tab) {
                                ChatOptimizationConfig config = config();
                                config.xOffset = 0;
                                config.yOffset = 0;
                                tab.refreshValues();
                            }
                        })
                .build().compactHeader();
    }

    private static ModernFormSettingsTab.StateAdapter<State> adapter() {
        return new ModernFormSettingsTab.StateAdapter<State>() {
            @Override
            public void load() {
                ChatOptimizationConfig.load();
            }

            @Override
            public State capture() {
                return new State();
            }

            @Override
            public State copy(State state) {
                return state == null ? null : new State(state);
            }

            @Override
            public void restore(State state) {
                if (state != null) {
                    state.apply();
                }
            }

            @Override
            public void save() {
                normalizeAndPersist();
            }

            @Override
            public boolean supportsDefaults() {
                return true;
            }

            @Override
            public State createDefaults() {
                return State.defaults();
            }
        };
    }

    private static ModernFormSettingsTab.BooleanValue bool(final String name) {
        return new ModernFormSettingsTab.BooleanValue() {
            @Override
            public boolean get() {
                return State.getBoolean(name);
            }

            @Override
            public void set(boolean value) {
                State.setBoolean(name, value);
            }
        };
    }

    private static ModernFormSettingsTab.IntValue integer(final String name) {
        return new ModernFormSettingsTab.IntValue() {
            @Override
            public int get() {
                return State.getInt(name);
            }

            @Override
            public void set(int value) {
                State.setInt(name, value);
            }
        };
    }

    private static ModernFormSettingsTab.FloatValue decimal(final String name) {
        return new ModernFormSettingsTab.FloatValue() {
            @Override
            public float get() {
                return State.getFloat(name);
            }

            @Override
            public void set(float value) {
                State.setFloat(name, value);
            }
        };
    }

    private static ModernFormSettingsTab.TextValue text(final String name) {
        return new ModernFormSettingsTab.TextValue() {
            @Override
            public String get() {
                return State.getText(name);
            }

            @Override
            public void set(String value) {
                State.setText(name, value);
            }
        };
    }

    private static ModernFormSettingsTab.ChoiceValue<TimedMessageMode> choiceTimedMode() {
        return new ModernFormSettingsTab.ChoiceValue<TimedMessageMode>() {
            @Override
            public TimedMessageMode get() {
                TimedMessageMode value = config().timedMessageMode;
                return value == null ? TimedMessageMode.SEQUENTIAL : value;
            }

            @Override
            public void set(TimedMessageMode value) {
                config().timedMessageMode = value == null ? TimedMessageMode.SEQUENTIAL : value;
            }
        };
    }

    private static ModernFormSettingsTab.ChoiceValue<ImageQuality> choiceImageQuality() {
        return new ModernFormSettingsTab.ChoiceValue<ImageQuality>() {
            @Override
            public ImageQuality get() {
                ImageQuality value = config().imageQuality;
                return value == null ? ImageQuality.MEDIUM : value;
            }

            @Override
            public void set(ImageQuality value) {
                config().imageQuality = value == null ? ImageQuality.MEDIUM : value;
            }
        };
    }

    private static ChatOptimizationConfig config() {
        if (ChatOptimizationConfig.INSTANCE == null) {
            ChatOptimizationConfig.load();
        }
        return ChatOptimizationConfig.INSTANCE;
    }

    private static void normalizeAndPersist() {
        ChatOptimizationConfig settings = config();
        String previousPath = settings.backgroundImagePath == null ? "" : settings.backgroundImagePath;
        String canonicalPath = TextureManagerHelper.canonicalizeImagePath(previousPath);
        if (!previousPath.equals(canonicalPath)) {
            TextureManagerHelper.unloadTexture(previousPath);
        }
        settings.backgroundImagePath = canonicalPath;
        settings.imageQuality = settings.imageQuality == null ? ImageQuality.MEDIUM : settings.imageQuality;
        settings.timedMessageMode = settings.timedMessageMode == null ? TimedMessageMode.SEQUENTIAL
                : settings.timedMessageMode;
        settings.antiSpamThresholdSeconds = Math.max(1, settings.antiSpamThresholdSeconds);
        settings.timedMessageIntervalSeconds = Math.max(1, settings.timedMessageIntervalSeconds);
        settings.backgroundTransparencyPercent = clamp(settings.backgroundTransparencyPercent, 0, 100);
        settings.backgroundImageScale = clamp(settings.backgroundImageScale, 10, 300);
        settings.backgroundCropX = Math.max(0, settings.backgroundCropX);
        settings.backgroundCropY = Math.max(0, settings.backgroundCropY);
        settings.blacklist = normalizeCommaList(settings.blacklist);
        settings.whitelist = normalizeCommaList(settings.whitelist);
        settings.timedMessages = normalizeMessages(settings.timedMessages);
        applyChatPresentation(settings);
        TextureManagerHelper.clearCache();
        if (settings.enableTimedMessage) {
            GlobalEventListener.timedMessageTickCounter = 0;
        }
        ChatOptimizationConfig.save();
        PacketInterceptConfig.load();
        PacketInterceptConfig.ensureBuiltinRules();
        PacketInterceptConfig.save();
    }

    private static void applyChatPresentation(ChatOptimizationConfig settings) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameSettings == null) {
            return;
        }
        minecraft.gameSettings.chatScale = clamp(settingsChatScale(settings), 0.0F, 1.0F);
        minecraft.gameSettings.chatWidth = clamp(settingsChatWidth(settings), 0.0F, 1.0F);
        minecraft.gameSettings.saveOptions();
    }

    private static float settingsChatScale(ChatOptimizationConfig settings) {
        return settings == null ? 1.0F : State.draftChatScale;
    }

    private static float settingsChatWidth(ChatOptimizationConfig settings) {
        return settings == null ? 1.0F : State.draftChatWidth;
    }

    private static List<String> normalizeCommaList(List<String> source) {
        List<String> values = new ArrayList<>();
        if (source != null) {
            for (String sourceValue : source) {
                if (sourceValue == null) {
                    continue;
                }
                String[] parts = sourceValue.replace('，', ',').split(",");
                for (String part : parts) {
                    String value = part == null ? "" : part.trim();
                    if (!value.isEmpty()) {
                        values.add(value);
                    }
                }
            }
        }
        return values;
    }

    private static List<String> normalizeMessages(List<String> source) {
        List<String> values = new ArrayList<>();
        if (source != null) {
            for (String sourceValue : source) {
                if (sourceValue == null) {
                    continue;
                }
                String value = sourceValue.trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        if (values.isEmpty()) {
            values.add("");
        }
        return values;
    }

    private static List<String> parseMessageText(String value) {
        List<String> values = new ArrayList<>();
        String safe = value == null ? "" : value;
        String[] parts = safe.split("\\\\n", -1);
        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        if (values.isEmpty()) {
            values.add("");
        }
        return values;
    }

    private static String formatMessageText(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        List<String> safe = new ArrayList<>();
        for (String value : values) {
            String trimmed = value == null ? "" : value.trim();
            if (!trimmed.isEmpty()) {
                safe.add(trimmed.replace("\\n", " "));
            }
        }
        return String.join(MESSAGE_SEPARATOR, safe);
    }

    private static String formatCommaList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        List<String> safe = new ArrayList<>();
        for (String value : values) {
            String trimmed = value == null ? "" : value.trim();
            if (!trimmed.isEmpty()) {
                safe.add(trimmed);
            }
        }
        return String.join(", ", safe);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Defensive state used by the generic form's save, revert and default actions. */
    private static final class State {
        private final boolean enableSmartCopy;
        private final boolean copyWithFormatting;
        private final boolean enableAntiSpam;
        private final boolean enableTimestamp;
        private final boolean enableBlacklist;
        private final boolean enableWhitelist;
        private final boolean antiSpamScrollToBottom;
        private final boolean enableTimedMessage;
        private final boolean regexFilter;
        private final int antiSpamThresholdSeconds;
        private final List<String> timedMessages;
        private final int timedMessageIntervalSeconds;
        private final TimedMessageMode timedMessageMode;
        private final boolean smooth;
        private final int backgroundTransparencyPercent;
        private final int xOffset;
        private final int yOffset;
        private final String backgroundImagePath;
        private final ImageQuality imageQuality;
        private final int backgroundImageScale;
        private final int backgroundCropX;
        private final int backgroundCropY;
        private final List<String> blacklist;
        private final List<String> whitelist;
        private final float chatScale;
        private final float chatWidth;

        private State() {
            ChatOptimizationConfig settings = config();
            enableSmartCopy = settings.enableSmartCopy;
            copyWithFormatting = settings.copyWithFormatting;
            enableAntiSpam = settings.enableAntiSpam;
            enableTimestamp = settings.enableTimestamp;
            enableBlacklist = settings.enableBlacklist;
            enableWhitelist = settings.enableWhitelist;
            antiSpamScrollToBottom = settings.antiSpamScrollToBottom;
            enableTimedMessage = settings.enableTimedMessage;
            regexFilter = settings.regexFilter;
            antiSpamThresholdSeconds = settings.antiSpamThresholdSeconds;
            timedMessages = copyList(settings.timedMessages);
            timedMessageIntervalSeconds = settings.timedMessageIntervalSeconds;
            timedMessageMode = settings.timedMessageMode == null ? TimedMessageMode.SEQUENTIAL : settings.timedMessageMode;
            smooth = settings.smooth;
            backgroundTransparencyPercent = settings.backgroundTransparencyPercent;
            xOffset = settings.xOffset;
            yOffset = settings.yOffset;
            backgroundImagePath = settings.backgroundImagePath == null ? "" : settings.backgroundImagePath;
            imageQuality = settings.imageQuality == null ? ImageQuality.MEDIUM : settings.imageQuality;
            backgroundImageScale = settings.backgroundImageScale;
            backgroundCropX = settings.backgroundCropX;
            backgroundCropY = settings.backgroundCropY;
            blacklist = copyList(settings.blacklist);
            whitelist = copyList(settings.whitelist);
            Minecraft minecraft = Minecraft.getMinecraft();
            chatScale = minecraft == null || minecraft.gameSettings == null ? 1.0F : minecraft.gameSettings.chatScale;
            chatWidth = minecraft == null || minecraft.gameSettings == null ? 1.0F : minecraft.gameSettings.chatWidth;
            State.draftChatScale = chatScale;
            State.draftChatWidth = chatWidth;
        }

        private State(State source) {
            enableSmartCopy = source.enableSmartCopy;
            copyWithFormatting = source.copyWithFormatting;
            enableAntiSpam = source.enableAntiSpam;
            enableTimestamp = source.enableTimestamp;
            enableBlacklist = source.enableBlacklist;
            enableWhitelist = source.enableWhitelist;
            antiSpamScrollToBottom = source.antiSpamScrollToBottom;
            enableTimedMessage = source.enableTimedMessage;
            regexFilter = source.regexFilter;
            antiSpamThresholdSeconds = source.antiSpamThresholdSeconds;
            timedMessages = copyList(source.timedMessages);
            timedMessageIntervalSeconds = source.timedMessageIntervalSeconds;
            timedMessageMode = source.timedMessageMode;
            smooth = source.smooth;
            backgroundTransparencyPercent = source.backgroundTransparencyPercent;
            xOffset = source.xOffset;
            yOffset = source.yOffset;
            backgroundImagePath = source.backgroundImagePath;
            imageQuality = source.imageQuality;
            backgroundImageScale = source.backgroundImageScale;
            backgroundCropX = source.backgroundCropX;
            backgroundCropY = source.backgroundCropY;
            blacklist = copyList(source.blacklist);
            whitelist = copyList(source.whitelist);
            chatScale = source.chatScale;
            chatWidth = source.chatWidth;
            State.draftChatScale = chatScale;
            State.draftChatWidth = chatWidth;
        }

        private static State defaults() {
            return new State(true, false, true, false, false, false, false, false, false, 15,
                    singletonEmpty(), 60, TimedMessageMode.SEQUENTIAL, true, 50, 0, 0, "", ImageQuality.MEDIUM,
                    100, 0, 0, new ArrayList<String>(), new ArrayList<String>(), 1.0F, 1.0F);
        }

        private State(boolean enableSmartCopy, boolean copyWithFormatting, boolean enableAntiSpam,
                boolean enableTimestamp, boolean enableBlacklist, boolean enableWhitelist, boolean antiSpamScrollToBottom,
                boolean enableTimedMessage, boolean regexFilter, int antiSpamThresholdSeconds, List<String> timedMessages,
                int timedMessageIntervalSeconds, TimedMessageMode timedMessageMode, boolean smooth,
                int backgroundTransparencyPercent, int xOffset, int yOffset, String backgroundImagePath,
                ImageQuality imageQuality, int backgroundImageScale, int backgroundCropX, int backgroundCropY,
                List<String> blacklist, List<String> whitelist, float chatScale, float chatWidth) {
            this.enableSmartCopy = enableSmartCopy;
            this.copyWithFormatting = copyWithFormatting;
            this.enableAntiSpam = enableAntiSpam;
            this.enableTimestamp = enableTimestamp;
            this.enableBlacklist = enableBlacklist;
            this.enableWhitelist = enableWhitelist;
            this.antiSpamScrollToBottom = antiSpamScrollToBottom;
            this.enableTimedMessage = enableTimedMessage;
            this.regexFilter = regexFilter;
            this.antiSpamThresholdSeconds = antiSpamThresholdSeconds;
            this.timedMessages = copyList(timedMessages);
            this.timedMessageIntervalSeconds = timedMessageIntervalSeconds;
            this.timedMessageMode = timedMessageMode;
            this.smooth = smooth;
            this.backgroundTransparencyPercent = backgroundTransparencyPercent;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.backgroundImagePath = backgroundImagePath == null ? "" : backgroundImagePath;
            this.imageQuality = imageQuality == null ? ImageQuality.MEDIUM : imageQuality;
            this.backgroundImageScale = backgroundImageScale;
            this.backgroundCropX = backgroundCropX;
            this.backgroundCropY = backgroundCropY;
            this.blacklist = copyList(blacklist);
            this.whitelist = copyList(whitelist);
            this.chatScale = chatScale;
            this.chatWidth = chatWidth;
        }

        private void apply() {
            ChatOptimizationConfig settings = config();
            settings.enableSmartCopy = enableSmartCopy;
            settings.copyWithFormatting = copyWithFormatting;
            settings.enableAntiSpam = enableAntiSpam;
            settings.enableTimestamp = enableTimestamp;
            settings.enableBlacklist = enableBlacklist;
            settings.enableWhitelist = enableWhitelist;
            settings.antiSpamScrollToBottom = antiSpamScrollToBottom;
            settings.enableTimedMessage = enableTimedMessage;
            settings.regexFilter = regexFilter;
            settings.antiSpamThresholdSeconds = antiSpamThresholdSeconds;
            settings.timedMessages = copyList(timedMessages);
            settings.timedMessageIntervalSeconds = timedMessageIntervalSeconds;
            settings.timedMessageMode = timedMessageMode;
            settings.smooth = smooth;
            settings.backgroundTransparencyPercent = backgroundTransparencyPercent;
            settings.xOffset = xOffset;
            settings.yOffset = yOffset;
            settings.backgroundImagePath = backgroundImagePath;
            settings.imageQuality = imageQuality;
            settings.backgroundImageScale = backgroundImageScale;
            settings.backgroundCropX = backgroundCropX;
            settings.backgroundCropY = backgroundCropY;
            settings.blacklist = copyList(blacklist);
            settings.whitelist = copyList(whitelist);
            State.draftChatScale = chatScale;
            State.draftChatWidth = chatWidth;
            applyChatPresentation(settings);
            TextureManagerHelper.clearCache();
        }

        private static boolean getBoolean(String name) {
            ChatOptimizationConfig settings = config();
            if ("enableSmartCopy".equals(name)) return settings.enableSmartCopy;
            if ("copyWithFormatting".equals(name)) return settings.copyWithFormatting;
            if ("enableAntiSpam".equals(name)) return settings.enableAntiSpam;
            if ("enableTimestamp".equals(name)) return settings.enableTimestamp;
            if ("enableBlacklist".equals(name)) return settings.enableBlacklist;
            if ("enableWhitelist".equals(name)) return settings.enableWhitelist;
            if ("antiSpamScrollToBottom".equals(name)) return settings.antiSpamScrollToBottom;
            if ("enableTimedMessage".equals(name)) return settings.enableTimedMessage;
            if ("regexFilter".equals(name)) return settings.regexFilter;
            if ("smooth".equals(name)) return settings.smooth;
            return false;
        }

        private static void setBoolean(String name, boolean value) {
            ChatOptimizationConfig settings = config();
            if ("enableSmartCopy".equals(name)) settings.enableSmartCopy = value;
            else if ("copyWithFormatting".equals(name)) settings.copyWithFormatting = value;
            else if ("enableAntiSpam".equals(name)) settings.enableAntiSpam = value;
            else if ("enableTimestamp".equals(name)) settings.enableTimestamp = value;
            else if ("enableBlacklist".equals(name)) settings.enableBlacklist = value;
            else if ("enableWhitelist".equals(name)) settings.enableWhitelist = value;
            else if ("antiSpamScrollToBottom".equals(name)) settings.antiSpamScrollToBottom = value;
            else if ("enableTimedMessage".equals(name)) settings.enableTimedMessage = value;
            else if ("regexFilter".equals(name)) settings.regexFilter = value;
            else if ("smooth".equals(name)) settings.smooth = value;
        }

        private static int getInt(String name) {
            ChatOptimizationConfig settings = config();
            if ("antiSpamThresholdSeconds".equals(name)) return settings.antiSpamThresholdSeconds;
            if ("timedMessageIntervalSeconds".equals(name)) return settings.timedMessageIntervalSeconds;
            if ("backgroundTransparencyPercent".equals(name)) return settings.backgroundTransparencyPercent;
            if ("backgroundImageScale".equals(name)) return settings.backgroundImageScale;
            if ("backgroundCropX".equals(name)) return settings.backgroundCropX;
            if ("backgroundCropY".equals(name)) return settings.backgroundCropY;
            if ("xOffset".equals(name)) return settings.xOffset;
            if ("yOffset".equals(name)) return settings.yOffset;
            return 0;
        }

        private static void setInt(String name, int value) {
            ChatOptimizationConfig settings = config();
            if ("antiSpamThresholdSeconds".equals(name)) settings.antiSpamThresholdSeconds = value;
            else if ("timedMessageIntervalSeconds".equals(name)) settings.timedMessageIntervalSeconds = value;
            else if ("backgroundTransparencyPercent".equals(name)) settings.backgroundTransparencyPercent = value;
            else if ("backgroundImageScale".equals(name)) settings.backgroundImageScale = value;
            else if ("backgroundCropX".equals(name)) settings.backgroundCropX = value;
            else if ("backgroundCropY".equals(name)) settings.backgroundCropY = value;
            else if ("xOffset".equals(name)) settings.xOffset = value;
            else if ("yOffset".equals(name)) settings.yOffset = value;
        }

        private static float getFloat(String name) {
            if ("chatScale".equals(name)) return draftChatScale;
            if ("chatWidth".equals(name)) return draftChatWidth;
            return 0.0F;
        }

        private static void setFloat(String name, float value) {
            if ("chatScale".equals(name)) draftChatScale = value;
            else if ("chatWidth".equals(name)) draftChatWidth = value;
            applyChatPresentation(config());
        }

        private static String getText(String name) {
            ChatOptimizationConfig settings = config();
            if ("timedMessages".equals(name)) return formatMessageText(settings.timedMessages);
            if ("blacklist".equals(name)) return formatCommaList(settings.blacklist);
            if ("whitelist".equals(name)) return formatCommaList(settings.whitelist);
            if ("backgroundImagePath".equals(name)) return settings.backgroundImagePath == null ? "" : settings.backgroundImagePath;
            return "";
        }

        private static void setText(String name, String value) {
            ChatOptimizationConfig settings = config();
            if ("timedMessages".equals(name)) settings.timedMessages = parseMessageText(value);
            else if ("blacklist".equals(name)) settings.blacklist = normalizeCommaList(singleton(value));
            else if ("whitelist".equals(name)) settings.whitelist = normalizeCommaList(singleton(value));
            else if ("backgroundImagePath".equals(name)) settings.backgroundImagePath = value == null ? "" : value.trim();
        }

        private static List<String> singletonEmpty() {
            List<String> values = new ArrayList<>();
            values.add("");
            return values;
        }

        private static List<String> singleton(String value) {
            List<String> values = new ArrayList<>();
            values.add(value == null ? "" : value);
            return values;
        }

        private static List<String> copyList(List<String> source) {
            return source == null ? new ArrayList<String>() : new ArrayList<String>(source);
        }

        private static float draftChatScale = 1.0F;
        private static float draftChatWidth = 1.0F;
    }
}
