package com.wjx.wjxaiagentprojectback.service;


/**
 * 主要处理manager层下的图片cos上传
 */
public interface ScreenshotService {

    public String generateAndUploadScreenshot(String webUrl);
}
