package com.yasirkhan.transactional.controllers;

import com.yasirkhan.transactional.services.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallet")
public class WalletController {

    @Autowired
    private WalletService walletService;

    @PostMapping("/transfer")
    public ResponseEntity<String> transfer(@RequestParam Long senderId, @RequestParam Long receiverId, @RequestParam double amount) {
        walletService.transfer(senderId, receiverId, amount);
        return ResponseEntity.ok("Transaction Successful");
    }
}
