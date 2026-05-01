package LLD.musicStreamingSystem.solution.states;

import LLD.musicStreamingSystem.solution.enums.PlayerStatus;
import LLD.musicStreamingSystem.solution.model.Player;

public class StoppedState implements PlayerState {

    @Override
    public void play(Player player) {
        System.out.println("Starting the player");
        player.changeState(new PlayingState());
        player.setStatus(PlayerStatus.PLAYING);
    }

    @Override
    public void pause(Player player) {
        System.out.println("Player is stopped. Can't pause.");
    }

    @Override
    public void stop(Player player) {
        System.out.println("Player is already stopped.");
    }

}
