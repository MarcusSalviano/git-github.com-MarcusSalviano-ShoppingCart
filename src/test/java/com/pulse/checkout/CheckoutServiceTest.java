package com.pulse.checkout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pulse.checkout.domain.model.*;
import com.pulse.checkout.repository.CartRepository;
import com.pulse.checkout.repository.CustomerAddressRepository;
import com.pulse.checkout.repository.OrderRepository;
import com.pulse.checkout.service.CheckoutService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CustomerAddressRepository addressRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private CheckoutService checkoutService;

    private Cart cart;
    private Customer customer;
    private CustomerAddress address;
    private Product product1;
    private Product product2;
    private CartItem cartItem1;
    private CartItem cartItem2;

    @BeforeEach
    void setUp() {
        customer = new Customer("John Doe", "john@example.com");
        customer.setId(1L);

        address = new CustomerAddress();
        address.setId(1L);
        address.setCustomer(customer);
        address.setStreet("Main St");
        address.setCity("Springfield");
        address.setState("IL");
        address.setZipCode("12345");

        product1 = new Product("Product 1", BigDecimal.valueOf(10.99));
        product1.setId(1L);
        product2 = new Product("Product 2", BigDecimal.valueOf(5.50));
        product2.setId(2L);

        cart = new Cart(customer);
        cart.setId(1L);
        cart.setCheckedOut(false);

        cartItem1 = new CartItem(product1, 2, cart);
        cartItem2 = new CartItem(product2, 3, cart);

        List<CartItem> items = new ArrayList<>();
        items.add(cartItem1);
        items.add(cartItem2);
        cart.setItems(items);
    }

    @Test
    void checkout_Successful() {
        // Arrange
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Order result = checkoutService.checkout(1L, 1L, ShippingMethod.STANDARD, PaymentMethod.CREDIT_CARD);

        // Assert
        assertNotNull(result);
        assertEquals(customer, result.getCustomer());
        assertEquals(address, result.getShippingAddress());
        assertEquals(ShippingMethod.STANDARD, result.getShippingMethod());
        assertEquals(PaymentMethod.CREDIT_CARD, result.getPaymentMethod());
        assertNotNull(result.getOrderDate());
        assertFalse(result.getOrderDate().isAfter(LocalDateTime.now()));

        // Verify items and total
        assertEquals(2, result.getItems().size());
        BigDecimal expectedTotal = product1.getPrice().multiply(BigDecimal.valueOf(2))
                .add(product2.getPrice().multiply(BigDecimal.valueOf(3)));
        assertEquals(expectedTotal, result.getTotal());

        // Verify cart is marked as checked out
        assertTrue(cart.isCheckedOut());

        // Verify repository interactions
        verify(cartRepository).findById(1L);
        verify(addressRepository).findById(1L);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void checkout_CartNotFound_ThrowsException() {
        // Arrange
        when(cartRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(Exception.class, () ->
                checkoutService.checkout(1L, 1L, ShippingMethod.STANDARD, PaymentMethod.CREDIT_CARD)
        );

        verify(cartRepository).findById(1L);
        verifyNoInteractions(addressRepository, orderRepository);
    }

    @Test
    void checkout_AddressNotFound_ThrowsException() {
        // Arrange
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () ->
                checkoutService.checkout(1L, 1L, ShippingMethod.STANDARD, PaymentMethod.CREDIT_CARD)
        );

        verify(cartRepository).findById(1L);
        verify(addressRepository).findById(1L);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void checkout_CartAlreadyCheckedOut_ThrowsException() {
        // Arrange
        cart.setCheckedOut(true);
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));

        // Act & Assert
        assertThrows(IllegalStateException.class, () ->
                checkoutService.checkout(1L, 1L, ShippingMethod.STANDARD, PaymentMethod.CREDIT_CARD)
        );

        verify(cartRepository).findById(1L);
        verifyNoInteractions(addressRepository, orderRepository);
    }

    @Test
    void checkout_EmptyCart_SuccessfulWithZeroTotal() {
        // Arrange
        cart.setItems(new ArrayList<>());
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Order result = checkoutService.checkout(1L, 1L, ShippingMethod.EXPRESS, PaymentMethod.PIX);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getItems().size());
        assertEquals(BigDecimal.ZERO, result.getTotal());
        assertTrue(cart.isCheckedOut());
    }

    @Test
    void checkout_VerifyOrderItemDetails() {
        // Arrange
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Order result = checkoutService.checkout(1L, 1L, ShippingMethod.CORREIOS_PAC, PaymentMethod.BOLETO);

        // Assert
        assertEquals(2, result.getItems().size());

        OrderItem item1 = result.getItems().get(0);
        assertEquals(product1.getName(), item1.getProductName());
        assertEquals(cartItem1.getQuantity(), item1.getQuantity());
        assertEquals(product1.getPrice(), item1.getUnitPrice());

        OrderItem item2 = result.getItems().get(1);
        assertEquals(product2.getName(), item2.getProductName());
        assertEquals(cartItem2.getQuantity(), item2.getQuantity());
        assertEquals(product2.getPrice(), item2.getUnitPrice());
    }
}