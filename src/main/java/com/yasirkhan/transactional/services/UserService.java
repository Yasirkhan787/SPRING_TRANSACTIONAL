package com.yasirkhan.transactional.services;

import com.yasirkhan.transactional.dtos.UserRequest;
import com.yasirkhan.transactional.dtos.UserResponse;
import com.yasirkhan.transactional.entities.User;
import com.yasirkhan.transactional.entities.UserContact;
import com.yasirkhan.transactional.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

//    @Transactional(propagation = Propagation.NOT_SUPPORTED )
    public void debit(Long userId, double amount) {

        // Retrieve the User
        User user = userRepository.findById(userId).orElseThrow();

        // Updating the balance
        user.setBalance(user.getBalance() - amount);

        userRepository.save(user);
        entityManager.flush();  // Ensure the update is sent to the DB

    }

//    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void credit(Long userId, double amount) {

        User user = userRepository.findById(userId).orElseThrow();
        user.setBalance(user.getBalance() + amount);
        userRepository.save(user);

        var r = 1/0;
        user.setBalance(user.getBalance() + amount);
        userRepository.save(user);
    }

    public UserResponse addUser(UserRequest request) {

        // Build the User first without the contacts
        User user = User
                .builder()
                .name(request.name())
                .balance(request.balance())
                .contacts(new ArrayList<>())
                .build();

        // Loop through the phone numbers and link BOTH sides
        for (String phoneNumber : request.contacts()) {
            UserContact userContact = new UserContact();
            userContact.setPhoneNumber(phoneNumber);

            // The contact must know who its user is so it can save the Foreign Key
            userContact.setUser(user);
            // Add the contact to the user's list
            user.getContacts().add(userContact);
        }

        // Save the User (CascadeType.ALL will automatically save the linked contacts)
        User saveUser = userRepository.save(user);

        return UserResponse
                .builder()
                .id(saveUser.getId())
                .name(saveUser.getName())
                .balance(saveUser.getBalance())
                .contacts(saveUser.getContacts())
                .build();
    }

    // Get User Data
    public List<UserResponse> getAllUsers() {

        List<User> users = userRepository.findAll();
        return users.stream().map((user) -> UserResponse
                .builder()
                .id(user.getId())
                .name(user.getName())
                .balance(user.getBalance())
                .contacts(user.getContacts())
                .build()
        ).toList();

    }

}
