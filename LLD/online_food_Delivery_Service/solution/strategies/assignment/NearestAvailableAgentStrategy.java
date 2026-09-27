package LLD.online_food_Delivery_Service.solution.strategies.assignment;

import LLD.online_food_Delivery_Service.solution.models.Address;
import LLD.online_food_Delivery_Service.solution.models.DeliveryAgent;
import LLD.online_food_Delivery_Service.solution.models.Order;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class NearestAvailableAgentStrategy implements DeliveryAssignmentStrategy {
    @Override
    public Optional<DeliveryAgent> findAgent(Order order, List<DeliveryAgent> agents) {
        Address restaurantAddress = order.getRestaurant().getAddress();
        Address customerAddress = order.getCustomer().getAddress();
        return agents.stream()
                .filter(DeliveryAgent::isAvailable)
                .min(Comparator.comparingDouble(agent -> calculateTotalDistance(agent, restaurantAddress, customerAddress)));
    }

    private double calculateTotalDistance(DeliveryAgent agent, Address restaurantAddress, Address customerAddress) {
        double agentToRestaurantDistance = agent.getAddress().distanceTo(restaurantAddress);
        double restaurantToCustomerDistance = restaurantAddress.distanceTo(customerAddress);

        return agentToRestaurantDistance + restaurantToCustomerDistance;
    }
}
