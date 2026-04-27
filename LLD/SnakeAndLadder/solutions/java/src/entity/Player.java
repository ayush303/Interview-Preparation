package entity;

public class Player {
    String name;
    int position;

    public Player(String name) {
        this.name = name;
        this.position = 0; // Starting position
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
