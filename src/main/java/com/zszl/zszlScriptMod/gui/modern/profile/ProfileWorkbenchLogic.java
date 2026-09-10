package com.zszl.zszlScriptMod.gui.modern.profile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.zszl.zszlScriptMod.system.ProfileConfigFieldCodec.ConfigField;

/** Pure selection, filter and layout rules for the native profile workbench. */
public final class ProfileWorkbenchLogic {

    public enum ProfileBadge {
        CURRENT,
        DEFAULT,
        NONE
    }

    public static final class FileClickResult {
        public final Set<String> checked;
        public final int selectedIndex;
        public final int lastAnchor;

        public FileClickResult(Set<String> checked, int selectedIndex, int lastAnchor) {
            this.checked = checked;
            this.selectedIndex = selectedIndex;
            this.lastAnchor = lastAnchor;
        }
    }

    private ProfileWorkbenchLogic() {
    }

    public static boolean matchesFileFilter(String path, String displayName, String query) {
        String keyword = normalizeSearch(query);
        if (keyword.isEmpty()) {
            return true;
        }
        String normalizedPath = normalizeSearch(path);
        String localizedName = normalizeSearch(displayName);
        String label = normalizeSearch(fileLabel(displayName, path));
        return normalizedPath.contains(keyword) || localizedName.contains(keyword) || label.contains(keyword);
    }

    public static String fileLabel(String displayName, String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        String name = displayName == null ? "" : displayName;
        if (name.equalsIgnoreCase(normalized) || name.isEmpty()) {
            return normalized;
        }
        return name + " (" + normalized + ")";
    }

    public static ProfileBadge badge(String profileName, String activeProfile, String defaultProfile) {
        if (profileName != null && profileName.equals(activeProfile)) {
            return ProfileBadge.CURRENT;
        }
        if (profileName != null && profileName.equals(defaultProfile)) {
            return ProfileBadge.DEFAULT;
        }
        return ProfileBadge.NONE;
    }

    public static List<ConfigField> visibleFields(List<ConfigField> allFields, Set<String> collapsedGroups) {
        List<ConfigField> result = new ArrayList<ConfigField>();
        if (allFields == null) {
            return result;
        }
        for (ConfigField field : allFields) {
            if (field == null) {
                continue;
            }
            if (isHiddenByCollapse(field.getSelector(), collapsedGroups)) {
                continue;
            }
            result.add(field);
        }
        return result;
    }

    public static FileClickResult applyFileClick(List<String> files, Set<String> currentChecked, int selectedIndex,
            int lastAnchor, int clickIndex, boolean checkbox, boolean shift, boolean ctrl) {
        List<String> safeFiles = files == null ? new ArrayList<String>() : files;
        if (clickIndex < 0 || clickIndex >= safeFiles.size()) {
            return new FileClickResult(copy(currentChecked), selectedIndex, lastAnchor);
        }
        Set<String> checked = copy(currentChecked);
        String path = safeFiles.get(clickIndex);
        int nextSelected = clickIndex;
        int nextAnchor = lastAnchor;
        if (checkbox) {
            toggle(checked, path);
            nextAnchor = clickIndex;
        } else if (shift && !safeFiles.isEmpty()) {
            int anchor = lastAnchor < 0 ? clickIndex : lastAnchor;
            int min = Math.min(anchor, clickIndex);
            int max = Math.max(anchor, clickIndex);
            checked.clear();
            for (int i = min; i <= max; i++) {
                checked.add(safeFiles.get(i));
            }
            if (lastAnchor < 0) {
                nextAnchor = clickIndex;
            }
        } else if (ctrl) {
            toggle(checked, path);
            nextAnchor = clickIndex;
        } else {
            checked.clear();
            checked.add(path);
            nextAnchor = clickIndex;
        }
        return new FileClickResult(checked, nextSelected, nextAnchor);
    }

    public static double clampProfileRatio(double ratio) {
        return clamp(ratio, 0.10D, 0.40D, 0.18D);
    }

    public static double clampFileRatio(double ratio) {
        return clamp(ratio, 0.16D, 0.55D, 0.28D);
    }

    public static int[] columnWidths(int total, double profileRatio, double fileRatio) {
        int safeTotal = Math.max(1, total);
        int[] minimums = columnMinimums(safeTotal);
        int profileMin = minimums[0];
        int fileMin = minimums[1];
        int previewMin = minimums[2];

        int maxProfileWidth = Math.max(profileMin, safeTotal - fileMin - previewMin);
        int profileWidth = Math.max(profileMin,
                Math.min((int) Math.round(safeTotal * clampProfileRatio(profileRatio)), maxProfileWidth));
        int remainingAfterProfile = Math.max(1, safeTotal - profileWidth);

        int maxFileWidth = Math.max(fileMin, remainingAfterProfile - previewMin);
        int fileWidth = Math.max(fileMin,
                Math.min((int) Math.round(remainingAfterProfile * clampFileRatio(fileRatio)), maxFileWidth));
        int previewWidth = safeTotal - profileWidth - fileWidth;
        if (previewWidth < previewMin) {
            int need = previewMin - previewWidth;
            int reducibleFile = Math.max(0, fileWidth - fileMin);
            int reduceFile = Math.min(need, reducibleFile);
            fileWidth -= reduceFile;
            previewWidth += reduceFile;
            need -= reduceFile;
            if (need > 0) {
                int reducibleProfile = Math.max(0, profileWidth - profileMin);
                int reduceProfile = Math.min(need, reducibleProfile);
                profileWidth -= reduceProfile;
                previewWidth += reduceProfile;
            }
        }
        previewWidth = safeTotal - profileWidth - fileWidth;
        if (previewWidth < 1) {
            previewWidth = 1;
            if (fileWidth > 1) {
                fileWidth--;
            } else if (profileWidth > 1) {
                profileWidth--;
            }
            previewWidth = safeTotal - profileWidth - fileWidth;
        }
        return new int[] { profileWidth, fileWidth, previewWidth };
    }

