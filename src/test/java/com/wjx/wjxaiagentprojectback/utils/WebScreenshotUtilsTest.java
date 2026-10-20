package com.wjx.wjxaiagentprojectback.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
public class WebScreenshotUtilsTest {

    @Test
    void saveWebPageScreenshot() {
        String testUrl = "https://www.bilibili.com/";
        // WebScreenshotUtils 是 Spring Bean，这里直接 new 一个实例做单测（无状态依赖）
        String webPageScreenshot = new WebScreenshotUtils().saveWebPageScreenshot(testUrl);
        Assertions.assertNotNull(webPageScreenshot);
    }
}
