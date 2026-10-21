package com.yasirkhan.transactional.services;

import com.yasirkhan.transactional.dtos.UserRequest;
import com.yasirkhan.transactional.entities.User;
import com.yasirkhan.transactional.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserServiceForSerializable {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserService userService;

    // Transaction A: Update balance
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void updateUserBalance(Long userId, double amount) throws InterruptedException {

        double balance = debit(userId, amount);

        // Simulate a long-running transaction (does not commit yet)
        System.out.println("Transaction A: Balance updated to " + balance);
        Thread.sleep(5000);
        System.out.println("Transaction A: Committed the update");
    }

    // Transaction B: Reading balance
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public double readBalance(Long userId) {

        User user = userRepository
                .findById(userId)
                .orElseThrow();

        System.out.println("Transaction B: Read balance as " + user.getBalance());
        return user.getBalance();

    }

    // Transaction B: Read balance multiple times
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void readBalanceMultiple(Long userId) {

        // First Read
        User user = userRepository
                .findById(userId)
                .orElseThrow();

        System.out.println("Transaction B: First Read balance as " + user.getBalance());

        // Simulate a delay to allow Transaction A to update the balance
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // CLEAR THE L1 CACHE: Force Hibernate to hit the database again
        entityManager.clear();

        // Second Read
        User user2 = userRepository
                .findById(userId)
                .orElseThrow();

        System.out.println("Transaction B: Second Read balance as " + user2.getBalance());
    }

    // Solution of phantom read problem
    // Transaction A: Creating new user with balance greater then 500
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void addUser(UserRequest request) {
        userService.addUser(request);
        System.out.println("Transaction A: Committed the update");
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void getUserByBalanceGreaterThan(double balance) {

        // First Read
        List<User> user = getUser(balance);
        System.out.println("Transaction B: First Read - Total User: "+ user.size());

        // Simulate a delay to allow Transaction A to update the balance
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // CLEAR THE L1 CACHE: Force Hibernate to hit the database again
        entityManager.clear();

        // Second Read
        List<User> user2 = getUser(balance);
        System.out.println("Transaction B: Second Read - Total User: "+ user2.size());

    }

    public double debit(Long userId, double amount) {

        // Retrieve the User
        User user = userRepository.findById(userId).orElseThrow();

        // Updating the balance
        user.setBalance(user.getBalance() - amount);

        User savedUser = userRepository.save(user);
        entityManager.flush();  // Ensure the update is sent to the DB

        return savedUser.getBalance();
    }

    public List<User> getUser(double balance) {
        return userRepository.findByBalanceGreaterThan(balance);
    }

}