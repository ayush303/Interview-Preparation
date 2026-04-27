package entity;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Board {
    int size;
    ConcurrentHashMap<Integer, Integer> snakesAndLadders; // Maps start position to the entity (Snake or Ladder)

    public Board(int size, List<BoardEntity> entities) {
        this.size = size;

        for (BoardEntity entity : entities) {
            snakesAndLadders.put(entity.getStart(), entity.getEnd());
        }
    }

    public int getSize() {
        return size;
    }

    public int getFinalPosition(int position) {
        return snakesAndLadders.getOrDefault(position, position);
    }
}
