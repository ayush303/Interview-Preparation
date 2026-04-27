import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import entity.Board;
import entity.BoardEntity;
import entity.Dice;
import entity.Player;
import enums.GameStatus;

public class Game {
    private Board board;
    private Queue<Player> players;
    private GameStatus status;
    private Dice dice;
    private Player winner;

    public Game(Builder builder) {
        this.board = builder.board;
        this.players = new LinkedList<>(builder.players);
        this.dice = builder.dice;
        this.status = GameStatus.NOT_STARTED;
        this.winner = null;
    }

    public void play() {
        if (players.size() < 2) {
            throw new IllegalStateException("At least two players are required to start the game.");
        }
        this.status = GameStatus.RUNNING;
        System.out.println("Game started with " + players.size() + " players.");

        while (status == GameStatus.RUNNING) {
            Player currentPlayer = players.poll();
            takeTurn(currentPlayer);

            // If the game is not finished and the player didn't roll a 6, add them back to
            // the queue
            if (status == GameStatus.RUNNING) {
                players.add(currentPlayer);
            }
        }

        System.out.println("Game Finished!");
        if (winner != null) {
            System.out.printf("The winner is %s!\n", winner.getName());
        }
    }

    private void takeTurn(Player player) {
        int roll = dice.roll();
        int currentPosition = player.getPosition();
        int nextPosition = currentPosition + roll;

        if (nextPosition > board.getSize()) {
            System.out.printf("Oops, %s needs to land exactly on %d. Turn skipped.\n", player.getName(),
                    board.getSize());
            return;
        }

        if (nextPosition == board.getSize()) {
            player.setPosition(nextPosition);
            winner = player;
            status = GameStatus.FINISHED;
            System.out.printf("%s rolled a %d and moved to position %d. %s wins!\n", player.getName(), roll,
                    nextPosition, player.getName());
        }

        int finalPosition = board.getFinalPosition(nextPosition);

        if (finalPosition > nextPosition) {
            System.out.printf("%s rolled a %d and climbed a ladder from %d to %d.\n", player.getName(), roll,
                    nextPosition, finalPosition);
        } else if (finalPosition < nextPosition) {
            System.out.printf("%s rolled a %d and got bitten by a snake from %d to %d.\n", player.getName(), roll,
                    nextPosition, finalPosition);
        } else {
            System.out.printf("%s rolled a %d and moved to position %d.\n", player.getName(), roll, nextPosition);
        }

        player.setPosition(finalPosition);

        if (roll == 6) {
            System.out.printf("%s rolled a 6 and gets another turn!\n", player.getName());
            takeTurn(player);
        }
    }

    // 🧱 Inner Builder class
    public static class Builder {
        private Board board;
        private Queue<Player> players;
        private Dice dice;

        public Builder setBoard(int boardSize, List<BoardEntity> boardEntities) {
            this.board = new Board(boardSize, boardEntities);
            return this;
        }

        public Builder setPlayers(List<String> playerNames) {
            this.players = new LinkedList<>();
            for (String playerName : playerNames) {
                players.add(new Player(playerName));
            }
            return this;
        }

        public Builder setDice(Dice dice) {
            this.dice = dice;
            return this;
        }

        public Game build() {
            if (board == null || players == null || dice == null) {
                throw new IllegalStateException("Board, Players, and Dice must be set.");
            }
            return new Game(this);
        }
    }

}
