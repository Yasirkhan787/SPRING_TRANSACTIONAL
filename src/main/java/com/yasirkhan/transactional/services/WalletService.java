package com.yasirkhan.transactional.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

@Service
public class WalletService {

    @Autowired
    private UserService userService;

    @Transactional
    public void transfer(Long senderId, Long receiverId, double amount) {
        userService.debit(senderId, amount);
        userService.credit(receiverId, amount);
    }
}
