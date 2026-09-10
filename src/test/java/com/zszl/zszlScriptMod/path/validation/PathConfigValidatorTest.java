package com.zszl.zszlScriptMod.path.validation;

import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.path.ActionParameterVariableResolver;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PathConfigValidatorTest {

    @Test
    public void extractLeafKeyStripsArrayIndexesBeforeMatchingExpressionKeys() throws Exception {
        Method method = PathConfigValidator.class.getDeclaredMethod("extractLeafKey", String.class);
        method.setAccessible(true);

        assertEquals("expressions", method.invoke(null, "expressions[0]"));
        assertEquals("expressions", method.invoke(null, "params.expressions[0]"));
        assertEquals("expression", method.invoke(null, "conditions[2].expression"));
    }

    @Test
    public void expressionArrayEntriesAreSkippedDuringVariableReferenceValidation() throws Exception {
        Method method = PathConfigValidator.class.getDeclaredMethod(
                "shouldSkipVariableReferenceValidation", String.class, String.class, String.class);
        method.setAccessible(true);

        boolean skipped = (Boolean) method.invoke(null, "expressions", "expressions[0]", "name");
        assertTrue(skipped);
    }

    @Test
    public void playerAreaCenterAllowsScopedVariablesInsideArrayLiteral() throws Exception {
        JsonObject params = new JsonObject();
        params.addProperty("center", "[global.cbt1, 64, global.cbt2]");
        List<PathConfigValidator.Issue> issues = new ArrayList<>();
        Method method = PathConfigValidator.class.getDeclaredMethod("validatePlayerAreaCenter", String.class,
                int.class, int.class, JsonObject.class, ActionParameterVariableResolver.Context.class, List.class);
        method.setAccessible(true);

        method.invoke(null, "test", 0, 0, params,
                ActionParameterVariableResolver.buildContext("test", Collections.emptyList()), issues);

        assertFalse(issues.stream().anyMatch(issue -> "player_area_center_parse".equals(issue.getCode())));
    }

    @Test
    public void playerAreaCenterAllowsWholeScopedArrayVariable() throws Exception {
        JsonObject params = new JsonObject();
        params.addProperty("center", "global.cbt1");
        List<PathConfigValidator.Issue> issues = new ArrayList<>();
        Method method = PathConfigValidator.class.getDeclaredMethod("validatePlayerAreaCenter", String.class,
                int.class, int.class, JsonObject.class, ActionParameterVariableResolver.Context.class, List.class);
        method.setAccessible(true);

        method.invoke(null, "test", 0, 0, params,
                ActionParameterVariableResolver.buildContext("test", Collections.emptyList()), issues);

        assertFalse(issues.stream().anyMatch(issue -> "player_area_center_parse".equals(issue.getCode())));
    }
}
