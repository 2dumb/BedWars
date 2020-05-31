package org.screamingsandals.bedwars.commands.game.admin;

import org.bukkit.entity.Player;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.Permissions;
import org.screamingsandals.bedwars.commands.BedWarsCommand;
import org.screamingsandals.bedwars.commands.game.admin.actions.Action;
import org.screamingsandals.bedwars.commands.game.admin.actions.ActionType;
import org.screamingsandals.bedwars.commands.game.admin.actions.add.AddSpawnerAction;
import org.screamingsandals.bedwars.commands.game.admin.actions.add.AddStoreAction;
import org.screamingsandals.bedwars.commands.game.admin.actions.add.AddTeamAction;
import org.screamingsandals.bedwars.commands.game.admin.actions.set.SetBorderAction;
import org.screamingsandals.bedwars.commands.game.admin.actions.set.SetSpawnAction;
import org.screamingsandals.bedwars.game.Game;
import org.screamingsandals.bedwars.game.GameBuilder;
import org.screamingsandals.lib.commands.common.RegisterCommand;
import org.screamingsandals.lib.commands.common.SubCommandBuilder;
import org.screamingsandals.lib.commands.common.interfaces.ScreamingCommand;
import org.screamingsandals.lib.gamecore.core.GameManager;
import org.screamingsandals.lib.gamecore.core.GameState;

import java.util.*;

import static org.screamingsandals.lib.gamecore.language.GameLanguage.mpr;

@RegisterCommand(subCommand = true)
public class AdminCommand implements ScreamingCommand {
    private final Map<ActionType.Add, Action> addActions = new HashMap<>();
    private final Map<ActionType.Set, Action> setActions = new HashMap<>();

    @Override
    public void register() {
        SubCommandBuilder.bukkitSubCommand()
                .createSubCommand(BedWarsCommand.COMMAND_NAME, "admin", Permissions.ADMIN_COMMAND, Collections.emptyList())
                .handleSubPlayerCommand(this::handleCommand)
                .handleSubPlayerTab(this::handleTab);

        setActions.put(ActionType.Set.BORDER, new SetBorderAction());
        setActions.put(ActionType.Set.SPAWN, new SetSpawnAction());

        addActions.put(ActionType.Add.STORE, new AddStoreAction());
        addActions.put(ActionType.Add.SPAWNER, new AddSpawnerAction());
        addActions.put(ActionType.Add.TEAM, new AddTeamAction());
    }

    private void handleCommand(Player player, List<String> args) {
        final var argsSize = args.size();
        if (argsSize < 3) {
            mpr("commands.admin.errors.not_enough_arguments").send(player);
            return;
        }

        final var gameManager = Main.getGameManager();
        final var gameName = args.get(1);
        final var action = args.get(2).toLowerCase();
        final List<String> subList = args.subList(2, argsSize);

        GameBuilder gameBuilder = null;
        if (!action.equals("create")
                && !action.equals("edit")) {
            if (!gameManager.isInBuilder(gameName)) {
                mpr("game-builder.check-integrity.errors.not-created-yet").send(player);
                return;
            }

            gameBuilder = gameManager.getGameBuilder(gameName);

            if (gameBuilder == null) {
                mpr("game-builder.check-integrity.errors.not-created-yet").send(player);
                return;
            }
        }

        switch (action) {
            case "create":
                handleCreate(gameName, player);
                break;
            case "set":
            case "add":
                handleActions(gameBuilder, player, subList);
                break;
            case "save":
                handleSave(gameBuilder, player);
                break;
            case "edit":
                handleEdit(gameManager, gameName, player);
                break;
            default:
                mpr("commands.admin.errors.invalid_action")
                        .replace("%action%", action)
                        .send(player);
                break;
        }
    }

    private List<String> handleTab(Player player, List<String> args) {
        final var gameManager = Main.getGameManager();
        final List<String> toReturn = new LinkedList<>();
        final var argsSize = args.size();

        if (argsSize < 2) {
            return toReturn;
        }

        var gameName = args.get(1);

        if (argsSize == 2) {
            final List<String> available = new LinkedList<>();

            available.addAll(gameManager.getRegisteredGamesNames());
            available.addAll(gameManager.getRegisteredBuildersNames());

            for (String found : available) {
                if (found.startsWith(gameName)) {
                    toReturn.add(found);
                }
            }

            return toReturn;
        }

        if (argsSize == 3) {
            final var typed = args.get(2);
            final List<String> available = new ArrayList<>(List.of("add", "set"));

            if (!gameManager.isInBuilder(gameName) && !gameManager.isGameRegistered(gameName)) {
                available.add("create");
            }

            if (gameManager.isInBuilder(gameName) && gameManager.getGameBuilder(gameName).isReadyToSave(false)) {
                available.add("save");
            }

            if (gameManager.isGameRegistered(gameName)) {
                available.add("edit");
            }

            //TODO: edit this to be more good while we have multiple games
            //list.of(stop, start, maintenance) if no game is in edit mode

            for (String found : available) {
                if (found.startsWith(typed)) {
                    toReturn.add(found);
                }
            }

            return toReturn;
        }

        GameBuilder gameBuilder = null;
        if (gameManager.isInBuilder(gameName)) {
            gameBuilder = gameManager.getGameBuilder(gameName);
        }

        final var action = args.get(2).toLowerCase();
        final var subList = args.subList(3, argsSize);

        if (gameBuilder == null) {
            return toReturn;
        }

        switch (action) {
            case "add": {
                if (checkPermissions(player, Permissions.ADMIN_COMMAND_ACTION_ADD)) {
                    return handleAddTab(gameBuilder, player, subList);
                }
                break;
            }
            case "set": {
                if (checkPermissions(player, Permissions.ADMIN_COMMAND_ACTION_SET)) {
                    return handleSetTab(gameBuilder, player, subList);
                }
                break;
            }
        }
        return toReturn;
    }

