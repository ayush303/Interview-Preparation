package LLD.online_food_Delivery_Service.solution.strategies.search;

import LLD.online_food_Delivery_Service.solution.models.Address;
import LLD.online_food_Delivery_Service.solution.models.Restaurant;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SearchByProximityStrategy implements RestaurantSearchStrategy {
    private final Address userLocation;
    private final double maxDistanceKm;

    public SearchByProximityStrategy(Address userLocation, double maxDistanceKm) {
        this.userLocation = userLocation;
        this.maxDistanceKm = maxDistanceKm;
    }


    @Override
    public List<Restaurant> filter(List<Restaurant> allRestaurants) {
        return allRestaurants.stream()
                .filter(r -> userLocation.distanceTo(r.getAddress()) <= maxDistanceKm)
                .sorted(Comparator.comparingDouble(r -> userLocation.distanceTo(r.getAddress())))
                .collect(Collectors.toList());
    }
}
