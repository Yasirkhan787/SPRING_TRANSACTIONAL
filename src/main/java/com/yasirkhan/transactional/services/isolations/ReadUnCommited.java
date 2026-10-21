package com.yasirkhan.transactional.services.isolations;

import com.yasirkhan.transactional.services.UserService;
import com.yasirkhan.transactional.services.UserServiceForReadUnCommitted;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReadUnCommited {

    @Autowired
    private UserServiceForReadUnCommitted userService;

    public void testReadUnCommited(Long id) throws InterruptedException {
        // Start Transaction A (Thread 1) to update the balance but not commit, then roll back
        Thread threadA = new Thread(() -> {
            try {
                userService.updateUserBalance(id, 500); // Change the balance
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Start Transaction B (Thread 2) to read the balance
        Thread threadB = new Thread(() -> {
            try {
                Thread.sleep(2000);  // Wait a moment to ensure Thread A starts and holds the transaction
                double balance = userService.readBalance(id);// Read balance during Transaction A
                System.out.println("Balance read by Transaction B: " + balance);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
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
