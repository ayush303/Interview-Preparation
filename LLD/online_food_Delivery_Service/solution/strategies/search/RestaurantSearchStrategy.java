package LLD.online_food_Delivery_Service.solution.strategies.search;

import LLD.online_food_Delivery_Service.solution.models.Restaurant;

import java.util.List;

public interface RestaurantSearchStrategy {
    List<Restaurant> filter(List<Restaurant> allRestaurants);
}
