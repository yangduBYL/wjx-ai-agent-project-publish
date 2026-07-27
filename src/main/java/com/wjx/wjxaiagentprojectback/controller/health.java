package com.wjx.wjxaiagentprojectback.controller;

import com.wjx.wjxaiagentprojectback.ai.AiChatService;
import com.wjx.wjxaiagentprojectback.code.AiCodeGeneratorFactory;
import com.wjx.wjxaiagentprojectback.common.BaseResponse;
import com.wjx.wjxaiagentprojectback.common.ResultUtils;
import com.wjx.wjxaiagentprojectback.damon.enums.CodeTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/health")
public class health {

    @Resource
    private AiCodeGeneratorFactory aiCodeGeneratorFactory;
    @GetMapping
    public BaseResponse<String> health(){

        return ResultUtils.success("health");
    }

}
