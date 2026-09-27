package LLD.online_food_Delivery_Service.solution.strategies.search;

import LLD.online_food_Delivery_Service.solution.models.Restaurant;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class SearchByMenuKeywordStrategy implements RestaurantSearchStrategy {
    private final String keyword;

    public SearchByMenuKeywordStrategy(String keyword) {
        this.keyword = keyword.toLowerCase(Locale.ROOT);
    }


    @Override
    public List<Restaurant> filter(List<Restaurant> allRestaurants) {
        return allRestaurants.stream()
                .filter(r -> r.getMenu()
                        .getItems().values()
                        .stream()
                        .anyMatch(item -> item.getName()
                                .toLowerCase(Locale.ROOT)
                                .contains(keyword)))
                .collect(Collectors.toList());
    }
}
