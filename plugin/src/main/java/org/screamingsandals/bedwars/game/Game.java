package org.screamingsandals.bedwars.game;

import org.screamingsandals.bedwars.api.events.game.BedWarsGameCyclePrepareEvent;
import org.screamingsandals.bedwars.game.cycle.MultiGameBungeeCycle;
import org.screamingsandals.bedwars.game.cycle.MultiGameCycle;
import org.screamingsandals.bedwars.game.cycle.SingleGameBungeeCycle;
import org.screamingsandals.bedwars.game.phase.*;
import org.screamingsandals.lib.gamecore.GameCore;
import org.screamingsandals.lib.gamecore.core.GameFrame;
import org.screamingsandals.lib.gamecore.core.GameState;
import org.screamingsandals.lib.gamecore.core.cycle.GameCycle;

import java.util.Optional;

public class Game extends GameFrame {

    public Game(String gameName) {
        super(gameName);
    }

    @Override
    public void loadDefaults() {
        super.loadDefaults();
    }

    @Override
    public boolean prepare() {
        if (!super.prepare()) {
            return false;
        }

        if (gameCycle != null) {
            gameCycle.getAvailablePhases().clear();
        } else {
            final var preparedCycle = prepareGameCycle();

            if (preparedCycle.isEmpty()) {
                return false;
            }
            gameCycle = preparedCycle.get();
        }

        gameCycle.addPhase(GameState.LOADING, new LoadingPhase(gameCycle));
        gameCycle.addPhase(GameState.WAITING, new WaitingPhase(gameCycle));
        gameCycle.addPhase(GameState.PRE_GAME_COUNTDOWN, new StartingPhase(gameCycle, getStartTime()));
        gameCycle.addPhase(GameState.IN_GAME, new InGamePhase(gameCycle, getGameTime()));
        gameCycle.addPhase(GameState.RESTART, new RestartPhase(gameCycle));
        return true;
    }

    private Optional<GameCycle> prepareGameCycle() {
        GameCycle toReturn = null;
        final var gameType = GameCore.getGameManager().getCycleType();
        final var event = new BedWarsGameCyclePrepareEvent(this);
        if (GameCore.fireEvent(event)) {
            toReturn = event.getGameCycle();
        }

        if (toReturn != null) {
            return Optional.of(toReturn);
        }

        switch (gameType) {
            case MULTI_GAME:
                toReturn = new MultiGameCycle(this);
                break;

            case MULTI_GAME_BUNGEE:
                toReturn = new MultiGameBungeeCycle(this);
                break;
            case SINGLE_GAME_BUNGEE:
                toReturn = new SingleGameBungeeCycle(this);
                break;
        }

        if (toReturn == null) {
            return Optional.empty();
        }

        return Optional.of(toReturn);
    }
}
