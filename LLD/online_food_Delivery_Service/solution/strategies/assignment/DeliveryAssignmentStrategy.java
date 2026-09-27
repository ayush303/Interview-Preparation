package LLD.online_food_Delivery_Service.solution.strategies.assignment;

import LLD.online_food_Delivery_Service.solution.models.DeliveryAgent;
import LLD.online_food_Delivery_Service.solution.models.Order;

import java.util.List;
import java.util.Optional;

public interface DeliveryAssignmentStrategy {
    Optional<DeliveryAgent> findAgent(Order order, List<DeliveryAgent> agents);
}
