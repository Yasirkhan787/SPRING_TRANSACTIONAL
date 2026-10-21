package com.yasirkhan.transactional.services;

import com.yasirkhan.transactional.entities.User;
import com.yasirkhan.transactional.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Service
public class UserServiceForReadUnCommitted {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    // Transaction A: Update balance
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public void updateUserBalance(Long userId, double amount) throws InterruptedException {

        double balance = debit(userId, amount);

        // Simulate a long-running transaction (does not commit yet)
        System.out.println("Transaction A: Balance updated to " + balance);

        Thread.sleep(5000);

         System.out.println("Transaction A: Rolling back the update");
         TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); //explicit rollback

        //System.out.println("Transaction A: Committed the update");
    }

    // Solves No Problem, We used whenever our application have only read operation. Write operation cause dirty reads
    // Transaction B: Reading balance
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public double readBalance(Long userId) {

        // Retrieve the user and read its balance (potentially dirty read)
        User user = userRepository
                .findById(userId)
                .orElseThrow();

        System.out.println("Transaction B: Read balance as " + user.getBalance());
        return user.getBalance();

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
