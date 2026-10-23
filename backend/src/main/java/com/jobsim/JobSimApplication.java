package com.jobsim;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * JobSim AI 后端启动类。
 * MapperScan 扫描所有 MyBatis-Plus Mapper 接口，避免在每个 Mapper 上重复加注解。
 */
@SpringBootApplication
@MapperScan("com.jobsim.mapper")
public class JobSimApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobSimApplication.class, args);
    }
}
