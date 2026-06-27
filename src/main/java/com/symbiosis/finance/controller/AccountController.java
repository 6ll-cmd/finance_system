package com.symbiosis.finance.controller;

import com.symbiosis.finance.entity.Account;
import com.symbiosis.finance.mapper.AccountMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final AccountMapper accountMapper;

    public AccountController(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @GetMapping("/accounts")
    public List<Account> list() {
        return accountMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Account>()
                        .orderByAsc(Account::getId));
    }
}
