package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.*;
import com.api.v1.bearerauth.model.enumerate.OrderStatus;
import com.api.v1.bearerauth.model.enumerate.OrderType;
import com.api.v1.bearerauth.model.enumerate.OptionType;
import com.api.v1.bearerauth.model.request.OrderRequest;
import com.api.v1.bearerauth.model.request.OrderStatusRequest;
import com.api.v1.bearerauth.model.response.CustomerRewardResponse;
import com.api.v1.bearerauth.model.response.OrderItemResponse;
import com.api.v1.bearerauth.model.response.OrderResponse;
import com.api.v1.bearerauth.repository.CustomerRepository;
import com.api.v1.bearerauth.repository.OrderTbRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.service.OrderItemService;
import com.api.v1.bearerauth.service.OrderService;
import com.api.v1.bearerauth.service.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final double DELIVERY_FEE = 2.0;

    private final OrderTbRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CurrentUserService currentUserService;
    private final OrderItemService orderItemService;
    private final RewardService rewardService;

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> list(int page, int size, Sort.Direction direction) {
        AppUser user = currentUserService.getCurrentUser();
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(direction, "orderId"));
        if (user.getRole().name().equals("ADMIN")) {
            return orderRepository.findAll(pageable).map(this::toResponse);
        }
        return orderRepository.findByCustomerUser(user, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        OrderTb order = findOrder(id);
        checkOwnerOrAdmin(order);
        return toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse create(OrderRequest request) {
        AppUser user = currentUserService.getCurrentUser();
        Customer customer = customerRepository.findByUser(user)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Customer profile not found"));

        OrderTb order = new OrderTb();
        order.setCustomer(customer);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.processing);
        applyOrderFieldsAndItems(order, request);

        // Flush first so the order has an ID before the reward references it.
        order = orderRepository.saveAndFlush(order);
        applySelectedReward(order, request.getRewardId(), calculateFoodSubtotal(order.getItems()));
        order = orderRepository.save(order);
        return toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse update(Long id, OrderRequest request) {
        OrderTb order = findOrder(id);
        checkOwnerOrAdmin(order);
        if (order.getStatus() != OrderStatus.processing) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Only processing orders can be updated");
        }

        // If checkout is edited, release its previous reward before optionally selecting another.
        rewardService.releaseReservedRewardForOrder(order);
        applyOrderFieldsAndItems(order, request);
        order = orderRepository.saveAndFlush(order);
        applySelectedReward(order, request.getRewardId(), calculateFoodSubtotal(order.getItems()));
        order = orderRepository.save(order);
        return toResponse(order);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        OrderTb order = findOrder(id);
        checkOwnerOrAdmin(order);
        if (order.getStatus() != OrderStatus.processing) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Only processing orders can be deleted");
        }
        rewardService.releaseReservedRewardForOrder(order);
        orderRepository.delete(order);
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatusRequest request) {
        OrderTb order = findOrder(id);
        OrderStatus previousStatus = order.getStatus();
        OrderStatus newStatus = request.getStatus();

        if (previousStatus == newStatus) return toResponse(order);
        if (previousStatus != OrderStatus.processing) {
            throw new CustomException(HttpStatus.BAD_REQUEST,
                    "Only processing orders can transition to purchased or cancelled");
        }

        order.setStatus(newStatus);
        order = orderRepository.saveAndFlush(order);
        rewardService.handleOrderStatusChange(order, previousStatus, newStatus);
        return toResponse(order);
    }

    private void applyOrderFieldsAndItems(OrderTb order, OrderRequest request) {
        if (request.getOrderType() == null) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Order type is required");
        }
        if (request.getPaymentMethod() == null) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Payment method is required");
        }

        order.setOrderType(request.getOrderType());
        order.setPaymentMethod(request.getPaymentMethod());

        boolean isDelivery = request.getOrderType() == OrderType.DELIVERY;
        if (isDelivery && (request.getDeliveryAddress() == null || request.getDeliveryAddress().isBlank())) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Delivery address is required");
        }
        order.setDeliveryAddress(isDelivery ? request.getDeliveryAddress().trim() : "");
        order.setDeliveryLatitude(isDelivery ? request.getDeliveryLatitude() : null);
        order.setDeliveryLongitude(isDelivery ? request.getDeliveryLongitude() : null);
        order.setDeliveryFee(isDelivery ? DELIVERY_FEE : 0.0);

        order.getItems().clear();
        List<ProductOrder> items = orderItemService.createItems(order, request.getItems());
        order.getItems().addAll(items);

        // Unit price already contains the selected SIZE's full price; only ADD_ON is added here.
        double subtotal = calculateFoodSubtotal(items);
        order.setDiscount(0.0);
        order.setTotalAmount(roundMoney(subtotal + order.getDeliveryFee()));
    }

    private void applySelectedReward(OrderTb order, Long rewardId, double foodSubtotal) {
        double discount = rewardId == null ? 0.0
                : rewardService.reserveReward(order.getCustomer(), rewardId, foodSubtotal, order);
        order.setDiscount(roundMoney(discount));
        order.setTotalAmount(roundMoney(foodSubtotal + order.getDeliveryFee() - discount));
    }

    private double calculateFoodSubtotal(List<ProductOrder> items) {
        if (items == null) return 0.0;
        double subtotal = items.stream().mapToDouble(item -> {
            double addOns = item.getSelectedOptions() == null ? 0.0 : item.getSelectedOptions().stream()
                    .filter(option -> option.getOptionType() == OptionType.ADD_ON)
                    .mapToDouble(option -> option.getExtraPrice() == null ? 0.0 : option.getExtraPrice())
                    .sum();
            return (item.getUnitPrice() + addOns) * item.getQuantity();
        }).sum();
        return roundMoney(subtotal);
    }

    private OrderTb findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private void checkOwnerOrAdmin(OrderTb order) {
        AppUser user = currentUserService.getCurrentUser();
        if (!user.getRole().name().equals("ADMIN")
                && !order.getCustomer().getUser().getId().equals(user.getId())) {
            throw new CustomException(HttpStatus.FORBIDDEN, "You can only access your own orders");
        }
    }

    private OrderResponse toResponse(OrderTb order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderDate(order.getOrderDate());
        response.setStatus(order.getStatus());
        response.setOrderType(order.getOrderType());
        response.setPaymentMethod(order.getPaymentMethod());
        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setDeliveryLatitude(order.getDeliveryLatitude());
        response.setDeliveryLongitude(order.getDeliveryLongitude());
        response.setDeliveryFee(roundMoney(order.getDeliveryFee() == null ? 0.0 : order.getDeliveryFee()));
        response.setDiscount(roundMoney(order.getDiscount() == null ? 0.0 : order.getDiscount()));
        response.setTotalAmount(roundMoney(order.getTotalAmount() == null ? 0.0 : order.getTotalAmount()));
        response.setCustomerId(order.getCustomer().getCustomerId());
        response.setSubtotal(calculateFoodSubtotal(order.getItems()));

        CustomerRewardResponse usedReward = rewardService.getRewardUsedForOrder(order.getOrderId());
        if (usedReward != null) {
            response.setRewardId(usedReward.getRewardId());
            response.setRewardTitle(usedReward.getTitle());
        }

        List<OrderItemResponse> itemResponses = new ArrayList<>();
        int totalItems = 0;
        for (ProductOrder item : order.getItems()) {
            OrderItemResponse itemResponse = new OrderItemResponse();
            itemResponse.setId(item.getId());
            itemResponse.setProductId(item.getProduct().getProductId());
            itemResponse.setName(item.getProduct().getProductName());
            itemResponse.setQuantity(item.getQuantity());
            itemResponse.setUnitPrice(roundMoney(item.getUnitPrice()));

            String size = item.getSelectedOptions().stream()
                    .filter(option -> option.getOptionType() == OptionType.SIZE)
                    .map(ProductOrderOption::getOptionName).findFirst().orElse(null);
            itemResponse.setSize(size);

            List<String> addOns = item.getSelectedOptions().stream()
                    .filter(option -> option.getOptionType() == OptionType.ADD_ON)
                    .map(ProductOrderOption::getOptionName).toList();
            itemResponse.setAddOns(addOns);

            double addOnTotal = item.getSelectedOptions().stream()
                    .filter(option -> option.getOptionType() == OptionType.ADD_ON)
                    .mapToDouble(option -> option.getExtraPrice() == null ? 0.0 : option.getExtraPrice()).sum();
            itemResponse.setSubtotal(roundMoney((item.getUnitPrice() + addOnTotal) * item.getQuantity()));
            itemResponses.add(itemResponse);
            totalItems += item.getQuantity();
        }
        response.setItems(itemResponses);
        response.setTotalItems(totalItems);
        return response;
    }

    private double roundMoney(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
