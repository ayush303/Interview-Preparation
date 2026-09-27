package LLD.online_food_Delivery_Service.solution.observer;

import LLD.online_food_Delivery_Service.solution.enums.OrderStatus;

public interface Subject {
    void addObserver(Observer observer);
    void removeObserver(Observer observer);
    void notifyObservers(OrderStatus status);
}
