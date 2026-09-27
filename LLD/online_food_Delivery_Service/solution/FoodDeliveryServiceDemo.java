package LLD.online_food_Delivery_Service.solution;

import LLD.online_food_Delivery_Service.solution.enums.OrderStatus;
import LLD.online_food_Delivery_Service.solution.models.*;
import LLD.online_food_Delivery_Service.solution.strategies.assignment.NearestAvailableAgentStrategy;
import LLD.online_food_Delivery_Service.solution.strategies.search.RestaurantSearchStrategy;
import LLD.online_food_Delivery_Service.solution.strategies.search.SearchByCityStrategy;
import LLD.online_food_Delivery_Service.solution.strategies.search.SearchByMenuKeywordStrategy;
import LLD.online_food_Delivery_Service.solution.strategies.search.SearchByProximityStrategy;

import java.math.BigDecimal;
import java.util.List;

public class FoodDeliveryServiceDemo {
    public static void main(String[] args) {
        // 1. Setup the system
        FoodDeliveryService service = FoodDeliveryService.getInstance();
        service.setAssignmentStrategy(new NearestAvailableAgentStrategy());

        // 2. Define Addresses
        Address aliceAddress = new Address("123 Maple St", "Springfield", "12345", 40.7128, -74.0060);
        Address pizzaAddress = new Address("456 Oak Ave", "Springfield", "12345", 40.7138, -74.0070);
        Address burgerAddress = new Address("789 Pine Ln", "Springfield", "12345", 40.7108, -74.0050);
        Address tacoAddress = new Address("101 Elm Ct", "Shelbyville", "54321", 41.7528, -75.0160);

        // 3. Register entities
        Customer alice = service.registerCustomer("Alice", "123-4567-890", aliceAddress);
        Restaurant pizzaPalace = service.registerRestaurant("Pizza Palace", pizzaAddress);
        Restaurant burgerBarn = service.registerRestaurant("Burger Barn", burgerAddress);
        Restaurant tacoTown = service.registerRestaurant("Taco Town", tacoAddress);
        DeliveryAgent bob = service.registerDeliveryAgent("Bob", "321-4567-880",
                new Address("1 B", "Springfield", "12345", 40.7100, -74.0000));

        // 4. Setup menus (price, portions in stock)
        pizzaPalace.addToMenu(new MenuItem("P001", "Margherita Pizza", new BigDecimal("12.99"), 20));
        pizzaPalace.addToMenu(new MenuItem("P002", "Veggie Pizza", new BigDecimal("11.99"), 20));
        burgerBarn.addToMenu(new MenuItem("B001", "Classic Burger", new BigDecimal("8.99"), 20));
        tacoTown.addToMenu(new MenuItem("T001", "Crunchy Taco", new BigDecimal("3.50"), 20));

        // 5. Demonstrate Search Functionality
        System.out.println("\n--- 1. Searching for Restaurants ---");

        // (A) Search by City
        System.out.println("\n(A) Restaurants in 'Springfield':");
        List<RestaurantSearchStrategy> citySearch = List.of(new SearchByCityStrategy("Springfield"));
        List<Restaurant> springfieldRestaurants = service.searchRestaurants(citySearch);
        springfieldRestaurants.forEach(r -> System.out.println("  - " + r.getName()));

        // (B) Search for restaurants near Alice
        System.out.println("\n(B) Restaurants within 1 km of Alice:");
        List<RestaurantSearchStrategy> proximitySearch = List.of(new SearchByProximityStrategy(aliceAddress, 1.0));
        List<Restaurant> nearbyRestaurants = service.searchRestaurants(proximitySearch);
        nearbyRestaurants.forEach(r -> System.out.printf("  - %s (%.2f km)\n", r.getName(), aliceAddress.distanceTo(r.getAddress())));

        // (C) Search for restaurants that serve 'Pizza'
        System.out.println("\n(C) Restaurants that serve 'Pizza':");
        List<RestaurantSearchStrategy> menuSearch = List.of(new SearchByMenuKeywordStrategy("Pizza"));
        List<Restaurant> pizzaRestaurants = service.searchRestaurants(menuSearch);
        pizzaRestaurants.forEach(r -> System.out.println("  - " + r.getName()));

        // (D) Combined Search: Find restaurants near Alice that serve 'Burger'
        System.out.println("\n(D) Burger joints near Alice:");
        List<RestaurantSearchStrategy> combinedSearch = List.of(
                new SearchByProximityStrategy(aliceAddress, 1.0),
                new SearchByMenuKeywordStrategy("Burger")
        );
        List<Restaurant> burgerJointsNearAlice = service.searchRestaurants(combinedSearch);
        burgerJointsNearAlice.forEach(r -> System.out.println("  - " + r.getName()));

        // 6. Demonstrate Browsing a Menu
        System.out.println("\n--- 2. Browsing a Menu ---");
        System.out.println("\nMenu for 'Pizza Palace':");
        Menu pizzaMenu = service.getRestaurantMenu(pizzaPalace.getId());
        pizzaMenu.getItems().values().forEach(item ->
                System.out.printf("  - %s: $%.2f\n", item.getName(), item.getPrice())
        );

        // 7. Alice places an order from a searched restaurant
        System.out.println("\n--- 3. Placing an Order ---");
        Restaurant chosenRestaurant = pizzaRestaurants.get(0);
        MenuItem chosenItem = chosenRestaurant.getMenu().getItem("P001");

        System.out.printf("\nAlice is ordering '%s' from '%s'.\n", chosenItem.getName(), chosenRestaurant.getName());
        Order order = service.placeOrder(alice.getId(), chosenRestaurant.getId(), List.of(new OrderItem(chosenItem, 1)));

        System.out.printf("Order total: $%.2f\n", order.getTotalAmount());

        System.out.println("\n--- Restaurant confirms the order ---");
        service.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED);

        System.out.println("\n--- Restaurant starts preparing the order ---");
        service.updateOrderStatus(order.getId(), OrderStatus.PREPARING);

        System.out.println("\n--- Alice tries to cancel, but the kitchen has started ---");
        try {
            service.cancel(order.getId());
        } catch (IllegalStateException e) {
            System.out.println("Cancel refused: " + e.getMessage());
        }

        System.out.println("\n--- Order is ready for pickup ---");
        System.out.println("System will now find the nearest available delivery agent...");
        service.updateOrderStatus(order.getId(), OrderStatus.READY_FOR_PICKUP);
        System.out.println("Assigned agent: " + order.getDeliveryAgent().getName());

        System.out.println("\n--- Agent delivers the order ---");
        service.updateOrderStatus(order.getId(), OrderStatus.DELIVERED);
        System.out.printf("Order status: %s. %s is available again: %s (now at %s)\n",
                order.getOrderStatus(), bob.getName(), bob.isAvailable(), bob.getAddress().getStreet());
    }
}
