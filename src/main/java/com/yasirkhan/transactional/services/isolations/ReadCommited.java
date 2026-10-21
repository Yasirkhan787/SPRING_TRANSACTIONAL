package com.yasirkhan.transactional.services.isolations;

import com.yasirkhan.transactional.services.UserServiceForReadCommitted;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReadCommited {

    @Autowired
    private UserServiceForReadCommitted userService;

    public void testReadCommited(Long id) throws InterruptedException {
        // Start Transaction A (Thread 1) to update the balance but not commit, then roll back
        // 1000.0 -> 500.0
        Thread threadA = new Thread(() -> {
            try {
                userService.updateUserBalance(id, 500); // Change the balance to 500.0
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Start Transaction B (Thread 2) to read the balance
        // 1000.0
        Thread threadB = new Thread(() -> {
            try {

                Thread.sleep(2000);  // Wait a moment to ensure Thread A starts and holds the transaction

                // double balance = userService.readBalance(id); // Read balance during Transaction A
                // System.out.println("Balance read by Transaction B: " + balance);  // Read stock before and after Transaction A's update

                // This Method caused non_repeatable read problem
                userService.readBalanceMultiple(id); // Read balance before and after Transaction A's update
            } catch (Exception e) {
                System.out.println("Transaction B: Exception occurred: " + e.getMessage());
            }
        });

        // Start the threads
        threadA.start();
        threadB.start();

        // Wait for threads to complete
        threadA.join();
        threadB.join();
    }

}
