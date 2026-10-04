package com.lixiaopu.pojo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CartDTO {
    @NotNull
    @JsonAlias("items")
    private List<@Valid CartProductDTO> cartItems;
}
