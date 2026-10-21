package com.yasirkhan.transactional.configs;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;

@Configuration
public class TransactionManagerConfig {

    @Bean("transactionManager")
    public PlatformTransactionManager platformTransactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory) {

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
                super.doBegin(transaction, definition);
                System.out.println("Transaction Logger: New Transaction Started (propagation: )" + definition.getPropagationBehavior()+ "name: " + definition.getName());
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                super.doCommit(status);
                System.out.println("Transaction Logger: Transaction Commited (name: " + status.getTransactionName());
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                super.doRollback(status);
                System.out.println("Transaction Logger: Transaction Rollbacker (name: " + status.getTransactionName());
            }
        };
    }
}