    private static int[] columnMinimums(int total) {
        int profilePreferred = Math.max(120, Math.min(150, Math.max(120, total / 4)));
        int filePreferred = Math.max(170, Math.min(190, Math.max(170, total / 3)));
        int previewPreferred = Math.max(220, Math.min(260, Math.max(220, total / 3)));
        return fitWidthsToTotal(total,
                new int[] { profilePreferred, filePreferred, previewPreferred },
                new int[] { Math.min(profilePreferred, 96), Math.min(filePreferred, 132),
                        Math.min(previewPreferred, 168) });
    }

    private static int[] fitWidthsToTotal(int totalWidth, int[] preferredWidths, int[] floorWidths) {
        int count = Math.min(preferredWidths.length, floorWidths.length);
        int[] result = new int[count];
        if (count == 0 || totalWidth <= 0) {
            return result;
        }
        int preferredSum = 0;
        int floorSum = 0;
        for (int i = 0; i < count; i++) {
            int floor = Math.max(1, floorWidths[i]);
            int preferred = Math.max(floor, preferredWidths[i]);
            floorWidths[i] = floor;
            result[i] = preferred;
            preferredSum += preferred;
            floorSum += floor;
        }
        if (preferredSum <= totalWidth) {
            return result;
        }
        if (floorSum >= totalWidth) {
            return scaleWidthsToTotal(totalWidth, floorWidths);
        }
        int overflow = preferredSum - totalWidth;
        while (overflow > 0) {
            int reducibleTotal = 0;
            for (int i = 0; i < count; i++) {
                reducibleTotal += Math.max(0, result[i] - floorWidths[i]);
            }
            if (reducibleTotal <= 0) {
                break;
            }
            int reducedThisPass = 0;
            for (int i = 0; i < count && overflow > 0; i++) {
                int reducible = Math.max(0, result[i] - floorWidths[i]);
                if (reducible <= 0) {
                    continue;
                }
                int reduce = Math.max(1, (int) Math.floor(overflow * (reducible / (double) reducibleTotal)));
                reduce = Math.min(reduce, reducible);
                result[i] -= reduce;
                overflow -= reduce;
                reducedThisPass += reduce;
            }
            if (reducedThisPass <= 0) {
                break;
            }
        }
        while (overflow > 0) {
            boolean reduced = false;
            for (int i = count - 1; i >= 0 && overflow > 0; i--) {
                if (result[i] > floorWidths[i]) {
                    result[i]--;
                    overflow--;
                    reduced = true;
                }
            }
            if (!reduced) {
                break;
            }
        }
        return result;
    }

    private static int[] scaleWidthsToTotal(int totalWidth, int[] basisWidths) {
        int[] result = new int[basisWidths.length];
        if (basisWidths.length == 0 || totalWidth <= 0) {
            return result;
        }
        int basisSum = 0;
        for (int width : basisWidths) {
            basisSum += Math.max(1, width);
        }
        if (basisSum <= 0) {
            basisSum = basisWidths.length;
        }
        int used = 0;
        double[] fractions = new double[basisWidths.length];
        for (int i = 0; i < basisWidths.length; i++) {
            double scaled = Math.max(1, basisWidths[i]) * (double) totalWidth / (double) basisSum;
            int width = Math.max(1, (int) Math.floor(scaled));
            result[i] = width;
            fractions[i] = scaled - width;
            used += width;
        }
        while (used > totalWidth) {
            int index = indexOfLargest(result);
            if (index < 0 || result[index] <= 1) {
                break;
            }
            result[index]--;
            used--;
        }
        while (used < totalWidth) {
            int index = indexOfLargestFraction(fractions);
            if (index < 0) {
                index = indexOfLargest(basisWidths);
            }
            if (index < 0) {
                break;
            }
            result[index]++;
            fractions[index] = 0.0D;
            used++;
        }
        return result;
    }

    private static boolean isHiddenByCollapse(String selector, Set<String> collapsedGroups) {
        if (selector == null || collapsedGroups == null || collapsedGroups.isEmpty()) {
            return false;
        }
        for (String group : collapsedGroups) {
            if ("/".equals(group) && selector.startsWith("/")) {
                return true;
            }
            if (group != null && selector.startsWith(group + "/")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeSearch(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\\', '/').replace(" ", "").trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> copy(Set<String> source) {
        return source == null ? new LinkedHashSet<String>() : new LinkedHashSet<String>(source);
    }

    private static void toggle(Set<String> values, String value) {
        if (!values.add(value)) {
            values.remove(value);
        }
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int indexOfLargest(int[] values) {
        int index = -1;
        int best = Integer.MIN_VALUE;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > best) {
                best = values[i];
                index = i;
            }
        }
        return index;
    }

    private static int indexOfLargestFraction(double[] values) {
        int index = -1;
        double best = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > best) {
                best = values[i];
                index = i;
            }
        }
        return index;
    }
}
