package com.wjx.wjxaiagentprojectback;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableCaching
@SpringBootApplication()
@EnableAspectJAutoProxy//开启切面
@MapperScan("com.wjx.wjxaiagentprojectback.mapper")
public class WjxAiAgentProjectBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(WjxAiAgentProjectBackApplication.class, args);
    }
}
