package LLD.musicStreamingSystem.solution.states;

import LLD.musicStreamingSystem.solution.enums.PlayerStatus;
import LLD.musicStreamingSystem.solution.model.Player;

public class PlayingState implements PlayerState {

    @Override
    public void play(Player player) {
        System.out.println("Player is already playing.");
    }

    @Override
    public void pause(Player player) {
        System.out.println("Pausing the player.");
        player.changeState(new PausedState());
        player.setStatus(PlayerStatus.PLAUSED);
    }

    @Override
    public void stop(Player player) {
        System.out.println("Stopping the player.");
        player.changeState(new StoppedState());
        player.setStatus(PlayerStatus.STOPPED);
    }

}
