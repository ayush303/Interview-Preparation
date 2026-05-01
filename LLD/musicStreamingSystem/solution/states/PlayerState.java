package LLD.musicStreamingSystem.solution.states;

import LLD.musicStreamingSystem.solution.model.Player;

public interface PlayerState {
    void play(Player player);

    void pause(Player player);

    void stop(Player player);
}
