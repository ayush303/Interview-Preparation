package LLD.online_food_Delivery_Service.solution.strategies.search;

import LLD.online_food_Delivery_Service.solution.models.Restaurant;

import java.util.List;
import java.util.stream.Collectors;

public class SearchByCityStrategy implements RestaurantSearchStrategy {
    private final String city;

    public SearchByCityStrategy(String city) {
        this.city = city;
    }

    @Override
    public List<Restaurant> filter(List<Restaurant> allRestaurants) {
        return allRestaurants.stream()
                .filter(r -> r.getAddress().getCity().equals(city))
                .collect(Collectors.toList());
    }
}
