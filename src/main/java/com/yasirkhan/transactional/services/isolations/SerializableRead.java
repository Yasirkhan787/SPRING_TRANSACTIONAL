package com.yasirkhan.transactional.services.isolations;

import com.yasirkhan.transactional.dtos.UserRequest;
import com.yasirkhan.transactional.services.UserServiceForSerializable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SerializableRead {

    @Autowired
    private UserServiceForSerializable userService;

    public void testSerializableRead(Long id) throws InterruptedException {
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
                Thread.sleep(1000); // Ensure Transaction A starts first
                userService.readBalanceMultiple(id);
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

    public void testPhantomReadSolution() throws InterruptedException {

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
                System.out.println("Error occurred in Thread A: " + e.getMessage());
            }
        });

        // Start Transaction B (Thread 2) to read the Users
        Thread threadB = new Thread(() -> {
            try {
                userService.getUserByBalanceGreaterThan(500);
            } catch (Exception e) {
                System.out.println("Error occurred in Thread B: " + e.getMessage());
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