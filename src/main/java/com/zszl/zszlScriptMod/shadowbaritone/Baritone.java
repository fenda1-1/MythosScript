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

package com.zszl.zszlScriptMod.shadowbaritone;

import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.Settings;
import com.zszl.zszlScriptMod.shadowbaritone.api.behavior.IBehavior;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.listener.IEventBus;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.IBaritoneProcess;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.IElytraProcess;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.IPlayerContext;
import com.zszl.zszlScriptMod.shadowbaritone.behavior.*;
import com.zszl.zszlScriptMod.shadowbaritone.cache.WorldProvider;
import com.zszl.zszlScriptMod.shadowbaritone.command.ExampleBaritoneControl;
import com.zszl.zszlScriptMod.shadowbaritone.command.manager.CommandManager;
import com.zszl.zszlScriptMod.shadowbaritone.event.GameEventHandler;
import com.zszl.zszlScriptMod.shadowbaritone.process.*;
import com.zszl.zszlScriptMod.shadowbaritone.selection.SelectionManager;
import com.zszl.zszlScriptMod.shadowbaritone.utils.BlockStateInterface;
import com.zszl.zszlScriptMod.shadowbaritone.utils.GuiClick;
import com.zszl.zszlScriptMod.shadowbaritone.utils.InputOverrideHandler;
import com.zszl.zszlScriptMod.shadowbaritone.utils.PathingControlManager;
import com.zszl.zszlScriptMod.shadowbaritone.utils.player.BaritonePlayerContext;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author Brady
 * @since 7/31/2018
 */
public class Baritone implements IBaritone {

    private static final ThreadPoolExecutor threadPool;

