package com.yasirkhan.transactional.controllers;

import com.yasirkhan.transactional.services.isolations.ReadCommited;
import com.yasirkhan.transactional.services.isolations.ReadUnCommited;
import com.yasirkhan.transactional.services.isolations.RepeatableRead;
import com.yasirkhan.transactional.services.isolations.SerializableRead;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/isolation")
public class IsolationController {

    @Autowired
    private ReadUnCommited readUnCommited;

    @Autowired
    private ReadCommited readCommited;

    @Autowired
    private RepeatableRead repeatableRead;

    @Autowired
    private SerializableRead serializable;

    @GetMapping("/readUnCommit")
    public String testReadUnCommitIsolation() throws InterruptedException {
        readUnCommited.testReadUnCommited(2L);
        return "Success Read Un-Commit";
    }

    @GetMapping("/readCommit")
    public String testReadCommitIsolation() throws InterruptedException {
        readCommited.testReadCommited(2L);
        return "Success Read Commit";
    }

    @GetMapping("/repeatableRead")
    public String testRepeatableReadIsolation() throws InterruptedException {
        repeatableRead.testRepeatableRead(2L);
        return "Success Repeatable Read";
    }

    @GetMapping("/serializable")
    public String testSerializableIsolation() throws InterruptedException {
        serializable.testSerializableRead(2L);
        return "Success Serializable Read";
    }

    @GetMapping("/phantomProblem")
    public String testPhantomProblem() throws InterruptedException {
        repeatableRead.testPhantomReadProblem();
        return "Success Phantom Problem Created";
    }

    @GetMapping("/phantomSolution")
    public String testPhantomSolution() throws InterruptedException {
        serializable.testPhantomReadSolution();
        return "Success Phantom Read Solution";
    }

}
