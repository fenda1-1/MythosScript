package com.mythos.mythosScriptMod.shadowbaritone.utils;

import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.calc.IPath;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.mythos.mythosScriptMod.shadowbaritone.api.process.PathingCommand;
import com.mythos.mythosScriptMod.shadowbaritone.api.process.PathingCommandType;

public class PathingCommandPath extends PathingCommand {

    public final IPath desiredPath;

    public PathingCommandPath(Goal goal, PathingCommandType commandType, IPath desiredPath) {
        super(goal, commandType);
        this.desiredPath = desiredPath;
    }
}
