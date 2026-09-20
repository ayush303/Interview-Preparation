package LLD.ticketManagementSystem.solution.Observer;

public interface Subject {
    void addObserver(Observer o);
    void notifyObservers(String message);
}
