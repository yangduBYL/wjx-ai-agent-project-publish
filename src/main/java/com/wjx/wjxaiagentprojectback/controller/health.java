package com.wjx.wjxaiagentprojectback.controller;

import com.wjx.wjxaiagentprojectback.common.BaseResponse;
import com.wjx.wjxaiagentprojectback.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class health {
    @GetMapping
    public BaseResponse<String> health(){
        return ResultUtils.success("health");
    }
}
