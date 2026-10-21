package com.yasirkhan.transactional.services;

import com.yasirkhan.transactional.entities.User;
import com.yasirkhan.transactional.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceForReadCommitted {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    // Transaction A: Update balance
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateUserBalance(Long userId, double amount) throws InterruptedException {

        double balance = debit(userId, amount);

        // Simulate a long-running transaction (does not commit yet)
        System.out.println("Transaction A: Balance updated to " + balance);
        Thread.sleep(5000);

        // System.out.println("Transaction A: Rolling back the update");
        // TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); //explicit rollback
        System.out.println("Transaction A: Committed the update");
    }

    // Solves Dirty Read Problem
    // Transaction B: Reading balance
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public double readBalance(Long userId) {

        // Retrieve the user and read its balance (potentially dirty read)
        User user = userRepository
                .findById(userId)
                .orElseThrow();

        System.out.println("Transaction B: Read balance as " + user.getBalance());
        return user.getBalance();

    }

    // non-repeatable problem occurred when multiple reads happened
    // Transaction B: Read balance multiple times
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void readBalanceMultiple(Long userId) {

        // First Read
        User user = userRepository
                .findById(userId)
                .orElseThrow();

        System.out.println("Transaction B: First Read balance as " + user.getBalance());

        // Simulate a delay to allow Transaction A to update the stock
        try {
            // Increase to 4000ms. Thread B started 2s late, plus 4s = 6s total.
            // This guarantees Thread A (which sleeps for 5s) has fully committed.
            Thread.sleep(4000);
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

    public double debit(Long userId, double amount) {

        // Retrieve the User
        User user = userRepository.findById(userId).orElseThrow();

        // Updating the balance
        user.setBalance(user.getBalance() - amount);

        User savedUser = userRepository.save(user);
        entityManager.flush();  // Ensure the update is sent to the DB

        return savedUser.getBalance();

    }

}
