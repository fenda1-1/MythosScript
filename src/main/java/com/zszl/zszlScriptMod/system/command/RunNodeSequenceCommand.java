package com.zszl.zszlScriptMod.system.command;

import com.zszl.zszlScriptMod.path.node.NodeExecutionContext;
import com.zszl.zszlScriptMod.path.node.NodeGraph;
import com.zszl.zszlScriptMod.path.node.NodeSequenceRunner;
import com.zszl.zszlScriptMod.path.node.NodeSequenceStorage;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public class RunNodeSequenceCommand extends CommandBase {

    @Override
    public String getName() {
        return "run_node_sequence";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/run_node_sequence <name>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args == null || args.length == 0) {
            throw new CommandException("用法: /run_node_sequence <name>");
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            throw new CommandException("当前没有可执行命令的玩家实例");
        }

        String graphName = String.join(" ", args).trim();
        if (graphName.isEmpty()) {
            throw new CommandException("节点图名称不能为空");
        }

        NodeSequenceStorage.LoadResult loadResult = NodeSequenceStorage.loadAll();
        if (!loadResult.isCompatible()) {
            throw new CommandException("节点图存储不兼容: " + loadResult.getMessage());
        }

        NodeGraph graph = findGraphByName(loadResult.getSequences(), graphName);
        if (graph == null) {
            throw new CommandException("未找到节点图: " + graphName);
        }

        NodeExecutionContext context = new NodeExecutionContext();
        NodeSequenceRunner runner = new NodeSequenceRunner(graph, context);
        boolean finished = runner.run(mc.player);

        if (finished && context.isCompleted()) {
            mc.player.sendMessage(new TextComponentString(
                    TextFormatting.GREEN + "[节点编辑器] 已执行节点图: " + graph.getName()));
            return;
        }

        if (context.isWaiting()) {
            mc.player.sendMessage(new TextComponentString(
                    TextFormatting.YELLOW + "[节点编辑器] 节点图进入等待状态: "
                            + safe(context.getCurrentNodeId()) + " @ " + graph.getName()));
            return;
        }

        throw new CommandException("执行失败 @ " + safe(context.getCurrentNodeId()) + ": " + context.getErrorMessage());
    }

    private NodeGraph findGraphByName(List<NodeGraph> graphs, String graphName) {
        if (graphs == null || graphName == null) {
            return null;
        }
        for (NodeGraph graph : graphs) {
            if (graph == null || graph.getName() == null) {
                continue;
            }
            if (graphName.equalsIgnoreCase(graph.getName().trim())) {
                return graph;
            }
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
