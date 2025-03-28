package com.pulse.checkout.domain.dto;

import com.pulse.checkout.domain.model.Cart;
import com.pulse.checkout.domain.model.CartItem;

import java.util.List;

public record CartDto (
    Long id,
    List<CartItem> items,
    CustomerDto customer,
    boolean checkedOut
) {
    public CartDto (Cart cart) {
        this(cart.getId(), cart.getItems(), new CustomerDto(cart.getCustomer()), cart.isCheckedOut());
    }
}
