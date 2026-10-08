package com.wjx.wjxaiagentprojectback.controller;

import com.wjx.wjxaiagentprojectback.code.AiCodeGeneratorFacade;
import com.wjx.wjxaiagentprojectback.common.BaseResponse;
import com.wjx.wjxaiagentprojectback.common.ResultUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/health")
public class health {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @GetMapping
    public BaseResponse<String> health(){

        return ResultUtils.success("health");
    }

}
