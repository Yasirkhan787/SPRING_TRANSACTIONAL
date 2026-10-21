package com.yasirkhan.transactional.controllers;

import com.yasirkhan.transactional.dtos.UserRequest;
import com.yasirkhan.transactional.dtos.UserResponse;
import com.yasirkhan.transactional.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse> addUser(@RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.addUser(request));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }
}
