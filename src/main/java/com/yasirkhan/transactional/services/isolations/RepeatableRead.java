package com.yasirkhan.transactional.services.isolations;

import com.yasirkhan.transactional.dtos.UserRequest;
import com.yasirkhan.transactional.services.UserService;
import com.yasirkhan.transactional.services.UserServiceForRepeatableRead;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.springframework.data.jpa.domain.AbstractPersistable_.id;

@Service
public class RepeatableRead {

    @Autowired
    private UserServiceForRepeatableRead userService;

    public void testRepeatableRead(Long id) throws InterruptedException {
        // Start Transaction A (Thread 1) to update the balance
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

    public void testPhantomReadProblem() throws InterruptedException {

        // Start Transaction A (Thread 1) to update the Users
        Thread threadA = new Thread(() -> {
            try {
                Thread.sleep(500); // Wait half a second so Thread B's 1st read finishes first
                userService.addUser(
                        UserRequest
                                .builder()
                                .name("New User by Transaction A")
                                .balance(1000.0)
                                .contacts(List.of("12345678910"))
                                .build()
                );
            } catch (Exception e) {
                System.out.println("Error occurred in Thread A");;
            }
        });

        // Start Transaction B (Thread 2) to read the Users
        Thread threadB = new Thread(() -> {
            try {
                userService.getUserByBalanceGreaterThan(500);
            } catch (Exception e) {
                System.out.println("Error occurred in Thread A");;
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