    static {
        threadPool = new ThreadPoolExecutor(4, 8, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(256), r -> {
            Thread thread = new Thread(r, "shadowbaritone-worker");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    private final Minecraft mc;
    private final Path directory;

    private final GameEventHandler gameEventHandler;

    private final PathingBehavior pathingBehavior;
    private final LookBehavior lookBehavior;
    private final InventoryBehavior inventoryBehavior;
    private final InputOverrideHandler inputOverrideHandler;

    private final FollowProcess followProcess;
    private final MineProcess mineProcess;
    private final GetToBlockProcess getToBlockProcess;
    private final CustomGoalProcess customGoalProcess;
    private final BuilderProcess builderProcess;
    private final ExploreProcess exploreProcess;
    private final FarmProcess farmProcess;
    private final InventoryPauserProcess inventoryPauserProcess;
    private final KillAuraOrbitProcess killAuraOrbitProcess;
    private final IElytraProcess elytraProcess;

    private final PathingControlManager pathingControlManager;
    private final SelectionManager selectionManager;
    private final CommandManager commandManager;

    private final IPlayerContext playerContext;
    private final WorldProvider worldProvider;

    public BlockStateInterface bsi;

    Baritone(Minecraft mc) {
        this.mc = mc;
        this.gameEventHandler = new GameEventHandler(this);

        this.directory = mc.mcDataDir.toPath().resolve("shadowbaritone");
        if (!Files.exists(this.directory)) {
            try {
                Files.createDirectories(this.directory);
            } catch (IOException ignored) {
            }
        }

        // Define this before behaviors try and get it, or else it will be null and the
        // builds will fail!
        this.playerContext = new BaritonePlayerContext(this, mc);

        {
            this.lookBehavior = new LookBehavior(this);
            this.registerBehavior(this.lookBehavior);

            this.pathingBehavior = new PathingBehavior(this);
            this.registerBehavior(this.pathingBehavior);

            this.inventoryBehavior = new InventoryBehavior(this);
            this.registerBehavior(this.inventoryBehavior);

            this.inputOverrideHandler = new InputOverrideHandler(this);
            this.registerBehavior(this.inputOverrideHandler);

            this.registerBehavior(new WaypointBehavior(this));
        }

        this.pathingControlManager = new PathingControlManager(this);
        {
            this.followProcess = new FollowProcess(this);
            this.pathingControlManager.registerProcess(this.followProcess);

            this.mineProcess = new MineProcess(this);
            this.pathingControlManager.registerProcess(this.mineProcess);

            this.customGoalProcess = new CustomGoalProcess(this); // very high iq
            this.pathingControlManager.registerProcess(this.customGoalProcess);

            this.getToBlockProcess = new GetToBlockProcess(this);
            this.pathingControlManager.registerProcess(this.getToBlockProcess);

            BuilderProcess builderProcessTmp = null;
            try {
                builderProcessTmp = new BuilderProcess(this);
                this.pathingControlManager.registerProcess(builderProcessTmp);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            this.builderProcess = builderProcessTmp;

            ExploreProcess exploreProcessTmp = null;
            try {
                exploreProcessTmp = new ExploreProcess(this);
                this.pathingControlManager.registerProcess(exploreProcessTmp);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            this.exploreProcess = exploreProcessTmp;

            FarmProcess farmProcessTmp = null;
            try {
                farmProcessTmp = new FarmProcess(this);
                this.pathingControlManager.registerProcess(farmProcessTmp);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            this.farmProcess = farmProcessTmp;

            InventoryPauserProcess inventoryPauserProcessTmp = null;
            try {
                inventoryPauserProcessTmp = new InventoryPauserProcess(this);
                this.pathingControlManager.registerProcess(inventoryPauserProcessTmp);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            this.inventoryPauserProcess = inventoryPauserProcessTmp;

            KillAuraOrbitProcess killAuraOrbitProcessTmp = null;
            try {
                killAuraOrbitProcessTmp = new KillAuraOrbitProcess(this);
                this.pathingControlManager.registerProcess(killAuraOrbitProcessTmp);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            this.killAuraOrbitProcess = killAuraOrbitProcessTmp;

            IElytraProcess elytraProcessTmp = null;
            try {
                elytraProcessTmp = ElytraProcess.create(this);
                this.pathingControlManager.registerProcess(elytraProcessTmp);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            this.elytraProcess = elytraProcessTmp;

            try {
                this.pathingControlManager.registerProcess(new BackfillProcess(this));
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        this.worldProvider = new WorldProvider(this);
        this.selectionManager = new SelectionManager(this);
        this.commandManager = new CommandManager(this);
        this.registerBehavior(new ExampleBaritoneControl(this));
    }

    public void registerBehavior(IBehavior behavior) {
        this.gameEventHandler.registerEventListener(behavior);
    }

    public <T extends IBehavior> T registerBehavior(Function<Baritone, T> constructor) {
        final T behavior = constructor.apply(this);
        this.registerBehavior(behavior);
        return behavior;
    }

    public <T extends IBaritoneProcess> T registerProcess(Function<Baritone, T> constructor) {
        final T behavior = constructor.apply(this);
        this.pathingControlManager.registerProcess(behavior);
        return behavior;
    }

    @Override
    public PathingControlManager getPathingControlManager() {
        return this.pathingControlManager;
    }

    @Override
    public InputOverrideHandler getInputOverrideHandler() {
        return this.inputOverrideHandler;
    }

    @Override
    public CustomGoalProcess getCustomGoalProcess() {
        return this.customGoalProcess;
    }

    @Override
    public GetToBlockProcess getGetToBlockProcess() {
        return this.getToBlockProcess;
    }

    @Override
    public IPlayerContext getPlayerContext() {
        return this.playerContext;
    }

    @Override
    public FollowProcess getFollowProcess() {
        return this.followProcess;
    }

    @Override
    public BuilderProcess getBuilderProcess() {
        return this.builderProcess;
    }

    public InventoryBehavior getInventoryBehavior() {
        return this.inventoryBehavior;
    }

    @Override
    public LookBehavior getLookBehavior() {
        return this.lookBehavior;
    }

    @Override
    public ExploreProcess getExploreProcess() {
        return this.exploreProcess;
    }

    @Override
    public MineProcess getMineProcess() {
        return this.mineProcess;
    }

    @Override
    public FarmProcess getFarmProcess() {
        return this.farmProcess;
    }

    public InventoryPauserProcess getInventoryPauserProcess() {
        return this.inventoryPauserProcess;
    }

    public KillAuraOrbitProcess getKillAuraOrbitProcess() {
        return this.killAuraOrbitProcess;
    }

    @Override
    public PathingBehavior getPathingBehavior() {
        return this.pathingBehavior;
    }

    @Override
    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    @Override
    public WorldProvider getWorldProvider() {
        return this.worldProvider;
    }

    @Override
    public IEventBus getGameEventHandler() {
        return this.gameEventHandler;
    }

    @Override
    public CommandManager getCommandManager() {
        return this.commandManager;
    }

    @Override
    public IElytraProcess getElytraProcess() {
        return this.elytraProcess;
    }

    @Override
    public void openClick() {
        new Thread(() -> {
            try {
                Thread.sleep(100);
                mc.addScheduledTask(() -> mc.displayGuiScreen(new GuiClick()));
            } catch (Exception ignored) {
            }
        }).start();
    }

    public Path getDirectory() {
        return this.directory;
    }

    public static Settings settings() {
        return BaritoneAPI.getSettings();
    }

    public static Executor getExecutor() {
        return threadPool;
    }
}

