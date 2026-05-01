package LLD.musicStreamingSystem.solution.states;

import LLD.musicStreamingSystem.solution.enums.PlayerStatus;
import LLD.musicStreamingSystem.solution.model.Player;

public class PausedState implements PlayerState {
    @Override
    public void play(Player player) {
        System.out.println("Resuming the player");
        player.changeState(new PlayingState());
        player.setStatus(PlayerStatus.PLAYING);
    }

    @Override
    public void pause(Player player) {
        System.out.println("Player is already paused.");
    }

    @Override
    public void stop(Player player) {
        System.out.println("Stopping the player from paused state.");
        player.changeState(new StoppedState());
        player.setStatus(PlayerStatus.STOPPED);
    }

}