    private void handleCreate(String gameName, Player player) {
        final var gameManager = Main.getGameManager();
        if (gameManager.isGameRegistered(gameName) || gameManager.isInBuilder(gameName)) {
            mpr("general.errors.game-already-created")
                    .replace("%game%", gameName)
                    .send(player);
            return;
        }

        final var gameBuilder = new GameBuilder();
        gameBuilder.create(gameName, player);

        gameManager.registerBuilder(gameBuilder.getGameFrame().getUuid(), gameBuilder);
    }

    private void handleActions(GameBuilder gameBuilder, Player player, List<String> args) {
        final var currentGame = gameBuilder.getGameFrame();
        final var argsSize = args.size();

        if (argsSize == 1) {
            mpr("general.errors.not-enough-args").send(player);
            return;
        }

        final var action = args.get(0);
        final var whatToDo = args.get(1);
        final var subList = args.subList(2, argsSize);

        switch (action) {
            case "add": {
                switch (whatToDo) {
                    case "team": {
                        addActions.get(ActionType.Add.TEAM).handleCommand(gameBuilder, player, subList);
                        break;
                    }
                    case "spawner": {
                        addActions.get(ActionType.Add.SPAWNER).handleCommand(gameBuilder, player, subList);
                        break;
                    }
                    case "store":
                    case "shop": {
                        addActions.get(ActionType.Add.STORE).handleCommand(gameBuilder, player, subList);
                        break;
                    }
                    default:
                        mpr("general.errors.unknown-parameter")
                                .game(currentGame)
                                .send(player);
                        break;
                }
                break;
            }
            case "set": {
                switch (whatToDo) {
                    case "border": {
                        setActions.get(ActionType.Set.BORDER).handleCommand(gameBuilder, player, subList);
                        break;
                    }
                    case "spawn": {
                        setActions.get(ActionType.Set.SPAWN).handleCommand(gameBuilder, player, subList);
                        break;
                    }
                    default:
                        mpr("general.errors.unknown-parameter")
                                .game(currentGame)
                                .send(player);
                        break;
                }
                break;
            }
            default:
                mpr("general.errors.unknown-parameter")
                        .game(currentGame)
                        .send(player);
                break;
        }
    }

    private List<String> handleAddTab(GameBuilder gameBuilder, Player player, List<String> args) {
        final var argsSize = args.size();
        if (argsSize == 0) {
            return Collections.emptyList();
        }

        final var action = args.get(0);
        final List<String> toReturn = new LinkedList<>();

        if (argsSize == 1) {
            final List<String> available = new ArrayList<>(List.of("team", "spawner", "store"));
            for (String found : available) {
                if (found.startsWith(action)) {
                    toReturn.add(found);
                }
            }
            return toReturn;
        }

        final var subList = args.subList(1, argsSize);
        switch (action) {
            case "team":
                return addActions.get(ActionType.Add.TEAM).handleTab(gameBuilder, player, subList);
            case "spawner":
                return addActions.get(ActionType.Add.SPAWNER).handleTab(gameBuilder, player, subList);
            case "store":
            case "shop":
                return addActions.get(ActionType.Add.STORE).handleTab(gameBuilder, player, subList);
        }

        return toReturn;
    }

    private List<String> handleSetTab(GameBuilder gameBuilder, Player player, List<String> args) {
        final var argsSize = args.size();
        if (argsSize == 0) {
            return Collections.emptyList();
        }

        final var action = args.get(0);
        final List<String> toReturn = new LinkedList<>();

        if (argsSize == 1) {
            final List<String> available = new ArrayList<>(List.of("border", "spawn"));

            for (String found : available) {
                if (found.startsWith(action)) {
                    toReturn.add(found);
                }
            }
            return toReturn;
        }

        final var subList = args.subList(1, argsSize);
        switch (action) {
            case "border":
                return setActions.get(ActionType.Set.BORDER).handleTab(gameBuilder, player, subList);
            case "spawn":
                return setActions.get(ActionType.Set.SPAWN).handleTab(gameBuilder, player, subList);
        }

        return toReturn;
    }

    public void handleSave(GameBuilder gameBuilder, Player player) {
        if (gameBuilder == null) {
            mpr("commands.admin.actions.save.cannot-save").sendList(player);
            return;
        }

        gameBuilder.save(player);
    }

    public void handleEdit(GameManager<?> gameManager, String gameName, Player player) {
        if (!gameManager.isGameRegistered(gameName)) {
            mpr("commands.admin.actions.edit.invalid-entry")
                    .replace("%gameName%", gameName)
                    .sendList(player);
            return;
        }

        final var game = gameManager.getRegisteredGame(gameName);

        if (game.isEmpty()) {
            mpr("commands.admin.actions.edit.invalid-entry")
                    .replace("%gameName%", gameName)
                    .sendList(player);
            return;
        }

        final var gameFrame = game.get();

        if (gameFrame.getActiveState() != GameState.DISABLED) {
            mpr("commands.admin.actions.edit.running").send(player);
            if (gameFrame.getActiveState() == GameState.MAINTENANCE) {
                gameFrame.stopMaintenance();
            }
            gameFrame.stop();
            mpr("commands.admin.actions.edit.stopped").send(player);
        }

        mpr("commands.admin.actions.edit.success")
                .replace("%gameName%", gameName)
                .send(player);

        final var builder = new GameBuilder();
        builder.load((Game) gameFrame, player);
        gameManager.registerBuilder(gameFrame.getUuid(),builder);
    }

    private boolean checkPermissions(Player player, String permission) {
        return player.hasPermission(permission);
    }

}
