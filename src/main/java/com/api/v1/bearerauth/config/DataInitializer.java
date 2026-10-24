//package com.api.v1.bearerauth.config;
//
//import com.api.v1.bearerauth.model.entity.AppUser;
//import com.api.v1.bearerauth.model.entity.Category;
//import com.api.v1.bearerauth.model.entity.Customer;
//import com.api.v1.bearerauth.model.entity.Product;
//import com.api.v1.bearerauth.model.enumerate.Roles;
//import com.api.v1.bearerauth.repository.AppUserRepository;
//import com.api.v1.bearerauth.repository.CategoryRepository;
//import com.api.v1.bearerauth.repository.ProductRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.annotation.Order;
//import org.springframework.security.crypto.password.PasswordEncoder;
//
//@Configuration
//@RequiredArgsConstructor
//public class DataInitializer {
//    @Bean
//    @Order(2)
//    CommandLineRunner seedData(AppUserRepository users, ProductRepository products, CategoryRepository categories, PasswordEncoder encoder) {
//        return args -> {
//            if (users.findByEmail("admin@foodie.com").isEmpty()) {
//                AppUser admin = new AppUser();
//                admin.setEmail("admin@foodie.com");
//                admin.setPassword(encoder.encode("Admin@12345"));
//                admin.setRole(Roles.ADMIN);
//                admin.setEnabled(true);
//
//                Customer customer = new Customer();
//                customer.setCustomerName("Foodie Admin");
//                customer.setAddress("Phnom Penh");
//                customer.setPhoneNumber("012345678");
//                customer.setUser(admin);
//                admin.setCustomer(customer);
//
//                users.save(admin);
//            }
//
//            Category burger = category(categories, "Burger");
//            Category pizza = category(categories, "Pizza");
//            Category combos = category(categories, "Combos");
//            Category drinks = category(categories, "Drinks");
//
//            if (products.count() == 0) {
//                add(products, "Classic Cheeseburger", "Flame Grilled Beef", 5.99, 4.8, "assets/images/burger.png", burger);
//                add(products, "Bacon Whopper", "Crispy Bacon & Cheddar", 6.80, 4.9, "assets/images/burger2.png", burger);
//                add(products, "Mushroom Swiss Burger", "Sauteed Mushrooms", 6.25, 4.6, "assets/images/burger3.png", burger);
//                add(products, "Spicy Zinger Burger", "Crispy Fried Chicken", 5.50, 4.7, "assets/images/burger4.png", burger);
//                add(products, "Double Smash Burger", "Caramelized Onions", 7.50, 4.9, "assets/images/burger5.png", burger);
//                add(products, "Pepperoni Supreme", "Mozzarella & Oregano", 11.99, 4.9, "assets/images/pizza.png", pizza);
//                add(products, "Margherita Delight", "Fresh Basil & Tomato", 9.50, 4.5, "assets/images/pizza2.jpg", pizza);
//                add(products, "BBQ Chicken Pizza", "Smoky BBQ & Red Onion", 12.50, 4.8, "assets/images/pizza3.jpg", pizza);
//                add(products, "Four Cheese Feast", "Parmesan, Cheddar, Mozz", 10.99, 4.7, "assets/images/pizza4.jpg", pizza);
//                add(products, "Hawaiian Special", "Ham & Sweet Pineapple", 10.50, 4.3, "assets/images/pizza5.jpg", pizza);
//                add(products, "Family Feast Combo", "2 Burgers, 2 Drinks, Fries", 18.99, 4.9, "assets/images/combo1.jpg", combos);
//                add(products, "Solo Meal Deal", "1 Burger, Fries & Soda", 8.99, 4.7, "assets/images/combo2.jpg", combos);
//                add(products, "Pizza & Wings Combo", "Medium Pizza + 6 Wings", 16.50, 4.8, "assets/images/combo3.jpg", combos);
//                add(products, "Double Trouble Pack", "2 Smash Burgers & Shake", 14.20, 4.6, "assets/images/combo4.jpg", combos);
//                add(products, "Iced Vanilla Latte", "Cold Brewed Coffee", 3.50, 4.6, "assets/images/drink1.jpg", drinks);
//                add(products, "Fresh Mango Smoothie", "100% Real Fruit", 4.25, 4.8, "assets/images/drink2.jpg", drinks);
//                add(products, "Chocolate Milkshake", "Rich Creamy Fudge", 4.50, 4.9, "assets/images/drink3.jpg", drinks);
//                add(products, "Classic Lemonade", "Freshly Squeezed", 2.99, 4.4, "assets/images/drink4.jpg", drinks);
//            }
//        };
//    }
//
//    private Category category(CategoryRepository repository, String name) {
//        return repository.findByNameIgnoreCase(name).orElseGet(() -> {
//            Category category = new Category();
//            category.setName(name);
//            return repository.save(category);
//        });
//    }
//
//    private void add(ProductRepository repository, String name, String description, double price, double rating, String image, Category category) {
//        Product product = new Product();
//        product.setProductName(name);
//        product.setDescription(description);
//        product.setUnitPrice(price);
//        product.setRating(rating);
//        product.setImageUrl(image);
//        product.setCategory(category);
//        repository.save(product);
//    }
//}
