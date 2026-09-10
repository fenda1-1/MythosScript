package com.zszl.zszlScriptMod.gui.modern.path;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext;
import com.zszl.zszlScriptMod.gui.modern.core.ModernTabDescriptor;
/** Routes for path, action, sequence and template workbenches. */
public final class ModernPathRoutes {
    private ModernPathRoutes() { }

    public static List<ModernTabDescriptor> routes() {
        return Collections.unmodifiableList(Arrays.asList(
                route("reload_paths", "gui.modern.path.route.u001", "gui.modern.path.route.u002", "path_manager", "template_library"),
                route("path:", "gui.modern.path.route.u003", "gui.modern.path.route.u004", "path_manager"),
                route("custom_path:", "gui.modern.path.route.u005", "gui.modern.path.route.u006", "path_manager"),
                route("path_manager", "gui.modern.path.route.u007", "gui.modern.path.route.u008", "sequence_selector", "category_manager", "path_validation", "recording"),
                dependentRoute("recording", "gui.modern.path.route.u009", "gui.modern.path.route.u010", "path_manager"),
                dependentRoute("action_editor", "gui.modern.path.route.u012", "gui.modern.path.route.u013", "path_manager", "action_variables", "action_templates"),
                dependentRoute("expression_editor", "gui.modern.path.route.u014", "gui.modern.path.route.u015", "action_editor"),
                route("action_templates", "gui.modern.path.route.u016", "gui.modern.path.route.u017", "action_editor"),
                route("template_library", "gui.modern.path.route.u018", "gui.modern.path.route.u019", "action_templates"),
                route("sequence_selector", "gui.modern.path.route.u020", "gui.modern.path.route.u021", "path_manager"),
                route("path_validation", "gui.modern.path.route.u022", "gui.modern.path.route.u023", "path_manager"),
                route("execution_log", "gui.modern.path.route.u024", "gui.modern.path.route.u025", "path_manager"),
                route("category_manager", "gui.modern.path.route.u026", "gui.modern.path.route.u027", "path_manager"),
                dependentRoute("action_variables", "gui.modern.path.route.u028", "gui.modern.path.route.u029", "action_editor"),
                new ModernTabDescriptor("sequence_trigger_rules", "gui.modern.path.route.u031",
                        (minecraft, context) -> new ModernTriggerRulesTab(minecraft, context))));
    }

    public static ModernSettingsTab create(String command, ModernScreenContext context) {
        for (ModernTabDescriptor descriptor : routes()) {
            if (descriptor.getCommand().equals(command)) {
                return descriptor.getFactory().create(context == null ? null : context.getMinecraft(), context);
            }
        }
        return null;
    }

    private static ModernTabDescriptor route(String command, String title, String subtitle, String... children) {
        return new ModernTabDescriptor(command, title, (minecraft, context) ->
                new ModernPathWorkbenchTab(minecraft, title, subtitle, Arrays.asList(children),
                        context == null ? null : context.getRouteRequest(), command));
    }

    private static ModernTabDescriptor dependentRoute(String command, String title, String subtitle,
            String parentCommand, String... children) {
        return new ModernTabDescriptor(command, title, (minecraft, context) ->
                new ModernPathWorkbenchTab(minecraft, title, subtitle, Arrays.asList(children),
                        context == null ? null : context.getRouteRequest(), command), parentCommand);
    }
}
