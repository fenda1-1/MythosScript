package com.zszl.zszlScriptMod.shadowbaritone.utils;

import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.calc.IPath;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommand;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommandType;

public class PathingCommandPath extends PathingCommand {

    public final IPath desiredPath;

    public PathingCommandPath(Goal goal, PathingCommandType commandType, IPath desiredPath) {
        super(goal, commandType);
        this.desiredPath = desiredPath;
    }
}
