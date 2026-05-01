package LLD.musicStreamingSystem.solution.command;

import LLD.musicStreamingSystem.solution.model.Player;

public class PauseCommand implements Command {
    private final Player player;

    public PauseCommand(Player player) {
        this.player = player;
    }

    @Override
    public void execute() {
        player.clickPause();
    }

}
