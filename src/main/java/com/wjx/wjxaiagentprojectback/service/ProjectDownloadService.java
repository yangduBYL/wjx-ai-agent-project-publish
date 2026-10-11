package com.wjx.wjxaiagentprojectback.service;

import jakarta.servlet.http.HttpServletResponse;

public interface ProjectDownloadService {

    /**
     * 下载项目zip包
     * @param projectPath
     * @param downloadFileName
     * @param response
     */
    public void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
