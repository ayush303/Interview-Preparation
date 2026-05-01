package LLD.musicStreamingSystem.solution.command;

import LLD.musicStreamingSystem.solution.model.Player;

public class PlayCommand implements Command {
    private final Player player;

    public PlayCommand(Player player) {
        this.player = player;
    }

    @Override
    public void execute() {
        player.clickPlay();
    }

}
