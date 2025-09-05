package io.airlift.api.validation;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@ApiService(type = ServiceType.class, name = "standard", description = "Does standard things")
public class FoodServiceInvalid
{
    @ApiCreate(description = "Create food")
    public void createFood(Food food)
    {
    }
}
